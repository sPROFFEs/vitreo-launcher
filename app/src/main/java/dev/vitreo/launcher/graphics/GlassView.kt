package dev.vitreo.launcher.graphics

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import dev.vitreo.launcher.core.Appearance
import kotlin.math.max
import kotlin.math.min

object GlassDiagnostics {
    @Volatile var lastFailure: String = "none"
}

data class Viewport(val width: Float = 1f, val height: Float = 1f, val screenX: Float = 0f, val screenY: Float = 0f)

/** A passive view behind Compose content. It never intercepts touches or samples another app. */
class GlassView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var frame: WallpaperFrame? = null
    private var viewport = Viewport()
    private var appearance = Appearance()
    private var radiusDp = 28f
    private var panelTint = .18f
    private var sharp: BitmapShader? = null
    private var frosted: BitmapShader? = null
    private var fallback: BitmapShader? = null
    private var runtime: Shader? = null
    private var shaderFailed = false
    private val location = IntArray(2)
    private val matrix = Matrix()
    private val bounds = RectF()

    private var lastScreenX = Int.MIN_VALUE
    private var lastScreenY = Int.MIN_VALUE
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        getLocationOnScreen(location)
        if (location[0] != lastScreenX || location[1] != lastScreenY) {
            lastScreenX = location[0]; lastScreenY = location[1]
            invalidate()
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }
    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(preDraw)
        super.onDetachedFromWindow()
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun configure(frame: WallpaperFrame?, viewport: Viewport, appearance: Appearance, radius: Float, tint: Float) {
        val changed = this.frame !== frame || this.viewport != viewport || this.appearance != appearance || radiusDp != radius || panelTint != tint
        if (this.frame !== frame) {
            this.frame = frame
            sharp = frame?.let { BitmapShader(it.sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            frosted = frame?.let { BitmapShader(it.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            fallback = frame?.let { BitmapShader(it.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        this.viewport = viewport; this.appearance = appearance
        radiusDp = radius; panelTint = tint
        if (changed) invalidate()
    }

    private fun align(shader: BitmapShader, bitmap: Bitmap, subtractX: Float = 0f, subtractY: Float = 0f) {
        val scale = max(viewport.width / bitmap.width, viewport.height / bitmap.height)
        val x = (viewport.width - bitmap.width * scale) * .5f - subtractX
        val y = (viewport.height - bitmap.height * scale) * .5f - subtractY
        matrix.reset(); matrix.setScale(scale, scale); matrix.postTranslate(x, y)
        shader.setLocalMatrix(matrix)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val radius = min(radiusDp * density, min(width, height) * .5f)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val f = frame
        if (appearance.opaquePanels || f == null || viewport.width <= 1f) {
            paint.shader = null; paint.color = Color.rgb(26, 38, 54)
            canvas.drawRoundRect(bounds, radius, radius, paint)
            return
        }
        getLocationOnScreen(location)
        val x = location[0] - viewport.screenX
        val y = location[1] - viewport.screenY
        var usedAgsl = false
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && !appearance.reducedEffects && !shaderFailed) {
            try {
                if (runtime == null) runtime = Agsl.create(context)
                align(sharp!!, f.sharp); align(frosted!!, f.frosted)
                Agsl.configure(runtime!!, sharp!!, frosted!!, width.toFloat(), height.toFloat(), x, y, radius,
                    appearance.refraction, appearance.frost, panelTint)
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
            paint.shader = null; paint.color = Color.argb((panelTint * 255).toInt(), 9, 17, 28)
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }
        border.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0x9fffffff.toInt(), 0x20ffffff, 0x68ffffff), null, Shader.TileMode.CLAMP)
        border.strokeWidth = density * .8f
        bounds.inset(border.strokeWidth * .5f, border.strokeWidth * .5f)
        canvas.drawRoundRect(bounds, max(0f, radius - .4f * density), max(0f, radius - .4f * density), border)
    }
}

@RequiresApi(33)
private object Agsl {
    fun create(context: Context): RuntimeShader = RuntimeShader(context.assets.open("glass.agsl").bufferedReader().use { it.readText() })
    fun configure(shader: Shader, sharp: Shader, frosted: Shader, width: Float, height: Float,
                  x: Float, y: Float, radius: Float, strength: Float, frost: Float, tint: Float) {
        val s = shader as RuntimeShader
        s.setInputShader("wallpaper", sharp); s.setInputShader("frosted", frosted)
        s.setFloatUniform("resolution", width, height); s.setFloatUniform("origin", x, y)
        s.setFloatUniform("radius", radius); s.setFloatUniform("strength", strength)
        s.setFloatUniform("frostAmount", frost); s.setFloatUniform("tint", tint)
    }
}
