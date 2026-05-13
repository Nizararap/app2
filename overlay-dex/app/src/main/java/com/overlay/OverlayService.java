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

    private boolean isOverlayShown = false;
    private boolean isLoginShown = false;
    
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

// Validasi ulang jika ada key tersimpan, jika tidak langsung login
        String savedKey = authManager.getPlainKey();
        if (savedKey != null) {
            // Validasi ulang ke server (sekali saat startup)
            authManager.validateKey(savedKey, new KeyAuthManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    startConnectionChecker();
                }
                @Override
                public void onFailure(String reason) {
                    if (reason.startsWith("NET_ERROR")) {
                        // JANGAN logout jika hanya jaringan lambat saat virtual space baru dibuka
                        if (authManager.isKeyValid()) {
                            // Masuk menggunakan sisa waktu lokal (Offline Grace Period)
                            startConnectionChecker(); 
                        } else {
                            authManager.logout();
                            showLoginUI();
                        }
                    } else {
                        // Key terbukti expired atau dihapus dari github
                        authManager.logout();
                        showLoginUI();
                        Toast.makeText(OverlayService.this, reason, Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            showLoginUI();
        }
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
            // Setelah login sukses, tidak perlu validasi lagi, langsung start connection
            startConnectionChecker();
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
                    // Jika C++ hidup, munculkan overlay
                    if (!isOverlayShown) {
                        showOverlayUI();
                    }
                } else {
                    // Jika C++ mati (game ditutup), hancurkan overlay (mencegah zombie/double)
                    if (isOverlayShown) {
                        hideOverlayUI();
                    }
                }
                // SELALU ulangi pengecekan setiap 2 detik sebagai detak jantung (Heartbeat)
                handler.postDelayed(this, RETRY_DELAY_MS);
            }
        };
        handler.post(connectionChecker);
    }

    private boolean tryConnectToNative() {
        try {
            LocalSocket socket = new LocalSocket();
            socket.connect(new LocalSocketAddress("and.sys.audio.config", LocalSocketAddress.Namespace.ABSTRACT));
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

        // --- PERBAIKAN RADAR BORDER MISMATCH (Ada di bawah) ---
        WindowManager.LayoutParams radarParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        
        // Izinkan Radar tembus area Poni (Notch) layar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            radarParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

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
        // PENTING: Dihapus pemanggilan startConnectionChecker() dari sini
        // karena looping sudah di-handle penuh di atas
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
        hideOverlayUI();
        hideLoginUI();
    }
}