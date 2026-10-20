package dev.vitreo.launcher.core

/** A single immediate recovery for a quick off/on cycle, never a deferred screen-on launch. */
class LockScreenWakeWindow {
    private var offAt: Long? = null
    fun screenOff(now: Long) { offAt = now }
    fun cancel() { offAt = null }
    fun consumeWake(now: Long): Boolean {
        val off = offAt ?: return false
        offAt = null
        return now >= off && now - off <= 600L
    }
}
