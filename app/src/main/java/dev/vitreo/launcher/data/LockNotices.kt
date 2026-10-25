package dev.vitreo.launcher.data

import android.app.Notification

/** A lock card cannot accidentally render private text or retain an executable PendingIntent. */
data class LockNotice(val key: String, val app: String, val count: Int, val time: Long, val packageName: String = "", val appIcon: android.graphics.Bitmap? = null)
fun lockNotices(notices: List<Notice>): List<LockNotice> = notices
    .filter { it.visibility != Notification.VISIBILITY_SECRET && it.channelVisibility != Notification.VISIBILITY_SECRET }
    .groupBy { it.packageName }.values.map { group ->
        val newest=group.maxBy { it.time }
        val actionable=group.filter { it.intent!=null }.maxByOrNull { it.time } ?: newest
        LockNotice(actionable.key,newest.app,group.size,newest.time,newest.packageName,newest.appIcon)
    }.sortedByDescending { it.time }
