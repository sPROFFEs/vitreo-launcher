package dev.vitreo.launcher

import android.content.Context
import android.content.Intent
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.vitreo.launcher.graphics.GlassDiagnostics
import dev.vitreo.launcher.ui.LauncherReadyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    private fun openSettings() {
        compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
        compose.onNodeWithTag("today_page").assertExists()
        compose.onNodeWithTag("settings_button").performClick()
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
        compose.onNodeWithText("Sin resultados").assertExists()
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
        compose.onNodeWithText("Biblioteca de apps").assertExists()
    }
    @Test fun widgetGalleryOpensFromToday() {
        awaitReady()
        compose.onNodeWithTag("home_workspace").performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("Añadir widgets").performClick()
        compose.onNodeWithTag("widgets_title").assertExists()
        compose.onNodeWithText("Reloj y calendario en Inicio").assertExists()
    }
    @Test fun pullDownOpensSpotlight() {
        awaitReady()
        compose.onNodeWithTag("home_workspace").performTouchInput { swipeDown() }
        compose.onNodeWithTag("search_field").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("dock").assertIsDisplayed()
    }

}
