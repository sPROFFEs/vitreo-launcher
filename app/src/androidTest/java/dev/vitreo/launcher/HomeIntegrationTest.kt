package dev.vitreo.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.ui.AppReturnSerialKey
import dev.vitreo.launcher.ui.AppOpeningKey
import dev.vitreo.launcher.ui.HomeRevealSerialKey
import dev.vitreo.launcher.ui.LauncherReadyKey
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeIntegrationTest {
    companion object {
        private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command).use { fd ->
                java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText() }
            }

        @JvmField @ClassRule val home = RuleChain.outerRule(LocalWallpaperRule()).around(object : ExternalResource() {
            private var previous: String? = null
            private val animationSettings = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            private var previousAnimations = emptyMap<String, String>()
            override fun before() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                check(shell("getprop ro.kernel.qemu").trim() == "1") { "Requires a disposable emulator." }
                check(shell("pm install -r -t /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
                // Overview is owned by Quickstep. Its Home-stub dismissal races
                // with task loading when the emulator globally forces duration 0.
                // Exercise this cross-process motion with normal system timing.
                previousAnimations = animationSettings.associateWith { shell("settings get global $it").trim() }
                animationSettings.forEach { shell("settings put global $it 1") }
                previous = checkNotNull(shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                    .lineSequence().lastOrNull { it.matches(Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")) })
                // Android 15 chooses HOME task identity at creation. Selecting the
                // default afterwards leaves a standard task that HOME cannot reuse.
                shell("cmd package set-home-activity ${context.packageName}/dev.vitreo.launcher.MainActivity")
            }
            fun restore() {
                previous?.let { shell("cmd package set-home-activity $it") }
                previous = null
                previousAnimations.forEach { (key, value) ->
                    if (value == "null") shell("settings delete global $key")
                    else { check(value.matches(Regex("[0-9.]+"))); shell("settings put global $key $value") }
                }
                previousAnimations = emptyMap()
            }
            override fun after() {
                restore()
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                        .filterIsInstance<MainActivity>().distinct().forEach { it.finishAndRemoveTask() }
                }
                check(shell("pm uninstall dev.vitreo.fixture.uninstall").contains("Success"))
            }
        })
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val compose = createEmptyComposeRule()

    // Instrumentation.startActivitySync always sets an explicit component.
    // AOSP ActivityRecord therefore assigns a standard task to that launch,
    // even with CATEGORY_HOME. The shell's implicit HOME gets the real task type.
    @Before fun launchSystemHome() {
        // Keep a real non-HOME task in Overview. A fresh emulator can otherwise
        // exercise an empty/task-removal transition instead of an app switch.
        check(shell("am start -W -a android.settings.SETTINGS").contains("Status: ok"))
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -f 0x10008000")
    }
    private fun activity(): MainActivity {
        var found: MainActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = listOf(Stage.RESUMED, Stage.STARTED, Stage.CREATED, Stage.PAUSED, Stage.STOPPED)
                .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        return checkNotNull(found) { "System HOME has no MainActivity instance" }
    }

    private fun awaitReady() {
        compose.waitUntil(20_000) {
            runCatching {
                activity().hasWindowFocus() && compose.onAllNodesWithTag("launcher_root")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).any { it.config[LauncherReadyKey] }
            }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    /** Stock Google APIs emulator: Screenshot belongs to Overview, never its Home workspace. */
    private fun overviewVisible(): Boolean = InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any { window ->
        val root = window.root
        window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
            root?.packageName?.toString() in setOf("com.google.android.apps.nexuslauncher", "com.android.launcher3") &&
            root!!.findAccessibilityNodeInfosByViewId("${root.packageName}:id/action_screenshot")
                .any { it.isVisibleToUser }
    }

    @Test fun openingAppAndReturningTwicePreservesSearchWithAnimationsEnabled() {
        awaitReady()
        val main = activity()
        val model = ViewModelProvider(main)[LauncherViewModel::class.java]
        // A separate UID and normal Android activity with default Back behavior.
        // Settings can consume Back in its own navigation without leaving the app.
        val app = model.ui.value.apps.first {
            it.id == "dev.vitreo.fixture.uninstall/dev.vitreo.fixture.uninstall.MainActivity"
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val service = checkNotNull(automation.serviceInfo)
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = service
        compose.onNodeWithTag("library_button").performClick()
        compose.onNodeWithTag("search_field").performTextInput(app.label)
        repeat(2) { attempt ->
            val previous = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[AppReturnSerialKey]
            compose.onNodeWithTag("search_entry:${app.id}").performClick()
            compose.waitUntil(15_000) {
                main.lifecycle.currentState == Lifecycle.State.CREATED && !main.hasWindowFocus() &&
                    automation.windows.any { it.isFocused && it.root?.packageName?.toString() == app.packageName } &&
                    automation.windows.none { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            }
            automation.waitForIdle(500, 5_000)
            if (attempt == 0) shell("input keyevent KEYCODE_BACK")
            else shell("input keyevent KEYCODE_HOME")
            awaitReady()
            compose.waitUntil(15_000) {
                compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config.let {
                    it[AppReturnSerialKey] > previous && !it[AppOpeningKey]
                }
            }
            assertSame("System HOME must reuse its activity", main, activity())
            assertSame(model, ViewModelProvider(activity())[LauncherViewModel::class.java])
            compose.onNodeWithTag("search_field").assertTextEquals(app.label)
            compose.onNodeWithTag("search_entry:${app.id}").assertIsDisplayed()
        }
    }

    @Test fun dismissingForeignMusicOverlayKeepsHomeInPlace() {
        awaitReady()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = checkNotNull(automation.serviceInfo)
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        val main = activity()
        val before = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRevealSerialKey]
        val oldMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
            .find(shell("appops get dev.vitreo.fixture.uninstall SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
        try {
            shell("appops set dev.vitreo.fixture.uninstall SYSTEM_ALERT_WINDOW allow")
            repeat(2) {
                shell("am broadcast -n dev.vitreo.fixture.uninstall/.OverlayReceiver")
                compose.waitUntil(10_000) { !main.hasWindowFocus() && automation.windows.any { it.isFocused && it.root?.packageName?.toString()=="dev.vitreo.fixture.uninstall" } }
                assertEquals("A floating island leaves Home resumed", Lifecycle.State.RESUMED, main.lifecycle.currentState)
                // Focus can be reported before SurfaceFlinger publishes the overlay's
                // input region. UiAutomation synchronizes that transaction; shell input
                // can hit Home instead and leave the foreign window open on API 31.
                val down=android.os.SystemClock.uptimeMillis()
                for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                    val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,100f,450f,0)
                    event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try {
                        val dispatched=automation.injectInputEvent(event,true)
                        // The fixture removes itself on DOWN, cancelling this gesture's
                        // target. UP still finishes input, but may have no recipient.
                        if(action==android.view.MotionEvent.ACTION_DOWN)
                            assertTrue("Foreign overlay receives native DOWN",dispatched)
                    }
                    finally { event.recycle() }
                }
                awaitReady()
                assertEquals("Closing an overlay must not animate a new Home arrival",before,
                    compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRevealSerialKey])
            }
        } finally { shell("appops set dev.vitreo.fixture.uninstall SYSTEM_ALERT_WINDOW $oldMode") }
    }

    @Test fun recentsReturnUsesSharedSystemWallpaperAndRevealsHomeWithoutChangingItsLayout() {
        awaitReady()
        val model = ViewModelProvider(activity())[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val placed = model.ui.value.layout.placedApps()
        val manager = WallpaperManager.getInstance(context)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val service = checkNotNull(automation.serviceInfo)
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = service
        val colorsChanged = java.util.concurrent.atomic.AtomicBoolean(false)
        val colorsListener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) colorsChanged.set(true)
        }
        val previousWallpaperId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        manager.addOnColorsChangedListener(colorsListener, android.os.Handler(android.os.Looper.getMainLooper()))
        try {
            compose.runOnUiThread { model.appearance(original.copy(syncWallpaper = true)) }
            compose.waitUntil(20_000) { model.systemBackgroundVisible.value }
            val wallpaperId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            assertTrue(wallpaperId > 0)
            if (wallpaperId != previousWallpaperId) compose.waitUntil(20_000) { colorsChanged.get() }
            // SystemUI applies color overlays after WallpaperManager reports the
            // new image. Wait for a stable, focused activity/assets pair before
            // asking Quickstep to animate its own window during that relaunch.
            var observed: MainActivity? = null
            var assets: android.content.res.AssetManager? = null
            var stableSince = android.os.SystemClock.uptimeMillis()
            compose.waitUntil(15_000) {
                val current = runCatching { activity() }.getOrNull()
                val now = android.os.SystemClock.uptimeMillis()
                if (current == null || !current.hasWindowFocus() || current !== observed || current.resources.assets !== assets) {
                    observed = current; assets = current?.resources?.assets; stableSince = now
                }
                current != null && current.hasWindowFocus() && now - stableSince >= 2_000
            }
            repeat(2) {
                awaitReady()
                automation.waitForIdle(500, 5_000)
                val before = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRevealSerialKey]
                shell("input keyevent KEYCODE_APP_SWITCH")
                try {
                    compose.waitUntil(15_000) {
                        // Overview uses an input consumer, so Android can report
                        // no focused window and keep HOME resumed underneath.
                        // Accessibility windows contain only visible surfaces.
                        !runCatching { activity().hasWindowFocus() }.getOrDefault(true) && overviewVisible()
                    }
                } catch (failure: Throwable) {
                    android.util.Log.e("VitreoRecentsTest", "Focus=" + shell("dumpsys window").lineSequence()
                        .filter { "mCurrentFocus" in it || "mFocusedApp" in it }.joinToString() +
                        "; windows=" + automation.windows.joinToString {
                            "${it.type}:${it.isFocused}:${it.root?.packageName}"
                        }, failure)
                    throw failure
                }
                // Quickstep can host Overview in either RecentsActivity or its
                // launcher activity. Check its drawn task-action surface, without
                // taking WindowManager's global dump lock during the transition.
                automation.waitForIdle(500, 5_000)
                assertTrue("Overview task actions must be visible", overviewVisible())
                shell("input keyevent KEYCODE_HOME")
                awaitReady()
                compose.waitUntil(10_000) {
                    compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRevealSerialKey] > before
                }
                assertSame("Wallpaper color changes retain the ViewModel", model,
                    ViewModelProvider(activity())[LauncherViewModel::class.java])
                assertEquals(placed, model.ui.value.layout.placedApps())
                assertTrue(model.systemBackgroundVisible.value)
                assertTrue(activity().window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0)
                assertEquals("Return must not rewrite the wallpaper", wallpaperId, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
            }
            // PixelCopy waits for the Compose frame; UiAutomation includes the
            // separate Android wallpaper surface behind the transparent window.
            compose.onRoot().captureToImage().asAndroidBitmap()
            val screenshot = checkNotNull(automation.takeScreenshot())
            val directory = checkNotNull(activity().getExternalFilesDir("review"))
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "recents-return.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.runOnUiThread { model.appearance(model.ui.value.layout.appearance.copy(syncWallpaper = false)) }
            compose.waitUntil(10_000) { !model.systemBackgroundVisible.value }
        } finally {
            manager.removeOnColorsChangedListener(colorsListener)
            compose.runOnUiThread { model.appearance(original) }
        }
    }
}
