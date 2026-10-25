package dev.vitreo.launcher.island

import android.animation.ValueAnimator
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.core.content.ContextCompat
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Transient windows owned by the existing, system-bound notification listener. No extra service. */
@android.annotation.SuppressLint("StaticFieldLeak") // Application/display-window contexts only; windows are removed on every exit path.
object NotificationIsland {
    data class Status(val visible:Boolean=false,val expanded:Boolean=false,val peerActive:Boolean=false,
        val overlayPermission:Boolean=false,val connected:Boolean=false)
    private lateinit var app:Context
    private val handler=Handler(Looper.getMainLooper())
    private val active=MutableStateFlow(false)
    val enabled=active.asStateFlow()
    private val placement=MutableStateFlow(IslandLayout())
    val layout=placement.asStateFlow()
    private val mutableStatus=MutableStateFlow(Status())
    val status=mutableStatus.asStateFlow()
    private val events=IslandEvents()
    private var connected=false
    private var registered=false
    private var windowContext:Context?=null
    private val overlayContext:Context get() = windowContext ?: run {
        val display=app.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        app.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
            .also { windowContext=it }
    }
    private val windows get()=overlayContext.getSystemService(WindowManager::class.java)
    private var root:IslandWindow?=null
    private var card:FrameLayout?=null
    private val exitingWindows=mutableSetOf<IslandWindow>()
    private var animator:ValueAnimator?=null
    private var current:Notice?=null
    private var sample=false
    private var appearance=Appearance()
    private var lastBounds:IslandRect?=null
    private val expire=Runnable { hide(true) }
    private val ops get()=app.getSystemService(AppOpsManager::class.java)
    private val permissionChange=AppOpsManager.OnOpChangedListener { _, pkg -> if(pkg==app.packageName) handler.post { refresh() } }
    private val screen=object:BroadcastReceiver() {
        override fun onReceive(c:Context,i:Intent) {
            when(i.action) {
                Intent.ACTION_SCREEN_OFF -> hide(false)
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> if(status.value.expanded) collapse()
                Intent.ACTION_CONFIGURATION_CHANGED -> { windowContext=null; hide(false) }
                else -> if(!unlocked()) hide(false)
            }
        }
    }
    fun initialize(context:Context) {
        if(::app.isInitialized) return
        app=context.applicationContext
        active.value=IslandPreferences.enabled(app)
        placement.value=IslandPreferences.read(app)
    }
    fun hasPermission(context:Context)=Settings.canDrawOverlays(context)
    fun configure(value:Appearance) { appearance=value; if(root!=null) render(status.value.expanded,false) }
    fun setEnabled(context:Context,value:Boolean) {
        initialize(context)
        active.value=value;IslandPreferences.enabled(app,value)
        if(!value) { events.clear();hide(false) }
        syncRegistration()
        SystemBridge.islandChanged(app)
        publish()
    }
    fun update(value:IslandLayout) {
        placement.value=value.sanitized();IslandPreferences.write(app,placement.value)
        if(root!=null) { render(status.value.expanded,false); if(sample) deadline() }
    }
    internal fun connect(context:Context) {
        initialize(context);connected=true;NoticeIcons.clear();syncRegistration();publish()
    }
    internal fun disconnect() { connected=false;events.clear();hide(false);mutableStatus.value=status.value.copy(peerActive=false);syncRegistration();publish() }
    internal fun inbox(notices:List<Notice>) {
        // Notification presence is a conservative hint, not proof of another app's overlay permission.
        val peer=notices.any { it.packageName=="com.aoede.twa" && it.ongoing || it.mediaNotice }
        val changed=peer!=status.value.peerActive
        mutableStatus.value=status.value.copy(peerActive=peer)
        if(!sample && current!=null) {
            val live=notices.firstOrNull { it.key==current?.key }
            if(live==null || live.visibility==android.app.Notification.VISIBILITY_SECRET ||
                live.channelVisibility==android.app.Notification.VISIBILITY_SECRET || live.ongoing || live.mediaNotice) hide(true)
        }
        if(changed && root!=null) render(status.value.expanded,false)
    }
    internal fun posted(notice:Notice) {
        if(!active.value || !connected || !hasPermission(app) || !unlocked() || notice.packageName==app.packageName ||
            notice.ongoing || notice.mediaNotice || notice.visibility==android.app.Notification.VISIBILITY_SECRET ||
            notice.channelVisibility==android.app.Notification.VISIBILITY_SECRET) return
        if(!events.accept(notice.key,notice.title+"\u0000"+notice.text,notice.time,System.currentTimeMillis())) return
        if((status.value.peerActive || layout.value.reserveAlways) && layout.value.coexistence==1) return
        current=notice;sample=false;syncRegistration();render(false,true);deadline()
    }
    /** Explicit settings preview; never posts a system notification or requests permission itself. */
    fun preview(context:Context):Boolean {
        initialize(context)
        if(!hasPermission(app) || !unlocked()) return false
        current=Notice("island-preview",app.packageName,"Vitreo","Your notifications, at a glance",
            "Tap outside to collapse. Open an app or dismiss an alert from here.",System.currentTimeMillis(),false,null,
            appIcon=NoticeIcons.load(app,app.packageName,null))
        sample=true;syncRegistration();render(false,true);deadline();return root!=null
    }
    private fun unlocked()=app.getSystemService(PowerManager::class.java).isInteractive &&
        app.getSystemService(KeyguardManager::class.java).let { !it.isKeyguardLocked && !it.isDeviceLocked }
    private fun syncRegistration() {
        val needed=active.value && connected || sample || exitingWindows.isNotEmpty()
        if(needed && !registered) {
            ContextCompat.registerReceiver(app,screen,IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            },ContextCompat.RECEIVER_EXPORTED)
            ops.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,app.packageName,permissionChange)
            registered=true
        } else if(!needed && registered) {
            app.unregisterReceiver(screen);ops.stopWatchingMode(permissionChange);registered=false
        }
    }
    fun refresh() {
        if(!::app.isInitialized) return
        if(!hasPermission(app) || !unlocked() || !active.value && !sample) hide(false)
        else if(root!=null) render(status.value.expanded,false)
        publish()
    }
    private fun publish() { mutableStatus.value=status.value.copy(overlayPermission=hasPermission(app),connected=connected) }
    private fun dp(v:Int)=(v*overlayContext.resources.displayMetrics.density).roundToInt()
    private fun px(v:Int)=(v/overlayContext.resources.displayMetrics.density).roundToInt()
    private fun geometry():IslandBounds? {
        val metrics=windows.currentWindowMetrics
        val insets=metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.tappableElement())
        return islandBounds(layout.value,px(metrics.bounds.width()),px(metrics.bounds.height()),px(insets.left),px(insets.top),px(insets.right),px(insets.bottom),status.value.peerActive)
            ?.let { b -> fun scale(r:IslandRect)=IslandRect(dp(r.left),dp(r.top),dp(r.right),dp(r.bottom))
                IslandBounds(scale(b.capsule),b.handle?.let(::scale),scale(b.touch),scale(b.window)) }
    }
    private val reduced get()=appearance.reducedEffects || !ValueAnimator.areAnimatorsEnabled() || app.getSystemService(PowerManager::class.java).isPowerSaveMode
    private val cyber get()=appearance.themePreset==1
    private val accent get()=if(cyber) 0xff4de9e8.toInt() else Color.WHITE
    private fun shape(radius:Float)=GradientDrawable().apply {
        setColor(if(cyber) 0xff101317.toInt() else Color.BLACK);cornerRadius=radius
        setStroke(dp(1),if(cyber) 0xffe64455.toInt() else 0xff27272a.toInt())
    }
    private fun deadline() {
        handler.removeCallbacks(expire)
        val base=if(sample) 30_000 else if(status.value.expanded) 20_000 else layout.value.duration*1000
        val timeout=app.getSystemService(AccessibilityManager::class.java).getRecommendedTimeoutMillis(base,
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_CONTROLS)
        handler.postDelayed(expire,timeout.toLong())
    }
    fun collapse() { if(status.value.expanded) { render(false,true);deadline() } }
    private fun render(open:Boolean,animate:Boolean) {
        val notice=current ?: return
        if(!hasPermission(app) || !unlocked()) { hide(false);return }
        val bounds=geometry() ?: run { hide(false);return }
        animator?.cancel();animator=null
        val old=lastBounds ?: bounds.capsule
        val full=open || status.value.expanded && animate && !reduced
        val metric=windows.currentWindowMetrics
        val safe=metric.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val expandedWidth=minOf(dp(420),metric.bounds.width()-safe.left-safe.right-dp(24)).coerceAtLeast(dp(48))
        val expandedTop=maxOf(bounds.capsule.top,safe.top+dp(8))
        val available=metric.bounds.height()-safe.bottom-expandedTop-dp(16)
        if(open && available<dp(120)) { hide(false);return }
        val expandedContent=if(open) expanded(notice).also {
            it.measure(View.MeasureSpec.makeMeasureSpec(expandedWidth,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(minOf(dp(420),available),View.MeasureSpec.AT_MOST))
        } else null
        val target=if(open) IslandRect((metric.bounds.width()-expandedWidth)/2,expandedTop,
            (metric.bounds.width()+expandedWidth)/2,expandedTop+checkNotNull(expandedContent).measuredHeight) else bounds.capsule
        val entering=root==null
        val modeChanged=status.value.expanded!=open
        if(entering) clearExitingWindows()
        val view=root ?: IslandWindow(overlayContext)
        if(modeChanged) {
            view.animate().cancel();view.scaleX=1f;view.scaleY=1f;view.alpha=1f
        }
        if(!full) view.pivotX=(bounds.capsule.left+bounds.capsule.right)/2f-bounds.window.left
        val centerEntrance=entering && !open && animate && !reduced
        if(centerEntrance) { view.scaleX=.02f;view.alpha=0f }
        view.removeAllViews()
        view.importantForAccessibility=if(open) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.contentDescription=if(open) null else "Expand ${notice.app} notification"
        view.setOnClickListener(if(open) null else View.OnClickListener { render(true,true);deadline() })
        val tile=FrameLayout(overlayContext).apply {
            tag="vitreo-island-card";background=shape(if(cyber) dp(10).toFloat() else (if(open) dp(34) else target.height/2).toFloat())
            clipToOutline=true;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            elevation=dp(12).toFloat()
        }
        card=tile
        val originX=if(full) 0 else bounds.window.left
        val originY=if(full) 0 else bounds.window.top
        fun place(rect:IslandRect) {
            tile.layoutParams=FrameLayout.LayoutParams(rect.width,rect.height).apply { leftMargin=rect.left-originX;topMargin=rect.top-originY }
        }
        view.addView(tile)
        if(open) tile.addView(expandedContent,FrameLayout.LayoutParams(-1,-1))
        else {
            tile.addView(compact(notice),FrameLayout.LayoutParams(-1,-1))
            fun hit(rect:IslandRect,handle:Boolean=false) {
                view.addView(FrameLayout(overlayContext).apply {
                    tag=if(handle) "vitreo-island-handle" else "vitreo-island-touch"
                    if(handle) { background=shape(dp(7).toFloat());addView(View(overlayContext).apply { background=shape(dp(1).toFloat()).apply { setColor(0xffa8a8ad.toInt()) } },
                        FrameLayout.LayoutParams(dp(14),dp(2),Gravity.CENTER)) }
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO;setOnClickListener { render(true,true);deadline() }
                    minimumHeight=0;isClickable=true
                },FrameLayout.LayoutParams(rect.width,rect.height).apply { leftMargin=rect.left-originX;topMargin=rect.top-originY })
            }
            hit(bounds.touch);bounds.handle?.let { hit(it,true) }
            tile.contentDescription="Expand ${notice.app} notification";tile.setOnClickListener { render(true,true);deadline() }
        }
        val params=WindowManager.LayoutParams(if(full) -1 else bounds.window.width,if(full) -1 else bounds.window.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                if(open) WindowManager.LayoutParams.FLAG_DIM_BEHIND else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.LEFT;x=originX;y=originY;dimAmount=if(open) .18f else 0f
            setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title="Vitreo notification island"
        }
        place(if(animate && !reduced) old else target)
        try {
            if(root==null) windows.addView(view,params) else windows.updateViewLayout(view,params)
            root=view;mutableStatus.value=status.value.copy(visible=true,expanded=open,overlayPermission=true)
        } catch(_:RuntimeException) { hide(false);return }
        lastBounds=target
        if(centerEntrance) {
            // Aoede's compact entrance: full height, opening horizontally from the capsule centre.
            view.animate().scaleX(1f).alpha(1f).setDuration(280)
                .setInterpolator(android.view.animation.PathInterpolator(.16f,1f,.3f,1f)).start()
        } else if(animate && !reduced) {
            val content=tile.getChildAt(0);content.alpha=0f
            animator=ValueAnimator.ofFloat(0f,1f).apply {
                duration=if(open) 360 else 280;interpolator=android.view.animation.PathInterpolator(.22f,.8f,.18f,1f)
                addUpdateListener {
                    val f=it.animatedValue as Float
                    fun mix(a:Int,b:Int)=(a+(b-a)*f).roundToInt()
                    place(IslandRect(mix(old.left,target.left),mix(old.top,target.top),mix(old.right,target.right),mix(old.bottom,target.bottom)))
                    content.alpha=((f-.15f)/.85f).coerceIn(0f,1f)
                }
                addListener(object:android.animation.AnimatorListenerAdapter() {
                    private var cancelled=false
                    override fun onAnimationCancel(animation:android.animation.Animator) { cancelled=true }
                    override fun onAnimationEnd(animation:android.animation.Animator) {
                        if(!cancelled && !open && full) handler.post { if(root===view && !status.value.expanded) render(false,false) }
                    }
                });start()
            }
        }
        if(open) view.requestFocus()
    }
    private fun text(value:String,size:Float,bold:Boolean=false)=TextView(overlayContext).apply {
        text=value;textSize=size;setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        if(bold) typeface=Typeface.create(if(cyber) "monospace" else "sans-serif",Typeface.BOLD)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun icon(notice:Notice,size:Int)=ImageView(overlayContext).apply {
        setImageBitmap(notice.appIcon);if(notice.appIcon==null) setImageResource(android.R.drawable.ic_dialog_info)
        contentDescription="${notice.app} app icon";scaleType=ImageView.ScaleType.FIT_CENTER
        background=shape(dp(8).toFloat());clipToOutline=true
        layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
    }
    private fun bell()=ImageView(overlayContext).apply { setImageResource(dev.vitreo.launcher.R.drawable.ic_island_bell);setColorFilter(accent);contentDescription="New notification" }
    private fun compact(notice:Notice)=LinearLayout(overlayContext).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),0,dp(12),0)
        addView(icon(notice,minOf(26,layout.value.height-10)))
        addView(View(overlayContext),LinearLayout.LayoutParams(0,1,1f)) // Clear the central camera region.
        addView(bell(),LinearLayout.LayoutParams(dp(22),dp(minOf(22,layout.value.height-8))))
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private fun expanded(notice:Notice)=ScrollView(overlayContext).apply {
        isFillViewport=true
        addView(LinearLayout(overlayContext).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(20),dp(22),dp(14));setOnClickListener { deadline() }
            addView(LinearLayout(overlayContext).apply {
                gravity=Gravity.CENTER_VERTICAL;addView(icon(notice,32));addView(text(notice.app,14f,true),LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(10) })
                addView(bell(),LinearLayout.LayoutParams(dp(22),dp(22)))
            })
            addView(text(notice.title.ifBlank { "New notification" },18f,true).apply { maxLines=2;setPadding(0,dp(16),0,dp(4)) })
            if(notice.text.isNotBlank()) addView(text(notice.text,14f).apply { maxLines=5;setTextColor(0xffc7c7cc.toInt());setLineSpacing(dp(2).toFloat(),1f) })
            addView(LinearLayout(overlayContext).apply {
                val stacked=resources.configuration.fontScale>1.3f || px(windows.currentWindowMetrics.bounds.width())<340
                orientation=if(stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity=Gravity.END;setPadding(0,dp(12),0,0)
                fun action(label:String,run:()->Unit) { addView(Button(overlayContext).apply {
                    text=label;isAllCaps=false;minHeight=dp(48);setTextColor(accent);background=GradientDrawable().apply { setColor(0xff242426.toInt());cornerRadius=dp(16).toFloat() }
                    setOnClickListener { run() }
                },(if(stacked) LinearLayout.LayoutParams(-1,-2) else LinearLayout.LayoutParams(0,-2,1f)).apply {
                    marginStart=dp(4);marginEnd=dp(4);if(stacked) bottomMargin=dp(6)
                }) }
                if(!sample && notice.intent!=null) action("Open") {
                    val live=SystemBridge.state.value.notices.firstOrNull{it.key==notice.key}
                    // Keep the user-visible overlay attached during the user-initiated send.
                    // Android 15's background-start exemption requires a visible overlay.
                    val opened=live!=null && launchNotification(app,live.intent)
                    hide(false)
                    if(!opened) Toast.makeText(app,"This notification is no longer available.",Toast.LENGTH_SHORT).show()
                }
                if(!sample && notice.clearable) action("Dismiss") { SystemBridge.dismiss(notice.key);hide(true) }
                action("Collapse") { collapse() }
            })
        },FrameLayout.LayoutParams(-1,-2))
    }
    private fun removeExitingWindow(view:IslandWindow) {
        exitingWindows.remove(view)
        view.animate().withEndAction(null).cancel()
        runCatching { windows.removeViewImmediate(view) }
    }
    private fun clearExitingWindows() {
        exitingWindows.toList().forEach(::removeExitingWindow)
    }
    private fun hide(animate:Boolean) {
        val animated=animate && !reduced
        if(!animated) clearExitingWindows()
        // Close the compact capsule, as in Aoede, instead of scaling a fullscreen input window.
        if(animated && status.value.expanded && root!=null) render(false,false)
        handler.removeCallbacks(expire);animator?.cancel();animator=null
        val old=root;root=null;card=null;lastBounds=null;current=null;sample=false
        mutableStatus.value=status.value.copy(visible=false,expanded=false)
        if(old!=null) {
            old.animate().cancel()
            if(animated && old.isAttachedToWindow) {
                old.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                val released=runCatching {
                    val lp=old.layoutParams as WindowManager.LayoutParams
                    lp.flags=lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    lp.alpha=.8f // Android 12 permits touches through the non-interactive exit window.
                    windows.updateViewLayout(old,lp)
                }.isSuccess
                if(released) {
                    exitingWindows.add(old)
                    old.animate().scaleX(.02f).alpha(0f).setDuration(180)
                        .setInterpolator(android.view.animation.PathInterpolator(.4f,0f,1f,1f))
                        .withEndAction { removeExitingWindow(old);syncRegistration() }.start()
                } else runCatching { windows.removeViewImmediate(old) }
            } else runCatching { windows.removeViewImmediate(old) }
        }
        // Keep screen/permission observers until an exit completes, so revocation or lock
        // also removes windows whose disappearance animation has already started.
        syncRegistration()
    }
    private class IslandWindow(context:Context):FrameLayout(context) {
        private var swallow=false
        private var focused=false
        init { isFocusableInTouchMode=true }
        override fun dispatchTouchEvent(e:MotionEvent):Boolean {
            if(e.actionMasked==MotionEvent.ACTION_DOWN) {
                deadline();swallow=false
                // Yield only when the actual touch is obscured. Partial-window coverage also
                // describes our capsule sitting under Android's status/camera band.
                if(e.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED!=0) {
                    hide(false);swallow=true
                } else if(status.value.expanded) {
                    val r=Rect();card?.getHitRect(r)
                    if(!r.contains(e.x.toInt(),e.y.toInt())) { swallow=true;collapse() }
                }
            }
            if(swallow) return true
            return super.dispatchTouchEvent(e)
        }
        override fun onWindowFocusChanged(hasFocus:Boolean) {
            super.onWindowFocusChanged(hasFocus)
            val lost=focused && !hasFocus;focused=hasFocus
            if(lost && status.value.expanded) handler.post { if(root===this && !hasWindowFocus()) collapse() }
        }
        override fun dispatchKeyEvent(e:KeyEvent):Boolean {
            if(e.keyCode==KeyEvent.KEYCODE_BACK && status.value.expanded) { if(e.action==KeyEvent.ACTION_UP) collapse();return true }
            return super.dispatchKeyEvent(e)
        }
    }
}
