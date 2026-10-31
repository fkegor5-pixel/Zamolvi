package kz.gazeclick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeMathTest {
    @Test fun calibrationRecoversAffineMap() {
        val feats = mutableListOf<FloatArray>()
        val targets = mutableListOf<FloatArray>()
        for (a in listOf(0.4f, 0.5f, 0.6f)) for (b in listOf(-0.05f, 0f, 0.05f)) {
            feats += floatArrayOf(a, b)
            // rotated/mirrored on purpose: the fit must handle swapped axes
            targets += floatArrayOf(100 + 2000 * b, 50 - 3000 * a + 10 * b)
        }
        val p = Calibration.load(Calibration.fit(feats, targets)!!.save())!!.map(floatArrayOf(0.45f, 0.02f))
        assertEquals(140f, p[0], 0.5f)
        assertEquals(-1299.8f, p[1], 0.5f)
    }

    @Test fun degenerateCalibrationIsRejected() {
        val same = List(9) { floatArrayOf(0.5f, 0f) }
        assertEquals(null, Calibration.fit(same, List(9) { floatArrayOf(it * 10f, it * 20f) }))
    }

    @Test fun gazeFeatureCentredIris() {
        val xs = mapOf(33 to 0f, 133 to 1f, 468 to 0.5f, 362 to 2f, 263 to 3f, 473 to 2.5f)
        val ys = mapOf(33 to 0f, 133 to 0f, 468 to 0.1f, 362 to 0f, 263 to 0f, 473 to 0.1f)
        val f = gazeFeature({ xs[it]!! }, { ys[it]!! })
        assertEquals(0.5f, f[0], 1e-6f)
        assertEquals(0.1f, f[1], 1e-6f)
    }

    private fun DoubleBlink.feed(vararg ev: Pair<Long, Float>) = ev.map { update(it.second, it.first) }

    @Test fun twoQuickBlinksClick() {
        val r = DoubleBlink().feed(0L to 0f, 100L to 0.9f, 250L to 0.1f, 500L to 0.9f, 650L to 0.1f)
        assertEquals(listOf(false, false, false, false, true), r)
    }

    @Test fun singleOrSlowBlinksDoNotClick() {
        assertFalse(DoubleBlink().feed(0L to 0.9f, 150L to 0.1f).last())
        // too far apart
        assertFalse(DoubleBlink().feed(0L to 0.9f, 150L to 0.1f, 1200L to 0.9f, 1300L to 0.1f).any { it })
        // eyes held shut, then one blink
        assertFalse(DoubleBlink().feed(0L to 0.9f, 2000L to 0.1f, 2200L to 0.9f, 2300L to 0.1f).any { it })
        // a third blink right after a click starts a new pair rather than clicking again
        val d = DoubleBlink()
        assertTrue(d.feed(0L to 0.9f, 100L to 0.1f, 300L to 0.9f, 400L to 0.1f).last())
        assertFalse(d.feed(600L to 0.9f, 700L to 0.1f).last())
    }
}
