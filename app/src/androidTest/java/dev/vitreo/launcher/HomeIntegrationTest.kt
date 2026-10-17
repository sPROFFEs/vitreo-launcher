package dev.vitreo.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.ui.HomeRevealSerialKey
import dev.vitreo.launcher.ui.LauncherReadyKey
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeIntegrationTest {
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText() }
        }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val home = object : ExternalResource() {
        private var previous: String? = null
        override fun before() {
            check(shell("getprop ro.kernel.qemu").trim() == "1") { "Requires a disposable emulator." }
            previous = checkNotNull(shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                .lineSequence().lastOrNull { it.matches(Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")) })
            // Android 15 chooses HOME task identity at creation. Selecting the
            // default afterwards leaves a standard task that HOME cannot reuse.
            shell("cmd package set-home-activity ${context.packageName}/dev.vitreo.launcher.MainActivity")
        }
        override fun after() { previous?.let { shell("cmd package set-home-activity $it") } }
    }
    private val compose = AndroidComposeTestRule(
        ActivityScenarioRule<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),
        activityProvider = { rule ->
            var activity: MainActivity? = null
            rule.scenario.onActivity { activity = it }
            checkNotNull(activity)
        }
    )
    @get:Rule val rules: RuleChain = RuleChain.outerRule(home).around(compose)

    private fun awaitReady() {
        compose.waitUntil(20_000) {
            compose.activityRule.scenario.state == Lifecycle.State.RESUMED && runCatching {
                compose.activity.hasWindowFocus() && compose.onAllNodesWithTag("launcher_root")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).any { it.config[LauncherReadyKey] }
            }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    @Test fun recentsReturnUsesSharedSystemWallpaperAndRevealsHomeWithoutChangingItsLayout() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val placed = model.ui.value.layout.placedApps()
        val manager = WallpaperManager.getInstance(context)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val service = checkNotNull(automation.serviceInfo)
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = service
        try {
            compose.runOnUiThread { model.appearance(original.copy(syncWallpaper = true, wallpaperDim = .23f)) }
            compose.waitUntil(20_000) { model.systemBackgroundVisible.value }
            val wallpaperId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            assertTrue(wallpaperId > 0)
            repeat(2) {
                awaitReady()
                val before = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRevealSerialKey]
                shell("input keyevent KEYCODE_APP_SWITCH")
                compose.waitUntil(15_000) {
                    // Focus loss alone precedes the actual task transition.
                    // Wallpaper colors can also relaunch activities in this gap.
                    compose.activityRule.scenario.state == Lifecycle.State.CREATED &&
                        shell("dumpsys activity activities").lineSequence().any {
                            ("mResumedActivity" in it || "topResumedActivity" in it) && "RecentsActivity" in it
                        } && automation.windows.any {
                            it.isFocused && it.root?.packageName?.toString() != context.packageName
                        }
                }
                automation.waitForIdle(200, 5_000)
                shell("input keyevent KEYCODE_HOME")
                awaitReady()
                compose.waitUntil(10_000) {
                    compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRevealSerialKey] > before
                }
                assertSame("Wallpaper color changes retain the ViewModel", model,
                    ViewModelProvider(compose.activity)[LauncherViewModel::class.java])
                assertEquals(placed, model.ui.value.layout.placedApps())
                assertTrue(model.systemBackgroundVisible.value)
                assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0)
                assertEquals("Return must not rewrite the wallpaper", wallpaperId, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
            }
            // PixelCopy waits for the Compose frame; UiAutomation includes the
            // separate Android wallpaper surface behind the transparent window.
            compose.onRoot().captureToImage().asAndroidBitmap()
            val screenshot = checkNotNull(automation.takeScreenshot())
            val directory = checkNotNull(compose.activity.getExternalFilesDir("review"))
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "recents-return.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.runOnUiThread { model.appearance(model.ui.value.layout.appearance.copy(syncWallpaper = false)) }
            compose.waitUntil(10_000) { !model.systemBackgroundVisible.value }
        } finally { compose.runOnUiThread { model.appearance(original) } }
    }
}
