package dev.vitreo.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.core.IslandLayout
import dev.vitreo.launcher.core.islandBounds
import dev.vitreo.launcher.data.SystemBridge
import dev.vitreo.launcher.island.NotificationIsland
import kotlin.math.roundToInt

@Composable
internal fun NotificationIslandSettings() {
    val context=LocalContext.current
    val enabled by NotificationIsland.enabled.collectAsStateWithLifecycle()
    val a by NotificationIsland.layout.collectAsStateWithLifecycle()
    val status by NotificationIsland.status.collectAsStateWithLifecycle()
    val bridge by SystemBridge.state.collectAsStateWithLifecycle()
    var hint by remember { mutableStateOf<String?>(null) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) NotificationIsland.refresh() }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
    }
    fun change(v:IslandLayout) { NotificationIsland.update(v) }
    IosGroup {
        IosToggleRow("Enable notification island",enabled,modifier=Modifier.testTag("island_switch")) { NotificationIsland.setEnabled(context,it) }
    }
    IslandFootnote("A temporary island above your apps when a new notification arrives. Tap the app icon, capsule or handle to expand; tap outside or press Back to collapse. Existing alerts are not replayed when you enable it. Ongoing media uses your music island instead.")
    IosSectionLabel("Android permissions")
    IosGroup {
        IosRow("Display over other apps",if(status.overlayPermission) "Allowed" else "Required") {
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}"))) }
                .onFailure { hint="Open Android Settings → Apps → Special app access → Display over other apps." }
        };IosDivider()
        IosRow("Android notification access",if(bridge.access) "Allowed" else "Required") {
            runCatching { context.startActivity(SystemBridge.accessIntent(context)) }
                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
        };IosDivider()
        IosRow("Preview island",if(status.visible) "Visible" else "Try it",enabled=status.overlayPermission) {
            if(!NotificationIsland.preview(context)) hint="Unlock your device and allow Display over other apps to preview."
        }
    }
    IslandFootnote("Both permissions are required for incoming alerts. The island disappears on lock, screen-off, access revocation or when disabled. Content stays in memory; it is never backed up or uploaded. Android notification banners remain controlled by Android.")
    hint?.let { IslandFootnote(it) }
    IosSectionLabel("Placement preview")
    IslandPlacementPreview(a,status.peerActive)
    IslandFootnote("Black: capsule · cyan: touch area · gray: handle. Values are in dp, measured from the top of the screen. The interaction area stays below the camera and status bar. Use ± for one-dp adjustments; Preview shows the actual position on your device.")
    IosSectionLabel("Capsule · same controls as Aoede")
    IosGroup {
        IslandSlider("Width",a.width,112..320,"island_width"){change(a.copy(width=it))}
        IslandSlider("Height",a.height,28..72,"island_height"){change(a.copy(height=it))}
        IslandSlider("Top position",a.top,0..240,"island_top"){change(a.copy(top=it))}
        IslandSlider("Horizontal position",a.horizontal,-100..100,"island_horizontal",true){change(a.copy(horizontal=it))}
    }
    IosSectionLabel("Handle")
    IosGroup {
        IslandSlider("Width",a.handleWidth,16..128,"island_handle_width"){change(a.copy(handleWidth=it))}
        IslandSlider("Height · 0 hides",a.handleHeight,0..32,"island_handle_height"){change(a.copy(handleHeight=it))}
        IosToggleRow("Follow capsule vertically",a.handleTop<0){change(a.copy(handleTop=if(it) -1 else maxOf(a.top+a.height+4,72)))}
        if(a.handleTop>=0) IslandSlider("Top position",a.handleTop,0..320,"island_handle_top"){change(a.copy(handleTop=it))}
        IslandSlider("Horizontal position",a.handleHorizontal,-100..100,"island_handle_horizontal",true){change(a.copy(handleHorizontal=it))}
    }
    IosSectionLabel("Touch area")
    IosGroup {
        IslandSlider("Width",a.touchWidth,48..320,"island_touch_width"){change(a.copy(touchWidth=it))}
        IslandSlider("Height",a.touchHeight,48..160,"island_touch_height"){change(a.copy(touchHeight=it))}
        IosToggleRow("Follow safe top position",a.touchTop<0){change(a.copy(touchTop=if(it) -1 else maxOf(a.top,48)))}
        if(a.touchTop>=0) IslandSlider("Top position",a.touchTop,0..320,"island_touch_top"){change(a.copy(touchTop=it))}
        IslandSlider("Horizontal position",a.touchHorizontal,-100..100,"island_touch_horizontal",true){change(a.copy(touchHorizontal=it))}
    }
    IosSectionLabel("Other islands & overlays")
    IosGroup {
        listOf("Move below the other island","Pause notification island","Use independent placement").forEachIndexed { i,title ->
            IosRow(title,if(a.coexistence==i) "Selected" else ""){change(a.copy(coexistence=i))};if(i<2) IosDivider()
        }
    }
    IslandFootnote(if(status.peerActive) "Media activity detected. Your coexistence preference is being applied."
        else "Automatically reserves space for Aoede's ongoing player and other media notifications. Android does not reveal whether another app has actually drawn an island or its dimensions. Match the reserved area to that app's layout, or reserve it manually for other overlays.")
    if(a.coexistence!=2) IosGroup {
        IosToggleRow("Always reserve space",a.reserveAlways){change(a.copy(reserveAlways=it))}
        IslandSlider("Other island · bottom edge",a.reservedBottom,0..400,"island_reserved_bottom"){change(a.copy(reservedBottom=it))}
    }
    IosSectionLabel("Timing & reset")
    IosGroup {
        IslandSlider("Compact display time",a.duration,3..15,"island_duration",unit="s"){change(a.copy(duration=it))}
        IosDivider();IosRow("Reset island layout"){change(IslandLayout())}
    }
    IslandFootnote("Expanded alerts allow more reading time. Android accessibility timeout preferences are respected. Activation and layout are stored only on this device; importing a Home backup cannot enable an overlay.")
}

