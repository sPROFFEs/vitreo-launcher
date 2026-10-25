package dev.vitreo.launcher.data

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Android owns channel importance. These links never cancel alerts or change notification policy. */
object NotificationSettingsNavigation {
    fun category(packageName:String,channelId:String):Intent =
        if(channelId.isBlank()) app(packageName) else Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,packageName).putExtra(Settings.EXTRA_CHANNEL_ID,channelId)
    fun app(packageName:String)=Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName)
    // This settings action exists on older builds, before its constant became public in API 33.
    fun allApps()=Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS")
    fun open(context:Context,packageName:String?=null,channelId:String=""):Boolean {
        val intents=if(packageName==null) listOf(allApps(),Intent(Settings.ACTION_SETTINGS))
            else listOf(category(packageName,channelId),app(packageName),allApps())
        return intents.any { intent -> runCatching {
            if(context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess }
    }
}
