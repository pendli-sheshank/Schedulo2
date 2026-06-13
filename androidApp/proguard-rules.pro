# Keep Firestore model classes (uses reflection for deserialization)
-keep class com.example.Job { *; }
-keep class com.example.Shift { *; }
-keep class com.example.PayAdjustment { *; }
-keep class com.example.AlarmRescheduleReceiver { *; }

# Keep Firestore @Exclude annotations
-keepattributes *Annotation*

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
    **[] values();
}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.Json <fields>;
}

# Keep line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Coroutines
-dontwarn kotlinx.coroutines.**
