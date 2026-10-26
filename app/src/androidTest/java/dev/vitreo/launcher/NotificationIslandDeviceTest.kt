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
import dev.vitreo.launcher.data.NotificationSettingsNavigation
import dev.vitreo.launcher.data.StateStore
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
    private var oldAnimatorScale="1"
    private var oldWindowScale="1"
    private var oldAppearance:Appearance?=null
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
            lp?.title?.toString()=="Vitreo notification island" && lp.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE==0
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
        await { NotificationIsland.status.value.expanded && inspect { islandView()?.let { it.isLaidOut && it.hasWindowFocus() }==true } }
    }
    private fun button(label:String)=tapView { root -> descendants(root).filterIsInstance<android.widget.Button>().firstOrNull { it.text.toString()==label } }
    private fun post(title:String,media:Boolean=false) {
        shell("am broadcast -n dev.vitreo.fixture.uninstall/.OverlayReceiver --es operation notice --es title $title --ez media $media")
    }
    @Before fun prepare() {
        check(shell("getprop ro.kernel.qemu").trim()=="1")
        // Finish Home's initial appearance effect before configuring native overlays.
        // Otherwise a slow first composition can overwrite the test's appearance.
        await { inspect { androidx.lifecycle.ViewModelProvider(compose.activity)[LauncherViewModel::class.java].ui.value.ready } }
        val model=inspect { androidx.lifecycle.ViewModelProvider(compose.activity)[LauncherViewModel::class.java] }
        oldAppearance=model.ui.value.layout.appearance
        val fixtureAppearance=inspect { model.appearance(Appearance(syncWallpaper=false));model.ui.value.layout.appearance }
        compose.waitForIdle()
        await { StateStore(context).load().state.appearance==fixtureAppearance }
        oldAnimatorScale=shell("settings get global animator_duration_scale").trim()
        oldWindowScale=shell("settings get global window_animation_scale").trim()
        val info=checkNotNull(automation.serviceInfo)
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo=info
        oldAccess=SystemBridge.hasAccess(context);oldEnabled=NotificationIsland.enabled.value;oldLayout=NotificationIsland.layout.value
        oldMode=Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)").find(shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
        check(shell("pm install -r -t /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
        if(Build.VERSION.SDK_INT>=33) shell("pm grant dev.vitreo.fixture.uninstall android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        main { NotificationIsland.update(IslandLayout(duration=15));NotificationIsland.setEnabled(context,true) }
        shell("cmd notification allow_listener ${SystemBridge.component(context).flattenToString()}")
        await { SystemBridge.state.value.connected && SystemBridge.state.value.access }
        main { NotificationIsland.configure(fixtureAppearance);NotificationIsland.refresh() }
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
        if(oldAnimatorScale=="null") shell("settings delete global animator_duration_scale")
        else shell("settings put global animator_duration_scale $oldAnimatorScale")
        if(oldWindowScale=="null") shell("settings delete global window_animation_scale")
        else shell("settings put global window_animation_scale $oldWindowScale")
        main { NotificationIsland.setEnabled(context,oldEnabled) }
        oldAppearance?.let { original ->
            main { androidx.lifecycle.ViewModelProvider(compose.activity)[LauncherViewModel::class.java].appearance(original) }
            compose.waitForIdle()
            await { StateStore(context).load().state.appearance==original }
        }
    }
    @Test fun capsuleOpensAndClosesHorizontallyFromItsOwnCentre() {
        shell("settings put global animator_duration_scale 3")
        await { inspect { android.animation.ValueAnimator.areAnimatorsEnabled() } }
        // Offset the handle and touch region: the enclosing window's centre is different.
        main {
            NotificationIsland.update(IslandLayout(horizontal=-35,handleHorizontal=80,touchHorizontal=65,duration=15))
            assertTrue(NotificationIsland.preview(context))
        }
        // addView returns before WindowManager attaches the native root on its next frame.
        await { inspect { islandView()?.let { it.isLaidOut && it.scaleX in .02f..0.9f }==true } }
        inspect {
            val view=checkNotNull(islandView())
            assertEquals(1f,view.scaleY,0f)
        }
        await { inspect { islandView()?.let { it.isLaidOut && it.scaleX==1f && it.alpha==1f }==true } }
        inspect {
            val view=checkNotNull(islandView())
            val capsule=checkNotNull(view.findViewWithTag<android.view.View>("vitreo-island-card"))
            assertEquals(capsule.left+capsule.width/2f,view.pivotX,1f)
            assertTrue(kotlin.math.abs(view.pivotX-view.width/2f)>10)
        }
        post("Centre-close")
        await { SystemBridge.state.value.notices.any { it.title=="Centre-close" } }
        val exiting=inspect { checkNotNull(islandView()) }
        shell("am broadcast -n dev.vitreo.fixture.uninstall/.OverlayReceiver --es operation notice --ez cancel true")
        await { !NotificationIsland.status.value.visible }
        await { inspect { exiting.isAttachedToWindow && exiting.scaleX in .03f..0.95f } }
        inspect {
            assertEquals(1f,exiting.scaleY,0f)
            val flags=(exiting.layoutParams as android.view.WindowManager.LayoutParams).flags
            assertTrue(flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE!=0)
            assertTrue(flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE!=0)
        }
        // A replacement during exit must remove the retiring surface before attaching its own.
        main { assertTrue(NotificationIsland.preview(context));assertFalse(exiting.isAttachedToWindow) }
        shell("input keyevent KEYCODE_SLEEP")
        try { await { !NotificationIsland.status.value.visible && inspect {
            android.view.inspector.WindowInspector.getGlobalWindowViews().none {
                it.isAttachedToWindow && (it.layoutParams as? android.view.WindowManager.LayoutParams)?.title?.toString()=="Vitreo notification island"
            }
        } } }
        finally { shell("input keyevent KEYCODE_WAKEUP");shell("input keyevent KEYCODE_MENU") }
    }

    @Test fun reduceMotionSkipsCentreAnimationAndRemovesDismissedWindowImmediately() {
        shell("settings put global animator_duration_scale 3")
        val model=inspect { androidx.lifecycle.ViewModelProvider(compose.activity)[LauncherViewModel::class.java] }
        val original=model.ui.value.layout.appearance
        try {
            // Use the real setting so Compose and listener reconnections agree.
            main { model.appearance(original.copy(reducedEffects=true)) }
            compose.waitForIdle()
            await { StateStore(context).load().state.appearance.reducedEffects }
            main { assertTrue(NotificationIsland.preview(context)) }
            await { islandReady() }
            inspect { val view=checkNotNull(islandView());assertEquals(1f,view.scaleX,0f);assertEquals(1f,view.alpha,0f) }
            post("Reduced-motion")
            await { SystemBridge.state.value.notices.any { it.title=="Reduced-motion" } }
            val old=inspect { checkNotNull(islandView()) }
            shell("am broadcast -n dev.vitreo.fixture.uninstall/.OverlayReceiver --es operation notice --ez cancel true")
            await { !NotificationIsland.status.value.visible }
            assertFalse(inspect { old.isAttachedToWindow })
        } finally {
            main { model.appearance(original) }
            compose.waitForIdle()
            await { StateStore(context).load().state.appearance.reducedEffects==original.reducedEffects }
        }
    }

    @Test fun expansionAndCollapseDoNotAddSystemWindowMovement() {
        shell("settings put global window_animation_scale 3")
        val model=inspect { androidx.lifecycle.ViewModelProvider(compose.activity)[LauncherViewModel::class.java] }
        val original=model.ui.value.layout.appearance
        fun assertSurfaceAtCard(name:String) {
            val committed=java.util.concurrent.atomic.AtomicBoolean(false)
            main {
                val view=checkNotNull(islandView())
                view.viewTreeObserver.registerFrameCommitCallback { committed.set(true) }
                view.invalidate()
            }
            await { committed.get() }
            val bounds=inspect {
                val view=checkNotNull(checkNotNull(islandView()).findViewWithTag<android.view.View>("vitreo-island-card"))
                val xy=IntArray(2);view.getLocationOnScreen(xy)
                android.graphics.Rect(xy[0],xy[1],xy[0]+view.width,xy[1]+view.height)
            }
            val screen=checkNotNull(automation.takeScreenshot())
            try {
                val directory=checkNotNull(context.getExternalFilesDir("review"));directory.mkdirs()
                java.io.File(directory,"$name.png").outputStream().use { screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                val inset=(6*context.resources.displayMetrics.density).toInt()
                for(x in listOf(bounds.left+inset,bounds.right-inset)) {
                    val color=screen.getPixel(x,bounds.centerY())
                    assertTrue("$name: compositor must keep the card at $bounds; pixel ($x,${bounds.centerY()})=${Integer.toHexString(color)}",
                        android.graphics.Color.red(color)<12 && android.graphics.Color.green(color)<12 && android.graphics.Color.blue(color)<12)
                }
            } finally { screen.recycle() }
        }
        try {
            // Isolate WindowManager's motion from Vitreo's own morph, as when Reduce Motion is on.
            main { model.appearance(original.copy(reducedEffects=true)) }
            compose.waitForIdle()
            await { StateStore(context).load().state.appearance.reducedEffects }
            main { assertTrue(NotificationIsland.preview(context)) }
            await { islandReady() }
            main { checkNotNull(islandView()).findViewWithTag<android.view.View>("vitreo-island-touch").performClick() }
            await { inspect { islandView()?.let { it.width==context.resources.displayMetrics.widthPixels && it.hasWindowFocus() }==true } }
            assertSurfaceAtCard("island-expanded-position")
            main { NotificationIsland.collapse() }
            await { inspect { islandView()?.let { it.isLaidOut && it.width<context.resources.displayMetrics.widthPixels }==true } }
            assertSurfaceAtCard("island-collapsed-position")
        } finally {
            main { model.appearance(original) }
            compose.waitForIdle()
            await { StateStore(context).load().state.appearance.reducedEffects==original.reducedEffects }
        }
    }

    @Test fun cardCornersMorphWithTheVisibleBounds() {
        shell("settings put global animator_duration_scale 3")
        val model = inspect { androidx.lifecycle.ViewModelProvider(compose.activity)[LauncherViewModel::class.java] }
        val original = model.ui.value.layout.appearance
        val samples = java.util.concurrent.CopyOnWriteArrayList<Pair<Float, Float>>()
        var recording = true
        val callback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(time: Long) {
                if (!recording) return
                islandView()?.findViewWithTag<android.view.View>("vitreo-island-card")?.let { tile ->
                    val params = tile.layoutParams as android.widget.FrameLayout.LayoutParams
                    val radius = (tile.background as android.graphics.drawable.GradientDrawable).cornerRadius
                    samples.add(radius to minOf(params.width, params.height) / 2f)
                }
                android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }
        try {
            main { model.appearance(original.copy(themePreset = 0, reducedEffects = false)) }
            compose.waitForIdle()
            main { assertTrue(NotificationIsland.preview(context)) }
            await { islandReady() && inspect { checkNotNull(islandView()).scaleX == 1f } }
            val compactRadius = inspect {
                (checkNotNull(islandView()).findViewWithTag<android.view.View>("vitreo-island-card").background as android.graphics.drawable.GradientDrawable).cornerRadius
            }
            main { android.view.Choreographer.getInstance().postFrameCallback(callback) }
            tapView { it.findViewWithTag("vitreo-island-touch") }
            // Window geometry is rounded to whole display pixels (e.g. 89px at 420dpi).
            val expandedRadius = Math.round(34 * context.resources.displayMetrics.density).toFloat()
            await { samples.any { (r, _) -> r > compactRadius + 1f && r < expandedRadius - 1f } }
            try {
                await { samples.any { (r, _) -> kotlin.math.abs(r - expandedRadius) < .1f } }
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("Expected settled radius=$expandedRadius px; observed=${samples.map { it.first }.distinct()}", failure)
            }
            samples.clear()
            main { NotificationIsland.collapse() }
            await { samples.any { (r, _) -> r > compactRadius + 1f && r < expandedRadius - 1f } }
            await { inspect { islandView()?.let { it.width < context.resources.displayMetrics.widthPixels } == true } }
            assertTrue(samples.isNotEmpty())
            assertTrue("The corner must remain inside the current silhouette", samples.all { (radius, maximum) -> radius >= 0f && radius <= maximum + .5f })
        } finally {
            main { recording = false; android.view.Choreographer.getInstance().removeFrameCallback(callback); model.appearance(original) }
            compose.waitForIdle()
        }
    }

    @Test fun interruptedMorphAndForegroundRefreshKeepTheVisiblePosition() {
        shell("settings put global animator_duration_scale 0")
        await { inspect { !android.animation.ValueAnimator.areAnimatorsEnabled() } }
        shell("settings put global animator_duration_scale 10")
        await { inspect { android.animation.ValueAnimator.areAnimatorsEnabled() } }
        fun bounds():android.graphics.Rect {
            fun read():android.graphics.Rect {
                val view=checkNotNull(checkNotNull(islandView()).findViewWithTag<android.view.View>("vitreo-island-card"))
                val lp=view.layoutParams as android.widget.FrameLayout.LayoutParams
                return android.graphics.Rect(lp.leftMargin,lp.topMargin,lp.leftMargin+lp.width,lp.topMargin+lp.height)
            }
            return if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) read() else inspect { read() }
        }
        main { assertTrue(NotificationIsland.preview(context)) }
        await { inspect { islandView()?.let { it.isLaidOut && it.scaleX in .1f..0.85f }==true } }
        val compactWidth=bounds().width()
        val wide=context.resources.displayMetrics.widthPixels-(40*context.resources.displayMetrics.density).toInt()
        main {
            val root=checkNotNull(islandView())
            val lp=root.layoutParams as android.view.WindowManager.LayoutParams
            val before=bounds()
            val pivot=lp.x+root.pivotX
            val left=(pivot+(lp.x+before.left-pivot)*root.scaleX).toDouble()
            val right=(pivot+(lp.x+before.right-pivot)*root.scaleX).toDouble()
            root.findViewWithTag<android.view.View>("vitreo-island-touch").performClick()
            assertEquals("Early expansion starts at the currently visible left edge",left,bounds().left.toDouble(),1.0)
            assertEquals("Early expansion starts at the currently visible right edge",right,bounds().right.toDouble(),1.0)
        }
        await { bounds().width() in (compactWidth+20)..wide }
        main {
            val before=bounds()
            NotificationIsland.refresh()
            NotificationIsland.configure(Appearance())
            assertEquals("A foreground refresh must not finish the morph",before,bounds())
            NotificationIsland.collapse()
            assertEquals("Reversing must start at the visible position",before,bounds())
        }
        await { bounds().width() in (compactWidth+5)..wide }
        main {
            val before=bounds()
            assertTrue(NotificationIsland.preview(context))
            val lp=checkNotNull(islandView()).layoutParams as android.view.WindowManager.LayoutParams
            assertEquals("An interrupted collapse retains its fullscreen coordinates",-1,lp.width)
            assertEquals(0,lp.x);assertEquals(0,lp.y)
            assertEquals("Replacement continues from the current frame",before,bounds())
        }
        await { inspect { islandView()?.let { it.isLaidOut && it.width<context.resources.displayMetrics.widthPixels }==true } }
        assertEquals(compactWidth,bounds().width())
    }

    @Test fun channelSettingsShortcutPreservesNotificationAndCategoryIdentity() {
        post("Banner-settings")
        await { SystemBridge.state.value.notices.any { it.title=="Banner-settings" } }
        val notice=SystemBridge.state.value.notices.first { it.title=="Banner-settings" }
        assertEquals("island",notice.channelId)
        assertTrue(notice.channelName.isNotBlank())
        val intent=NotificationSettingsNavigation.category(notice.packageName,notice.channelId)
        assertEquals(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,intent.action)
        assertEquals(notice.packageName,intent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
        assertEquals(notice.channelId,intent.getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID))
        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            NotificationSettingsNavigation.category(notice.packageName,"").action)
        try {
            main { assertTrue(NotificationSettingsNavigation.open(compose.activity,notice.packageName,notice.channelId)) }
            await { automation.windows.any { it.isFocused && it.root?.packageName?.toString()=="com.android.settings" &&
                it.root?.findAccessibilityNodeInfosByText(notice.channelName)?.any { node -> node.isVisibleToUser }==true } }
            assertTrue(SystemBridge.state.value.notices.any { it.key==notice.key })
        } finally {
            shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${context.packageName}/dev.vitreo.launcher.MainActivity")
        }
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
