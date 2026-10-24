package dev.vitreo.launcher.ui

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
            GlassPanel(Modifier.fillMaxWidth().clickable(onClickLabel="Unlock to open ${notice.app}") { onOpen(notice.key) },radius=24f,tint=.23f,interactive=true) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    LauncherSymbol(Icons.Outlined.NotificationsNone,null,Modifier.size(26.dp),tint=Color.White)
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text(notice.app,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(if(notice.count==1) "Notification · Unlock to view" else "${notice.count} notifications · Unlock to view",color=Color.White.copy(alpha=.72f),fontSize=13.sp)
                    }
                }
            }
        }
    }
}
