package dev.vitreo.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.data.LockNotice

@Composable
fun LockNotificationCards(notices: List<LockNotice>, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    LazyColumn(modifier.fillMaxWidth().clipToBounds().testTag("lock_notifications"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items(notices,key={it.key}) { notice ->
            GlassPanel(Modifier.fillMaxWidth().testTag("lock_notice:${notice.packageName}").clickable(onClickLabel="Unlock to open ${notice.app}") { onOpen(notice.key) },
                radius=if(LocalGlass.current.appearance.iosClassic) 22f else 24f,tint=.23f,
                material=GlassMaterial.Regular,contentColor=Color.White,interactive=true) {
                Row(Modifier.heightIn(min=76.dp).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(if(LocalCyber.current) CyberShape else IconShape).background(Color.White.copy(alpha=.12f)), contentAlignment=Alignment.Center) {
                        if (notice.appIcon != null) Image(notice.appIcon.asImageBitmap(), "${notice.app} app icon", Modifier.fillMaxSize())
                        else LauncherSymbol(Icons.Outlined.NotificationsNone,null,Modifier.size(24.dp),tint=Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(notice.app,Modifier.weight(1f),color=Color.White,fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if (notice.time > 0) Text(android.text.format.DateUtils.getRelativeTimeSpanString(notice.time,System.currentTimeMillis(),60_000).toString(),
                                Modifier.padding(start=8.dp),color=Color.White.copy(alpha=.65f),fontSize=11.sp,maxLines=1)
                        }
                        Text(if(notice.count==1) "Notification · Unlock to view" else "${notice.count} notifications · Unlock to view",color=Color.White.copy(alpha=.72f),fontSize=13.sp)
                    }
                }
            }
        }
    }
}
