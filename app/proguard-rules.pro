# ProGuard rules for SDG Claw

-keep class com.sdgclaw.** { *; }
-keep class kotlinx.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.json.** { *; }

# Kotlin serialization
-keep class kotlinx.serialization.** { *; }

# Room
-keep class androidx.room.** { *; }

# Prevent obfuscation of data classes
-keepclassmembers class * {
    @kotlin.metadata.KotlinMetadata <fields>;
}