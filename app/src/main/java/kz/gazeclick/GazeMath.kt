package kz.gazeclick

import kotlin.math.abs

/**
 * Where the irises sit inside the eyes, averaged over both eyes.
 * [0] = along the eye (corner to corner, 0..1), [1] = across it, both in eye-width units.
 * Indices are MediaPipe face mesh: corners 33/133 and 362/263, iris centres 468 and 473.
 */
fun gazeFeature(x: (Int) -> Float, y: (Int) -> Float): FloatArray {
    fun eye(c1: Int, c2: Int, iris: Int): FloatArray {
        val ex = x(c2) - x(c1)
        val ey = y(c2) - y(c1)
        val len2 = ex * ex + ey * ey
        val ix = x(iris) - x(c1)
        val iy = y(iris) - y(c1)
        return floatArrayOf((ix * ex + iy * ey) / len2, (iy * ex - ix * ey) / len2)
    }
    val r = eye(33, 133, 468)
    val l = eye(362, 263, 473)
    return floatArrayOf((r[0] + l[0]) / 2, (r[1] + l[1]) / 2)
}

/**
 * Affine map gaze feature -> screen point, fitted from calibration targets.
 * Full affine (not per-axis) so camera rotation/mirroring is absorbed by the fit.
 */
class Calibration(private val cx: FloatArray, private val cy: FloatArray) {
    fun map(f: FloatArray) = floatArrayOf(
        cx[0] + cx[1] * f[0] + cx[2] * f[1],
        cy[0] + cy[1] * f[0] + cy[2] * f[1],
    )

    fun save(): String = (cx + cy).joinToString(",")

    companion object {
        fun load(s: String?): Calibration? {
            val v = s?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return null
            if (v.size != 6) return null
            return Calibration(v.subList(0, 3).toFloatArray(), v.subList(3, 6).toFloatArray())
        }

        fun fit(features: List<FloatArray>, targets: List<FloatArray>): Calibration? {
            val cx = leastSquares(features, targets.map { it[0] }) ?: return null
            val cy = leastSquares(features, targets.map { it[1] }) ?: return null
            return Calibration(cx, cy)
        }

        // Normal equations for rows [1, fx, fy], solved by Gauss-Jordan with pivoting.
        private fun leastSquares(f: List<FloatArray>, t: List<Float>): FloatArray? {
            val m = Array(3) { DoubleArray(4) }
            for (i in f.indices) {
                val r = doubleArrayOf(1.0, f[i][0].toDouble(), f[i][1].toDouble())
                for (a in 0..2) {
                    for (b in 0..2) m[a][b] += r[a] * r[b]
                    m[a][3] += r[a] * t[i]
                }
            }
            for (c in 0..2) {
                val p = (c..2).maxBy { abs(m[it][c]) }
                if (abs(m[p][c]) < 1e-12) return null
                val tmp = m[c]; m[c] = m[p]; m[p] = tmp
                for (r in 0..2) if (r != c) {
                    val k = m[r][c] / m[c][c]
                    for (j in c..3) m[r][j] -= k * m[c][j]
                }
            }
            return FloatArray(3) { (m[it][3] / m[it][it]).toFloat() }
        }
    }
}

/** Two short blinks in a row = click. Score: 0 = eyes open, 1 = closed. */
class DoubleBlink(
    private val closeAt: Float = 0.5f,
    private val openAt: Float = 0.3f,
    private val maxBlinkMs: Long = 500,
    private val maxGapMs: Long = 700,
) {
    private var closed = false
    private var closedAt = 0L
    private var lastBlinkEnd = -1L

    fun update(score: Float, t: Long): Boolean {
        if (!closed && score > closeAt) {
            closed = true
            closedAt = t
        } else if (closed && score < openAt) {
            closed = false
            when {
                t - closedAt > maxBlinkMs -> lastBlinkEnd = -1 // held eyes shut: not a blink
                lastBlinkEnd >= 0 && closedAt - lastBlinkEnd <= maxGapMs -> {
                    lastBlinkEnd = -1
                    return true
                }
                else -> lastBlinkEnd = t
            }
        }
        return false
    }
}
