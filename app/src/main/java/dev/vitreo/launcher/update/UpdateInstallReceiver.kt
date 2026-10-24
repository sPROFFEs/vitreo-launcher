package dev.vitreo.launcher.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { AppUpdates.result(context,intent) }
}
