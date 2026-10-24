package dev.vitreo.launcher.data

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/** Only call in response to an explicit tap, after Android has dismissed the real keyguard. */
fun launchNotification(context: Context, intent: PendingIntent?): Boolean {
    if(intent==null || context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != false) return false
    return runCatching {
        val options=ActivityOptions.makeBasic().apply {
            if(Build.VERSION.SDK_INT>=34) setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }.toBundle()
        // A result/source-task association would remove the destination with the private lock task.
        intent.send(context,0,null,null,null,null,options)
        true
    }.getOrDefault(false)
}
