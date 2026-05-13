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

   /* private static final String KEY_DB_URL = "https://raw.githubusercontent.com/Nizararap/Internal-keys/refs/heads/main/keys.txt";

// PERBAIKAN: Ditambah huruf "X" di belakang agar genap 16 Byte!
    private static final byte[] ENC_KEY = "v1pK3y#2026!SecX".getBytes();*/
  // --- ANTI-DUMP STRING OBFUSCATION ---
  
    private static final int[] URL_DATA = {35, 63, 63, 59, 56, 113, 100, 100, 41, 42, 44, 101, 28, 34, 63, 35, 62, 37, 62, 56, 30, 41, 40, 36, 37, 63, 30, 37, 63, 101, 40, 36, 38, 100, 5, 34, 49, 42, 41, 42, 41, 42, 59, 100, 2, 37, 63, 30, 41, 37, 42, 39, 102, 32, 30, 50, 56, 100, 41, 30, 29, 56, 100, 35, 30, 42, 31, 56, 100, 38, 42, 34, 37, 100, 32, 30, 50, 56, 101, 63, 51, 63};
    private static final int[] KEY_DATA = {45, 26, 59, 0, 24, 50, 104, 25, 27, 25, 21, 106, 24, 30, 40, 19};

    // Fungsi rahasia untuk menerjemahkan angka jadi Teks saat Runtime
    private static String decode(int[] input) {
        byte[] result = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = (byte) (input[i] ^ 0x4B); // XOR Key 75
        }
        return new String(result);
    }

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

    // Ambil array data [0] = plainKey,[1] = expirySec
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
                URL url = new URL(decode(URL_DATA)); // <--- BACA DARI ARRAY RAHASIA
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    mainHandler.post(() -> callback.onFailure("NET_ERROR: Server error " + responseCode));
                    return;
                }

                // Ambil waktu server absolut (Anti ubah tanggal HP)
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
                        saveEncryptedData(userKey, expiry);
                        mainHandler.post(callback::onSuccess);
                    } else {
                        mainHandler.post(() -> callback.onFailure("Key sudah expired!"));
                    }
                } else {
                    mainHandler.post(() -> callback.onFailure("Key tidak valid!"));
                }
           } catch (Exception e) {
                // Jangan gunakan e.getMessage() agar URL tidak bocor saat error/tidak ada internet!
                mainHandler.post(() -> callback.onFailure("NET_ERROR: Gagal terhubung ke server auth!"));
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
        SecretKeySpec keySpec = new SecretKeySpec(decode(KEY_DATA).getBytes(), "AES"); // <--- BACA AES DARI ARRAY RAHASIA
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding"); // <--- BARIS INI JANGAN SAMPAI HILANG
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(text.getBytes("UTF-8"));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String decrypt(String encryptedBase64) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(decode(KEY_DATA).getBytes(), "AES"); // <--- BACA AES DARI ARRAY RAHASIA
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding"); // <--- BARIS INI JANGAN SAMPAI HILANG
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