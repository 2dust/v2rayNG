# ======================
# Optimization Settings
# ======================
-optimizationpasses 5
-dontpreverify
-dontoptimize
-allowaccessmodification
-overloadaggressively

# ======================
# Keep Android Components
# ======================
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
-keep class * extends android.app.Application

# ======================
# Keep Models & Annotations
# ======================
-keep class com.v2ray.** { *; }
-keepclassmembers class com.v2ray.** { *; }

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ======================
# Gson / JSON Serialization
# ======================
-keep class com.google.gson.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ======================
# OkHttp & Networking
# ======================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# ======================
# Prevent Warnings
# ======================
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn sun.misc.Unsafe

# ======================
# Logging (Optional: strip in release)
# ======================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
