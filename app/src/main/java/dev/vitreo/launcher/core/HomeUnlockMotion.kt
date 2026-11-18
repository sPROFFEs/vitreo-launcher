package dev.vitreo.launcher.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State

/** Retained with Home's ViewModel because keyguard can relaunch the activity on unlock. */
internal class HomeUnlockMotion {
    var serial by mutableIntStateOf(0)
        private set
    private val animationProgress = mutableFloatStateOf(1f)
    val progress: State<Float> get() = animationProgress
    var sceneReady = false
    private enum class Phase { Settled, Prepared, Revealing }
    private var phase = Phase.Settled
    private var lockedOverHome = false
    private var unlockAt: Long? = null
    val pending get() = phase == Phase.Prepared
    val holdingIcons get() = phase != Phase.Settled
    var restoreAt: Long? = null
        private set

    fun sleep(keyguardLocked: Boolean) {
        if (!pending) {
            restoreAt = null
            lockedOverHome = false
        }
        lockedOverHome = lockedOverHome || keyguardLocked
        unlockAt = null
        phase = Phase.Prepared
        animationProgress.floatValue = 0f
    }

    fun wake(keyguardLocked: Boolean) {
        if (pending && keyguardLocked) lockedOverHome = true
    }

    fun userPresent(now: Long) {
        if (lockedOverHome && unlockAt == null) unlockAt = now
    }

    fun leaveHome() {
        phase = Phase.Settled
        lockedOverHome = false
        unlockAt = null
        restoreAt = null
        animationProgress.floatValue = 1f
    }

    fun visibleFrame(now: Long) {
        // Window focus and keyguard notifications can arrive after Android
        // draws Home. Allow a short handoff, then show the settled workspace.
        if (pending && restoreAt == null) restoreAt = now + 420L
    }

    fun restoreIfOverdue(now: Long) {
        if (restoreAt?.let { now >= it } == true) leaveHome()
    }

    fun beginReveal(now: Long): Int? {
        restoreIfOverdue(now)
        if (!pending) return null
        val reveal = lockedOverHome && (unlockAt?.let { now - it <= 1_500L } ?: true)
        if (!reveal) {
            leaveHome()
            return null
        }
        restoreAt = now + 800L
        phase = Phase.Revealing
        return ++serial
    }

    fun renderFrame(animationSerial: Int, fraction: Float) {
        if (phase == Phase.Revealing && serial == animationSerial) animationProgress.floatValue = fraction
    }

    fun finishReveal(animationSerial: Int) {
        if (phase == Phase.Revealing && serial == animationSerial) leaveHome()
    }
}
