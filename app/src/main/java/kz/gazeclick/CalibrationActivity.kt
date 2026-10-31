package kz.gazeclick

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections

/** Shows 9 dots one by one; the user looks at each, then an affine map is fitted. */
class CalibrationActivity : ComponentActivity() {
    private lateinit var view: TargetView
    @Volatile private var samples: MutableList<FloatArray>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TargetView(this)
        setContentView(view)
        TrackerService.start(this) // camera must run to calibrate
        TrackerService.sink = { f, closed -> if (closed < 0.3f) samples?.add(f) }
        lifecycleScope.launch {
            try { calibrate() } finally { TrackerService.sink = null }
        }
    }

    private suspend fun calibrate() {
        view.text = "Смотрите на красную точку. Голову держите неподвижно."
        delay(2500) // camera warm-up + read the hint
        view.text = null
        val loc = IntArray(2).also { view.getLocationOnScreen(it) }
        val feats = mutableListOf<FloatArray>()
        val targets = mutableListOf<FloatArray>()
        for (fy in listOf(0.1f, 0.5f, 0.9f)) for (fx in listOf(0.1f, 0.5f, 0.9f)) {
            val x = view.width * fx
            val y = view.height * fy
            view.target(x, y)
            delay(1000) // eyes travel to the dot
            val s = Collections.synchronizedList(mutableListOf<FloatArray>())
            samples = s
            delay(1200)
            samples = null
            val got = synchronized(s) { s.toList() }
            if (got.size < 5) return fail("Лицо не видно камере. Нужен хороший свет и телефон перед лицом.")
            feats += floatArrayOf(got.map { it[0] }.average().toFloat(), got.map { it[1] }.average().toFloat())
            targets += floatArrayOf(loc[0] + x, loc[1] + y)
        }
        val c = Calibration.fit(feats, targets) ?: return fail("Калибровка не удалась, попробуйте ещё раз.")
        TrackerService.saveCalibration(this, c)
        Toast.makeText(this, "Готово! Двойное моргание = нажатие.", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun fail(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}

class TargetView(ctx: Context) : View(ctx) {
    private var tx = Float.NaN
    private var ty = 0f
    var text: String? = null
        set(v) { field = v; invalidate() }
    private val d = resources.displayMetrics.density
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18 * d; textAlign = Paint.Align.CENTER }

    fun target(x: Float, y: Float) { tx = x; ty = y; invalidate() }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        text?.let { c.drawText(it, width / 2f, height / 2f, white) }
        if (!tx.isNaN()) {
            c.drawCircle(tx, ty, 16 * d, red)
            c.drawCircle(tx, ty, 4 * d, white)
        }
    }
}
