package dev.vitreo.launcher

import android.content.Intent
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
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
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun awaitReady() {
        compose.waitUntil(timeoutMillis = 20_000) {
            val nodes = compose.onAllNodesWithTag("launcher_root").fetchSemanticsNodes()
            nodes.any { it.config.contains(LauncherReadyKey) && it.config[LauncherReadyKey] }
        }
        compose.waitForIdle()
    }
    @Test fun homeAndSettingsOpen() {
        awaitReady()
        compose.onNodeWithTag("launcher_root").assertExists()
        compose.onNodeWithTag("settings_button").performClick()
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
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("settings_title").assertExists()
        val activity = compose.activity
        val launchIntent = Intent(activity.intent)
        try {
            compose.runOnUiThread {
                activity.startActivity(Intent(activity, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("settings_title").fetchSemanticsNodes().isEmpty()
            }
            assertSame(activity, compose.activity)
            compose.runOnUiThread {
                assertEquals(Intent.ACTION_MAIN, activity.intent.action)
                assertTrue(activity.intent.hasCategory(Intent.CATEGORY_HOME))
            }
            compose.onNodeWithTag("launcher_root").assertIsDisplayed()
        } finally {
            // onNewIntent stores HOME, but ActivityScenario matches lifecycle events
            // against the original LAUNCHER intent. Restore it even if an assertion fails
            // so the rule can observe DESTROYED and clean up before the next test.
            compose.runOnUiThread { activity.intent = launchIntent }
        }
        compose.activityRule.scenario.close()
        assertEquals(Lifecycle.State.DESTROYED, compose.activityRule.scenario.state)
    }
    @Test fun activityRecreationKeepsLauncherUsable() {
        awaitReady()
        compose.activityRule.scenario.recreate()
        awaitReady()
        compose.onNodeWithTag("library_button").performClick()
        compose.onNodeWithTag("library_title").assertExists()
    }

}
