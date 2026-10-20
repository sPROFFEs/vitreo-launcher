package dev.vitreo.launcher

import android.app.Application
import java.io.File

class VitreoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        dev.vitreo.launcher.lock.LockScreenRuntime.initialize(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Local only. Export is a separate, explicit user action.
            runCatching {
                File(filesDir, "last-crash.txt").writeText(
                    "Vitreo ${BuildConfig.VERSION_NAME}\n${throwable.stackTraceToString().take(32768)}")
            }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(1) }
        }
    }
}
