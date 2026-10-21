package dev.vitreo.launcher.graphics

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.vitreo.launcher.core.Appearance
import kotlin.math.max
import kotlin.math.min

object GlassDiagnostics {
    var lastFailure: String by mutableStateOf("none")
        private set
    @Volatile var hardwareRefractionDrawn: Boolean = false
        private set
    fun rendered() { hardwareRefractionDrawn = true }
    fun failed(error: Exception) { lastFailure = "${error.javaClass.simpleName}: ${error.message?.take(600)}" }
}

data class Viewport(val width: Float = 1f, val height: Float = 1f, val screenX: Float = 0f, val screenY: Float = 0f)

/** Draws inside the Compose display list: no AndroidView, pre-draw listener or idle render loop. */
class GlassRenderer(private val context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var frame: WallpaperFrame? = null
    private var viewport = Viewport()
    private var appearance = Appearance()
    private var radiusDp = 28f
    private var panelTint = .18f
    private var regular = false
    private var foregroundDark = false
    private var touchX = .5f
    private var touchY = .5f
    private var pressed = 0f
    private var sharp: BitmapShader? = null
    private var frosted: BitmapShader? = null
    private var fallback: BitmapShader? = null
    private var fallbackSharp: BitmapShader? = null
    private var runtime: Shader? = null
    private var shaderFailed = false
    private val matrix = Matrix()
    private val bounds = RectF()
    private var borderWidth = 0
    private var borderHeight = 0
    private val innerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun configure(frame: WallpaperFrame?, viewport: Viewport, appearance: Appearance, radius: Float, tint: Float,
        regular: Boolean = false, touchX: Float = .5f, touchY: Float = .5f, pressed: Float = 0f, foregroundDark: Boolean = false) {
        if (this.frame !== frame) {
            this.frame = frame
            sharp = frame?.let { texture(it.sharp) }
            frosted = frame?.let { texture(it.frosted) }
            fallbackSharp = frame?.let { BitmapShader(it.sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            fallback = frame?.let { BitmapShader(it.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        this.viewport = viewport; this.appearance = appearance
        radiusDp = radius; panelTint = tint
        this.regular = regular; this.foregroundDark = foregroundDark; this.touchX = touchX; this.touchY = touchY; this.pressed = pressed
    }

    private fun texture(bitmap: Bitmap) = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        // Paint.FILTER_BITMAP_FLAG is ignored for inputs to a RuntimeShader.
        if (Build.VERSION.SDK_INT >= 33) filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

    private fun align(shader: BitmapShader, bitmap: Bitmap, subtractX: Float = 0f, subtractY: Float = 0f) {
        val scale = max(viewport.width / bitmap.width, viewport.height / bitmap.height)
        val x = (viewport.width - bitmap.width * scale) * .5f - subtractX
        val y = (viewport.height - bitmap.height * scale) * .5f - subtractY
        matrix.reset(); matrix.setScale(scale, scale); matrix.postTranslate(x, y)
        shader.setLocalMatrix(matrix)
    }

    fun draw(canvas: Canvas, width: Int, height: Int, screenX: Float, screenY: Float) {
        if (width <= 0 || height <= 0) return
        val density = context.resources.displayMetrics.density
        val radius = min(radiusDp * density, min(width, height) * .5f)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val f = frame
        val dark = appearance.interfaceStyle == 2 || appearance.interfaceStyle == 0 &&
            context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val materialDark = if (regular) dark else !foregroundDark
        val backdropBrightness = (f?.brightness ?: 0f) * (1f - appearance.wallpaperDim)
        val opacity = if (regular) max(panelTint, if (dark) .60f else .66f)
            else max(panelTint, if (foregroundDark) .18f else if (backdropBrightness > .65f) .35f else .13f)
        val materialColor = if (materialDark) Color.rgb(23, 25, 32) else Color.rgb(248, 249, 253)
        if (appearance.opaquePanels || f == null || viewport.width <= 1f) {
            paint.shader = null; paint.color = materialColor
            canvas.drawRoundRect(bounds, radius, radius, paint)
            return
        }
        val x = screenX - viewport.screenX
        val y = screenY - viewport.screenY
        var usedAgsl = false
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && !appearance.reducedEffects && !shaderFailed) {
            try {
                if (runtime == null) runtime = Agsl.create(context)
                align(sharp!!, f.sharp); align(frosted!!, f.frosted)
                Agsl.configure(runtime!!, sharp!!, frosted!!, width.toFloat(), height.toFloat(), x, y, radius,
                    appearance.refraction * if (regular) .85f else 1f,
                    if (regular) max(.72f, appearance.frost) else appearance.frost, opacity, materialDark,
                    touchX, touchY, pressed, density, appearance.wallpaperDim)
                paint.shader = runtime; paint.color = Color.WHITE
                canvas.drawRoundRect(bounds, radius, radius, paint)
                usedAgsl = true
                GlassDiagnostics.rendered()
            } catch (e: Exception) {
                // A driver/compiler failure must not make the home screen unusable.
                shaderFailed = true
                GlassDiagnostics.failed(e)
            }
        }
        if (!usedAgsl) {
            // Blend cached textures rather than re-blurring or rebuilding a bitmap.
            // Clear controls retain wallpaper detail on Android 12 and in Battery Saver.
            align(fallbackSharp!!, f.sharp, x, y)
            paint.shader = fallbackSharp; paint.color = Color.WHITE
            canvas.drawRoundRect(bounds, radius, radius, paint)
            val frost = if (regular) max(.78f, appearance.frost) else appearance.frost
            align(fallback!!, f.frosted, x, y)
            paint.shader = fallback; paint.alpha = (frost * 255).toInt()
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.shader = null; paint.color = Color.argb((appearance.wallpaperDim * 255).toInt(), 0, 0, 0)
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.color = Color.argb((opacity * 255).toInt(), Color.red(materialColor), Color.green(materialColor), Color.blue(materialColor))
            canvas.drawRoundRect(bounds, radius, radius, paint)
            if (pressed > .001f) {
                paint.shader = RadialGradient(width * touchX, height * touchY, max(width, height).toFloat(),
                    intArrayOf(Color.argb((pressed * 35).toInt(), 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(bounds, radius, radius, paint)
                paint.shader = null
            }
        }
        if (width != borderWidth || height != borderHeight) {
            borderWidth = width; borderHeight = height
            border.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0x9cffffff.toInt(), 0x16ffffff, 0x09ffffff, 0x60ffffff), null, Shader.TileMode.CLAMP)
            innerBorder.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0x24ffffff, 0x03ffffff, 0x1b000000, 0x2affffff), null, Shader.TileMode.CLAMP)
        }
        border.strokeWidth = density * .65f
        bounds.inset(border.strokeWidth * .5f, border.strokeWidth * .5f)
        canvas.drawRoundRect(bounds, max(0f, radius - .4f * density), max(0f, radius - .4f * density), border)
        if (!usedAgsl) {
            // The cached Android 12 / reduced-effects material still has depth at the rim.
            innerBorder.strokeWidth = density * 2f
            bounds.inset(density * 2f, density * 2f)
            canvas.drawRoundRect(bounds, max(0f, radius - 2.4f * density), max(0f, radius - 2.4f * density), innerBorder)
        }
    }
}

@RequiresApi(33)
private object Agsl {
    private var source: String? = null
    fun create(context: Context): RuntimeShader {
        val code = source ?: context.assets.open("glass.agsl").bufferedReader().use { it.readText() }.also { source = it }
        return RuntimeShader(code)
    }
    fun configure(shader: Shader, sharp: Shader, frosted: Shader, width: Float, height: Float,
                  x: Float, y: Float, radius: Float, strength: Float, frost: Float, tint: Float, dark: Boolean,
                  touchX: Float, touchY: Float, pressed: Float, density: Float, dim: Float) {
        val s = shader as RuntimeShader
        s.setInputShader("wallpaper", sharp); s.setInputShader("frosted", frosted)
        s.setFloatUniform("resolution", width, height); s.setFloatUniform("origin", x, y)
        s.setFloatUniform("radius", radius); s.setFloatUniform("strength", strength)
        s.setFloatUniform("frostAmount", frost); s.setFloatUniform("tint", tint)
        s.setFloatUniform("touchPoint", touchX * width, touchY * height); s.setFloatUniform("pressed", pressed)
        s.setFloatUniform("pixelDensity", density)
        s.setFloatUniform("wallpaperDim", dim)
        val color = if (dark) .065f else .96f
        s.setFloatUniform("tintColor", color, color, if (dark) .09f else .99f)
    }
}
