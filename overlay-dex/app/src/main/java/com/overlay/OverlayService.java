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

public class OverlayService extends Service {
    private WindowManager windowManager;
    private OverlayView overlayView;
    private RadarView radarView;

    private Handler handler;
    private Runnable connectionChecker;
    private boolean isOverlayShown = false;
    private boolean isLoginShown = false;
    private LoginView loginView;
    private KeyAuthManager authManager;
    private Thread monitorThread;
    private Handler expiryHandler = new Handler(Looper.getMainLooper());
    private Runnable expiryChecker;

    private static final int RETRY_DELAY_MS = 1500;
    private static final int MONITOR_INTERVAL_MS = 3000;

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

        checkAuthAndStart();
    }

    private void checkAuthAndStart() {
        if (authManager.isKeyValid()) {
            startConnectionChecker();
            startExpiryMonitor();
        } else {
            showLoginUI();
        }
    }

    private void showLoginUI() {
        if (isLoginShown) return;
        isLoginShown = true;

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams loginParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        loginParams.gravity = Gravity.CENTER;

        loginView = new LoginView(this, windowManager, loginParams, () -> {
            hideLoginUI();
            checkAuthAndStart();
        });

        windowManager.addView(loginView, loginParams);
    }

    private void hideLoginUI() {
        if (isLoginShown && loginView != null) {
            try {
                windowManager.removeView(loginView);
            } catch (Exception ignored) {}
            isLoginShown = false;
            loginView = null;
        }
    }

    private void startExpiryMonitor() {
        if (expiryChecker != null) expiryHandler.removeCallbacks(expiryChecker);
        
        expiryChecker = new Runnable() {
            @Override
            public void run() {
                if (!authManager.isKeyValid()) {
                    // Key Expired!
                    authManager.logout();
                    hideOverlayUI();
                    showLoginUI();
                    android.widget.Toast.makeText(OverlayService.this, "VIP Key Expired!", android.widget.Toast.LENGTH_LONG).show();
                } else {
                    // Cek setiap 30 detik
                    expiryHandler.postDelayed(this, 30000);
                }
            }
        };
        expiryHandler.post(expiryChecker);
    }

    private void startConnectionChecker() {
        connectionChecker = new Runnable() {
            @Override
            public void run() {
                if (tryConnectToNative()) {
                    if (!isOverlayShown) {
                        showOverlayUI();
                        startConnectionMonitor();
                    }
                } else {
                    // Gagal, coba lagi nanti (tanpa menampilkan apapun)
                    handler.postDelayed(this, RETRY_DELAY_MS);
                }
            }
        };
        handler.post(connectionChecker);
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
        // Mencegah menu terbuat 2 kali (Double Window)
        if (isOverlayShown) return; 

        // Bersihkan view lama yang mungkin tersangkut oleh Virtual Space
        if (overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        if (radarView != null) {
            try { windowManager.removeView(radarView); } catch (Exception ignored) {}
        }

        isOverlayShown = true;
        handler.removeCallbacks(connectionChecker);

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
        menuParams.x = 0;
        menuParams.y = 0;
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
        // Mulai reconnect di background
        startConnectionChecker();
    }

    private void startConnectionMonitor() {
        monitorThread = new Thread(() -> {
            while (isOverlayShown) {
                try {
                    Thread.sleep(MONITOR_INTERVAL_MS);
                    if (!tryConnectToNative()) {
                        handler.post(this::hideOverlayUI);
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitorThread.start();
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
        expiryHandler.removeCallbacks(expiryChecker);
        if (monitorThread != null) monitorThread.interrupt();
        hideOverlayUI();
        hideLoginUI();
    }
}