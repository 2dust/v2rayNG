# ============================================================
# v2rayNG ProGuard / R8 Rules
# ============================================================

# ---- 通用配置 ----

# 保留源文件名和行号，便于崩溃堆栈分析
-keepattributes SourceFile,LineNumberTable

# 保留注解（@SerializedName, @Keep, @JvmStatic 等）
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# 混淆后重映射源文件名，隐藏真实文件路径
-renamesourcefileattribute SourceFile

# ---- GSON 序列化数据类 ----
# 这些类的字段名直接用作 JSON key，混淆后会导致序列化/反序列化失败
# 涉及：服务器配置存储（MMKV）、V2Ray 核心配置生成、v2rayN 格式导入导出、
# GitHub API 响应解析、证书指纹请求/响应、IP 查询响应等

# 保留所有 DTO 数据类（含 entities 子包）及其字段、构造方法
-keep class com.v2ray.ang.dto.** { *; }
-keep class com.v2ray.ang.dto.entities.** { *; }

# 通用 GSON 规则：保留被 @SerializedName 标注的字段
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Serializable 数据类 ----
# 这些类通过 Intent.putExtra() 在进程间传递（UI 进程 ↔ :RunSoLibV2RayDaemon 进程）
# 混淆后 serialVersionUID 变化会导致跨进程反序列化失败
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---- JNI / Native 调用 ----

# hev-socks5-tunnel: JNI external 方法依赖函数名匹配，类名和方法名都不能混淆
-keep class com.v2ray.ang.service.TProxyService { *; }
-keep class com.v2ray.ang.service.TProxyService$* { *; }

# libv2ray (gomobile 绑定): Go native 层通过反射调用 Java 绑定类
# go.Seq 是 gomobile 的基础序列化类，libv2ray 包含所有核心 API
-keep class go.** { *; }
-keep class libv2ray.** { *; }

# CoreCallbackHandler / ProcessFinder 接口实现类被 Go 层通过 gomobile 回调
# 这些是 CoreServiceManager 的内部私有类，R8 默认会混淆，必须显式保留
-keep class com.v2ray.ang.core.CoreServiceManager$CoreCallback { *; }
-keep class com.v2ray.ang.core.CoreServiceManager$XrayProcessFinder { *; }

# contracts 接口（Tun2SocksControl, ServiceControl, IDialerService）
# 被 JNI 相关类实现，方法签名需要保持一致
-keep interface com.v2ray.ang.contracts.** { *; }

# ---- BuildConfig ----
# 项目显式开启了 buildConfig = true，运行时通过 BuildConfig.DISTRIBUTION 读取分发渠道
-keep class com.v2ray.ang.BuildConfig { *; }

# ---- Kotlin Metadata ----
-keep class kotlin.Metadata { *; }

# ---- 枚举类 ----
# 枚举的 values() / valueOf() 可能被反射调用
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
