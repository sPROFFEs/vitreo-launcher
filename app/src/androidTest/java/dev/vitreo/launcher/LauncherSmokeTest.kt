package dev.vitreo.launcher

import android.content.Context
import android.content.Intent
import android.graphics.RuntimeShader
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Offset
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
import dev.vitreo.launcher.ui.LauncherReadyKey
import dev.vitreo.launcher.ui.HomeRowsKey
import dev.vitreo.launcher.ui.PhotoZoomKey
import dev.vitreo.launcher.data.AppCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {
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
        compose.waitUntil(timeoutMillis = 20_000) {
            val nodes = compose.onAllNodesWithTag("launcher_root").fetchSemanticsNodes()
            nodes.any { it.config.contains(LauncherReadyKey) && it.config[LauncherReadyKey] }
        }
        compose.waitForIdle()
    }
    private fun captureEvidence(name: String) {
        compose.waitForIdle()
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
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
    @Test fun librarySearchOpens() {
        awaitReady()
        compose.onNodeWithTag("library_button").performClick()
        compose.onNodeWithTag("search_field").performTextInput("camera")
        compose.onNodeWithTag("library_title").assertExists()
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
        openSettings()
        compose.onNodeWithTag("settings_title").assertExists()
        val activity = compose.activity
        compose.runOnUiThread {
            activity.startActivity(Intent(activity, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("settings_title").fetchSemanticsNodes().isEmpty()
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
        compose.onNodeWithText("Internet").assertExists()
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

}
