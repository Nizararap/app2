# Aturan Dasar (Mencegah Error)
-keep class com.overlay.OverlayService { *; }
-keep class com.overlay.OverlayView { *; }
-keep class com.overlay.LoginView { *; }

-keepclassmembers class com.overlay.KeyAuthManager {
    public boolean isKeyValid();
    public void validateKey(java.lang.String, com.overlay.KeyAuthManager$AuthCallback);
}

-keep class org.json.** { *; }

# =========================================================
# ADVANCED OBFUSCATION (MENGHANCURKAN STRUKTUR KODE)
# =========================================================

# Menghilangkan nama file asli dan atribut sumber
-renamesourcefileattribute SourceFile
-keepattributes !*Annotation*,!Signature,!Exceptions,!InnerClasses,!EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Pindahkan semua class yang tidak dikeep ke root (menghilangkan struktur folder com/overlay)
-repackageclasses ''
-allowaccessmodification

# Optimasi maksimal (Menyulitkan decompilation)
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5

# Membuang Log agar alur aplikasi tidak bisa dilacak via Logcat
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}