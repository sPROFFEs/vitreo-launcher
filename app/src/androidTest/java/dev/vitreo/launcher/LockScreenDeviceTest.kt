package dev.vitreo.launcher

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.lock.LockScreenActivity
import dev.vitreo.launcher.lock.LockScreenRuntime
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** Last, isolated phase only: changes emulator-wide HOME and secure credentials, restores both. */
@RunWith(AndroidJUnit4::class)
class LockScreenDeviceTest {
    companion object {
        private const val PIN = "24681357" // Disposable emulator fixture, never a real user's credential.
        private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
        private val context get() = ApplicationProvider.getApplicationContext<Context>()
        private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText() }
        }
        private fun await(label: String, predicate: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (!predicate()) {
                check(SystemClock.uptimeMillis() < deadline) { "Timed out: $label" }
                SystemClock.sleep(100)
            }
        }
        private fun systemNode(id: String): android.view.accessibility.AccessibilityNodeInfo? =
            instrumentation.uiAutomation.windows.asSequence().mapNotNull { it.root }
                .filter { it.packageName?.toString() == "com.android.systemui" }
                .flatMap { it.findAccessibilityNodeInfosByViewId("com.android.systemui:id/$id").asSequence() }
                .firstOrNull { it.isVisibleToUser }
        private fun enterPin(pin: String = PIN) {
            await("native PIN entry is ready") { systemNode("pinEntry")?.isFocused == true }
            pin.forEach { digit ->
                val key = checkNotNull(systemNode("key$digit"))
                check(key.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            }
            check(checkNotNull(systemNode("key_enter")).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        }
        @JvmField @ClassRule val device = object : ExternalResource() {
            private var home: String? = null
            private var timeout: String? = null
            private var instant: String? = null
            private var immersiveConfirmation: String? = null
            private var pinInstalled = false
            override fun before() {
                check(shell("getprop ro.kernel.qemu").trim() == "1") { "Refusing credential changes on a physical device." }
                check(!context.getSystemService(KeyguardManager::class.java).isDeviceSecure) { "Emulator must not have a pre-existing credential." }
                val automation = instrumentation.uiAutomation
                val service = checkNotNull(automation.serviceInfo)
                service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                automation.serviceInfo = service
                shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
                home = checkNotNull(shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                    .lineSequence().lastOrNull { it.matches(Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")) })
                timeout = shell("settings get secure lock_screen_lock_after_timeout").trim()
                instant = shell("settings get secure lockscreen.power_button_instantly_locks").trim()
                immersiveConfirmation = shell("settings get secure immersive_mode_confirmations").trim()
                // As in Android CTS, acknowledge the one-time fullscreen tutorial in the fixture.
                // Its delayed system window otherwise steals focus during the first drag.
                shell("settings put secure immersive_mode_confirmations confirmed")
                check(shell("locksettings set-pin $PIN").startsWith("Pin set to "))
                pinInstalled = true
                shell("settings put secure lock_screen_lock_after_timeout 0")
                shell("settings put secure lockscreen.power_button_instantly_locks 1")
                shell("cmd package set-home-activity ${context.packageName}/dev.vitreo.launcher.MainActivity")
                check(shell("pm install -r -t /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
                shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME")
            }
            override fun after() {
                instrumentation.runOnMainSync { LockScreenRuntime.setEnabled(context, false) }
                try {
                    // Teardown must restore our fixture even when a body failed while still locked.
                    // Authentication assertions above never use privileged clearing as an unlock path.
                    if (pinInstalled) {
                        check(shell("locksettings clear --old $PIN").contains("Lock credential cleared"))
                        await("fixture credential removed") { !context.getSystemService(KeyguardManager::class.java).isDeviceSecure }
                    }
                    shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
                } finally {
                    listOf("lock_screen_lock_after_timeout" to timeout, "lockscreen.power_button_instantly_locks" to instant).forEach { (key, value) ->
                        if (value == "null" || value == null) shell("settings delete secure $key")
                        else { check(value.matches(Regex("[0-9]+"))); shell("settings put secure $key $value") }
                    }
                    when (immersiveConfirmation) {
                        "confirmed" -> shell("settings put secure immersive_mode_confirmations confirmed")
                        "" -> shell("settings put secure immersive_mode_confirmations ''")
                        "null", null -> shell("settings delete secure immersive_mode_confirmations")
                        else -> error("Unexpected immersive tutorial fixture state")
                    }
                    home?.let { shell("cmd package set-home-activity $it") }
                    shell("pm uninstall dev.vitreo.fixture.uninstall")
                    instrumentation.runOnMainSync {
                        Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                            .filter { it is MainActivity || it is LockScreenActivity }.distinct().forEach { it.finishAndRemoveTask() }
                    }
                }
            }
        }
    }
    @get:Rule val compose = createEmptyComposeRule()
    private val keyguard get() = context.getSystemService(KeyguardManager::class.java)
    private fun lockActivity(): LockScreenActivity? {
        var result: LockScreenActivity? = null
        instrumentation.runOnMainSync {
            result = listOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.STOPPED).flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
            }.filterIsInstance<LockScreenActivity>().firstOrNull { !it.isFinishing }
        }
        return result
    }
    private fun prepareAndWake(fromPreview: Boolean = false) {
        instrumentation.runOnMainSync {
            LockScreenRuntime.configure(Appearance(reducedEffects = true))
            assertNull(LockScreenRuntime.setEnabled(context, true))
        }
        shell("am start -W -n dev.vitreo.fixture.uninstall/.MainActivity")
        if (fromPreview) {
            instrumentation.runOnMainSync { LockScreenActivity.preview(context, Appearance(reducedEffects = true)) }
            await("preview focused") { lockActivity()?.let { it.preview && it.hasWindowFocus() } == true }
        }
        shell("input keyevent KEYCODE_SLEEP")
        await("real keyguard locked with custom activity prepared while asleep") { keyguard.isDeviceLocked && lockActivity()?.preview == false }
        shell("input keyevent KEYCODE_WAKEUP")
        await("custom lock has input focus") { lockActivity()?.hasWindowFocus() == true }
        compose.onNodeWithTag("lock_screen").assertIsDisplayed()
        compose.onNodeWithTag("lock_close").assertContentDescriptionEquals("Use system lock screen")
        assertTrue(keyguard.isKeyguardLocked)
        assertTrue(keyguard.isDeviceLocked)
    }
    private fun awaitSystemAuth() {
        await("Android authentication owns focus") {
            keyguard.isDeviceLocked && lockActivity()?.hasWindowFocus() == false && systemNode("pinEntry")?.isFocused == true
        }
    }
    @Test fun upwardGestureRequiresRealAndroidPinAndReturnsToPreviousApp() {
        prepareAndWake()
        val density = context.resources.displayMetrics.density
        compose.onNodeWithTag("lock_swipe").performTouchInput {
            val start = Offset(center.x * .5f, center.y)
            swipe(start, start - Offset(0f, 30f * density), 350)
        }
        assertTrue(lockActivity()?.hasWindowFocus() == true)
        assertTrue(keyguard.isDeviceLocked)
        compose.onNodeWithTag("lock_swipe").performTouchInput {
            val start = Offset(center.x * .5f, center.y)
            swipe(start, start - Offset(0f, 160f * density), 500)
        }
        awaitSystemAuth()
        enterPin("11111111")
        await("Android rejects incorrect PIN") {
            instrumentation.uiAutomation.windows.any { it.root?.findAccessibilityNodeInfosByText("Wrong PIN")?.isNotEmpty() == true }
        }
        assertTrue("An incorrect PIN must never unlock", keyguard.isDeviceLocked)
        enterPin()
        await("real authenticated unlock") { !keyguard.isDeviceLocked && lockActivity() == null }
        await("return to previous app") {
            instrumentation.uiAutomation.windows.any { it.isFocused && it.root?.packageName?.toString() == "dev.vitreo.fixture.uninstall" }
        }
    }
    @Test fun cancellingAuthenticationAndDisablingPreserveNativeKeyguard() {
        prepareAndWake(fromPreview = true)
        compose.onNodeWithTag("lock_unlock").performClick()
        awaitSystemAuth()
        shell("input keyevent KEYCODE_BACK")
        await("cancel returns to custom screen") { lockActivity()?.hasWindowFocus() == true }
        assertTrue(keyguard.isDeviceLocked)
        compose.onNodeWithTag("lock_unlock").assertIsEnabled()
        instrumentation.runOnMainSync { assertNull(LockScreenRuntime.setEnabled(context, false)) }
        await("disabled custom screen removed") { lockActivity() == null }
        assertTrue("Turning appearance off must never dismiss keyguard", keyguard.isDeviceLocked)
        shell("wm dismiss-keyguard")
        enterPin()
        await("native unlock still works") { !keyguard.isDeviceLocked }
    }
}