@Composable
private fun IslandFootnote(value:String) { Text(value,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp,modifier=Modifier.padding(horizontal=17.dp,vertical=12.dp)) }

@Composable
private fun IslandSlider(title:String,value:Int,range:IntRange,tag:String,percent:Boolean=false,unit:String="dp",change:(Int)->Unit) {
    Column(Modifier.padding(horizontal=12.dp,vertical=9.dp)) {
        Row(Modifier.padding(horizontal=5.dp)) {
            Text(title,Modifier.weight(1f),fontSize=14.sp)
            Text(if(percent) "$value%" else "$value $unit",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
        }
        Row {
            TextButton(onClick={change((value-1).coerceIn(range))},enabled=value>range.first,modifier=Modifier.size(48.dp).testTag("$tag-decrease")) { Text("−") }
            IosSlider(value.toFloat(),{change(it.roundToInt())},valueRange=range.first.toFloat()..range.last.toFloat(),
                modifier=Modifier.weight(1f).testTag(tag))
            TextButton(onClick={change((value+1).coerceIn(range))},enabled=value<range.last,modifier=Modifier.size(48.dp).testTag("$tag-increase")) { Text("+") }
        }
    }
}

@Composable
private fun IslandPlacementPreview(a:IslandLayout,peer:Boolean) {
    Canvas(Modifier.fillMaxWidth().height(210.dp).testTag("island_placement_preview")) {
        val scale=minOf(size.width/380f,size.height/440f)
        val ox=(size.width-360*scale)/2
        drawRoundRect(Color(0xff35363b),Offset(ox,0f),Size(360*scale,440*scale),CornerRadius(20*scale))
        fun paint(r:dev.vitreo.launcher.core.IslandRect,color:Color) { drawRoundRect(color,Offset(ox+r.left*scale,r.top*scale),Size(r.width*scale,r.height*scale),CornerRadius(12*scale)) }
        islandBounds(a,360,800,0,36,0,24,peer)?.let { b ->
            paint(b.touch,Color.Cyan.copy(alpha=.25f));paint(b.capsule,Color.Black);b.handle?.let { paint(it,Color.LightGray) }
        }
    }
}
