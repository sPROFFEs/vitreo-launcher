package dev.vitreo.launcher

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.appwidget.AppWidgetManager
import android.graphics.RuntimeShader
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.media.AudioManager
import dev.vitreo.launcher.core.DEFAULT_TODAY_SECTIONS
import dev.vitreo.launcher.core.DEFAULT_CONTROL_SHORTCUTS
import dev.vitreo.launcher.ui.DockVisibilityKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.graphics.GlassDiagnostics
import dev.vitreo.launcher.ui.AppReturnSerialKey
import dev.vitreo.launcher.ui.AppOpeningKey
import dev.vitreo.launcher.ui.LauncherReadyKey
import dev.vitreo.launcher.ui.HomeRowsKey
import dev.vitreo.launcher.ui.PhotoZoomKey
import dev.vitreo.launcher.data.AppCatalog
import dev.vitreo.launcher.data.WidgetController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {
    companion object {
        // Actual Android wallpaper synchronization is covered by HomeIntegrationTest.
        // Color overlays can recreate every activity several seconds after a write.
        @JvmField @ClassRule val wallpaper = LocalWallpaperRule()
    }
    // Android distinguishes HOME and ordinary app tasks. Start as HOME so
    // subsequent HOME intents reuse the same task and still match ActivityScenario.
    @get:Rule val compose = AndroidComposeTestRule(
        activityRule = ActivityScenarioRule<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        ),
        activityProvider = { rule ->
            var activity: MainActivity? = null
            rule.scenario.onActivity { activity = it }
            checkNotNull(activity)
        }
    )
    private fun awaitReady() {
        try {
            compose.waitUntil(timeoutMillis = 20_000) {
                runCatching {
                    val activity = compose.activity
                    val nodes = compose.onAllNodesWithTag("launcher_root").fetchSemanticsNodes(atLeastOneRootRequired = false)
                    activity.hasWindowFocus() && compose.activityRule.scenario.state == Lifecycle.State.RESUMED &&
                        nodes.any { it.config.contains(LauncherReadyKey) && it.config[LauncherReadyKey] }
                }.getOrDefault(false)
            }
            compose.waitForIdle()
        } catch (failure: Throwable) {
            val activity = compose.activity
            val model = ViewModelProvider(activity)[LauncherViewModel::class.java]
            val roots = compose.onAllNodesWithTag("launcher_root").fetchSemanticsNodes(atLeastOneRootRequired = false)
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val focus = automation.executeShellCommand("dumpsys window").use { fd ->
                java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { reader ->
                    reader.lineSequence().filter { "mCurrentFocus" in it || "mFocusedApp" in it }.joinToString()
                }
            }
            android.util.Log.e("VitreoReadyTest", "state=${compose.activityRule.scenario.state}; focus=${activity.hasWindowFocus()}; " +
                "ready=${model.ui.value.ready}; frame=${model.wallpaper.value != null}; background=${model.background.value != null}; " +
                "roots=${roots.map { it.config.getOrNull(LauncherReadyKey) }}; $focus", failure)
            val directory = checkNotNull(activity.getExternalFilesDir("review")); directory.mkdirs()
            val image = File(directory, "readiness-failure.png")
            if (!image.exists()) automation.takeScreenshot()?.let { bitmap ->
                image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            throw failure
        }
    }

    @After fun dismissSearchAndImeBeforeScenarioCloses() {
        // ActivityScenario finishes its task using another activity. Hide the
        // search IME and settle our own window first, so its pending focus/input
        // connection cannot leak into the next independently created scenario.
        if (compose.activityRule.scenario.state != Lifecycle.State.DESTROYED) {
            returnHome()
            // Accessibility can retain an empty IME window after Gboard hides.
            // The activity's platform insets report its actual visibility.
            compose.waitUntil(5_000) {
                compose.activity.window.decorView.rootWindowInsets
                    ?.isVisible(android.view.WindowInsets.Type.ime()) == false
            }
        }
    }
    private fun captureEvidence(name: String, dialogTag: String? = null) {
        compose.waitForIdle()
        // PixelCopy waits for the current Compose frame. A raw UiAutomation
        // screenshot can still contain the previous size/theme after semantics update.
        val screenshot = if (dialogTag == null) compose.onRoot().captureToImage().asAndroidBitmap() else {
            compose.onNodeWithTag(dialogTag).assertIsDisplayed()
            // Dialog and IME are separate windows; PixelCopy of the activity misses both.
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.waitForIdle(200, 5_000)
            checkNotNull(automation.takeScreenshot())
        }
        // adb pull copies the file as bytes; it does not depend on run-as output framing.
        val directory = checkNotNull(compose.activity.getExternalFilesDir("review"))
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    private fun openSettings() {
        compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
        compose.onNodeWithTag("today_page").assertExists()
        compose.onNodeWithTag("settings_button").performClick()
    }
    private fun homeIconTags(): List<String> = compose.onAllNodes(SemanticsMatcher("home icon") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("home_entry:") == true
    }).fetchSemanticsNodes().sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
        .map { it.config[SemanticsProperties.TestTag] }


    @Test fun lockEditorAndBothPreviewsKeepActivationOffAndNeverAuthenticate() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val context = compose.activity
        assertTrue(!dev.vitreo.launcher.lock.LockScreenRuntime.enabled.value)
        try {
            openSettings()
            compose.onNodeWithText("Lock Screen").performScrollTo().performClick()
            compose.onNodeWithTag("lock_enable").assertIsOff()
            compose.onNodeWithText("Cyberpunk", substring = false).performScrollTo().performClick()
            assertEquals(2, model.ui.value.layout.appearance.lockScreen.theme)
            compose.onNodeWithText("iOS", substring = false).performClick()
            assertEquals(1, model.ui.value.layout.appearance.lockScreen.theme)
            compose.onNodeWithTag("lock_enable").assertIsOff()
            returnHome()
            listOf("lock-ios" to 1, "lock-cyber" to 2, "lock-large-clock" to 1).forEach { (name, theme) ->
                compose.runOnUiThread {
                    dev.vitreo.launcher.lock.LockScreenActivity.preview(context, original.copy(lockScreen =
                        dev.vitreo.launcher.core.LockScreenSettings(theme = theme, clockSize = if (name == "lock-large-clock") 132f else 104f,
                            clockStyle = if (name == "lock-large-clock") 2 else 0, wallpaper = if (theme == 2) 5 else -1)))
                }
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("lock_screen").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("lock_wallpaper").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                compose.onNodeWithTag("lock_clock").assertIsDisplayed()
                if (theme == 2) compose.onNodeWithText("IDENTITY VERIFICATION // ANDROID").assertIsDisplayed()
                else compose.onNodeWithText("IDENTITY VERIFICATION // ANDROID").assertDoesNotExist()
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("lock_clock").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val image = compose.onNodeWithTag("lock_screen").captureToImage().asAndroidBitmap()
                val directory = checkNotNull(context.getExternalFilesDir("review")); check(directory.exists() || directory.mkdirs())
                File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                assertTrue("$name clock must fit: " + layouts.joinToString { "size=${it.size}, constraints=${it.layoutInput.constraints}, font=${it.layoutInput.style.fontSize}, lineHeight=${it.layoutInput.style.lineHeight}, widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}" },
                    layouts.isNotEmpty() && layouts.none { it.didOverflowWidth || it.didOverflowHeight })
                compose.onNodeWithTag("lock_unlock").performClick()
                compose.onNodeWithTag("lock_message").assertTextEquals("Preview only. Android handles real authentication.")
                assertTrue(!dev.vitreo.launcher.lock.LockScreenRuntime.enabled.value)
                if (name == "lock-ios") returnHome() // Reopening a retained preview must use the new style.
                else compose.onNodeWithTag("lock_close").performClick()
                awaitReady()
            }
        } finally {
            returnHome()
            compose.runOnUiThread { model.appearance(original) }
        }
    }

    private fun returnHome() {
        val delivered = java.util.UUID.randomUUID().toString()
        val marker = "dev.vitreo.launcher.test.HOME_DELIVERY"
        compose.runOnUiThread { compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(marker, delivered)) }
        compose.waitUntil(10_000) {
            // An already visible Home is not proof that Android delivered the
            // new intent. Do not finish the scenario with that delivery pending.
            compose.activity.intent.getStringExtra(marker) == delivered &&
            compose.onAllNodesWithTag("launcher_root").fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { it.config.getOrNull(DockVisibilityKey) == 1f } &&
                compose.onAllNodesWithTag("control_center").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("search_field").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }
    private fun openControlsFromEdge() {
        val startY = with(compose.density) { 100.dp.toPx() }
        val distance = with(compose.density) { 65.dp.toPx() }
        compose.onNodeWithTag("launcher_root").performTouchInput {
            swipe(Offset(width * .8f, startY), Offset(width * .8f, startY + distance), 400)
        }
        compose.onNodeWithTag("control_center").assertIsDisplayed()
    }
    private fun openLibrary() {
        repeat(10) {
            if (runCatching { compose.onNodeWithTag("app_library").assertIsDisplayed() }.isFailure)
                compose.onNodeWithTag("home_workspace").performTouchInput { swipeLeft() }
        }
        compose.onNodeWithTag("app_library").assertIsDisplayed()
    }

    @Test fun dockTracksPagerInBothDirectionsWithoutRepackingHome() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val placed = model.ui.value.layout.placedApps()
        val rows = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRowsKey]
        fun visibility() = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[DockVisibilityKey]
        fun intermediate(towardsToday: Boolean) {
            compose.onNodeWithTag("home_workspace").performTouchInput {
                down(Offset(width * .5f, height * .45f))
                moveTo(Offset(width * (if (towardsToday) .83f else .77f), height * .45f), delayMillis = 250)
            }
            try {
                assertTrue("Dock must follow the finger, got ${visibility()}", visibility() > .05f && visibility() < .95f)
                if (towardsToday) captureEvidence("animated-dock")
                compose.onNodeWithTag("home_workspace").performTouchInput {
                    moveTo(Offset(width * .5f, height * .45f), delayMillis = 300)
                }
            } finally { compose.onNodeWithTag("home_workspace").performTouchInput { up() } }
        }
        intermediate(true)
        assertEquals(1f, visibility(), .01f)
        compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
        assertEquals(0f, visibility(), .01f)
        returnHome()
        openLibrary()
        assertEquals(0f, visibility(), .01f)
        intermediate(false)
        assertEquals(0f, visibility(), .01f)
        returnHome()
        assertEquals(1f, visibility(), .01f)
        assertEquals(rows, compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRowsKey])
        assertEquals(placed, model.ui.value.layout.placedApps())
    }

    @Test fun todayCardsCanBeReorderedRemovedRestoredAndMovedHome() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        try {
            compose.runOnUiThread { model.appearance(original.copy(todaySections = DEFAULT_TODAY_SECTIONS, hiddenTodaySections = emptySet(), showWidgets = false)) }
            compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
            compose.onNodeWithTag("edit_today").performClick()
            val distance = with(compose.density) { 90.dp.toPx() }
            compose.onNodeWithTag("today_drag:clock_calendar").performTouchInput {
                down(center); advanceEventTime(650); moveBy(Offset(0f, distance), delayMillis = 300); up()
            }
            assertEquals("suggestions", model.ui.value.layout.appearance.todaySections.first())
            compose.onNodeWithTag("today_page").performScrollToNode(hasTestTag("today_up:clock_calendar"))
            compose.onNodeWithTag("today_up:clock_calendar").performClick()
            assertEquals(DEFAULT_TODAY_SECTIONS, model.ui.value.layout.appearance.todaySections)
            compose.onNodeWithTag("today_page").performScrollToNode(hasTestTag("today_up:suggestions"))
            compose.onNodeWithTag("today_up:suggestions").performClick()
            assertEquals("suggestions", model.ui.value.layout.appearance.todaySections.first())
            captureEvidence("today-customized")
            for (id in listOf("clock_calendar", "suggestions")) {
                compose.onNodeWithTag("today_page").performScrollToNode(hasTestTag("remove_today:$id"))
                compose.onNodeWithTag("remove_today:$id").performClick()
            }
            assertEquals(setOf("clock_calendar", "suggestions"), model.ui.value.layout.appearance.hiddenTodaySections)
            assertTrue(!model.ui.value.layout.appearance.showWidgets)
            compose.activityRule.scenario.recreate()
            awaitReady()
            compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
            compose.onNodeWithTag("today_section:clock_calendar").assertDoesNotExist()
            compose.onNodeWithTag("today_section:suggestions").assertDoesNotExist()
            compose.onNodeWithTag("edit_today").performClick()
            compose.onNodeWithTag("today_page").performScrollToNode(hasTestTag("reset_today"))
            compose.onNodeWithTag("reset_today").performClick()
            assertEquals(DEFAULT_TODAY_SECTIONS, model.ui.value.layout.appearance.todaySections)
            assertTrue(model.ui.value.layout.appearance.hiddenTodaySections.isEmpty())
            compose.onNodeWithTag("today_page").performScrollToNode(hasTestTag("today_move_home"))
            compose.onNodeWithTag("today_move_home").performClick()
            assertTrue(model.ui.value.layout.appearance.showWidgets)
            assertTrue("clock_calendar" in model.ui.value.layout.appearance.hiddenTodaySections)
        } finally { compose.runOnUiThread { model.appearance(original) } }
    }

    @Test fun controlsOpenFromEveryPageDismissUpwardAndCanBeDisabled() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        try {
            compose.runOnUiThread { model.appearance(original.copy(controlCenter = true)) }
            openControlsFromEdge()
            compose.onNodeWithContentDescription("Internet settings").assertIsDisplayed()
            compose.onNodeWithTag("control_slider:Media volume").assertIsDisplayed()
            captureEvidence("control-center")
            compose.onNodeWithTag("control_center").performTouchInput {
                swipe(Offset(width * .5f, height - 20f), Offset(width * .5f, height * .55f), 350)
            }
            compose.onNodeWithTag("control_center").assertDoesNotExist()
            compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
            compose.onNodeWithTag("today_controls").performClick()
            compose.onNodeWithTag("control_center").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close").performClick()
            compose.onNodeWithTag("today_page").assertIsDisplayed()
            returnHome()
            openLibrary()
            openControlsFromEdge()
            compose.onNodeWithContentDescription("Close").performClick()
            compose.onNodeWithTag("app_library").assertIsDisplayed()
            returnHome()
            openSettings()
            compose.onNodeWithText("Control Center").performClick()
            compose.onNodeWithTag("control_center_switch").performClick()
            assertTrue(!model.ui.value.layout.appearance.controlCenter)
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithTag("today_controls").assertDoesNotExist()
            returnHome()
            val startY = with(compose.density) { 100.dp.toPx() }
            compose.onNodeWithTag("home_workspace").performTouchInput {
                swipe(Offset(width * .8f, startY), Offset(width * .8f, startY + 220f), 400)
            }
            compose.onNodeWithTag("control_center").assertDoesNotExist()
            compose.onNodeWithTag("search_field").assertExists()
            compose.activityRule.scenario.recreate()
            awaitReady()
            assertTrue(!model.ui.value.layout.appearance.controlCenter)
        } finally { compose.runOnUiThread { model.appearance(original) } }
    }

    @Test fun optionalPanelsShowRealNotificationsAndControlTheActiveMediaSession() {
        awaitReady()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText().trim() }
        }
        check(shell("getprop ro.kernel.qemu") == "1")
        val context = compose.activity
        val model = ViewModelProvider(context)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val listener = dev.vitreo.launcher.data.SystemBridge.component(context).flattenToString()
        val oldAccess = dev.vitreo.launcher.data.SystemBridge.hasAccess(context)
        val session = android.media.session.MediaSession(context, "Vitreo integration fixture")
        val idleSession = android.media.session.MediaSession(context, "Vitreo idle assistant fixture")
        var playCount = 0
        fun playback(playing: Boolean) = android.media.session.PlaybackState.Builder()
            .setActions(android.media.session.PlaybackState.ACTION_PLAY or android.media.session.PlaybackState.ACTION_PAUSE or
                android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or android.media.session.PlaybackState.ACTION_SEEK_TO)
            .setState(if (playing) android.media.session.PlaybackState.STATE_PLAYING else android.media.session.PlaybackState.STATE_PAUSED, 10000, if (playing) 1f else 0f).build()
        try {
            check(shell("pm install -r -t /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
            if (Build.VERSION.SDK_INT >= 33) shell("pm grant dev.vitreo.fixture.uninstall android.permission.POST_NOTIFICATIONS")
            compose.runOnUiThread {
                model.appearance(original.copy(homeStatusBar = true, notificationCenter = true, mediaIntegration = true, controlCenter = true))
                idleSession.setPlaybackState(android.media.session.PlaybackState.Builder()
                    .setState(android.media.session.PlaybackState.STATE_NONE, 0, 0f).build())
                idleSession.isActive = true
                session.setCallback(object : android.media.session.MediaSession.Callback() {
                    override fun onPlay() { playCount++; session.setPlaybackState(playback(true)) }
                    override fun onPause() { session.setPlaybackState(playback(false)) }
                    override fun onSeekTo(position: Long) {
                        session.setPlaybackState(android.media.session.PlaybackState.Builder(playback(false))
                            .setState(android.media.session.PlaybackState.STATE_PAUSED, position, 0f).build())
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
                session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "CI track")
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Vitreo fixture")
                    .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 180000).build())
                session.setPlaybackState(playback(false)); session.isActive = true
            }
            shell("cmd notification allow_listener $listener")
            try {
                compose.waitUntil(15_000) { dev.vitreo.launcher.data.SystemBridge.state.value.media.title == "CI track" }
            } catch (failure: Throwable) {
                val bridge = dev.vitreo.launcher.data.SystemBridge.state.value
                val active = runCatching { context.getSystemService(android.media.session.MediaSessionManager::class.java)
                    .getActiveSessions(dev.vitreo.launcher.data.SystemBridge.component(context)).map { "${it.packageName}:${it.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)}:${it.playbackState?.state}" } }.getOrElse { listOf(it.javaClass.simpleName) }
                // Disposable emulator fixture only; never log notification content in the app.
                android.util.Log.e("VitreoBridgeTest", "access=${bridge.access}; connected=${bridge.connected}; media=${bridge.media}; active=$active; enabled=${model.ui.value.layout.appearance.mediaIntegration}")
                throw failure
            }
            compose.onNodeWithTag("home_status_bar").assertIsDisplayed()
            compose.onNodeWithTag("home_battery", useUnmergedTree = true).assertIsDisplayed()
            compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()) == false }
            captureEvidence("home-status-bar")
            compose.onNodeWithTag("launcher_root").performTouchInput {
                swipe(Offset(width * .85f, 80f * context.resources.displayMetrics.density),
                    Offset(width * .85f, 120f * context.resources.displayMetrics.density), 600)
            }
            compose.onNodeWithTag("control_center").assertDoesNotExist()
            openControlsFromEdge()
            compose.onNodeWithText("CI track").assertIsDisplayed()
            compose.onNodeWithTag("media_command:88").assertIsNotEnabled()
            compose.onNodeWithTag("media_command:85").performClick()
            compose.waitUntil(5_000) { playCount == 1 && dev.vitreo.launcher.data.SystemBridge.state.value.media.playing }
            captureEvidence("control-center-session")
            compose.onNodeWithTag("control_page:1").performClick()
            compose.onNodeWithTag("media_seek").performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
            compose.waitUntil(5_000) { dev.vitreo.launcher.data.SystemBridge.state.value.media.position == 90000L }
            returnHome()
            shell("am start -W -n dev.vitreo.fixture.uninstall/.MainActivity --ez postNotifications true")
            awaitReady()
            compose.waitUntil(10_000) { dev.vitreo.launcher.data.SystemBridge.state.value.notices.any { it.title == "Native fixture alert" } }
            compose.onNodeWithTag("launcher_root").performTouchInput {
                swipe(Offset(width * .3f, 80f * context.resources.displayMetrics.density),
                    Offset(width * .3f, 175f * context.resources.displayMetrics.density), 500)
            }
            compose.onNodeWithTag("notification_center").assertIsDisplayed()
            compose.onNodeWithText("Native fixture alert").assertIsDisplayed()
            captureEvidence("notification-center")
            compose.onNodeWithTag("clear_notifications").performClick()
            compose.waitUntil(5_000) { dev.vitreo.launcher.data.SystemBridge.state.value.notices.none { it.title == "Native fixture alert" } }
            returnHome()
            compose.runOnUiThread { model.appearance(original.copy(notificationCenter = false, mediaIntegration = false, homeStatusBar = false)) }
            compose.waitUntil(5_000) { dev.vitreo.launcher.data.SystemBridge.state.value.notices.isEmpty() && !dev.vitreo.launcher.data.SystemBridge.state.value.media.available }
            compose.onNodeWithTag("home_status_bar").assertDoesNotExist()
            assertTrue(!dev.vitreo.launcher.data.StateCodec.encode(model.ui.value.layout).contains("CI track"))
            compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()) == true }
            if (!oldAccess) {
                compose.runOnUiThread { model.appearance(original.copy(notificationCenter = true, mediaIntegration = true)) }
                compose.waitUntil(5_000) { dev.vitreo.launcher.data.SystemBridge.state.value.media.available }
                shell("cmd notification disallow_listener $listener")
                compose.waitUntil(10_000) { !dev.vitreo.launcher.data.SystemBridge.state.value.access && !dev.vitreo.launcher.data.SystemBridge.state.value.media.available && dev.vitreo.launcher.data.SystemBridge.state.value.notices.isEmpty() }
            }
        } finally {
            compose.runOnUiThread { session.release(); idleSession.release(); model.appearance(original) }
            if (!oldAccess) shell("cmd notification disallow_listener $listener")
            shell("pm uninstall dev.vitreo.fixture.uninstall")
        }
    }

    @Test fun controlsChangeRealVolumeAndPersistHomeBrightnessAndShortcuts() {
        awaitReady()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val qemu = instrumentation.uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText().trim() }
        }
        check(qemu == "1") // Never change a personal device's media volume in CI fixtures.
        val audio = compose.activity.getSystemService(AudioManager::class.java)
        val oldVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        try {
            compose.runOnUiThread { model.appearance(original.copy(controlCenter = true, homeBrightness = -1f, controlShortcuts = DEFAULT_CONTROL_SHORTCUTS, controlWide = emptySet())) }
            openControlsFromEdge()
            val next = if (oldVolume < maximum) oldVolume + 1 else oldVolume - 1
            compose.onNodeWithTag("control_slider:Media volume").performSemanticsAction(SemanticsActions.SetProgress) { it(next.toFloat() / maximum) }
            assertEquals(next, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            compose.onNodeWithTag("control_slider:Home brightness").performSemanticsAction(SemanticsActions.SetProgress) { it(.35f) }
            assertEquals(.35f, model.ui.value.layout.appearance.homeBrightness, .001f)
            compose.waitForIdle() // Window attributes change in the following composition commit.
            compose.runOnUiThread { assertEquals(.35f, compose.activity.window.attributes.screenBrightness, .001f) }
            compose.onNodeWithTag("system_brightness").performClick()
            assertEquals(-1f, model.ui.value.layout.appearance.homeBrightness, .001f)
            compose.onNodeWithTag("control_slider:Home brightness").performSemanticsAction(SemanticsActions.SetProgress) { it(.35f) }
            compose.onNodeWithTag("edit_controls").performClick()
            compose.onNodeWithTag("resize_control:timer").performClick()
            assertTrue("timer" in model.ui.value.layout.appearance.controlWide)
            compose.onNodeWithTag("add_control").performScrollTo().performClick()
            compose.onNodeWithTag("control_option:camera").performScrollTo().performClick()
            compose.onNodeWithTag("control_option:nfc").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Move NFC up").performScrollTo().performClick()
            assertTrue("camera" !in model.ui.value.layout.appearance.controlShortcuts)
            assertTrue(model.ui.value.layout.appearance.controlShortcuts.indexOf("nfc") < model.ui.value.layout.appearance.controlShortcuts.lastIndex)
            compose.onNodeWithTag("control_search").performTextInput("nfc")
            compose.onNodeWithTag("control_option:nfc").assertExists()
            compose.onNodeWithTag("control_option:timer").assertDoesNotExist()
            captureEvidence("control-picker", dialogTag = "control_picker")
            compose.onNodeWithText("Done").performClick()
            compose.waitForIdle()
            captureEvidence("control-center-edit")
            compose.onNodeWithTag("edit_controls").performClick()
            compose.onNodeWithTag("control_page:1").performClick()
            captureEvidence("control-center-media")
            compose.onNodeWithTag("control_page:2").performClick()
            compose.onNodeWithText("Connectivity").assertIsDisplayed()
            captureEvidence("control-center-connectivity")
            compose.onNodeWithTag("control_page:0").performClick()
            compose.onNodeWithTag("control:Camera").assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            awaitReady()
            assertEquals(.35f, model.ui.value.layout.appearance.homeBrightness, .001f)
            assertTrue("camera" !in model.ui.value.layout.appearance.controlShortcuts)
            assertTrue("nfc" in model.ui.value.layout.appearance.controlShortcuts)
            assertTrue("timer" in model.ui.value.layout.appearance.controlWide)
            compose.runOnUiThread { assertEquals(.35f, compose.activity.window.attributes.screenBrightness, .001f) }
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0)
            compose.runOnUiThread { model.appearance(original) }
        }
    }

    @Test fun cyberpunkThemeAppliesAcrossHomeLibraryAndMenusWithoutChangingLayout() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val placed = model.ui.value.layout.placedApps()
        compose.runOnUiThread { model.appearance(original.copy(themePreset = 0, showWidgets = true, iconSize = 60f)) }
        try {
            openSettings()
            compose.onNodeWithText("Themes & Icons").performClick()
            compose.onNodeWithTag("theme_1").performClick()
            compose.waitUntil(10_000) { model.ui.value.layout.appearance.themePreset == 1 }
            captureEvidence("cyber-settings")
            compose.onNodeWithText("Done").performClick()
            returnHome()
            compose.onNodeWithTag("cyber_backdrop", useUnmergedTree = true).assertExists()
            compose.onNode(hasTestTag("clock_widget_card") and hasAnyAncestor(hasTestTag("home_builtin_widgets"))).assertIsDisplayed()
            assertEquals(placed, model.ui.value.layout.placedApps())
            captureEvidence("cyber-home")
            val first = homeIconTags().first { !it.startsWith("home_entry:folder:") }
            compose.onNodeWithTag(first).performTouchInput { longClick() }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Change Icon").assertIsDisplayed()
            captureEvidence("cyber-menu")
            returnHome()
            repeat(10) {
                if (compose.onAllNodesWithTag("app_library").fetchSemanticsNodes().isEmpty())
                    compose.onNodeWithTag("home_workspace").performTouchInput { swipeLeft() }
            }
            compose.onNodeWithTag("app_library").assertIsDisplayed()
            captureEvidence("cyber-library")
            val category = compose.onAllNodes(SemanticsMatcher("category") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library_category:") == true
            }).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag]
            compose.onNodeWithTag(category).performClick()
            compose.onNodeWithTag("category_panel").assertIsDisplayed()
            captureEvidence("cyber-category")
            returnHome()
            compose.activityRule.scenario.recreate()
            awaitReady()
            assertEquals(1, model.ui.value.layout.appearance.themePreset)
            assertEquals(placed, model.ui.value.layout.placedApps())
            compose.runOnUiThread { model.appearance(model.ui.value.layout.appearance.copy(reducedEffects = true)) }
            compose.onNodeWithTag("library_button").performClick()
            compose.onNodeWithTag("search_field").performTextInput("camera")
            compose.onNodeWithTag("library_title").assertIsDisplayed()
            returnHome()
            compose.runOnUiThread { model.appearance(original) }
            compose.onNodeWithTag("cyber_backdrop", useUnmergedTree = true).assertDoesNotExist()
        } finally { compose.runOnUiThread { model.appearance(original) } }
    }

    @Test fun immersiveCyberpunkIsOptInAndRespectsReducedMotion() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val placed = model.ui.value.layout.placedApps()
        try {
            compose.runOnUiThread { model.appearance(original.copy(themePreset = 1, cyberImmersive = false, cyberEffects = true, reducedEffects = false, palette = 0, usePhoto = false)) }
            openSettings()
            compose.onNodeWithText("Themes & Icons").performClick()
            compose.onNodeWithTag("cyber_immersive").performScrollTo().performClick()
            val oldFrame = model.wallpaper.value
            val oldBackground = model.background.value
            compose.onNodeWithTag("cyber_wallpaper:0").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.wallpaper.value !== oldFrame && model.background.value !== oldBackground }
            compose.onNodeWithText("Done").performClick()
            returnHome()
            compose.onNodeWithTag("cyber_ambient", useUnmergedTree = true).assertExists()
            captureEvidence("cyber-immersive")
            compose.activityRule.scenario.recreate()
            awaitReady()
            assertTrue(model.ui.value.layout.appearance.cyberImmersive)
            assertEquals(4, model.ui.value.layout.appearance.palette)
            assertEquals(placed, model.ui.value.layout.placedApps())
            compose.runOnUiThread { model.appearance(model.ui.value.layout.appearance.copy(reducedEffects = true)) }
            compose.onNodeWithTag("cyber_ambient", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnUiThread { model.appearance(model.ui.value.layout.appearance.copy(reducedEffects = false, cyberImmersive = false)) }
            compose.onNodeWithTag("cyber_ambient", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("cyber_backdrop", useUnmergedTree = true).assertExists()
        } finally { compose.runOnUiThread { model.appearance(original) } }
    }

    @Test fun builtInIconCanBeAssignedToAnyAppAndFollowsTheSelectedTheme() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val app = model.ui.value.apps.first { it.packageName == "com.android.settings" }
        val placed = model.ui.value.layout.placedApps()
        fun search() {
            returnHome()
            compose.onNodeWithTag("library_button").performClick()
            compose.onNodeWithTag("search_field").performTextInput(app.label)
        }
        fun selectedIcon() = compose.onNode(hasTestTag("theme_icon:${app.id}:photos") and
            hasAnyAncestor(hasTestTag("search_entry:${app.id}")), useUnmergedTree = true)
        fun awaitSelectedIcon() {
            compose.waitUntil(10_000) { runCatching { selectedIcon().isDisplayed() }.getOrDefault(false) }
            selectedIcon().assertIsDisplayed()
        }
        try {
            compose.runOnUiThread { model.appearance(original.copy(themePreset = 0, iconPack = "", iconOverrides = emptyMap())) }
            search()
            compose.onNodeWithTag("search_entry:${app.id}").performTouchInput { longClick() }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Change Icon").performClick()
            compose.onNodeWithText("Current theme").assertIsDisplayed()
            compose.onNodeWithTag("icon_search").performTextInput("photos")
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("pick_icon:photos").fetchSemanticsNodes().isNotEmpty() }
            captureEvidence("theme-icon-picker")
            compose.onNodeWithTag("pick_icon:photos").performClick()
            assertEquals(dev.vitreo.launcher.core.IconOverride(dev.vitreo.launcher.core.THEME_ICON_PACK, "photos"), model.ui.value.layout.appearance.iconOverrides[app.id])
            awaitSelectedIcon()
            compose.onNodeWithTag("search_field").assertTextEquals(app.label)
            captureEvidence("theme-icon-applied")
            compose.activityRule.scenario.recreate()
            awaitReady()
            assertEquals("photos", model.ui.value.layout.appearance.iconOverrides[app.id]?.drawable)
            compose.runOnUiThread { model.appearance(model.ui.value.layout.appearance.copy(themePreset = 1)) }
            search()
            awaitSelectedIcon()
            captureEvidence("theme-icon-cyber")
            assertEquals(placed, model.ui.value.layout.placedApps())
            compose.onNodeWithTag("search_entry:${app.id}").performTouchInput { longClick() }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Change Icon").performClick()
            compose.onNodeWithTag("reset_app_icon").performClick()
            assertTrue(model.ui.value.layout.appearance.iconOverrides[app.id] == null)
        } finally { compose.runOnUiThread { model.appearance(original) } }
    }

    @Test fun iconPackSelectionAndPerAppPickerSurviveRecreationAndPackRemoval() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val pack = "dev.vitreo.fixture.uninstall"
        val id = "$pack/$pack.MainActivity"
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = automation.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText() }
        }
        check(shell("getprop ro.kernel.qemu").trim() == "1")
        try {
            check(shell("pm install -r -t /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
            compose.waitUntil(20_000) { model.ui.value.apps.any { it.id == id } && model.ui.value.iconPacks.any { it.packageName == pack } }
            val placed = model.ui.value.layout.placedApps()
            openSettings()
            compose.onNodeWithText("Themes & Icons").performClick()
            compose.onNodeWithTag("pack:$pack").performScrollTo().performClick()
            compose.waitUntil(10_000) { model.ui.value.layout.appearance.iconPack == pack }
            assertEquals(placed, model.ui.value.layout.placedApps())
            compose.onNodeWithText("Done").performClick()
            compose.runOnUiThread { compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            compose.onNodeWithTag("library_button").performClick()
            compose.onNodeWithTag("search_field").performTextInput("Uninstall fixture")
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("external_icon:$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("search_entry:$id").performTouchInput { longClick() }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Change Icon").performClick()
            compose.onNodeWithTag("icon_search").performTextInput("fixture red")
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("pick_icon:fixture_red").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("pick_icon:fixture_red").performClick()
            compose.waitUntil(10_000) { model.ui.value.layout.appearance.iconOverrides[id]?.drawable == "fixture_red" }
            compose.activityRule.scenario.recreate()
            awaitReady()
            assertEquals("fixture_red", model.ui.value.layout.appearance.iconOverrides[id]?.drawable)
            check(shell("pm uninstall $pack").contains("Success"))
            compose.waitUntil(20_000) { model.ui.value.apps.none { it.packageName == pack } && model.ui.value.iconPacks.none { it.packageName == pack } }
            assertTrue(model.ui.value.layout.appearance.iconOverrides[id] == null)
            assertEquals(placed.filterNot { it.startsWith("$pack/") }, model.ui.value.layout.placedApps())
            assertEquals("Missing pack reference remains portable", pack, model.ui.value.layout.appearance.iconPack)
        } finally {
            shell("pm uninstall $pack")
            compose.runOnUiThread { model.appearance(original) }
        }
    }

    @Test fun nativeWidgetCanBeAddedResizedMovedAcrossPagesAndReturnedToToday() {
        awaitReady()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = compose.activity.applicationContext
        val provider = ComponentName(instrumentation.context.packageName, WidgetFixtureProvider::class.java.name)
        val manager = AppWidgetManager.getInstance(context)
        val controller = WidgetController(context)
        val before = controller.ids.toSet()
        val automation = instrumentation.uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET, "android.permission.CHANGE_COMPONENT_ENABLED_STATE")
        var id = -1
        try {
            context.packageManager.setComponentEnabledSetting(provider, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            compose.waitUntil(10_000) { manager.installedProviders.any { it.provider == provider } }
            val first = homeIconTags().first()
            compose.onNodeWithTag(first).performTouchInput { longClick() }
            compose.onNodeWithText("Edit Home Screen").performClick()
            compose.onNodeWithTag("add_home_widgets").performClick()
            compose.onNodeWithTag("widget_search").performScrollTo().performTextInput("Vitreo Test Widget")
            // Gboard changes the viewport asynchronously. Finish the query before
            // measuring/tapping the Add row, so it cannot move during the click.
            compose.onNodeWithTag("widget_search").performImeAction()
            compose.onNodeWithTag("widget_search").assertIsNotFocused()
            compose.waitUntil(5_000) {
                val insets = compose.activity.window.decorView.rootWindowInsets
                insets != null && !insets.isVisible(android.view.WindowInsets.Type.ime()) &&
                    insets.getInsets(android.view.WindowInsets.Type.ime()).bottom == 0
            }
            val addWidget = hasText("Vitreo Test Widget") and hasClickAction() and !hasSetTextAction()
            compose.onNodeWithTag("widget_catalog").performScrollToNode(addWidget)
            compose.onNode(addWidget).assertIsDisplayed().performClick()
            try {
                compose.waitUntil(10_000) {
                    context.getSharedPreferences("today-widgets", Context.MODE_PRIVATE).getString("ids", "").orEmpty()
                        .split(',').mapNotNull(String::toIntOrNull).any { it !in before }
                }
            } catch (failure: Throwable) {
                runCatching { captureEvidence("widget-add-failure", dialogTag = "widget_catalog") }
                throw failure
            }
            id = WidgetController(context).ids.first { it !in before }
            compose.onNodeWithTag("home_widget:$id").assertIsDisplayed()
            compose.onNodeWithTag("native_widget:$id").assertIsDisplayed()
            val small = compose.onNodeWithTag("home_widget:$id").fetchSemanticsNode().boundsInRoot
            captureEvidence("home-widget-small")
            compose.onNodeWithTag("edit_widget:$id").performClick()
            compose.onNodeWithText("Medium").performClick()
            compose.onNodeWithTag("home_widget:$id").assertIsDisplayed()
            val medium = compose.onNodeWithTag("home_widget:$id").fetchSemanticsNode().boundsInRoot
            assertTrue("Medium must span more columns", medium.width > small.width * 1.8f)
            compose.onNodeWithTag("edit_widget:$id").performClick()
            compose.onNodeWithText("Next Page").performScrollTo().performClick()
            compose.onNodeWithTag("home_widget:$id").assertIsDisplayed()
            assertEquals(1, WidgetController(context).homePlacements.single { it.id == id }.page)
            captureEvidence("home-widget-medium")
            compose.activityRule.scenario.recreate()
            awaitReady()
            compose.onNodeWithTag("home_workspace").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("home_widget:$id").assertIsDisplayed()
            compose.onNodeWithTag("edit_widget:$id").performClick()
            compose.onNodeWithText("Today", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("today_page").assertIsDisplayed()
            compose.onNodeWithTag("today_page").performScrollToNode(hasTestTag("native_widget:$id"))
            compose.onNodeWithTag("native_widget:$id").assertIsDisplayed()
            assertTrue(id in WidgetController(context).todayIds)
            compose.onNodeWithTag("today_page").performScrollToNode(hasTestTag("edit_widget:$id"))
            compose.onNodeWithTag("edit_widget:$id").performClick()
            compose.onNodeWithText("Remove Widget").performScrollTo().performClick()
            assertTrue(id !in WidgetController(context).ids)
            assertEquals(null, manager.getAppWidgetInfo(id))
        } finally {
            if (id >= 0) WidgetController(context).remove(id)
            context.packageManager.setComponentEnabledSetting(provider, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            automation.dropShellPermissionIdentity()
        }
    }

    @Test fun modalBlocksHomeDraggingAndHidesUnderlyingAccessibilityNodes() {
        awaitReady()
        val icons = homeIconTags()
        assertTrue(icons.size >= 2)
        val first = icons[0]; val second = icons[1]
        val rows = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRowsKey]
        compose.onNodeWithTag(first).performTouchInput { longClick() }
        compose.onNodeWithText("Edit Home Screen").performClick()
        assertEquals("Edit mode must preserve the grid", rows,
            compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRowsKey])
        val source = compose.onNodeWithTag(first).fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithTag(second).fetchSemanticsNode().boundsInRoot.center
        val root = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().boundsInRoot.topLeft
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("settings_title").assertIsDisplayed()
        compose.onNodeWithTag(first).assertDoesNotExist()
        compose.onNodeWithTag("launcher_root").performTouchInput {
            down(source - root); advanceEventTime(700); moveTo(target - root, delayMillis = 200); up()
        }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("edit_done").performClick()
        assertTrue("A modal must not reorder icons behind it",
            compose.onNodeWithTag(first).fetchSemanticsNode().boundsInRoot.left <
                compose.onNodeWithTag(second).fetchSemanticsNode().boundsInRoot.left)
        assertEquals(rows, compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRowsKey])
    }

    @Test fun leavingHomeEndsEditMode() {
        awaitReady()
        compose.onNodeWithTag(homeIconTags().first()).performTouchInput { longClick() }
        compose.onNodeWithText("Edit Home Screen").performClick()
        compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
        compose.onNodeWithTag("today_page").assertIsDisplayed()
        compose.onNodeWithTag("edit_done").assertDoesNotExist()
    }

    @Test fun searchKeyboardDoesNotReflowHome() {
        awaitReady()
        val rows = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRowsKey]
        compose.onNodeWithTag("library_button").performClick()
        compose.onNodeWithTag("search_field").performTextInput("camera")
        compose.onNodeWithTag("library_title").assertIsDisplayed()
        assertEquals("Showing the IME must not resize the home grid", rows,
            compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRowsKey])
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("dock").assertIsDisplayed()
        assertEquals(rows, compose.onNodeWithTag("launcher_root").fetchSemanticsNode().config[HomeRowsKey])
    }

    @Test fun resetRestoresPreferredDockWithoutLosingApps() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val expected = AppCatalog(compose.activity).preferredDock(model.ui.value.apps)
        compose.runOnUiThread { model.resetLayout() }
        compose.waitForIdle()
        assertEquals(expected, model.ui.value.layout.dock)
        assertEquals(model.ui.value.apps.map { it.id }.toSet(), model.ui.value.layout.placedApps().toSet())
    }
    @Test fun homeAndSettingsOpen() {
        awaitReady()
        compose.onNodeWithTag("launcher_root").assertExists()
        openSettings()
        compose.onNodeWithTag("settings_title").assertExists()
    }
    @Test fun largerIconsFitTheDockAndPreserveTheLayout() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        val placed = model.ui.value.layout.placedApps()
        try {
            compose.runOnUiThread { model.appearance(original.copy(iconSize = 84f)) }
            compose.waitForIdle()
            val dock = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot
            val id = model.ui.value.layout.dock.first()
            val icon = compose.onNodeWithTag("icon_art:$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Large icons must exceed the old 68pt limit", icon.width / compose.activity.resources.displayMetrics.density > 68f)
            assertTrue(icon.left >= dock.left && icon.right <= dock.right && icon.top >= dock.top && icon.bottom <= dock.bottom)
            assertEquals(placed, model.ui.value.layout.placedApps())
            captureEvidence("large-icons")
        } finally {
            compose.runOnUiThread { model.appearance(original) }
            compose.waitForIdle()
        }
    }
    @Test fun librarySearchOpens() {
        awaitReady()
        compose.onNodeWithTag("library_button").performClick()
        compose.onNodeWithTag("search_field").performTextInput("camera")
        compose.onNodeWithTag("library_title").assertExists()
        returnHome()
        compose.waitUntil(5_000) {
            compose.activity.window.decorView.rootWindowInsets
                ?.isVisible(android.view.WindowInsets.Type.ime()) == false
        }
        compose.onNodeWithTag("dock").assertIsDisplayed()
    }
    @SdkSuppress(minSdkVersion = 33)
    @Test fun agslCompilesOnDevice() {
        if (Build.VERSION.SDK_INT >= 33) {
            awaitReady()
            val code = compose.activity.assets.open("glass.agsl").bufferedReader().use { it.readText() }
            RuntimeShader(code) // Throws on invalid AGSL: a fallback must not hide a broken shader in tests.
            compose.waitForIdle()
            assertEquals("none", GlassDiagnostics.lastFailure)
        }
    }
    @Test fun impossibleSearchHasNoResults() {
        awaitReady()
        compose.onNodeWithTag("library_button").performClick()
        compose.onNodeWithTag("search_field").performTextInput("zzzz-no-such-vitreo-app-193847")
        compose.onNodeWithText("No Results").assertExists()
    }
    @Test fun homeIntentClosesOpenSheet() {
        awaitReady()
        val activity = compose.activity
        repeat(3) {
            openSettings()
            compose.onNodeWithTag("settings_title").assertExists()
            compose.runOnUiThread {
                activity.startActivity(Intent(activity, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                // HOME can briefly detach the Compose owner while Android
                // reattaches the task. No root during that interval is not a result.
                compose.activityRule.scenario.state == Lifecycle.State.RESUMED && activity.hasWindowFocus() &&
                    compose.onAllNodesWithTag("launcher_root").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() &&
                    compose.onAllNodesWithTag("settings_title").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
            }
            assertSame(activity, compose.activity)
            assertEquals(Lifecycle.State.RESUMED, compose.activityRule.scenario.state)
            compose.runOnUiThread {
                assertEquals(Intent.ACTION_MAIN, activity.intent.action)
                assertTrue(activity.intent.hasCategory(Intent.CATEGORY_HOME))
            }
            compose.onNodeWithTag("launcher_root").assertIsDisplayed()
            compose.onNodeWithTag("dock").assertIsDisplayed()
        }
    }
    @Test fun activityRecreationKeepsLauncherUsable() {
        awaitReady()
        compose.activityRule.scenario.recreate()
        awaitReady()
        compose.onNodeWithTag("library_button").performClick()
        compose.onNodeWithTag("library_title").assertExists()
    }
    @Test fun appLibraryOpensAfterLastHomePage() {
        awaitReady()
        repeat(10) {
            if (!compose.onNodeWithTag("app_library").isDisplayed())
                compose.onNodeWithTag("home_workspace").performTouchInput { swipeLeft() }
        }
        compose.onNodeWithTag("app_library").assertIsDisplayed()
        compose.onNodeWithText("App Library").assertExists()
    }
    @Test fun widgetGalleryOpensFromToday() {
        awaitReady()
        compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
        compose.onNodeWithTag("today_page").performScrollToNode(hasTestTag("today_add_widgets"))
        compose.onNodeWithContentDescription("Add Widgets").performClick()
        compose.onNodeWithTag("widgets_title").assertExists()
        compose.onNodeWithText("Clock & Calendar on Home").assertExists()
    }

    @Test fun libraryCategoryHasItsOwnMaterialAndReadableLabelsInBothThemes() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        try {
            for (style in listOf(1, 2)) {
                compose.runOnUiThread { model.appearance(original.copy(interfaceStyle = style)) }
                repeat(10) {
                    if (!compose.onNodeWithTag("app_library").isDisplayed())
                        compose.onNodeWithTag("home_workspace").performTouchInput { swipeLeft() }
                }
                compose.onNodeWithTag("library_category:Suggestions").performClick()
                compose.onNodeWithTag("category_panel").assertIsDisplayed()
                val entry = compose.onAllNodes(SemanticsMatcher("category app") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("category_entry:") == true
                }).fetchSemanticsNodes().first()
                val id = entry.config[SemanticsProperties.TestTag].substringAfter("category_entry:")
                val label = model.ui.value.apps.first { it.id == id }.label
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                assertEquals(if (style == 1) Color(0xff1c1c1e) else Color(0xfff5f5f7), layouts.first().layoutInput.style.color)
                captureEvidence(if (style == 1) "category-light" else "category-dark")
                compose.onNodeWithContentDescription("Close").performClick()
            }
        } finally { compose.runOnUiThread { model.appearance(original) } }
    }

    @Test fun darkSettingsUsesLightTextOnItsDarkBackground() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val original = model.ui.value.layout.appearance
        try {
            compose.runOnUiThread { model.appearance(original.copy(interfaceStyle = 2)) }
            openSettings()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("settings_title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(Color(0xfff5f5f7), layouts.first().layoutInput.style.color)
            captureEvidence("settings-dark")
        } finally { compose.runOnUiThread { model.appearance(original) } }
    }

    private fun openPhotoEditor(model: LauncherViewModel): File {
        val image = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.rgb(40, 110, 180)) }
        val file = File(compose.activity.cacheDir, "wallpaper-editor-test.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnUiThread { model.preparePhoto(Uri.fromFile(file)) }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("wallpaper_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        return file
    }

    @Test fun fittingAndCancellingPhotoPreservesTheCurrentWallpaper() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val before = model.ui.value.layout.appearance
        val file = openPhotoEditor(model)
        try {
            compose.onNodeWithTag("wallpaper_editor").performTouchInput {
                pinch(Offset(width * .4f, height * .4f), Offset(width * .6f, height * .4f),
                    Offset(width * .3f, height * .4f), Offset(width * .7f, height * .4f))
            }
            assertTrue("The editor must handle a real pinch gesture",
                compose.onNodeWithTag("wallpaper_editor").fetchSemanticsNode().config[PhotoZoomKey] > 1f)
            compose.onNodeWithTag("wallpaper_fit").performClick()
            assertTrue(compose.onNodeWithTag("wallpaper_editor").fetchSemanticsNode().config[PhotoZoomKey] < .5f)
            captureEvidence("wallpaper-fit")
            compose.onNodeWithTag("wallpaper_fill").performClick()
            assertEquals(1f, compose.onNodeWithTag("wallpaper_editor").fetchSemanticsNode().config[PhotoZoomKey])
            compose.onNodeWithTag("wallpaper_cancel").performClick()
            compose.onNodeWithTag("wallpaper_editor").assertDoesNotExist()
            assertEquals(before, model.ui.value.layout.appearance)
        } finally { file.delete() }
    }

    @Test fun fittedPhotoCanBeSavedAndAdjustedAgainFromTheOriginal() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val before = model.ui.value.layout.appearance
        val file = openPhotoEditor(model)
        try {
            compose.onNodeWithTag("wallpaper_fit").performClick()
            val zoom = compose.onNodeWithTag("wallpaper_editor").fetchSemanticsNode().config[PhotoZoomKey]
            compose.onNodeWithTag("wallpaper_save").performClick()
            compose.waitUntil(15_000) { model.photoDraft.value == null }
            assertTrue(model.ui.value.layout.appearance.usePhoto)
            assertEquals(zoom, model.ui.value.layout.appearance.photoTransform.zoom, .0001f)
            compose.runOnUiThread { model.preparePhoto() }
            compose.waitUntil(15_000) { model.photoDraft.value != null }
            assertEquals(1600, model.photoDraft.value!!.frame.sharp.width)
            assertEquals(900, model.photoDraft.value!!.frame.sharp.height)
            assertEquals(zoom, model.photoDraft.value!!.transform.zoom, .0001f)
            compose.onNodeWithTag("wallpaper_cancel").performClick()
        } finally { compose.runOnUiThread { model.discardPhoto(); model.appearance(before) }; file.delete() }
    }
    @Test fun pullDownOpensSpotlight() {
        awaitReady()
        compose.onNodeWithTag("home_workspace").performTouchInput { swipeDown() }
        compose.onNodeWithTag("search_field").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("dock").assertIsDisplayed()
    }

    @Test fun upperRightPullDownOpensControls() {
        awaitReady()
        compose.onNodeWithTag("home_workspace").performTouchInput {
            swipe(Offset(width * .85f, 20f), Offset(width * .85f, height * .55f), 400)
        }
        compose.onNodeWithTag("control_center").assertIsDisplayed()
        compose.onNodeWithContentDescription("Internet settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("dock").assertIsDisplayed()
    }

    @Test fun editModeDragReordersHomeIcons() {
        awaitReady()
        val icons = compose.onAllNodes(SemanticsMatcher("home icon") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("home_entry:") == true
        }).fetchSemanticsNodes().sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
        assertTrue("The emulator must have at least two home apps", icons.size >= 2)
        val first = icons[0].config[SemanticsProperties.TestTag]
        val second = icons[1].config[SemanticsProperties.TestTag]
        compose.onNodeWithTag(first).performTouchInput { longClick() }
        compose.onNodeWithText("Edit Home Screen").performClick()
        compose.onNodeWithTag("edit_done").assertIsDisplayed()
        val source = compose.onNodeWithTag(first).fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithTag(second).fetchSemanticsNode().boundsInRoot.center
        val root = compose.onNodeWithTag("launcher_root").fetchSemanticsNode().boundsInRoot.topLeft
        compose.onNodeWithTag("launcher_root").performTouchInput {
            down(source - root)
            advanceEventTime(700)
            moveTo(target - root, delayMillis = 200)
            up()
        }
        compose.onNodeWithTag("edit_done").performClick()
        val firstAfter = compose.onNodeWithTag(first).fetchSemanticsNode().boundsInRoot
        val secondAfter = compose.onNodeWithTag(second).fetchSemanticsNode().boundsInRoot
        assertTrue("Dragging must change the order", secondAfter.left < firstAfter.left)
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }

    @Test fun uninstallRemovesAllGhostsButAnApkUpdatePreservesPlacement() {
        awaitReady()
        assertEquals("This scenario only changes a disposable emulator", "1", shell("getprop ro.kernel.qemu").trim())
        val fixture = "dev.vitreo.fixture.uninstall"
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        try {
            assertTrue(shell("pm install -r /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
            compose.waitUntil(15_000) { model.ui.value.apps.count { it.packageName == fixture } == 3 }
            val ids = model.ui.value.apps.filter { it.packageName == fixture }.map { it.id }
            val partner = model.ui.value.layout.home.filterIsInstance<dev.vitreo.launcher.core.HomeEntry.App>().first { it.id !in ids }.id
            compose.runOnUiThread {
                model.dockDrop(ids[0], 0)
                model.folder(ids[1], partner)
                model.recordLaunch(ids[2])
            }
            val placed = model.ui.value.layout
            val oldRecord = model.ui.value.apps.first { it.id == ids[0] }
            assertTrue(placed.dock.contains(ids[0]))
            assertTrue(placed.home.any { it is dev.vitreo.launcher.core.HomeEntry.Folder && ids[1] in it.apps })
            assertTrue(placed.home.any { it.id == ids[2] })
            assertTrue(shell("pm install -r /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
            compose.waitUntil(15_000) { model.ui.value.apps.any { it.id == ids[0] && it !== oldRecord } }
            assertEquals("APK replacement must preserve the Dock", placed.dock, model.ui.value.layout.dock)
            assertEquals("APK replacement must preserve Home and folders", placed.home, model.ui.value.layout.home)
            assertTrue(shell("pm uninstall $fixture").contains("Success"))
            compose.waitUntil(15_000) {
                val ui = model.ui.value
                ui.apps.none { it.packageName == fixture } && ui.layout.placedApps().none { it in ids } &&
                    ui.layout.knownApps.none { it in ids } && ui.recentApps.none { it in ids }
            }
            assertTrue("The surviving folder app must remain on Home", model.ui.value.layout.home.any { it.id == partner })
            ids.forEach { compose.onNodeWithTag("home_entry:$it").assertDoesNotExist() }
        } finally { shell("pm uninstall $fixture") }
    }

    private fun awaitFocusedWindow(packageName: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val service = checkNotNull(automation.serviceInfo)
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = service
        try {
            compose.waitUntil(15_000) {
                // isFocused is Android's input-focus flag; RESUMED alone precedes
                // the drawn window. UiAutomation.getWindows requires the flag above.
                automation.windows.any { window ->
                    window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
                        window.isFocused && window.root?.packageName?.toString() == packageName
                }
            }
        } catch (failure: Throwable) {
            android.util.Log.e("VitreoWindowTest", "Expected focused $packageName; windows=" + automation.windows.joinToString {
                "type=${it.type}, focused=${it.isFocused}, package=${it.root?.packageName}"
            }, failure)
            throw failure
        }
        automation.waitForIdle(200, 5_000)
    }

    private fun closeMenuWithBack() {
        val activity = compose.activity
        awaitFocusedWindow(activity.packageName)
        compose.waitUntil(10_000) {
            activity.hasWindowFocus() && androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true
        }
        shell("input keyevent KEYCODE_BACK")
        // Shell input injection returns before Android 15 dispatches onBackInvoked.
        compose.waitUntil(10_000) {
            activity.hasWindowFocus() &&
                compose.onAllNodesWithTag("launcher_root").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() &&
                compose.onAllNodesWithTag("context_menu").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
    }

    private fun openLibraryCategory() {
        repeat(10) {
            if (!compose.onNodeWithTag("app_library").isDisplayed())
                compose.onNodeWithTag("home_workspace").performTouchInput { swipeLeft() }
        }
        compose.onNodeWithTag("library_category:Suggestions").performClick()
        compose.onNodeWithTag("category_panel").assertIsDisplayed()
    }

    @Test fun categoryMenuLiftsTheActualIconAndRestoresItsCategory() {
        awaitReady()
        openLibraryCategory()
        val node = compose.onAllNodes(SemanticsMatcher("category app") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("category_entry:") == true
        }).fetchSemanticsNodes().first()
        val tag = node.config[SemanticsProperties.TestTag]
        val source = node.boundsInRoot
        compose.onNodeWithTag(tag).performTouchInput { longClick() }
        compose.onNodeWithTag("context_menu").assertIsDisplayed()
        compose.onNodeWithTag("category_panel").assertDoesNotExist()
        val lifted = compose.onNodeWithTag("context_icon").fetchSemanticsNode().boundsInRoot
        assertEquals(source.center.x, lifted.center.x, 3f)
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(source.top + 37f * density, lifted.center.y, 3f)
        captureEvidence("app-menu")
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("App Info").assertIsDisplayed()
        captureEvidence("app-menu-more")
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Edit Home Screen").assertIsDisplayed()
        closeMenuWithBack()
        compose.onNodeWithTag("context_menu").assertDoesNotExist()
        compose.onNodeWithTag("category_panel").assertIsDisplayed()
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Test fun dismissingSearchMenuKeepsTheSearchAndItsQuery() {
        awaitReady()
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val app = model.ui.value.apps.first { it.packageName == "com.android.settings" }
        compose.onNodeWithTag("library_button").performClick()
        compose.onNodeWithTag("search_field").performTextInput(app.label)
        compose.onNodeWithTag("search_entry:${app.id}").performTouchInput { longClick() }
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Create Folder…").performClick()
        compose.onNodeWithText("Create Folder").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("context_menu").assertIsDisplayed()
        closeMenuWithBack()
        compose.onNodeWithTag("search_field").assertTextEquals(app.label)
        compose.onNodeWithTag("search_entry:${app.id}").assertIsDisplayed()
    }

    @Test fun librarySearchShowsAllAppsAndFiltersThem() {
        awaitReady()
        repeat(10) {
            if (!compose.onNodeWithTag("app_library").isDisplayed())
                compose.onNodeWithTag("home_workspace").performTouchInput { swipeLeft() }
        }
        compose.onNodeWithText("App Library").performClick()
        compose.onNodeWithTag("alphabetical_apps").assertIsDisplayed()
        captureEvidence("alphabetical-library")
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val app = model.ui.value.apps.first { it.packageName == "com.android.settings" }
        compose.onNodeWithTag("search_field").performTextInput(app.label)
        compose.onNodeWithTag("alphabetical_entry:${app.id}").assertIsDisplayed()
    }

    @Test fun categoryPullCanCancelAndThenDismiss() {
        awaitReady()
        openLibraryCategory()
        val density = compose.activity.resources.displayMetrics.density
        val start = Offset(110 * density, 68 * density)
        compose.onNodeWithTag("launcher_root").performTouchInput { swipe(start, start + Offset(0f, 38 * density), 350) }
        compose.onNodeWithTag("category_panel").assertIsDisplayed()
        compose.onNodeWithTag("launcher_root").performTouchInput { swipe(start, start + Offset(0f, 130 * density), 350) }
        compose.onNodeWithTag("category_panel").assertDoesNotExist()
        compose.onNodeWithTag("app_library").assertIsDisplayed()
    }

    @Test fun widgetsOpenedFromSettingsReturnToSettings() {
        awaitReady()
        openSettings()
        compose.onNodeWithText("Widgets").performClick()
        compose.onNodeWithTag("widgets_title").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("settings_title").assertTextEquals("Settings")
    }

    @Test fun installedIconFillsTheRoundSystemMaskAndHasNoSquareForeground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 192, 192); icon.draw(android.graphics.Canvas(bitmap))
        assertEquals(0, AndroidColor.alpha(bitmap.getPixel(0, 0)))
        assertTrue("The artwork must fill the system mask", AndroidColor.alpha(bitmap.getPixel(96, 5)) > 240)
        val foreground = checkNotNull(context.getDrawable(R.drawable.ic_vitreo))
        val mark = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        foreground.setBounds(0, 0, 108, 108); foreground.draw(android.graphics.Canvas(mark))
        assertEquals("No inset square behind the V", 0, AndroidColor.alpha(mark.getPixel(26, 54)))
        val directory = checkNotNull(context.getExternalFilesDir("review"))
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "installed-icon.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

}
