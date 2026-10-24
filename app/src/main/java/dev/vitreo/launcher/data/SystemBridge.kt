package dev.vitreo.launcher.data

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ephemeral, user-authorized data. Never serialized, logged, backed up or sent over a network. */
data class NoticeAction(val title: String, val intent: PendingIntent)
data class Notice(val key: String, val packageName: String, val app: String, val title: String,
    val text: String, val time: Long, val clearable: Boolean, val intent: PendingIntent?,
    val actions: List<NoticeAction> = emptyList(), val visibility: Int = Notification.VISIBILITY_PRIVATE,
    val channelVisibility: Int = NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE)
data class NowPlaying(val packageName: String = "", val title: String = "Not Playing", val artist: String = "",
    val artwork: Bitmap? = null, val playing: Boolean = false, val actions: Long = 0,
    val position: Long = 0, val duration: Long = 0, val updatedAt: Long = 0, val speed: Float = 0f) {
    val available get() = packageName.isNotEmpty()
    fun supports(action: Long) = actions and action != 0L
}
data class BridgeState(val access: Boolean = false, val connected: Boolean = false,
    val notices: List<Notice> = emptyList(), val media: NowPlaying = NowPlaying())

object SystemBridge {
    private val mutable = MutableStateFlow(BridgeState())
    val state = mutable.asStateFlow()
    private var service: VitreoNotificationListener? = null
    private var configured = false
    private var notifications = false
    private var media = false
    private var manager: MediaSessionManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = mutableListOf<MediaController>()
    private var selected: MediaController? = null
    private var artworkSource: Bitmap? = null
    private var artworkPreview: Bitmap? = null
    private var observing = false
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { refreshSessions() }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onSessionDestroyed() { refresh() }
    }
    fun component(context: Context) = ComponentName(context, VitreoNotificationListener::class.java)
    fun hasAccess(context: Context): Boolean = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(component(context))
    fun accessIntent(context: Context) = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
    fun configure(context: Context, notifications: Boolean, media: Boolean) {
        configured = true
        this.notifications = notifications; this.media = media
        val access = hasAccess(context)
        mutable.value = mutable.value.copy(access = access,
            notices = if (access && notifications) mutable.value.notices else emptyList(),
            media = if (access && media) mutable.value.media else NowPlaying())
        if (!access) { stopMedia(); mutable.value = BridgeState(); return }
        if (media && service != null) startMedia(context) else stopMedia()
        refresh()
    }
    internal fun connect(listener: VitreoNotificationListener) {
        service = listener
        mutable.value = mutable.value.copy(connected = true)
        if (configured) configure(listener, notifications, media)
        else { val a = StateStore(listener).load().state.appearance; configure(listener, a.notificationCenter || dev.vitreo.launcher.lock.LockScreenRuntime.enabled.value && (a.lockScreen.showNotifications || a.lockScreen.notificationCount), a.mediaIntegration) }
    }
    internal fun disconnect(listener: VitreoNotificationListener) {
        if (service !== listener) return
        stopMedia(); service = null
        mutable.value = BridgeState(access = hasAccess(listener))
    }
    fun refresh() {
        val listener = service ?: return
        if (!hasAccess(listener)) { stopMedia(); mutable.value = BridgeState(); return }
        val notices = if (!notifications) emptyList() else runCatching {
            listener.activeNotifications.asSequence()
                .filter { it.user == Process.myUserHandle() && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .sortedByDescending { it.postTime }.take(100).map { sbn ->
                    val n = sbn.notification
                    val app = runCatching { listener.packageManager.getApplicationLabel(
                        listener.packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
                    Notice(sbn.key, sbn.packageName, app.take(160), n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(256).orEmpty(),
                        (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.take(2000).orEmpty(),
                        sbn.postTime, sbn.isClearable, n.contentIntent,
                        n.actions.orEmpty().filter { it.remoteInputs.isNullOrEmpty() }.take(3).mapNotNull {
                            val intent = it.actionIntent ?: return@mapNotNull null
                            NoticeAction(it.title?.toString()?.take(80).orEmpty(), intent)
                        }, visibility = n.visibility, channelVisibility = NotificationListenerService.Ranking().let { ranking ->
                            if (listener.currentRanking.getRanking(sbn.key, ranking)) ranking.channel?.lockscreenVisibility ?: NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE else NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE
                        })
                }.toList()
        }.getOrDefault(emptyList())
        mutable.value = mutable.value.copy(notices = notices)
        if (media && !observing) startMedia(listener) else if (media) refreshSessions()
    }
    fun dismiss(key: String) {
        if (notifications && mutable.value.notices.any { it.key == key && it.clearable }) runCatching { service?.cancelNotification(key) }
    }
    fun clear() { mutable.value.notices.filter { it.clearable }.forEach { dismiss(it.key) } }
    private fun startMedia(context: Context) {
        if (observing) return
        manager = context.getSystemService(MediaSessionManager::class.java)
        runCatching {
            manager?.addOnActiveSessionsChangedListener(sessions, component(context), handler)
            observing = true
            bindControllers(manager?.getActiveSessions(component(context)).orEmpty())
        }.onFailure { stopMedia() }
    }
    private fun stopMedia() {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }; controllers.clear(); selected = null
        if (observing) runCatching { manager?.removeOnActiveSessionsChangedListener(sessions) }
        observing = false; manager = null; artworkSource = null; artworkPreview = null
        mutable.value = mutable.value.copy(media = NowPlaying())
    }
    private fun refreshSessions() {
        val listener = service ?: return
        if (!media || !observing) return
        runCatching {
            val current = manager?.getActiveSessions(component(listener)).orEmpty().take(12)
            if (current.map { it.sessionToken } != controllers.map { it.sessionToken }) bindControllers(current)
            else publishMedia()
        }.onFailure { stopMedia() }
    }
    private fun bindControllers(values: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers.clear(); controllers.addAll(values.take(12))
        controllers.forEach { runCatching { it.registerCallback(callback, handler) } }
        publishMedia()
    }
    private fun publishMedia() {
        if (!media) return
        val current = selectMediaSession(controllers) { it.playbackState?.state }
        selected = current
        val snapshot = runCatching {
            if (current == null) { artworkSource = null; artworkPreview = null; return@runCatching NowPlaying() }
            val metadata = current.metadata
            val playback = current.playbackState
            val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val validArt = art?.takeUnless { it.isRecycled }
            if (validArt !== artworkSource) {
                artworkSource = validArt
                artworkPreview = validArt?.let {
                    val factor = minOf(1f, 512f / maxOf(it.width, it.height))
                    if (factor < 1f) Bitmap.createScaledBitmap(it, (it.width * factor).toInt().coerceAtLeast(1), (it.height * factor).toInt().coerceAtLeast(1), true) else it
                }
            }
            NowPlaying(current.packageName, (metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))?.take(256) ?: "Audio",
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.take(256).orEmpty(), artworkPreview,
                playback?.state == PlaybackState.STATE_PLAYING, playback?.actions ?: 0,
                playback?.position?.coerceAtLeast(0) ?: 0, metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
                playback?.lastPositionUpdateTime ?: 0, playback?.playbackSpeed ?: 0f)
        }.getOrDefault(NowPlaying())
        mutable.value = mutable.value.copy(media = snapshot)
    }
    fun command(code: Int): Boolean = runCatching {
        val player = selected ?: return false
        val state = mutable.value.media
        if (!media || service?.let { hasAccess(it) } != true) return false
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                val action = if (state.playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
                if (!state.supports(action) && !state.supports(PlaybackState.ACTION_PLAY_PAUSE)) return false
                if (state.playing) player.transportControls.pause() else player.transportControls.play()
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { if (!state.supports(PlaybackState.ACTION_SKIP_TO_NEXT)) return false; player.transportControls.skipToNext() }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (!state.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) return false; player.transportControls.skipToPrevious() }
            else -> return false
        }
        true
    }.getOrDefault(false)
    fun seek(position: Long): Boolean = runCatching {
        if (!media || service?.let { hasAccess(it) } != true || !state.value.media.supports(PlaybackState.ACTION_SEEK_TO)) return false
        selected?.transportControls?.seekTo(position.coerceIn(0, state.value.media.duration)) ?: return false
        true
    }.getOrDefault(false)
    fun openPlayer(): Boolean = runCatching {
        if (!media || service?.let { hasAccess(it) } != true) return false
        selected?.sessionActivity?.send() ?: return false
        true
    }.getOrDefault(false)
}

class VitreoNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = SystemBridge.connect(this)
    override fun onListenerDisconnected() = SystemBridge.disconnect(this)
    override fun onNotificationPosted(sbn: StatusBarNotification?) = SystemBridge.refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = SystemBridge.refresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = SystemBridge.refresh()
    override fun onDestroy() { SystemBridge.disconnect(this); super.onDestroy() }
}
