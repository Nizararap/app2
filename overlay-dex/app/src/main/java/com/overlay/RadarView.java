package com.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.view.View;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RadarView extends View {
    private Paint borderPaint, enemyPaint, textPaint;
    private List<PlayerInfo> players = new ArrayList<>();
    private boolean isRunning = true;
    private SharedPreferences prefs;

    // Cache untuk gambar yang sudah diproses & di-resize
    private Map<String, Bitmap> heroIconCache = new HashMap<>();
    private float currentIconSize = 0f;

    // Cache mentahan gambar original (Hanya disimpan sekali di RAM)
    private Map<String, Bitmap> preloadedIcons = new HashMap<>();

    private PlayerInfo[] playerPool = new PlayerInfo[20]; 

    private static class PlayerInfo {
        float x, y, z;
        int campType;
        String heroName;
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        float radius = bitmap.getWidth() / 2f;
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return output;
    }

    public RadarView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("mod_settings", Context.MODE_PRIVATE);
        
        for (int i = 0; i < 20; i++) {
            playerPool[i] = new PlayerInfo();
        }
        
        // Load & Decrypt heroes.bin SATU KALI saat pertama kali radar muncul
        loadAndDecryptHeroes(); 
        
        initPaints();
        startSocketThread();
    }

    // FUNGSI BARU: Ekstrak heroes.bin dari memori
    private void loadAndDecryptHeroes() {
        try {
            // Baca file tunggal heroes.bin dari folder assets
            InputStream is = getContext().getAssets().open("heroes.bin");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[4096];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            byte[] encryptedBytes = buffer.toByteArray();
            is.close();

            // 1. DEKRIPSI (XOR dengan kunci 0x5B)
            byte key = 0x5B;
            for (int i = 0; i < encryptedBytes.length; i++) {
                encryptedBytes[i] ^= key;
            }

            // 2. UNZIP DARI MEMORI RAM
            ByteArrayInputStream bais = new ByteArrayInputStream(encryptedBytes);
            ZipInputStream zis = new ZipInputStream(bais);
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".png")) {
                    // Decode PNG yang ada di dalam ZIP
                    Bitmap bmp = BitmapFactory.decodeStream(zis);
                    if (bmp != null) {
                        // Kunci pencarian: hilangkan ".png", jadikan huruf kecil, buang spasi
                        String searchKey = entry.getName().replace(".png", "").toLowerCase().replaceAll("[^a-z0-9]", "");
                        preloadedIcons.put(searchKey, bmp); // Simpan mentahannya di RAM
                    }
                }
                zis.closeEntry();
            }
            zis.close();
            bais.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initPaints() {
        borderPaint = new Paint();
        borderPaint.setColor(Color.argb(200, 255, 255, 255));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setAntiAlias(true);

        enemyPaint = new Paint();
        enemyPaint.setColor(Color.argb(180, 200, 0, 0));
        enemyPaint.setStyle(Paint.Style.FILL);
        enemyPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(22f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
    }

    private Bitmap getHeroIcon(String heroName, float targetSize) {
        String searchKey = heroName.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        // Jika ukuran icon berubah (user geser slider), bersihkan cache bulatan
        if (currentIconSize != targetSize) {
            heroIconCache.clear();
            currentIconSize = targetSize;
        }

        // Ambil icon bulat dari cache jika sudah ada
        if (heroIconCache.containsKey(searchKey)) {
            return heroIconCache.get(searchKey);
        }

        // Ambil gambar mentah dari memori preloaded
        Bitmap originalBmp = preloadedIcons.get(searchKey);
        if (originalBmp == null) {
            heroIconCache.put(searchKey, null);
            return null;
        }

        // Proses resize dan buat bulat (hanya 1x per hero)
        int size = (int) targetSize;
        if (size <= 0) size = 50;
        Bitmap scaledBmp = Bitmap.createScaledBitmap(originalBmp, size, size, true);
        Bitmap circularBmp = getCircularBitmap(scaledBmp); 
        
        heroIconCache.put(searchKey, circularBmp);
        return circularBmp;
    }

    private void startSocketThread() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    LocalSocket socket = new LocalSocket();
                    socket.connect(new LocalSocketAddress("mlbb_radar_socket", LocalSocketAddress.Namespace.ABSTRACT));
                    DataInputStream dis = new DataInputStream(socket.getInputStream());

                    byte[] countBuffer = new byte[4];
                    byte[] packetBuffer = new byte[48];

                    while (isRunning) {
                        dis.readFully(countBuffer);
                        int playerCount = ByteBuffer.wrap(countBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

                        if (playerCount > 20 || playerCount < 0) playerCount = 0; 

                        List<PlayerInfo> newPlayers = new ArrayList<>(playerCount);
                        for (int i = 0; i < playerCount; i++) {
                            dis.readFully(packetBuffer);
                            ByteBuffer bb = ByteBuffer.wrap(packetBuffer).order(ByteOrder.LITTLE_ENDIAN);

                            PlayerInfo p = playerPool[i]; 
                            p.x = bb.getFloat();
                            p.y = bb.getFloat();
                            p.z = bb.getFloat();
                            p.campType = bb.getInt();

                            byte[] nameBytes = new byte[32];
                            bb.get(nameBytes);
                            p.heroName = new String(nameBytes).trim();

                            newPlayers.add(p);
                        }
                        
                        synchronized (RadarView.this) {
                            players = newPlayers;
                        }
                        postInvalidate();
                    }
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private float[] worldToMinimap(int campType, float wx, float wz) {
        float angleCos = campType == 2 ? (float)Math.cos(Math.toRadians(314.60)) : (float)Math.cos(Math.toRadians(134.76));
        float angleSin = campType == 2 ? (float)Math.sin(Math.toRadians(314.60)) : (float)Math.sin(Math.toRadians(134.76));

        float negWz = -wz;
        float outX = (angleCos * wx - angleSin * negWz) / 74.11f;
        float outY = (angleSin * wx + angleCos * negWz) / 74.11f;
        return new float[]{outX, outY};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        boolean isEnabled = prefs.getBoolean("radar_enable", false);
        if (!isEnabled) return;

        boolean drawBorder = prefs.getBoolean("radar_border", true);
        float size = prefs.getFloat("radar_size", 338.0f);
        float posX = prefs.getFloat("radar_pos_x", 71.0f);
        float posY = prefs.getFloat("radar_pos_y", 0.0f);
        float iconSize = prefs.getFloat("radar_icon_size", 37.0f);

        if (drawBorder) {
            canvas.drawRect(posX, posY, posX + size, posY + size, borderPaint);
        }

        float halfIcon = iconSize * 0.5f;
        List<PlayerInfo> currentPlayers;
        synchronized (this) {
            currentPlayers = new ArrayList<>(players);
        }
        
        for (PlayerInfo p : currentPlayers) {
            float[] mmOut = worldToMinimap(p.campType, p.x, p.z);
            float drawX = (mmOut[0] * size) + posX + (size * 0.5f);
            float drawY = (mmOut[1] * size) + posY + (size * 0.5f);

            drawX = Math.max(posX, Math.min(drawX, posX + size));
            drawY = Math.max(posY, Math.min(drawY, posY + size));

            Bitmap heroIcon = getHeroIcon(p.heroName, iconSize);

            if (heroIcon != null) {
                canvas.drawBitmap(heroIcon, drawX - halfIcon, drawY - halfIcon, null);
            } else {
                canvas.drawCircle(drawX, drawY, halfIcon, enemyPaint);
                String init = p.heroName != null && p.heroName.length() >= 3 ? p.heroName.substring(0, 3) : "???";
                canvas.drawText(init.toUpperCase(), drawX, drawY + 8f, textPaint);
            }
            
            borderPaint.setColor(Color.argb(255, 255, 30, 30));
            canvas.drawCircle(drawX, drawY, halfIcon, borderPaint);
            borderPaint.setColor(Color.argb(200, 255, 255, 255));
        }
    }

    public List<String> getActiveEnemyNames() {
        List<String> names = new ArrayList<>();
        synchronized (this) {
            for (PlayerInfo p : players) {
                if (p.heroName != null && !p.heroName.isEmpty()) {
                    if (!names.contains(p.heroName)) {
                        names.add(p.heroName);
                    }
                }
            }
        }
        return names;
    }

    public void destroy() { isRunning = false; }
}