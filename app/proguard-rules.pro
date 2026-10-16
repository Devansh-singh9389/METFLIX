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
