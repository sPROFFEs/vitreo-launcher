import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val signingNames = listOf("VITREO_STORE_FILE", "VITREO_STORE_PASSWORD", "VITREO_KEY_ALIAS", "VITREO_KEY_PASSWORD")
val signing = signingNames.associateWith { System.getenv(it)?.takeIf(String::isNotBlank) }
val signingCount = signing.values.count { it != null }
check(signingCount == 0 || signingCount == signingNames.size) { "Incomplete release signing environment." }
val hasPrivateKey = signingCount == signingNames.size

android {
    namespace = "dev.vitreo.launcher"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "dev.vitreo.launcher"
        minSdk = 31
        targetSdk = 35
        versionCode = appVersion.getProperty("VERSION_CODE").toInt()
        versionName = appVersion.getProperty("VERSION_NAME")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    signingConfigs {
        if (hasPrivateKey) {
            create("privateRelease") {
                storeFile = file(signing.getValue("VITREO_STORE_FILE")!!)
                storePassword = signing.getValue("VITREO_STORE_PASSWORD")
                keyAlias = signing.getValue("VITREO_KEY_ALIAS")
                keyPassword = signing.getValue("VITREO_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasPrivateKey) signingConfig = signingConfigs.getByName("privateRelease")
            manifestPlaceholders["appLabel"] = "Vitreo"
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Vitreo Debug"
        }
        // Local-only positive self-update fixture. Never published by CI.
        create("updateFixture") {
            initWith(getByName("debug"))
            isDebuggable = false
            matchingFallbacks += "debug"
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            manifestPlaceholders["appLabel"] = "Vitreo Preview"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.merges += listOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt") }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
// A release is never silently unsigned. Preview remains an explicitly separate app.
tasks.configureEach {
    if (name in setOf("packageRelease", "validateSigningRelease", "bundleRelease")) {
        doFirst { check(hasPrivateKey) { "Release needs all four VITREO_* signing variables. Use assemblePreview for a test APK." } }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

androidComponents {
    onVariants(selector().withBuildType("updateFixture")) { variant ->
        variant.outputs.forEach {
            it.versionCode.set(appVersion.getProperty("VERSION_CODE").toInt() + 1)
            it.versionName.set(appVersion.getProperty("VERSION_NAME").substringBeforeLast('.') + "." + (appVersion.getProperty("VERSION_NAME").substringAfterLast('.').toInt() + 1))
        }
    }
}
