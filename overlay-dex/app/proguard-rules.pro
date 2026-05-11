# Melindungi kelas utama agar tidak di-obfuscate yang bisa merusak JNI/Reflection
-keep class com.overlay.OverlayService { *; }
-keep class com.overlay.OverlayView { *; }
-keep class com.overlay.LoginView { *; }

# Melindungi KeyAuthManager agar logika validasi lebih sulit dibaca
-keepclassmembers class com.overlay.KeyAuthManager {
    public boolean isKeyValid();
    public void validateKey(java.lang.String, com.overlay.KeyAuthManager$AuthCallback);
}

# Hapus Log untuk keamanan
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Melindungi JSON library
-keep class org.json.** { *; }
