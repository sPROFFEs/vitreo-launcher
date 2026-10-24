package dev.vitreo.launcher.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

object ApkVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer,0,n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, release: StableRelease) {
        require(file.isFile && file.length() == release.size) { "Incomplete update." }
        require(sha256(file) == release.sha256) { "Update checksum does not match." }
        val pm = context.packageManager
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("Invalid APK.")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        require(candidate.packageName == context.packageName) { "Wrong update package." }
        require(candidate.longVersionCode == release.code && candidate.versionName == release.version && release.code > installed.longVersionCode) { "Invalid update version." }
        val app = requireNotNull(candidate.applicationInfo)
        require(app.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 && app.minSdkVersion <= android.os.Build.VERSION.SDK_INT) { "Incompatible update." }
        fun signatures(info: android.content.pm.PackageInfo) = info.signingInfo?.apkContentsSigners.orEmpty()
            .map { it.toCharsString() }.toSet()
        val trusted = signatures(installed)
        require(trusted.isNotEmpty() && trusted == signatures(candidate)) { "Update signing certificate does not match." }
    }
}
