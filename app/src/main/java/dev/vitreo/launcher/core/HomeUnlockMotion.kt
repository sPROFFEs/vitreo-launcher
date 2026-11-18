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

    fun sleep(keyguardLocked: Boolean) {
        lockedOverHome = keyguardLocked || (pending && lockedOverHome)
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
        unlockAt = null
    }

    fun showUnlockedHome(now: Long) {
        if (!pending) return
        val reveal = lockedOverHome && (unlockAt?.let { now - it <= 1_500L } ?: true)
        if (reveal) {
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
