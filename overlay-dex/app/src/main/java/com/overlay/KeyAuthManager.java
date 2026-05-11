package com.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class KeyAuthManager {
    private static final String PREF_NAME = "v_auth";
    private static final String KEY_SAVED_KEY = "sk";
    private static final String KEY_EXPIRY = "ex";

    private static final String KEY_DB_URL = "https://raw.githubusercontent.com/Nizararap/Internal-keys/refs/heads/main/keys.txt";

    // Kunci enkripsi sederhana (bisa diganti dengan hash dari package name + salt)
    private static final byte[] ENC_KEY = "v1pK3y#2026!Sec".getBytes();

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

// Simpan key dan expiry terenkripsi dalam satu kesatuan
    public void saveEncryptedData(String plainKey, long expirySec) {
        try {
            String data = plainKey + "|" + expirySec;
            String encrypted = encrypt(data);
            prefs.edit().putString(KEY_SAVED_KEY, encrypted).apply();
        } catch (Exception ignored) {}
    }

    // Ambil array data [0] = plainKey, [1] = expirySec
    private String[] getDecryptedData() {
        try {
            String encrypted = prefs.getString(KEY_SAVED_KEY, null);
            if (encrypted == null) return null;
            String decrypted = decrypt(encrypted);
            return decrypted.split("\\|");
        } catch (Exception e) {
            return null;
        }
    }

    public String getPlainKey() {
        String[] data = getDecryptedData();
        return (data != null && data.length == 2) ? data[0] : null;
    }

    public long getExpiryTimestamp() {
        String[] data = getDecryptedData();
        if (data != null && data.length == 2) {
            try { return Long.parseLong(data[1]); } catch (Exception e) {}
        }
        return 0;
    }

    public boolean isKeyValid() {
        long expiry = getExpiryTimestamp();
        return (System.currentTimeMillis() / 1000) < expiry;
    }

    public long getRemainingTime() {
        long expiry = getExpiryTimestamp();
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
                // Matikan cache agar mendapat waktu server absolut (mencegah time spoofing)
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    final int code = responseCode;
                    mainHandler.post(() -> callback.onFailure("Server error: " + code));
                    return;
                }

                // AMBIL WAKTU SERVER (Bypass perlindungan ganti tanggal HP saat login)
                long serverTimeSec = conn.getHeaderFieldDate("Date", System.currentTimeMillis()) / 1000;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                boolean found = false;
                long expiry = 0;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split(":");
                    if (parts.length == 2 && parts[0].equals(hashedKey)) {
                        expiry = Long.parseLong(parts[1]);
                        found = true;
                        break;
                    }
                }
                reader.close();

                if (found) {
                    if (serverTimeSec < expiry) {
                        // Enkripsi dan simpan keduanya
                        saveEncryptedData(userKey, expiry);
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
        SharedPreferences.Editor editor = modPrefs.edit();
        editor.putBoolean("radar_enable", false);
        editor.putBoolean("aimbot_enable", false);
        editor.apply();
    }

    // ─── Enkripsi sederhana (AES) ───
    private String encrypt(String text) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(ENC_KEY, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(text.getBytes("UTF-8"));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String decrypt(String encryptedBase64) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(ENC_KEY, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP);
        return new String(cipher.doFinal(decoded), "UTF-8");
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