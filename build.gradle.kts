import java.util.Properties

val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.isFile) {
    val localProperties = Properties()
    localPropertiesFile.inputStream().use(localProperties::load)
    val proxyHost = localProperties.getProperty("crashlytics.proxy.host")?.trim()
    val proxyPort = localProperties.getProperty("crashlytics.proxy.port")?.trim()
    if (!proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()) {
        listOf("http", "https").forEach { scheme ->
            System.setProperty("$scheme.proxyHost", proxyHost)
            System.setProperty("$scheme.proxyPort", proxyPort)
            localProperties.getProperty("crashlytics.proxy.user")
                ?.takeIf { it.isNotBlank() }
                ?.let { System.setProperty("$scheme.proxyUser", it) }
            localProperties.getProperty("crashlytics.proxy.password")
                ?.takeIf { it.isNotBlank() }
                ?.let { System.setProperty("$scheme.proxyPassword", it) }
        }
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
