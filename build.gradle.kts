buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // 内置 Kotlin 与 Compose 编译插件共用版本目录，避免编译器版本不一致。
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
