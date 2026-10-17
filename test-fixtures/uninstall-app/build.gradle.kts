// Standalone test APK, installed/uninstalled only by disposable-emulator tests.
plugins { id("com.android.application") }
android {
    namespace = "dev.vitreo.fixture.uninstall"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig { applicationId = "dev.vitreo.fixture.uninstall"; minSdk = 31; targetSdk = 35; versionCode = 1; versionName = "1" }
}
