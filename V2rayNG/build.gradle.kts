// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // Do not apply here, just declare
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

buildscript {
    dependencies {
        // License plugin classpath
        classpath(libs.gradle.license.plugin)
    }
}
