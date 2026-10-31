package kz.gazeclick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/** Draws the gaze circle over every app and performs taps. Coordinates are screen pixels. */
class GazeAccessibilityService : AccessibilityService() {
    private lateinit var dot: DotView

    override fun onServiceConnected() {
        dot = DotView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        getSystemService(WindowManager::class.java).addView(dot, lp)
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        getSystemService(WindowManager::class.java).removeView(dot)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun show(x: Float, y: Float) = dot.post {
        val l = IntArray(2).also { dot.getLocationOnScreen(it) }
        clamp(x, y, l)?.let { dot.set(it[0] - l[0], it[1] - l[1]) }
    }

    fun hide() = dot.post { dot.set(Float.NaN, 0f) }

    fun tap(x: Float, y: Float) = dot.post {
        val l = IntArray(2).also { dot.getLocationOnScreen(it) }
        val p = clamp(x, y, l) ?: return@post
        val path = Path().apply { moveTo(p[0], p[1]) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(),
            null, null
        )
        dot.flash()
    }

    // Keep the point on screen: offscreen gestures throw.
    private fun clamp(x: Float, y: Float, l: IntArray): FloatArray? {
        if (dot.width == 0 || dot.height == 0) return null
        return floatArrayOf(
            x.coerceIn(l[0].toFloat(), (l[0] + dot.width - 1).toFloat()),
            y.coerceIn(l[1].toFloat(), (l[1] + dot.height - 1).toFloat()),
        )
    }

    companion object {
        @Volatile var instance: GazeAccessibilityService? = null
    }
}

class DotView(ctx: Context) : View(ctx) {
    private var cx = Float.NaN
    private var cy = 0f
    private var flashUntil = 0L
    private val r = 28 * resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * resources.displayMetrics.density
        color = 0xDD2196F3.toInt()
    }

    fun set(x: Float, y: Float) { cx = x; cy = y; invalidate() }

    fun flash() {
        flashUntil = SystemClock.uptimeMillis() + 300
        invalidate()
        postInvalidateDelayed(310)
    }

    override fun onDraw(c: Canvas) {
        if (cx.isNaN()) return
        fill.color = if (SystemClock.uptimeMillis() < flashUntil) 0xAA4CAF50.toInt() else 0x442196F3
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, ring)
    }
}
