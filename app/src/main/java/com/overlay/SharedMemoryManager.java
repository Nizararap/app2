package com.overlay;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class SharedMemoryManager {
    private static final String TAG = "SHM_MOD";
    private static final int SHM_SIZE = 4096;

    // -------------- offset dari SharedStruct.h (config 16 byte, game mulai di 16) ----------
    private static final int OFFSET_AIMBOT        = 0;   // bool
    private static final int OFFSET_AUTO_RETRI    = 3;   // bool (autoRetriBuff di C++)

    // game struct offset
    private static final int OFFSET_BATTLE_STARTED = 16;  // bool
    private static final int OFFSET_PLAYER_COUNT   = 20;  // int (setelah padding)
    private static final int OFFSET_PLAYERS_ARRAY  = 24;  // PlayerData[10]
    // ukuran satu PlayerData (diasumsikan 52 byte)
    private static final int SIZE_PLAYER           = 52;
    // di dalam PlayerData:
    private static final int OFF_PLAYER_POS_X      = 0;
    private static final int OFF_PLAYER_POS_Y      = 4;
    private static final int OFF_PLAYER_POS_Z      = 8;
    private static final int OFF_PLAYER_CAMP       = 12;
    private static final int OFF_PLAYER_IS_DEAD    = 48;

    private MappedByteBuffer buffer;

    public SharedMemoryManager(Context context) {
        try {
            File shmFile = new File(context.getFilesDir(), "mod_shm");
            if (!shmFile.exists()) shmFile.createNewFile();
            RandomAccessFile raf = new RandomAccessFile(shmFile, "rw");
            if (raf.length() < SHM_SIZE) raf.setLength(SHM_SIZE);
            buffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, SHM_SIZE);
            Log.i(TAG, "Shared Memory mapped");
        } catch (Exception e) {
            Log.e(TAG, "SHM error: " + e.getMessage());
        }
    }

    // --- write config to C++ ---
    public void setAimbot(boolean enabled) {
        if (buffer != null) buffer.put(OFFSET_AIMBOT, (byte)(enabled ? 1 : 0));
    }
    public void setAutoRetri(boolean enabled) {
        if (buffer != null) buffer.put(OFFSET_AUTO_RETRI, (byte)(enabled ? 1 : 0));
    }

    // --- read game data from C++ ---
    public boolean isBattleStarted() {
        if (buffer == null) return false;
        return buffer.get(OFFSET_BATTLE_STARTED) == 1;
    }
    public int getPlayerCount() {
        if (buffer == null) return 0;
        return buffer.getInt(OFFSET_PLAYER_COUNT);
    }

    // data player ke-i
    private int playerBaseOffset(int idx) {
        return OFFSET_PLAYERS_ARRAY + idx * SIZE_PLAYER;
    }
    public float getPlayerPosX(int idx) {
        return buffer.getFloat(playerBaseOffset(idx) + OFF_PLAYER_POS_X);
    }
    public float getPlayerPosY(int idx) {
        return buffer.getFloat(playerBaseOffset(idx) + OFF_PLAYER_POS_Y);
    }
    public float getPlayerPosZ(int idx) {
        return buffer.getFloat(playerBaseOffset(idx) + OFF_PLAYER_POS_Z);
    }
    public int getPlayerCamp(int idx) {
        return buffer.getInt(playerBaseOffset(idx) + OFF_PLAYER_CAMP);
    }
    public boolean isPlayerDead(int idx) {
        return buffer.get(playerBaseOffset(idx) + OFF_PLAYER_IS_DEAD) == 1;
    }
    public boolean isPlayerEnemy(int idx) {
        // asumsi camp kamu selalu 1, musuh camp != 1 (biasanya 2)
        return getPlayerCamp(idx) != 1;
    }
}