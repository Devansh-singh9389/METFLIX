# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coil
-keepclassmembers class * implements coil.request.ImageRequest$Listener {
    public <init>(...);
}

# Media3 ExoPlayer
-keepclassmembers class androidx.media3.** { *; }

# Compose TV & Material
-keep class androidx.tv.** { *; }
-keep interface androidx.tv.** { *; }

# Strip verbose/debug logs in optimized release build to save CPU cycles
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Keep performance settings and models
-keep class com.example.metflix.data.settings.** { *; }
-keep class com.example.metflix.data.entities.** { *; }
