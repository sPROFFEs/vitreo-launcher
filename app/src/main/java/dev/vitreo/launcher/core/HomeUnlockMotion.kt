package dev.vitreo.launcher.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Retained with Home's ViewModel because keyguard can relaunch the activity on unlock. */
internal class HomeUnlockMotion {
    var serial by mutableIntStateOf(0)
        private set
    private var sleptOverHome = false
    private var lockedOverHome = false
    private var unlockAt: Long? = null
    private var consumed = 0
    val pending get() = sleptOverHome

    fun sleep(keyguardLocked: Boolean) {
        sleptOverHome = true
        lockedOverHome = lockedOverHome || keyguardLocked
        unlockAt = null
    }

    fun wake(keyguardLocked: Boolean) {
        if (sleptOverHome && keyguardLocked) lockedOverHome = true
    }

    fun userPresent(now: Long) {
        if (lockedOverHome && unlockAt == null) unlockAt = now
    }

    fun leaveHome() {
        sleptOverHome = false
        lockedOverHome = false
        unlockAt = null
    }

    fun focusUnlockedHome(now: Long) {
        val reveal = lockedOverHome && (unlockAt?.let { now - it <= 1_500L } ?: true)
        leaveHome()
        if (reveal) serial++
    }

    fun consumeReveal(): Boolean {
        if (serial == consumed) return false
        consumed = serial
        return true
    }
}
