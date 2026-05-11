package com.overlay;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class SecureSession {
    private static final String SESSION_FILE = ".sys_auth_data";
    private static final byte[] KEY = "ManusSecurityKey".getBytes(StandardCharsets.UTF_8);

    public static void saveSession(Context context, String key, long timestamp) {
        try {
            String data = key + "|" + timestamp;
            byte[] encrypted = xor(data.getBytes(StandardCharsets.UTF_8));
            File file = new File(context.getFilesDir(), SESSION_FILE);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(encrypted);
            fos.close();
        } catch (Exception ignored) {}
    }

    public static String getSessionKey(Context context) {
        String data = readSession(context);
        if (data != null && data.contains("|")) {
            return data.split("\\|")[0];
        }
        return null;
    }

    public static long getSessionTimestamp(Context context) {
        String data = readSession(context);
        if (data != null && data.contains("|")) {
            try {
                return Long.parseLong(data.split("\\|")[1]);
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public static void clearSession(Context context) {
        File file = new File(context.getFilesDir(), SESSION_FILE);
        if (file.exists()) file.delete();
    }

    public static boolean isSessionFileExists(Context context) {
        return new File(context.getFilesDir(), SESSION_FILE).exists();
    }

    private static String readSession(Context context) {
        try {
            File file = new File(context.getFilesDir(), SESSION_FILE);
            if (!file.exists()) return null;
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            return new String(xor(buffer), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] xor(byte[] input) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (byte) (input[i] ^ KEY[i % KEY.length]);
        }
        return output;
    }
}
