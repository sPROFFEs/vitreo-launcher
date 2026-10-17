package dev.vitreo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vitreo.launcher.core.HomeWidgetPlacement
import dev.vitreo.launcher.data.WidgetController

@Composable
internal fun WidgetEditor(controller: WidgetController, id: Int, placement: HomeWidgetPlacement?, homePages: Int,
    defaultPage: Int, onHome: (HomeWidgetPlacement) -> Unit, onToday: () -> Unit, onRemove: () -> Unit,
    onConfigure: () -> Unit, onClose: () -> Unit, onMoveToday: (Int) -> Unit) {
    val context = LocalContext.current
    val provider = controller.manager.getAppWidgetInfo(id)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().testTag("widget_editor")) {
        SheetHeader(provider?.loadLabel(context.packageManager) ?: "Widget", onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (provider != null) WidgetPreview(provider)
            IosSectionLabel("Location")
            IosGroup {
                IosRow("Home Screen", if (placement != null) "Page ${placement.page + 1}" else "Move") {
                    onHome(placement ?: HomeWidgetPlacement(id, defaultPage))
                }
                IosDivider()
                IosRow("Today", if (placement == null) "Selected" else "Move") { onToday() }
            }
            if (placement != null) {
                IosSectionLabel("Size")
                IosSegmented(listOf("Small", "Medium", "Large"),
                    if (placement.rows >= 4) 2 else if (placement.columns >= 4) 1 else 0,
                    Modifier.testTag("widget_size")) { index ->
                    onHome(placement.copy(columns = if (index == 0) 2 else 4, rows = if (index == 2) 4 else 2))
                }
                IosSectionLabel("Page")
                IosGroup {
                    IosRow("Previous Page", enabled = placement.page > 0) { onHome(placement.copy(page = placement.page - 1, row = 0, column = 0)) }
                    IosDivider()
                    IosRow("Next Page", enabled = placement.page < 63) { onHome(placement.copy(page = placement.page + 1, row = 0, column = 0)) }
                    IosDivider()
                    IosRow("New Page") { onHome(placement.copy(page = homePages.coerceAtMost(63), row = 0, column = 0)) }
                }
                Text("In Edit Home Screen, hold and drag a widget to place it. Pause at the edge to change pages. Apps make room automatically.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            if (placement == null) {
                val position = controller.todayIds.indexOf(id)
                IosSectionLabel("Order in Today")
                IosGroup {
                    IosRow("Move Up", enabled = position > 0) { onMoveToday(-1) }; IosDivider()
                    IosRow("Move Down", enabled = position >= 0 && position < controller.todayIds.lastIndex) { onMoveToday(1) }
                }
            }
            IosSectionLabel("Widget")
            IosGroup {
                if (provider?.configure != null && provider.widgetFeatures and
                    android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0) {
                    IosRow("Configure Widget") { onConfigure() }
                    IosDivider()
                }
                IosRow("Remove Widget", destructive = true) { onRemove() }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
