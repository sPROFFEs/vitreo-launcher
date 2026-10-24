package dev.vitreo.launcher

import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.net.HttpsClient
import dev.vitreo.launcher.update.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateSafetyDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun debugCannotScheduleProductionUpdatesAndComponentsStayProtected() {
        assertFalse(AppUpdates.supported)
        assertNull(context.getSystemService(JobScheduler::class.java).getPendingJob(AppUpdates.JOB_ID))
        val pm=context.packageManager
        val job=pm.getServiceInfo(ComponentName(context,UpdateJobService::class.java),0)
        assertEquals("android.permission.BIND_JOB_SERVICE",job.permission)
        assertFalse(pm.getReceiverInfo(ComponentName(context,UpdateInstallReceiver::class.java),0).exported)
        assertFalse(pm.getActivityInfo(ComponentName(context,UpdatesActivity::class.java),0).exported)
        AppUpdates.result(context,Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID,123).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS))
        assertFalse(AppUpdates.state.value.needsConsent)
    }
    private fun release(file: File,code: Long)=StableRelease(BuildConfig.VERSION_NAME,code,"",file.length(),ApkVerifier.sha256(file),"","","")
    @Test fun intactInstalledApkCannotBeInstalledAsAnUpdateOrRollback() {
        val file=File(context.applicationInfo.sourceDir)
        assertTrue(runCatching { ApkVerifier.verify(context,file,release(file,BuildConfig.VERSION_CODE.toLong())) }.isFailure)
    }
    @Test fun tamperedOrNonApkDataNeverReachTheInstaller() {
        val file=File(context.cacheDir,"invalid-update.apk")
        try {
            file.writeText("not an apk")
            val metadata=release(file,BuildConfig.VERSION_CODE+1L)
            assertTrue(runCatching { ApkVerifier.verify(context,file,metadata) }.isFailure)
            file.appendText("tampered")
            assertTrue(runCatching { ApkVerifier.verify(context,file,metadata) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun unsafeSchemesHostsAndPortsAreBlockedBeforeAnyConnection() = runBlocking {
        for(url in listOf("http://github.com/file","https://example.com/file","https://github.com:8080/file","https://user@github.com/file")) {
            assertFalse(HttpsClient.allowed(url,HttpsClient.githubHosts))
            assertTrue(runCatching { HttpsClient.text(url,HttpsClient.githubHosts) }.isFailure)
        }
    }
}
