package dev.vitreo.launcher.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

/** No shell installer or root. Android remains responsible for the actual package replacement. */
object SelfUpdateInstaller {
    fun stage(context: Context, apk: File, release: StableRelease): Int {
        ApkVerifier.verify(context, apk, release)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName); setSize(release.size)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, release.size).use { output ->
                    apk.inputStream().use { it.copyTo(output) }; session.fsync(output)
                }
            }
            return id
        } catch (e: Exception) { runCatching { installer.abandonSession(id) }; throw e }
    }
    fun commit(context: Context, id: Int) {
        val installer = context.packageManager.packageInstaller
        val info = requireNotNull(installer.getSessionInfo(id))
        require(info.appPackageName == context.packageName && info.installerPackageName == context.packageName)
        val callback = PendingIntent.getBroadcast(context, id,
            Intent(context, UpdateInstallReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        installer.openSession(id).use { it.commit(callback.intentSender) }
    }
}
