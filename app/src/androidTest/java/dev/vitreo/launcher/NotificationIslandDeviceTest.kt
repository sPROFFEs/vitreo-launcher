package dev.vitreo.launcher

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.core.Appearance
import dev.vitreo.launcher.core.IslandLayout
import dev.vitreo.launcher.data.SystemBridge
import dev.vitreo.launcher.island.NotificationIsland
import dev.vitreo.launcher.lock.LockScreenActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIslandDeviceTest {
    companion object { @JvmField @ClassRule val wallpaper=LocalWallpaperRule() }
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
    private var oldAccess=false
    private var oldEnabled=false
    private var oldLayout=IslandLayout()
    private var oldMode="default"
    private fun shell(cmd:String)=automation.executeShellCommand(cmd).use { java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { r -> r.readText() } }
    private fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
    private fun <T> inspect(action:()->T):T {
        var result:Result<T>?=null
        main { result=runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
    private fun await(test:()->Boolean)=compose.waitUntil(15_000,test)
    private fun islandView():android.view.ViewGroup? = android.view.inspector.WindowInspector.getGlobalWindowViews()
        .lastOrNull {
            val lp=it.layoutParams as? android.view.WindowManager.LayoutParams
            it.isAttachedToWindow && lp?.title?.toString()=="Vitreo notification island" && lp.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE==0
        } as? android.view.ViewGroup
    private fun descendants(view:android.view.View):List<android.view.View> = listOf(view) +
        if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun islandReady()=inspect { islandView()?.let { it.isShown && it.isLaidOut && it.width>0 && it.height>0 }==true }
    private fun tapView(select:(android.view.ViewGroup)->android.view.View?) {
        await { inspect { islandView()?.let(select)?.let {
            val visible=android.graphics.Rect()
            it.isShown && it.width>0 && it.height>0 && it.getGlobalVisibleRect(visible) && visible.width()>=it.width && visible.height()>=it.height
        }==true } }
        val point=inspect {
            val v=checkNotNull(select(checkNotNull(islandView())))
            IntArray(2).also { v.getLocationOnScreen(it);it[0]+=v.width/2;it[1]+=v.height/2 }
        }
        // UiAutomation synchronizes native input-window transactions before injecting DOWN.
        // A shell tap can arrive after layout but before a newly attached overlay accepts input.
        val down=android.os.SystemClock.uptimeMillis()
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,point[0].toFloat(),point[1].toFloat(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
    }
    private fun expand() {
        tapView { it.findViewWithTag("vitreo-island-touch") }
        await { NotificationIsland.status.value.expanded && inspect { islandView()?.hasWindowFocus()==true } }
    }
    private fun button(label:String)=tapView { root -> descendants(root).filterIsInstance<android.widget.Button>().firstOrNull { it.text.toString()==label } }
    private fun post(title:String,media:Boolean=false) {
        shell("am broadcast -n dev.vitreo.fixture.uninstall/.OverlayReceiver --es operation notice --es title $title --ez media $media")
    }
    @Before fun prepare() {
        check(shell("getprop ro.kernel.qemu").trim()=="1")
        val info=checkNotNull(automation.serviceInfo)
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo=info
        oldAccess=SystemBridge.hasAccess(context);oldEnabled=NotificationIsland.enabled.value;oldLayout=NotificationIsland.layout.value
        oldMode=Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)").find(shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
        check(shell("pm install -r -t /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
        if(Build.VERSION.SDK_INT>=33) shell("pm grant dev.vitreo.fixture.uninstall android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        main { NotificationIsland.update(IslandLayout(duration=15));NotificationIsland.setEnabled(context,true);NotificationIsland.configure(Appearance()) }
        shell("cmd notification allow_listener ${SystemBridge.component(context).flattenToString()}")
        await { SystemBridge.state.value.connected && SystemBridge.state.value.access }
        main { NotificationIsland.refresh() }
    }
    @After fun restore() {
        main { androidx.test.runner.lifecycle.Stage.values().flatMap {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
        }.filterIsInstance<LockScreenActivity>().filter { it.preview }.forEach { it.finishAndRemoveTask() } }
        main { NotificationIsland.setEnabled(context,false);NotificationIsland.update(oldLayout) }
        shell("am broadcast -n dev.vitreo.fixture.uninstall/.OverlayReceiver --es operation notice --ez cancel true")
        shell("pm uninstall dev.vitreo.fixture.uninstall")
        if(!oldAccess) shell("cmd notification disallow_listener ${SystemBridge.component(context).flattenToString()}")
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $oldMode")
        main { NotificationIsland.setEnabled(context,oldEnabled) }
    }
    @Test fun realAlertHasActualAppIconExpandsCollapsesAndDismisses() {
        post("Island-alert")
        await { NotificationIsland.status.value.visible && islandReady() }
        val notice=SystemBridge.state.value.notices.first { it.title=="Island-alert" }
        assertNotNull("Real notification must carry its application identity",notice.appIcon)
        assertEquals("Uninstall fixture",notice.app)
        expand()
        inspect {
            val children=descendants(checkNotNull(islandView()))
            assertTrue(children.filterIsInstance<android.widget.TextView>().any { it.text.toString()=="Island-alert" })
            assertTrue(children.filterIsInstance<android.widget.ImageView>().any { it.contentDescription=="Uninstall fixture app icon" && it.drawable!=null })
            assertTrue(children.any { it.contentDescription=="New notification" })
        }
        main {
            val view=checkNotNull(islandView())
            val tile=view.findViewWithTag<android.view.View>("vitreo-island-card")
            assertTrue("Short alerts should hug their content",tile.height<260*context.resources.displayMetrics.density)
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val directory=checkNotNull(context.getExternalFilesDir("review"));check(directory.exists() || directory.mkdirs())
            java.io.File(directory,"notification-island-expanded.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
        shell("input tap 100 ${compose.activity.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds.bottom-(120*context.resources.displayMetrics.density).toInt()}")
        await { !NotificationIsland.status.value.expanded && compose.activity.hasWindowFocus() }
        assertTrue(NotificationIsland.status.value.visible)
        main { NotificationIsland.collapse() }
        await { islandReady() }
        expand()
        button("Dismiss")
        await { !NotificationIsland.status.value.visible && SystemBridge.state.value.notices.none { it.key==notice.key } }
    }
    @Test fun disabledIslandDoesNotReplayInboxAndPermissionRevocationRemovesWindow() {
        main { NotificationIsland.setEnabled(context,false) }
        post("Old-alert")
        // Notification Center can be off: enable it only to establish the old inbox snapshot.
        main { SystemBridge.configure(context,true,false) }
        await { SystemBridge.state.value.notices.any { it.title=="Old-alert" } }
        main { NotificationIsland.setEnabled(context,true) }
        assertFalse(NotificationIsland.status.value.visible)
        post("New-alert")
        await { NotificationIsland.status.value.visible }
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW deny")
        await { !NotificationIsland.status.value.visible && !NotificationIsland.status.value.overlayPermission }
        main { SystemBridge.configure(context,false,false) }
    }
    @Test fun otherMediaIslandReservesSpaceAndPausePolicyYields() {
        post("Media-fixture",true)
        await { NotificationIsland.status.value.peerActive }
        main { NotificationIsland.update(IslandLayout(reservedBottom=160,duration=15)) }
        post("Incoming-alert")
        await { NotificationIsland.status.value.visible }
        await { islandReady() }
        val rect=inspect { val v=checkNotNull(islandView());val xy=IntArray(2);v.getLocationOnScreen(xy);android.graphics.Rect(xy[0],xy[1],xy[0]+v.width,xy[1]+v.height) }
        assertTrue("Island clears reserved music overlay",rect.top>=168*context.resources.displayMetrics.density-2)
        main { NotificationIsland.update(IslandLayout(coexistence=1)) }
        await { !NotificationIsland.status.value.visible }
    }
    @Test fun screenOffClearsIslandWithoutWakingOrReplayingNotification() {
        post("Screen-off-alert")
        await { NotificationIsland.status.value.visible }
        try {
            shell("input keyevent KEYCODE_SLEEP")
            await { !NotificationIsland.status.value.visible }
            assertFalse(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("input keyevent KEYCODE_MENU")
        }
        await { context.getSystemService(android.os.PowerManager::class.java).isInteractive }
        assertFalse(NotificationIsland.status.value.visible)
    }

    @Test fun openNotificationWorksAboveAnotherAppWithoutForegroundHome() {
        val packageName=context.packageName
        val stockHome=listOf("com.google.android.apps.nexuslauncher","com.android.launcher3").firstNotNullOfOrNull { pkg ->
            shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME -p $pkg")
                .lineSequence().map { it.trim() }.firstOrNull { it.matches(Regex(Regex.escape(pkg)+"/[A-Za-z0-9_.$]+")) }
        }
        checkNotNull(stockHome) { "A pinned emulator stock launcher is required." }
        // A third UID in front, with both Vitreo and the notification creator in the background.
        // Resolve/open it explicitly: first installation can select Vitreo as default Home.
        // Protected Android Settings deliberately hides overlays and is not a valid host.
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $stockHome")
        try {
            post("Open-from-island")
            await { NotificationIsland.status.value.visible && islandReady() }
            expand()
            button("Open")
            await { !NotificationIsland.status.value.visible && automation.windows.any {
                it.isFocused && it.root?.findAccessibilityNodeInfosByText("Opened from notification")?.any { n -> n.isVisibleToUser }==true
            } }
        } finally { shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $packageName/dev.vitreo.launcher.MainActivity") }
    }

    @Test fun nativeLockStatusBarIsDefaultAndCustomBarRequiresSharedSwitch() {
        main { LockScreenActivity.preview(context,Appearance(reducedEffects=true)) }
        await { compose.onAllNodesWithTag("lock_screen").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
        compose.onNodeWithTag("lock_status_icons").assertDoesNotExist()
        var lock:LockScreenActivity?=null
        main { lock=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<LockScreenActivity>().first() }
        await { lock!!.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.statusBars())==true }
        main { lock!!.finishAndRemoveTask() }
        await { compose.activity.hasWindowFocus() }
        main { LockScreenActivity.preview(context,Appearance(homeStatusBar=true,reducedEffects=true)) }
        await { compose.onAllNodesWithTag("lock_status_icons").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
        compose.onNodeWithTag("lock_status_icons").assertIsDisplayed()
        main { androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<LockScreenActivity>().forEach { it.finishAndRemoveTask() } }
    }
}
