package dev.vitreo.launcher.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Retained with Home's ViewModel because keyguard can relaunch the activity on unlock. */
internal class HomeUnlockMotion {
    var serial by mutableIntStateOf(0)
        private set
    private enum class Phase { Settled, Prepared, Revealing }
    private var phase by mutableStateOf(Phase.Settled)
    private var lockedOverHome = false
    private var unlockAt: Long? = null
    private var consumed = 0
    val pending get() = phase == Phase.Prepared
    val holdingIcons get() = phase != Phase.Settled
    var stoppedForSleep = false
        private set
    var restoreAt: Long? = null
        private set

    fun sleep(keyguardLocked: Boolean, stopped: Boolean = false) {
        if (!pending) restoreAt = null
        lockedOverHome = keyguardLocked || (pending && lockedOverHome)
        stoppedForSleep = stopped || (pending && stoppedForSleep)
        unlockAt = null
        phase = Phase.Prepared
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
        stoppedForSleep = false
        unlockAt = null
        restoreAt = null
    }

    fun visibleFrame(now: Long) {
        // Window focus and keyguard notifications can arrive after Android
        // draws Home. Allow a short handoff, then show the settled workspace.
        if (pending && restoreAt == null) restoreAt = now + 160L
    }

    fun restoreIfOverdue(now: Long) {
        if (restoreAt?.let { now >= it } == true) leaveHome()
    }

    fun showUnlockedHome(now: Long) {
        restoreIfOverdue(now)
        if (!pending) return
        val reveal = lockedOverHome && (unlockAt?.let { now - it <= 1_500L } ?: true)
        if (reveal) {
            restoreAt = now + 600L
            serial++
            phase = Phase.Revealing
        } else leaveHome()
    }

    fun consumeReveal(): Boolean {
        if (phase != Phase.Revealing || serial == consumed) return false
        consumed = serial
        return true
    }

    fun finishReveal(animationSerial: Int) {
        if (phase == Phase.Revealing && serial == animationSerial) leaveHome()
    }

    /** Draw layers must stay prepared until the matching animation has been installed. */
    fun frameProgress(animationSerial: Int, progress: Float): Float = when (phase) {
        Phase.Settled -> 1f
        Phase.Prepared -> 0f
        Phase.Revealing -> if (animationSerial == serial) progress else 0f
    }
}
