package com.overlay;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private OverlayView overlayView;
    private RadarView radarView;
    private LoginView loginView;
    private KeyAuthManager authManager;

    private Handler handler;
    private Runnable connectionChecker;
    private Handler securityHandler = new Handler(Looper.getMainLooper());
    private Runnable sessionMonitor;

    private boolean isOverlayShown = false;
    private boolean isLoginShown = false;
    
    private static final long SESSION_LIMIT = 5 * 60 * 60 * 1000; // 5 Jam
    private static final int RETRY_DELAY_MS = 2000;

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        authManager = new KeyAuthManager(this);

        checkInitialSession();
    }

    private void checkInitialSession() {
        if (isSessionValid()) {
            startConnectionChecker();
            startSessionMonitor();
        } else {
            showLoginUI();
        }
    }

    private boolean isSessionValid() {
        if (!SecureSession.isSessionFileExists(this)) return false;
        long timestamp = SecureSession.getSessionTimestamp(this);
        return (System.currentTimeMillis() - timestamp) < SESSION_LIMIT;
    }

    private void startSessionMonitor() {
        if (sessionMonitor != null) securityHandler.removeCallbacks(sessionMonitor);
        sessionMonitor = new Runnable() {
            @Override
            public void run() {
                if (!isSessionValid()) {
                    forceLogout("Sesi berakhir atau file keamanan dihapus!");
                } else {
                    securityHandler.postDelayed(this, 30000); // Cek tiap 30 detik
                }
            }
        };
        securityHandler.post(sessionMonitor);
    }

    private void forceLogout(String reason) {
        authManager.logout();
        hideOverlayUI();
        showLoginUI();
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
    }

    private void showLoginUI() {
        if (isLoginShown) return;
        
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.CENTER;

        loginView = new LoginView(this, windowManager, lp, () -> {
            hideLoginUI();
            checkInitialSession();
        });

        try {
            windowManager.addView(loginView, lp);
            isLoginShown = true;
        } catch (Exception ignored) {}
    }

    private void hideLoginUI() {
        if (isLoginShown && loginView != null) {
            try { windowManager.removeView(loginView); } catch (Exception ignored) {}
            isLoginShown = false;
            loginView = null;
        }
    }

    private void startConnectionChecker() {
        if (connectionChecker != null) handler.removeCallbacks(connectionChecker);
        connectionChecker = new Runnable() {
            @Override
            public void run() {
                if (tryConnectToNative()) {
                    // Setiap kali konek, validasi repo dulu
                    validateRepoAndShow();
                } else {
                    handler.postDelayed(this, RETRY_DELAY_MS);
                }
            }
        };
        handler.post(connectionChecker);
    }

    private void validateRepoAndShow() {
        String key = SecureSession.getSessionKey(this);
        if (key == null) {
            forceLogout("Key tidak ditemukan!");
            return;
        }

        authManager.validateKey(key, new KeyAuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                if (!isOverlayShown) showOverlayUI();
            }

            @Override
            public void onFailure(String reason) {
                forceLogout(reason);
            }
        });
    }

    private boolean tryConnectToNative() {
        try {
            LocalSocket socket = new LocalSocket();
            socket.connect(new LocalSocketAddress("mlbb_config_socket", LocalSocketAddress.Namespace.ABSTRACT));
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showOverlayUI() {
        if (isOverlayShown) return;

        isOverlayShown = true;
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // Radar
        WindowManager.LayoutParams radarParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
        );
        radarView = new RadarView(this);
        windowManager.addView(radarView, radarParams);

        // Menu
        WindowManager.LayoutParams menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;
        overlayView = new OverlayView(this, windowManager, menuParams, radarView);
        windowManager.addView(overlayView, menuParams);
    }

    private void hideOverlayUI() {
        isOverlayShown = false;
        if (overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
        if (radarView != null) {
            try {
                radarView.destroy();
                windowManager.removeView(radarView);
            } catch (Exception ignored) {}
            radarView = null;
        }
        startConnectionChecker();
    }

    @Override
    public int onStartCommand(Intent i, int f, int s) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(connectionChecker);
        securityHandler.removeCallbacks(sessionMonitor);
        hideOverlayUI();
        hideLoginUI();
    }
}
