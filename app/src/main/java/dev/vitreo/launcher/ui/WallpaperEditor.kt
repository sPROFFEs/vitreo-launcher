package dev.vitreo.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.vitreo.launcher.LauncherViewModel
import dev.vitreo.launcher.core.*
import dev.vitreo.launcher.graphics.WallpaperStore
import kotlin.math.roundToInt

val PhotoZoomKey = SemanticsPropertyKey<Float>("PhotoZoom")

@Composable
fun WallpaperEditor(draft: LauncherViewModel.PhotoDraft, busy: Boolean, onCancel: () -> Unit,
    onSave: (PhotoTransform) -> Unit) {
    var zoom by rememberSaveable(draft.frame) { mutableFloatStateOf(draft.transform.zoom) }
    var x by rememberSaveable(draft.frame) { mutableFloatStateOf(draft.transform.x) }
    var y by rememberSaveable(draft.frame) { mutableFloatStateOf(draft.transform.y) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val bitmap = draft.frame.sharp
    val fit = photoFitZoom(bitmap.width.toFloat(), bitmap.height.toFloat(), size.width.toFloat(), size.height.toFloat())
    fun update(value: PhotoTransform) { zoom = value.zoom; x = value.x; y = value.y }
    val transform = PhotoTransform(zoom, x, y)
    // Pointer events may arrive in a batch before recomposition. Read the live
    // state here so every incremental pinch/pan is accumulated, even on a slow frame.
    fun current() = PhotoTransform(zoom, x, y)
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("wallpaper_editor").semantics { this[PhotoZoomKey] = zoom }) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(draft.frame, busy) {
            if (!busy) detectTransformGestures { centroid, pan, scale, _ ->
                update(transformPhoto(bitmap.width.toFloat(), bitmap.height.toFloat(), size.width.toFloat(), size.height.toFloat(),
                    current(), scale, pan.x, pan.y, centroid.x, centroid.y))
            }
        }) {
            drawIntoCanvas { WallpaperStore.drawPhoto(it.nativeCanvas, draft.frame, this.size.width, this.size.height, transform) }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .35f)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.testTag("wallpaper_cancel")) { Text("Cancel", color = Color.White) }
                Text("Move & Scale", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onSave(photoPlacement(bitmap.width.toFloat(), bitmap.height.toFloat(),
                    size.width.toFloat(), size.height.toFloat(), current()).transform) }, enabled = !busy,
                    modifier = Modifier.testTag("wallpaper_save")) { Text(if (busy) "Saving…" else "Save", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.weight(1f))
            Text("Pinch to zoom · Drag to move", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).background(Color.Black.copy(alpha = .5f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp))
            Column(Modifier.padding(16.dp).background(Color(0xeb191b22), RoundedCornerShape(28.dp)).padding(17.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Zoom", Modifier.weight(1f), color = Color.White)
                    Text("${(zoom * 100).roundToInt()}%", color = Color.White.copy(alpha = .8f))
                }
                IosSlider(zoom.coerceIn(fit, 6f), { update(transformPhoto(bitmap.width.toFloat(), bitmap.height.toFloat(),
                    size.width.toFloat(), size.height.toFloat(), current(), it / zoom, 0f, 0f)) },
                    valueRange = fit..6f, enabled = !busy, onDark = true, modifier = Modifier.testTag("wallpaper_zoom"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { update(PhotoTransform(fit)) }, enabled = !busy, modifier = Modifier.testTag("wallpaper_fit")) { Text("Fit", color = Color.White) }
                    TextButton(onClick = { update(PhotoTransform()) }, enabled = !busy, modifier = Modifier.testTag("wallpaper_fill")) { Text("Fill", color = Color.White) }
                    TextButton(onClick = { update(draft.transform) }, enabled = !busy) { Text("Reset", color = Color.White) }
                }
                Text("Fit keeps the whole photo and softly extends its background.", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            }
        }
    }
}
