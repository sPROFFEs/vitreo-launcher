package dev.vitreo.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.data.StateStore
import org.junit.rules.ExternalResource

/** Keep unrelated UI scenarios independent of SystemUI's asynchronous color overlays. */
class LocalWallpaperRule : ExternalResource() {
    private var previous: Boolean? = null
    private val store get() = StateStore(ApplicationProvider.getApplicationContext<Context>())

    override fun before() {
        val emulator = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("getprop ro.kernel.qemu").use { fd ->
                java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText().trim() }
            }
        check(emulator == "1") { "Requires a disposable emulator." }
        val state = store.load().state
        previous = state.appearance.syncWallpaper
        store.save(state.copy(appearance = state.appearance.copy(syncWallpaper = false)))
    }

    override fun after() {
        previous?.let { enabled ->
            val state = store.load().state
            store.save(state.copy(appearance = state.appearance.copy(syncWallpaper = enabled)))
        }
    }
}
