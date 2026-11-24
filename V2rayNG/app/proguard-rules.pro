# Rules for R8, which build.gradle.kts enables for release builds.

# Shrink and optimize, but keep the original names. The source is public, so
# renaming hides nothing and only makes stack traces in bug reports unreadable
# without the build's mapping file.
-dontobfuscate

# Each rule below keeps something the app reaches by name or through
# reflection at runtime, where R8 cannot see the use. Shrinking still applies
# without renaming: a field only Gson reads, or a constructor only Gson calls,
# would otherwise be removed as unused.

# Gson maps these classes to JSON by field name: the profiles, subscriptions
# and settings stored in MMKV, share links, subscription content and the core
# configuration. A renamed field would silently drop its value, and a removed
# no-argument constructor would make Gson skip Kotlin's default values. The
# Serializable messages passed between processes live here too.
-keep class com.v2ray.ang.dto.** { *; }

# Enum constants are stored and parsed by name.
-keepclassmembers enum com.v2ray.ang.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson needs generic signatures for TypeToken and annotations for
# @SerializedName.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# hev-socks5-tunnel registers its natives on this class by name
# (FindClass and RegisterNatives in hev-jni.c, PKGNAME in compile-hevtun.sh).
-keep class com.v2ray.ang.service.TProxyService {
    native <methods>;
}

# gomobile bindings for the Xray core: Go calls into these by name.
-keep class go.** { *; }
-keep class libv2ray.** { *; }

# MMKV calls back into Java from native code.
-keep class com.tencent.mmkv.** { *; }

# R8 9.5.x can non-deterministically class-merge this coroutines helper and
# narrow emitAbort's Continuation parameter. Keep its original placement and
# signature so independently built release APKs produce identical dex files.
-keep class kotlinx.coroutines.flow.FlowKt__LimitKt {
    *** emitAbort(...);
}

# Keep file names and line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable
