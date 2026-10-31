package kz.gazeclick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackerService.loadCalibration(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun btn(label: String, onClick: () -> Unit) =
            col.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })

        status = TextView(this).apply { textSize = 16f }
        col.addView(status)
        btn("1. Разрешить камеру") { camPerm.launch(Manifest.permission.CAMERA) }
        btn("2. Включить в «Спец. возможностях»") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("3. Калибровка") { if (needCamera()) startActivity(Intent(this, CalibrationActivity::class.java)) }
        btn("Старт") {
            if (!needCamera()) return@btn
            if (TrackerService.calibration == null) startActivity(Intent(this, CalibrationActivity::class.java))
            else TrackerService.start(this)
            status.postDelayed(::refresh, 500)
        }
        btn("Стоп") {
            stopService(Intent(this, TrackerService::class.java))
            status.postDelayed(::refresh, 300)
        }
        col.addView(TextView(this).apply {
            setPadding(0, pad, 0, 0)
            text = """
                Как пользоваться:
                • Телефон перед лицом, вертикально, лицо хорошо освещено.
                • Двигайте глазами, голову держите ровно — синий кружок покажет, куда вы смотрите.
                • Моргните два раза быстро — будет нажатие там, где кружок (он мигнёт зелёным).
                • Если кружок «уплывает» — повторите калибровку.

                Если в «Спец. возможностях» переключатель серый («Ограниченная настройка»):
                Настройки → Приложения → GazeClick → ⋮ (вверху справа) → «Разрешить ограниченные настройки».
            """.trimIndent()
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun needCamera(): Boolean {
        if (!hasCamera()) Toast.makeText(this, "Сначала разрешите камеру", Toast.LENGTH_SHORT).show()
        return hasCamera()
    }

    private fun refresh() {
        fun ok(b: Boolean) = if (b) "✅" else "❌"
        status.text = """
            Камера: ${ok(hasCamera())}
            Спец. возможности: ${ok(GazeAccessibilityService.instance != null)}
            Калибровка: ${ok(TrackerService.calibration != null)}
            Слежение: ${if (TrackerService.running) "включено" else "выключено"}
        """.trimIndent()
    }
}
