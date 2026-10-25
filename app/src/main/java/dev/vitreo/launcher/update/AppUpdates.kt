package dev.vitreo.launcher.update

import android.annotation.SuppressLint
import android.app.*
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.*
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.os.PowerManager
import android.os.UserManager
import androidx.core.content.edit
import dev.vitreo.launcher.BuildConfig
import dev.vitreo.launcher.R
import dev.vitreo.launcher.net.HttpsClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Only public, signed production builds self-update. Choices and installer state are device-local. */
object AppUpdates {
    const val JOB_ID = 80410
    private const val NOTICE_ID = 80411
    private const val CHANNEL = "software-updates"
    private const val DAY = 24*60*60*1000L
    val supported get() = BuildConfig.APPLICATION_ID == "dev.vitreo.launcher"
    @SuppressLint("StaticFieldLeak") // Application context only, never an Activity.
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var foregroundJob: Job? = null
    private var activeCheckJob: Job? = null
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val prefs get() = app.getSharedPreferences("software-updates",Context.MODE_PRIVATE)
    private val directory get() = File(app.filesDir,"app_updates").apply { mkdirs() }
    private val apk get() = File(directory,"stable.apk")
    private val partial get() = File(directory,"stable.part")
    private val installer get() = app.packageManager.packageInstaller
    private val release get() = runCatching { StableRelease.parse(prefs.getString("release",null) ?: return@runCatching null) }.getOrNull()
    private val sessionId get() = prefs.getInt("session",-1)
    private fun pending() = prefs.getString("consent",null)
    fun initialize(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        mutable.value = UpdateState(automatic=prefs.getBoolean("automatic",true), wifiOnly=prefs.getBoolean("wifi",true))
        if (!supported) return
        schedule()
        scope.launch { mutex.withLock {
            val r = release
            if (r != null && r.code <= BuildConfig.VERSION_CODE) clearInstall()
            val id = sessionId
            if (id >= 0 && installer.getSessionInfo(id) == null) {
                prefs.edit { remove("session"); remove("consent") }
            } else if (id >= 0 && installer.getSessionInfo(id)?.isCommitted == false) {
                // A process death between staging and commit must not strand the updater.
                runCatching { installer.abandonSession(id) }
                prefs.edit { remove("session"); remove("consent") }
            }
            publish(status = when { pending()!=null -> "Android needs your confirmation."; release?.code?.let { it > BuildConfig.VERSION_CODE } == true -> "An update is available."; else -> "Updates are checked daily." })
        } }
    }
    fun automatic(value: Boolean) { prefs.edit { putBoolean("automatic",value) }; publish(); schedule(); if(value) onForeground() }
    fun wifiOnly(value: Boolean) { prefs.edit { putBoolean("wifi",value) }; publish(); schedule() }
    private fun schedule() {
        val scheduler = app.getSystemService(JobScheduler::class.java)
        if (!supported || !prefs.getBoolean("automatic",true)) { scheduler.cancel(JOB_ID); return }
        val job = JobInfo.Builder(JOB_ID,ComponentName(app,UpdateJobService::class.java))
            .setRequiredNetworkType(if(prefs.getBoolean("wifi",true)) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .setRequiresBatteryNotLow(true).setPeriodic(DAY,6*60*60*1000L).build()
        val old = scheduler.getPendingJob(JOB_ID)
        if (old == null || old.networkType != job.networkType) scheduler.schedule(job)
    }
    fun onForeground() {
        if (!supported || !::app.isInitialized || !prefs.getBoolean("automatic",true) || foregroundJob?.isActive == true) return
        val now = System.currentTimeMillis()
        val due = now-prefs.getLong("checked",0) !in 0 until DAY
        val retryDue = now-prefs.getLong("failed",0) !in 0 until 60*60*1000L
        if (retryDue && (due || release?.code?.let { it>BuildConfig.VERSION_CODE } == true))
            foregroundJob = scope.launch { check(manual=false) }
    }
    fun checkNow() { if (supported && foregroundJob?.isActive != true) foregroundJob=scope.launch { check(manual=true) } }
    fun installNow() { if (supported && foregroundJob?.isActive != true) foregroundJob=scope.launch { check(manual=true,install=true) } }
    fun cancel() { activeCheckJob?.cancel(); foregroundJob?.cancel(); publish(status="Update paused. You can try again.",busy=false) }
    suspend fun backgroundCheck() { if (supported && prefs.getBoolean("automatic",true)) check(manual=false) }
    private fun publish(status: String = mutable.value.status, busy: Boolean = mutable.value.busy, progress: Int = mutable.value.progress) {
        mutable.value = UpdateState(prefs.getBoolean("automatic",true),prefs.getBoolean("wifi",true),busy,progress,status,
            release?.takeIf { it.code > BuildConfig.VERSION_CODE },pending()!=null,prefs.getLong("checked",0))
    }
    private fun installAllowedNow(): Boolean =
        app.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false &&
        app.getSystemService(UserManager::class.java)?.isUserForeground == true
    private suspend fun check(manual: Boolean, install: Boolean = false) = mutex.withLock {
        if (!supported) return@withLock
        activeCheckJob = currentCoroutineContext()[Job]
        publish("Checking for updates…",true,-1)
        try {
            if (sessionId >= 0 && installer.getSessionInfo(sessionId) != null) {
                publish(if(pending()!=null) "Android needs your confirmation." else "Android is finishing the update."); return@withLock
            }
            prefs.edit { remove("session"); remove("consent"); putLong("attempted",System.currentTimeMillis()) }
            val cached = release
            val fresh = System.currentTimeMillis() - prefs.getLong("checked",0) in 0 until DAY
            val found = if (!manual && cached != null && fresh) cached
                else ReleaseFetcher().fetch(prefs.getLong("apiRetryAt",0)) { retryAt -> prefs.edit { putLong("apiRetryAt",retryAt) } }
            val old = release
            if (old?.code != found.code || old.sha256 != found.sha256) { apk.delete(); partial.delete() }
            prefs.edit { putString("release",found.raw); putLong("checked",if (!manual && cached != null && fresh) prefs.getLong("checked",0) else System.currentTimeMillis()); remove("failed") }
            if (found.code <= BuildConfig.VERSION_CODE) { clearInstall(); publish("Vitreo is up to date."); return@withLock }
            if (!install && !prefs.getBoolean("automatic",true)) { publish("Vitreo ${found.version} is available."); return@withLock }
            if (!manual && app.getSystemService(PowerManager::class.java).isPowerSaveMode) { publish("Update waiting for Battery Saver to turn off."); return@withLock }
            if (prefs.getBoolean("wifi",true) && app.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered) {
                publish("Update waiting for an unmetered connection."); return@withLock
            }
            if (!app.packageManager.canRequestPackageInstalls()) { publish("Allow Vitreo to install updates in Android settings."); return@withLock }
            if (!apk.isFile || runCatching { ApkVerifier.verify(app,apk,found) }.isFailure) {
                require(directory.usableSpace > found.size*2) { "Free some storage space before updating." }
                publish("Downloading Vitreo ${found.version}…",progress=0)
                try {
                    HttpsClient.download(found.url,partial,found.size) { publish(progress=it) }
                    ApkVerifier.verify(app,partial,found)
                    apk.delete(); check(partial.renameTo(apk)) { "Could not save the update." }
                } finally { partial.delete() }
            }
            currentCoroutineContext().ensureActive()
            if (!installAllowedNow()) { publish("Update ready. Installation will wait until you unlock."); return@withLock }
            if (!manual && !prefs.getBoolean("automatic",true)) { publish("Update ready."); return@withLock }
            publish("Verifying and preparing installation…",progress=100)
            val id = SelfUpdateInstaller.stage(app,apk,found)
            prefs.edit(commit = true) { putInt("session",id) }
            // Verification/staging can take seconds. Check keyguard and the foreground user again.
            if (!installAllowedNow() || !manual && !prefs.getBoolean("automatic",true)) {
                installer.abandonSession(id); prefs.edit { remove("session") }
                publish("Update ready. Installation will wait until you unlock."); return@withLock
            }
            currentCoroutineContext().ensureActive()
            SelfUpdateInstaller.commit(app,id)
            publish("Android is installing the update…")
        } catch (e: CancellationException) {
            val id = sessionId
            if (id >= 0 && installer.getSessionInfo(id)?.isCommitted == false) {
                runCatching { installer.abandonSession(id) }; prefs.edit(commit = true) { remove("session") }
            }
            publish("Update paused. You can try again."); throw e
        }
        catch (e: Exception) {
            prefs.edit { putLong("failed",System.currentTimeMillis()) }
            val id = sessionId
            if (id >= 0 && installer.getSessionInfo(id)?.isCommitted == false) {
                runCatching { installer.abandonSession(id) }; prefs.edit { remove("session") }
            }
            publish(when (e) {
                is dev.vitreo.launcher.net.RateLimitedException -> "GitHub is temporarily limiting update checks. Please try again later."
                is java.io.IOException -> "Could not download or save the update. Check your connection and free space."
                is IllegalArgumentException, is IllegalStateException -> "The update could not be verified. Try again later."
                else -> "Android could not prepare this update. Check installation permissions and try again."
            })
        } finally { activeCheckJob = null; publish(busy=false) }
    }
    @Suppress("DEPRECATION")
    fun result(context: Context, intent: Intent) {
        initialize(context)
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)
        if (!supported || id < 0 || id != sessionId) return
        when(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val consent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                prefs.edit { putString("consent",consent.toUri(Intent.URI_INTENT_SCHEME)) }
                publish("Android needs your confirmation.",false); notifyConsent()
            }
            PackageInstaller.STATUS_SUCCESS -> { clearInstall(); publish("Update installed.",false) }
            else -> {
                prefs.edit { remove("session"); remove("consent") }; dismissNotice()
                publish("Installation was cancelled or declined. You can try again.",false)
            }
        }
    }
    fun confirm(context: Context) {
        val id = sessionId
        val info = installer.getSessionInfo(id)
        if (id < 0 || info?.appPackageName != app.packageName || info.installerPackageName != app.packageName) {
            prefs.edit { remove("session"); remove("consent") }; dismissNotice(); publish("Check for updates again."); return
        }
        runCatching { context.startActivity(Intent.parseUri(pending() ?: return,Intent.URI_INTENT_SCHEME)) }
            .onFailure { publish("Open Android installation settings and try again.") }
    }
    private fun clearInstall() {
        val id = sessionId; if(id>=0) runCatching { installer.abandonSession(id) }
        prefs.edit { remove("session"); remove("consent"); remove("release") }
        apk.delete(); partial.delete(); dismissNotice()
    }
    private fun dismissNotice() { app.getSystemService(NotificationManager::class.java).cancel(NOTICE_ID) }
    @SuppressLint("MissingPermission") // Permission denial is handled by canNotify + runCatching.
    private fun notifyConsent() {
        val manager=app.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Software updates",NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(app,0,Intent(app,UpdatesActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { manager.notify(NOTICE_ID,Notification.Builder(app,CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_update).setContentTitle("Vitreo update is ready")
            .setContentText("Tap to review Android's installation request.").setContentIntent(open).setAutoCancel(true).build()) }
    }
}
data class UpdateState(val automatic: Boolean = true, val wifiOnly: Boolean = true, val busy: Boolean = false,
    val progress: Int = -1, val status: String = "Updates are checked daily.", val release: StableRelease? = null,
    val needsConsent: Boolean = false, val checkedAt: Long = 0)
