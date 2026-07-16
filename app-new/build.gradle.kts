import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // no longer required with agp 9
    // alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "rj.qmce.lite"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "rj.qmce.litex"
        minSdk = 23
        targetSdk = 37
        versionCode = 18
        versionName = "0.4.3"
        multiDexEnabled = true
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += "armeabi-v7a"
        }
        multiDexKeepProguard = file("multidex-proguard.pro")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("dev") {
            // Using testkey
            storeFile = file("./testkey.jks")
            storePassword = "android"
            keyAlias = "key"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = false
            enableV4Signing = false
        }
    }

    buildTypes {
        val enableCodeShrinks = true
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("dev")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            if (enableCodeShrinks)
            {
                isMinifyEnabled = true
                //noinspection NotShrinkingResources
                isShrinkResources = false
            } else {
                isMinifyEnabled = false
                //noinspection NotShrinkingResources
                isShrinkResources = false
            }
            signingConfig = signingConfigs.getByName("dev")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging { jniLibs { useLegacyPackaging = false } }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("17"))
        freeCompilerArgs.addAll(
            "-opt-in=androidx.wear.compose.foundation.ExperimentalWearFoundationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}

androidComponents {
    onVariants { variant ->
        variant.run {
            signingConfig.run {
                enableV1Signing.set(true)
                enableV2Signing.set(true)
                enableV3Signing.set(false)
                enableV4Signing.set(false)
            }
            outputs.forEach { output ->
                val apkName = "QMCE-${output.versionName.get()}-${output.versionCode.get()}.apk"
                (output as VariantOutputImpl).outputFileName = apkName
            }
        }
    }
}

dependencies {
    // AppCenter

    implementation("com.microsoft.appcenter:appcenter-analytics:5.0.4")
    implementation("com.microsoft.appcenter:appcenter-crashes:5.0.4")

    // QQ API
    implementation(files("libs/qq-sdk.jar"))
    implementation(files("libs/qav-runtime.jar"))

    // Wear OS platform SDK
    implementation(libs.androidx.wear)

    // AndroidX Library
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.jessyan.autosize)
    implementation("androidx.multidex:multidex:2.0.1")

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material.ripple)

    // Wear Compose vendor source deps
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.graphics.path)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.bundled)

    // Navigation (needed by Wear Navigator source)
    implementation(libs.androidx.navigation.compose)

    // Navigation 3 (reserved for the upcoming back-stack migration)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // Coil for async image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // Kotlin Coroutines for Android
    implementation(libs.kotlinx.coroutines.android)

    // test (useless at all)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
