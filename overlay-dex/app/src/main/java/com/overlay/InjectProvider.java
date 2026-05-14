package com.overlay;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class InjectProvider extends ContentProvider {
    private static final String TAG = "InjectProvider";

    @Override
    public boolean onCreate() {
        Log.d(TAG, "InjectProvider onCreate: Initializing Overlay Service...");
        
        try {
            // 1. Jalankan OverlayService secara otomatis
            Intent intent = new Intent(getContext(), OverlayService.class);
            getContext().startService(intent);
            Log.d(TAG, "OverlayService started successfully.");
            
            // 2. Load library native (dexkit sesuai Android.mk)
            System.loadLibrary("dexkit");
            Log.d(TAG, "Native library 'dexkit' loaded successfully.");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during injection: " + e.getMessage());
        }
        
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
