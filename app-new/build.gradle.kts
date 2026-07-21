import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

val releaseKeyStorePassword = "android"
val releaseKeyAlias = "key"
val releaseKeyPassword = "android"

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
        versionCode = 21
        versionName = "0.4.6"
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
            storePassword = releaseKeyStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
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

    // 补药开 开了包大小爆炸
    //packaging { jniLibs { useLegacyPackaging = false } }
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

val configuredBuildToolsVersion = android.buildToolsVersion

/*
val ultraCompressReleaseApk = tasks.register("ultraCompressReleaseApk") {
    group = "build"
    description = "Rebuilds the signed release APK with maximum standard ZIP Deflate compression."
    dependsOn("assembleRelease")
    notCompatibleWithConfigurationCache("Runs external archive and signing tools.")

    doLast {
        val releaseDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val inputApk = releaseDirectory.listFiles()
            ?.firstOrNull { file ->
                file.extension == "apk" && !file.nameWithoutExtension.endsWith("-ultra")
            }
            ?: error("Release APK not found in ${releaseDirectory.absolutePath}")
        val outputApk = File(releaseDirectory, "${inputApk.nameWithoutExtension}-ultra.apk")
        val temporaryDirectory = layout.buildDirectory
            .dir("intermediates/ultraReleaseApk")
            .get()
            .asFile
        val unpackedDirectory = File(temporaryDirectory, "unpacked")
        val unalignedApk = File(temporaryDirectory, "unaligned.apk")
        val alignedApk = File(temporaryDirectory, "aligned.apk")
        val localPropertiesFile = rootProject.file("local.properties")
        val localProperties = Properties().apply {
            if (localPropertiesFile.isFile) {
                localPropertiesFile.inputStream().use(::load)
            }
        }
        val sdkDirectory = listOf(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            localProperties.getProperty("sdk.dir"),
        ).firstNotNullOfOrNull { path ->
            path?.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isDirectory)
        } ?: error(
            "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT, " +
                "or define sdk.dir in ${localPropertiesFile.absolutePath}",
        )
        val toolDirectory = File(sdkDirectory, "build-tools/$configuredBuildToolsVersion")
        check(toolDirectory.isDirectory) {
            "Configured Android build-tools $configuredBuildToolsVersion not found in " +
                "${toolDirectory.absolutePath}"
        }
        val zipAlign = File(toolDirectory, "zipalign")
        val apkSigner = File(toolDirectory, "apksigner")
        check(zipAlign.canExecute()) { "zipalign is unavailable: ${zipAlign.absolutePath}" }
        check(apkSigner.canExecute()) { "apksigner is unavailable: ${apkSigner.absolutePath}" }

        fun runCommand(workingDirectory: File, vararg arguments: String) {
            val process = ProcessBuilder(*arguments)
                .directory(workingDirectory)
                .inheritIO()
                .start()
            check(process.waitFor() == 0) { "Command failed: ${arguments.joinToString(" ")}" }
        }

        delete(temporaryDirectory)
        temporaryDirectory.mkdirs()
        runCommand(
            temporaryDirectory,
            "7z", "x", inputApk.absolutePath, "-o${unpackedDirectory.absolutePath}", "-y",
        )
        delete(File(unpackedDirectory, "META-INF"))

        val storeEntries = buildList {
            if (File(unpackedDirectory, "lib").isDirectory) add("lib")
            if (File(unpackedDirectory, "resources.arsc").isFile) add("resources.arsc")
        }
        if (storeEntries.isNotEmpty()) {
            runCommand(
                unpackedDirectory,
                "7z", "a", "-tzip", "-mx=0", unalignedApk.absolutePath, *storeEntries.toTypedArray(),
            )
        }

        val compressedEntries = unpackedDirectory.listFiles()
            ?.map(File::getName)
            ?.filterNot { name -> name == "lib" || name == "resources.arsc" || name == "META-INF" }
            .orEmpty()
        check(compressedEntries.isNotEmpty()) { "No APK entries available for compression" }
        runCommand(
            unpackedDirectory,
            "7z", "a", "-tzip", "-mm=Deflate", "-mx=9", "-mfb=258", "-mpass=15",
            unalignedApk.absolutePath,
            *compressedEntries.toTypedArray(),
        )
        runCommand(
            temporaryDirectory,
            zipAlign.absolutePath, "-f", "-p", "4", unalignedApk.absolutePath, alignedApk.absolutePath,
        )
        runCommand(
            temporaryDirectory,
            apkSigner.absolutePath,
            "sign",
            "--ks", file("./testkey.jks").absolutePath,
            "--ks-key-alias", releaseKeyAlias,
            "--ks-pass", "pass:$releaseKeyStorePassword",
            "--key-pass", "pass:$releaseKeyPassword",
            "--v1-signing-enabled", "true",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "false",
            "--v4-signing-enabled", "false",
            "--out", outputApk.absolutePath,
            alignedApk.absolutePath,
        )
        runCommand(temporaryDirectory, apkSigner.absolutePath, "verify", "--verbose", outputApk.absolutePath)
        logger.lifecycle(
            "Ultra-compressed release APK: ${outputApk.absolutePath} " +
                "(${inputApk.length()} B -> ${outputApk.length()} B)",
        )
    }
}

tasks.configureEach {
    if (name == "assembleRelease") {
        finalizedBy(ultraCompressReleaseApk)
    }
}
*/

dependencies {
    // AppCenter

    implementation(libs.appcenter.analytics)
    implementation(libs.appcenter.crashes)

    // QQ API
    implementation(files("libs/qq-sdk.jar"))
    // 新版jar不需要这个了
    //implementation(files("libs/qav-runtime.jar"))
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
    implementation(libs.androidx.multidex)

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
    // implementation(libs.androidx.emoji2)
    // implementation(libs.androidx.emoji2.bundled)

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
