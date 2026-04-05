import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.jaredsburrows.license")
}

android {
    namespace = "com.v2ray.ang"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.v2ray.ang"
        minSdk = 24
        targetSdk = 36
        versionCode = 718
        versionName = "2.0.18"
        multiDexEnabled = true

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (abiFilterList != null && abiFilterList.isNotEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                        "x86"
                    )
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("libs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        if (isFdroid) {
            val versionCodes =
                mapOf(
                    "armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0
                )

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter("ABI") ?: "universal"
                    output.outputFileName = "v2rayNG_${variant.versionName}-fdroid_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (100 * variant.versionCode + versionCodes[abi]!!).plus(5000000)
                    } else {
                        return@forEach
                    }
                }
        } else {
            val versionCodes =
                mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = if (output.getFilter("ABI") != null)
                        output.getFilter("ABI")
                    else
                        "universal"

                    output.outputFileName = "v2rayNG_${variant.versionName}_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                    } else {
                        return@forEach
                    }
                }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

dependencies {
    // Core Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.fragment)

    // UI Libraries
    implementation(libs.material)
    implementation(libs.toasty)
    implementation(libs.editorkit)
    implementation(libs.flexbox)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Language and Processing Libraries
    implementation(libs.language.base)
    implementation(libs.language.json)

    // Intent and Utility Libraries
    implementation(libs.quickie.foss)
    implementation(libs.core)

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Multidex Support
    implementation(libs.multidex)

    // Testing Libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.org.mockito.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// builds hev and libXrayLite if not already built
val repoRoot = rootProject.projectDir.parentFile ?: error("Expected git repo root above ${rootProject.projectDir}")

fun readAndroidSdkDir(): File {
    val lp = rootProject.projectDir.resolve("local.properties")
    check(lp.isFile) { "Create V2rayNG/local.properties with sdk.dir (Android Studio does this when you open the project)." }
    val props = Properties().apply { lp.reader().use { load(it) } }
    val raw = props.getProperty("sdk.dir")?.trim() ?: error("sdk.dir missing in local.properties")
    return File(raw).absoluteFile.also { check(it.isDirectory) { "SDK path does not exist: $it" } }
}

fun readNdkDir(sdkDir: File): File {
    System.getenv("NDK_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }
        ?.takeIf { it.isDirectory }?.let { return it.absoluteFile }
    System.getenv("ANDROID_NDK_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }
        ?.takeIf { it.isDirectory }?.let { return it.absoluteFile }
    val ver = (findProperty("v2rayN.ndkVersion") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        ?: "28.2.13676358"
    val ndk = File(sdkDir, "ndk/$ver")
    check(ndk.isDirectory) { "Install Android NDK $ver or set NDK_HOME / ANDROID_NDK_HOME" }
    return ndk.absoluteFile
}

val nativeSdkDir = lazy { readAndroidSdkDir() }
val nativeNdkDir = lazy { readNdkDir(nativeSdkDir.value) }

val hevtunSoOutputs = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").map { abi ->
    layout.projectDirectory.file("libs/$abi/libhev-socks5-tunnel.so").asFile
}
val libv2rayAarOutput = layout.projectDirectory.file("libs/libv2ray.aar").asFile

tasks.register<Exec>("prepareNativeHevtun") {
    group = "build"
    description = "Runs compile-hevtun.sh when any hevtun .so is missing under app/libs."
    onlyIf("hevtun .so missing") { hevtunSoOutputs.any { !it.isFile } }
    workingDir = repoRoot
    environment("NDK_HOME", nativeNdkDir.value.absolutePath)
    commandLine("bash", repoRoot.resolve("compile-hevtun.sh").absolutePath)
}

tasks.register<Exec>("prepareNativeLibXray") {
    group = "build"
    description = "Runs compile-libxray.sh when libv2ray.aar is missing under app/libs."
    onlyIf("libv2ray.aar missing") { !libv2rayAarOutput.isFile }
    workingDir = repoRoot
    environment(
        mapOf(
            "ANDROID_HOME" to nativeSdkDir.value.absolutePath,
            "ANDROID_NDK_HOME" to nativeNdkDir.value.absolutePath,
            "NDK_HOME" to nativeNdkDir.value.absolutePath,
        ),
    )
    commandLine("bash", repoRoot.resolve("compile-libxray.sh").absolutePath)
}

rootProject.tasks.register("prepareNativeDeps") {
    group = "build"
    description = "Build native deps into app/libs (both scripts)."
    dependsOn(tasks.named("prepareNativeHevtun"), tasks.named("prepareNativeLibXray"))
}

tasks.named("preBuild") {
    dependsOn(tasks.named("prepareNativeHevtun"), tasks.named("prepareNativeLibXray"))
}
