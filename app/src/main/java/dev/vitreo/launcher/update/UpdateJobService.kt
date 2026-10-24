package dev.vitreo.launcher.update

import android.app.job.JobService
import android.app.job.JobParameters
import kotlinx.coroutines.*

class UpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var stopped = false
    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        AppUpdates.initialize(this)
        job = scope.launch { try { AppUpdates.backgroundCheck() } finally { if (!stopped) jobFinished(params,false) } }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { stopped = true; job?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
