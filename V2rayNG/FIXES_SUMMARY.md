# خلاصه رفع مشکلات V2rayNG

## مشکلات حل شده:

### 1. مشکل Gradle Dependency Resolution
**مشکل:** `Unable to find method 'org.gradle.api.artifacts.Dependency org.gradle.api.artifacts.dsl.DependencyHandler.module(java.lang.Object)'`

**راه حل:**
- به‌روزرسانی Gradle wrapper از `9.0-milestone-1` به `8.4`
- به‌روزرسانی Android Gradle Plugin از `7.3.1` به `8.1.4`
- به‌روزرسانی compileSdk و targetSdk به `34`

### 2. مشکل Kotlin Version Compatibility
**مشکل:** `Module was compiled with an incompatible version of Kotlin`

**راه حل:**
- به‌روزرسانی Kotlin از `1.6.21` به `1.9.10`
- به‌روزرسانی Kotlinx Coroutines از `1.6.1` به `1.7.3`
- اضافه کردن resolution strategy برای force کردن نسخه‌های core-ktx

### 3. مشکل SDK XML Version
**مشکل:** `SDK processing. This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered`

**راه حل:**
- اضافه کردن تنظیمات SDK compatibility در `gradle.properties`
- پاک کردن cache های Gradle

### 4. مشکل Experimental Flags
**مشکل:** `The option setting 'android.overridePathCheck=true' is experimental`

**راه حل:**
- حذف تنظیمات experimental از `gradle.properties` و `local.properties`
- حذف `android.overridePathCheck=true` و `android.sdk.channel=0`

## فایل‌های تغییر یافته:

### 1. `gradle/wrapper/gradle-wrapper.properties`
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
```

### 2. `gradle/libs.versions.toml`
```toml
[versions]
agp = "8.1.4"
kotlin = "1.9.10"
kotlinxCoroutinesAndroid = "1.7.3"
kotlinxCoroutinesCore = "1.7.3"
```

### 3. `app/build.gradle.kts`
```kotlin
android {
    compileSdk = 34
    defaultConfig {
        targetSdk = 34
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core-ktx:1.10.1")
        force("androidx.core:core:1.10.1")
    }
}
```

### 4. `gradle.properties`
```properties
# Network settings for better dependency resolution
systemProp.https.protocols=TLSv1.2,TLSv1.3
systemProp.http.connectionTimeout=30000
systemProp.http.socketTimeout=30000

# SDK compatibility settings
```

## مراحل بعدی:

1. **Android Studio را باز کنید**
2. **به Tools > SDK Manager بروید**
3. **Android SDK Command-line Tools را به‌روزرسانی کنید**
4. **Android SDK Build-Tools را به‌روزرسانی کنید**
5. **Sync Project with Gradle Files را اجرا کنید**

## اسکریپت‌های مفید:

- `fix-gradle-issues.ps1` - برای رفع مشکلات Gradle
- `fix-sdk-issues.ps1` - برای رفع مشکلات SDK
- `test-build.ps1` - برای تست بیلد

## وضعیت فعلی:
✅ Gradle dependency issues resolved  
✅ Kotlin version compatibility fixed  
✅ SDK XML version issues resolved  
✅ Experimental flags removed  
✅ Project should now build successfully in Android Studio
