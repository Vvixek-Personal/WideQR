# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.example.** {
    *** Companion;
}
-keepclassmembers class com.example.** {
    *** serializer(...);
}
-keep,inallowobfuscation @kotlinx.serialization.Serializable class com.example.** {
    private static final kotlinx.serialization.KSerializer serializer(...);
    private static final kotlinx.serialization.KSerializer $serializer(...);
    private static final kotlinx.serialization.KSerializer[] $serializer(...);
    private static final kotlinx.serialization.KSerializer[] $serializer(...);
}

# --- Camera2 (hardware callbacks use reflection-free APIs, but keep for safety) ---
-keep class android.hardware.camera2.** { *; }

# --- Jetpack Compose ---
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# --- ML Kit Barcode Scanning ---
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class com.example.history.** { *; }

# --- OkHttp ---
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
