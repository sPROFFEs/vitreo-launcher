package dev.vitreo.launcher.graphics

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import dev.vitreo.launcher.core.Appearance
import kotlin.math.max
import kotlin.math.min

object GlassDiagnostics {
    @Volatile var lastFailure: String = "none"
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
    private var touchX = .5f
    private var touchY = .5f
    private var pressed = 0f
    private var sharp: BitmapShader? = null
    private var frosted: BitmapShader? = null
    private var fallback: BitmapShader? = null
    private var runtime: Shader? = null
    private var shaderFailed = false
    private val matrix = Matrix()
    private val bounds = RectF()
    private var borderWidth = 0
    private var borderHeight = 0

    fun configure(frame: WallpaperFrame?, viewport: Viewport, appearance: Appearance, radius: Float, tint: Float,
        regular: Boolean = false, touchX: Float = .5f, touchY: Float = .5f, pressed: Float = 0f) {
        if (this.frame !== frame) {
            this.frame = frame
            sharp = frame?.let { BitmapShader(it.sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            frosted = frame?.let { BitmapShader(it.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            fallback = frame?.let { BitmapShader(it.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        this.viewport = viewport; this.appearance = appearance
        radiusDp = radius; panelTint = tint
        this.regular = regular; this.touchX = touchX; this.touchY = touchY; this.pressed = pressed
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
        val materialDark = dark || !regular
        val opacity = if (regular) max(panelTint, if (dark) .74f else .80f)
            else max(panelTint, if ((f?.brightness ?: 0f) > .7f) .56f else .20f)
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
                    appearance.refraction * if (regular) .32f else 1f,
                    if (regular) max(.94f, appearance.frost) else appearance.frost, opacity, materialDark,
                    touchX, touchY, pressed)
                paint.shader = runtime; paint.color = Color.WHITE
                canvas.drawRoundRect(bounds, radius, radius, paint)
                usedAgsl = true
            } catch (e: Exception) {
                // A driver/compiler failure must not make the home screen unusable.
                shaderFailed = true
                GlassDiagnostics.lastFailure = "${e.javaClass.simpleName}: ${e.message?.take(600)}"
            }
        }
        if (!usedAgsl) {
            align(fallback!!, f.frosted, x, y)
            paint.shader = fallback; paint.color = Color.WHITE
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.shader = null; paint.color = Color.argb((opacity * 255).toInt(), Color.red(materialColor), Color.green(materialColor), Color.blue(materialColor))
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
        }
        border.strokeWidth = density * .65f
        bounds.inset(border.strokeWidth * .5f, border.strokeWidth * .5f)
        canvas.drawRoundRect(bounds, max(0f, radius - .4f * density), max(0f, radius - .4f * density), border)
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
                  touchX: Float, touchY: Float, pressed: Float) {
        val s = shader as RuntimeShader
        s.setInputShader("wallpaper", sharp); s.setInputShader("frosted", frosted)
        s.setFloatUniform("resolution", width, height); s.setFloatUniform("origin", x, y)
        s.setFloatUniform("radius", radius); s.setFloatUniform("strength", strength)
        s.setFloatUniform("frostAmount", frost); s.setFloatUniform("tint", tint)
        s.setFloatUniform("touchPoint", touchX * width, touchY * height); s.setFloatUniform("pressed", pressed)
        val color = if (dark) .065f else .96f
        s.setFloatUniform("tintColor", color, color, if (dark) .09f else .99f)
    }
}
