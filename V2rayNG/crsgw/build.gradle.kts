plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.npv.crsgw"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        viewBinding = true
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
    
    // testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.15-beta-1")


    testImplementation("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.junit.platform:junit-platform-engine:1.10.2")

    // 兼容 JUnit4（非必须，建议加上防止 IDE fallback）
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

    implementation("com.google.code.gson:gson:2.13.1")

    // Retrofit & Gson
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    // OkHttp（可选，用于日志）
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.16")

    // 协程支持（用于 ViewModel）
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    runtimeOnly("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")

    implementation("androidx.datastore:datastore:1.2.0-alpha02")
    implementation("androidx.datastore:datastore-preferences:1.2.0-alpha02")
    implementation("androidx.datastore:datastore-core:1.2.0-alpha02")
    implementation("androidx.datastore:datastore-preferences-core:1.2.0-alpha02")

    implementation("androidx.slidingpanelayout:slidingpanelayout:1.2.0")

    implementation("com.google.android.material:material:1.14.0-alpha01")

    // lottie animation
    implementation("com.airbnb.android:lottie:6.6.6")

}