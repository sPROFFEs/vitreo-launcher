package dev.vitreo.launcher

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.graphics.WallpaperStore
import dev.vitreo.launcher.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PerformanceRecoveryDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clockUpdatesItsConsumersWithoutRecomposingTheWorkspaceParent() {
        val tick = mutableLongStateOf(1_000L)
        val parentCompositions = AtomicInteger()
        compose.setContent {
            CompositionLocalProvider(LocalHomeClock provides tick) {
                SideEffect { parentCompositions.incrementAndGet() }
                HomeTimeContent { Text("Clock $it") }
            }
        }
        compose.onNodeWithText("Clock 1000").assertExists()
        val before = parentCompositions.get()
        compose.runOnIdle { tick.longValue = 2_000L }
        compose.onNodeWithText("Clock 2000").assertExists()
        assertEquals("A clock tick must not invalidate the parent grid", before, parentCompositions.get())
    }

    @Test fun repeatedHomeAndLockLoadsShareBoundedWallpaperFrames() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val first = WallpaperStore(app).load(false, 9)
        assertTrue(maxOf(first.sharp.width, first.sharp.height) <= 1600)
        repeat(6) {
            val again = WallpaperStore(app).load(false, 9)
            assertSame("Returning to Lock must not decode/blur the wallpaper again", first, again)
            assertFalse("Cached frames can still be drawn after another surface closes", again.sharp.isRecycled)
        }
        val other = WallpaperStore(app).load(false, 0)
        assertNotSame(first, other)
        assertSame(first, WallpaperStore(app).load(false, 9))
    }

    @Test fun repeatedThemeIconsShareBoundedArtWithoutRecyclingVisibleImages() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val first = checkNotNull(dev.vitreo.launcher.graphics.BundledArtwork.load(app, R.drawable.ios_browser))
        assertTrue(maxOf(first.width, first.height) <= 320)
        repeat(12) { assertSame(first, dev.vitreo.launcher.graphics.BundledArtwork.load(app, R.drawable.ios_browser)) }
        dev.vitreo.launcher.graphics.BundledArtwork.clear()
        assertFalse("Eviction must not invalidate images still owned by Compose", first.isRecycled)
    }

    @Test fun warmResumesDoNotStartAnotherPackageAndIconScan() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = ViewModelStore()
        lateinit var model: LauncherViewModel
        instrumentation.runOnMainSync {
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(
                ApplicationProvider.getApplicationContext()))[LauncherViewModel::class.java]
        }
        try {
            compose.waitUntil(30_000) { model.ui.value.ready }
            val before = model.catalogRefreshCount
            instrumentation.runOnMainSync { repeat(20) { model.refreshIfStale() } }
            assertEquals("Quick app/lock returns must reuse the catalog", before, model.catalogRefreshCount)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun editorCanRemoveWidgetsAndExposeAnEmptyAddAreaWithoutSaving() {
        var saved: Appearance? = null
        var cancelled = false
        compose.setContent {
            VitreoTheme(appearance = Appearance(reducedEffects = true)) {
                LockScreenEditor(Appearance(reducedEffects = true), onCancel = { cancelled = true },
                    onSave = { saved = Appearance(lockScreen = it) })
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lock_remove_widget:battery").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lock_remove_widget:battery").performClick()
        compose.onNodeWithTag("lock_widget:battery").assertDoesNotExist()
        compose.onNodeWithTag("lock_remove_widget:alarm").performClick()
        compose.onNodeWithTag("lock_add_widgets").assertExists().performClick()
        compose.onNodeWithTag("lock_widget_position").assertExists()
        compose.onNodeWithTag("lock_editor_cancel").performClick()
        assertTrue(cancelled)
        assertNull("Editor drafts must not commit without Done", saved)
    }
}
