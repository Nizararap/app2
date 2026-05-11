package com.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class KeyAuthManager {
    private static final String PREF_NAME = "vip_auth_prefs";
    private static final String KEY_DB_URL = "https://raw.githubusercontent.com/Nizararap/Internal-keys/refs/heads/main/keys.txt";
    
    private final Context context;
    private final SharedPreferences modPrefs;
    private final Handler mainHandler;

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String reason);
    }

    public KeyAuthManager(Context context) {
        this.context = context;
        this.modPrefs = context.getSharedPreferences("mod_settings", Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void validateKey(final String userKey, final AuthCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String hashedKey = sha256(userKey);
                URL url = new URL(KEY_DB_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(false);

                if (conn.getResponseCode() != 200) {
                    mainHandler.post(() -> callback.onFailure("Server Error: " + conn.getConnectTimeout()));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                boolean found = false;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().equals(hashedKey)) {
                        found = true;
                        break;
                    }
                }
                reader.close();

                if (found) {
                    // Simpan ke file terenkripsi
                    SecureSession.saveSession(context, userKey, System.currentTimeMillis());
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onFailure("Key tidak valid atau sudah expired!"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure("Koneksi gagal: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void logout() {
        SecureSession.clearSession(context);
        modPrefs.edit().clear().apply();
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
