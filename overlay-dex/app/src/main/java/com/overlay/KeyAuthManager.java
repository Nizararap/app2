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
import java.util.Iterator;
import org.json.JSONObject;

public class KeyAuthManager {
    private static final String PREF_NAME = "vip_auth_prefs";
    private static final String KEY_SAVED_KEY = "saved_vip_key";
    private static final String KEY_EXPIRY = "key_expiry_time";
    
    // URL dari Github User
    private static final String KEY_DB_URL = "https://raw.githubusercontent.com/Nizararap/Internal-keys/refs/heads/main/keys.json";
    
    private final SharedPreferences prefs;
    private final SharedPreferences modPrefs;
    private final Handler mainHandler;

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String reason);
    }

    public KeyAuthManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.modPrefs = context.getSharedPreferences("mod_settings", Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public boolean isKeyValid() {
        long expiry = prefs.getLong(KEY_EXPIRY, 0);
        return System.currentTimeMillis() / 1000 < expiry;
    }

    public long getRemainingTime() {
        long expiry = prefs.getLong(KEY_EXPIRY, 0);
        return Math.max(0, expiry - (System.currentTimeMillis() / 1000));
    }

    public void validateKey(final String userKey, final AuthCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String hashedKey = sha256(userKey);
                URL url = new URL(KEY_DB_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    final int code = responseCode;
                    mainHandler.post(() -> callback.onFailure("Server error: " + code));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                // Format JSON di github: { "hash1": expiry1, "hash2": expiry2 }
                JSONObject json = new JSONObject(sb.toString());
                if (json.has(hashedKey)) {
                    long expiry = json.getLong(hashedKey);
                    long now = System.currentTimeMillis() / 1000;

                    if (now < expiry) {
                        prefs.edit()
                            .putString(KEY_SAVED_KEY, userKey)
                            .putLong(KEY_EXPIRY, expiry)
                            .apply();
                        mainHandler.post(callback::onSuccess);
                    } else {
                        mainHandler.post(() -> callback.onFailure("Key sudah expired!"));
                    }
                } else {
                    mainHandler.post(() -> callback.onFailure("Key tidak valid!"));
                }
            } catch (Exception e) {
                final String msg = e.getMessage();
                mainHandler.post(() -> callback.onFailure("Koneksi gagal: " + msg));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void logout() {
        prefs.edit().clear().apply();
        // Matikan semua fitur di mod_settings
        SharedPreferences.Editor editor = modPrefs.edit();
        editor.putBoolean("radar_enable", false);
        editor.putBoolean("aimbot_enable", false);
        // Tambahkan key lain jika ada
        editor.apply();
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
