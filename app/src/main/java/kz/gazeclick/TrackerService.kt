package kz.gazeclick

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.concurrent.Executors

/** Foreground service: front camera -> face mesh -> cursor position and double-blink clicks. */
class TrackerService : LifecycleService() {
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var landmarker: FaceLandmarker
    private val blink = DoubleBlink()
    private var sx = Float.NaN // smoothed cursor, screen px
    private var sy = 0f

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        if (calibration == null) loadCalibration(this)
        landmarker = FaceLandmarker.createFromOptions(
            this,
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                .setRunningMode(RunningMode.VIDEO)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .build()
        )
        running = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(exec, ::onFrame)
            future.get().bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    // Not sticky: a silent restart from the background is not allowed to open the camera.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        exec.execute { landmarker.close() } // after any frame already in flight
        exec.shutdown()
        GazeAccessibilityService.instance?.hide()
        super.onDestroy()
    }

    private fun onFrame(img: ImageProxy) {
        if (!running) { img.close(); return }
        val t = SystemClock.uptimeMillis()
        val bmp = img.toBitmap()
        val rotation = img.imageInfo.rotationDegrees
        img.close()

        val res = landmarker.detectForVideo(
            BitmapImageBuilder(bmp).build(),
            ImageProcessingOptions.builder().setRotationDegrees(rotation).build(),
            t
        )
        val overlay = GazeAccessibilityService.instance
        val lm = res.faceLandmarks().firstOrNull()
        if (lm == null || lm.size < 478) { overlay?.hide(); return }

        val f = gazeFeature({ lm[it].x() }, { lm[it].y() })
        val shapes = res.faceBlendshapes().orElse(null)?.firstOrNull().orEmpty()
        val closed = shapes.filter { it.categoryName() == "eyeBlinkLeft" || it.categoryName() == "eyeBlinkRight" }
            .map { it.score() }.average().toFloat().takeIf { !it.isNaN() } ?: 0f

        sink?.let { it(f, closed); return } // calibrating: no cursor, no clicks
        val c = calibration ?: return

        // Only move while eyes are clearly open: closing lids drag the iris estimate down,
        // so the click lands where the user was looking before the blink.
        if (closed < 0.2f) {
            val p = c.map(f)
            if (sx.isNaN()) { sx = p[0]; sy = p[1] } else { sx += 0.3f * (p[0] - sx); sy += 0.3f * (p[1] - sy) }
            overlay?.show(sx, sy)
        }
        if (blink.update(closed, t) && !sx.isNaN()) overlay?.tap(sx, sy)
    }

    private fun startInForeground() {
        val ch = "gaze"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "Слежение за взглядом", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, ch)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Управление взглядом включено")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        ServiceCompat.startForeground(this, 1, n, type)
    }

    companion object {
        @Volatile var running = false
        @Volatile var calibration: Calibration? = null
        /** Set by CalibrationActivity to receive raw (feature, eyesClosedScore) per frame. */
        @Volatile var sink: ((FloatArray, Float) -> Unit)? = null

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, TrackerService::class.java))

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("gaze", Context.MODE_PRIVATE)
        fun loadCalibration(ctx: Context) { calibration = Calibration.load(prefs(ctx).getString("calib", null)) }
        fun saveCalibration(ctx: Context, c: Calibration) {
            calibration = c
            prefs(ctx).edit().putString("calib", c.save()).apply()
        }
    }
}
