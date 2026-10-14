package dev.vitreo.launcher.core

import java.util.concurrent.atomic.AtomicInteger

/** Keep Android configuration results tied to the request that launched them. */
class WidgetConfigurationRequests {
    private val callbacks = mutableMapOf<Int, (Boolean) -> Unit>()

    fun register(callback: (Boolean) -> Unit): Int {
        repeat(LAST - FIRST + 1) {
            val code = next.getAndUpdate { if (it == LAST) FIRST else it + 1 }
            if (code !in callbacks) {
                callbacks[code] = callback
                return code
            }
        }
        error("Too many outstanding widget configuration requests")
    }

    fun complete(code: Int, accepted: Boolean): Boolean {
        val callback = callbacks.remove(code) ?: return false
        callback(accepted)
        return true
    }

    private companion object {
        const val FIRST = 0x4000
        const val LAST = 0x7fff
        // Survives Activity recreation; old results cannot match a new request.
        val next = AtomicInteger(FIRST)
    }
}
