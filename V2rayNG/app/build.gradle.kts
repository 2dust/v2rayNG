plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.jaredsburrows.license")
}

android {
    namespace = "com.v2ray.ang"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.v2ray.ang"
        minSdk = 21
        targetSdk = 35
        versionCode = 668
        versionName = "1.10.18"
        multiDexEnabled = true

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (abiFilterList != null && abiFilterList.isNotEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true            // Enable code shrinking
            shrinkResources = true            // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release") // Make sure release signing is set
        }
        debug {
            isMinifyEnabled = false           // Keep debug readable
            shrinkResources = false
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
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    // Rename APKs and override version codes based on flavor and ABI
    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        val versionCodesFdroid =
            mapOf("armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0)
        val versionCodesPlay =
            mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

        variant.outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                val abi = output.getFilter("ABI") ?: "universal"
                if (isFdroid) {
                    output.outputFileName = "v2rayNG_${variant.versionName}-fdroid_${abi}.apk"
                    output.versionCodeOverride =
                        (100 * variant.versionCode + (versionCodesFdroid[abi] ?: 0)) + 5000000
                } else {
                    output.outputFileName = "v2rayNG_${variant.versionName}_${abi}.apk"
                    output.versionCodeOverride =
                        (1000000 * (versionCodesPlay[abi] ?: 0)) + variant.versionCode
                }
            }
    }

    buildFeatures {
        viewBinding = true       // Enable ViewBinding
        buildConfig = true       // Enable BuildConfig generation
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true   // Maintain legacy JNI packaging
        }
    }
}

dependencies {
    // Core libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX core libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)

    // UI libraries
    implementation(libs.material)
    implementation(libs.toasty)
    implementation(libs.editorkit)
    implementation(libs.flexbox)

    // Data and storage
    implementation(libs.mmkv.static)
    implementation(libs.gson)

    // Reactive and utility libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Language and processing
    implementation(libs.language.base)
    implementation(libs.language.json)

    // Intent and utility
    implementation(libs.quickie.foss)
    implementation(libs.core)

    // AndroidX lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background tasks
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Multidex support
    implementation(libs.multidex)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.org.mockito.mockito.inline)
    testImplementation(libs.mockito.kotlin)

    // Desugar Java 8+ features
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
