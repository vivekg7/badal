package io.github.vivekg7.badal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingTest {
    // A 1080×2340 phone and a landscape photo twice as wide as tall.
    private val w = 1080f
    private val h = 2340f
    private val iw = 4000
    private val ih = 2000

    private fun near(
        expected: Float,
        actual: Float,
    ) = assertEquals(expected, actual, 0.5f)

    @Test
    fun fillCoversTheScreenCentred() {
        val p = Framing.place(iw, ih, w, h, Framing.of(Fit.FILL))
        near(h / ih, p.scale)
        near(0f, p.top)
        near((w - iw * p.scale) / 2, p.left)
        assertFalse(Framing.gaps(p, iw, ih, w, h))
    }

    @Test
    fun wholeFitsAllOfItWithBandsAboveAndBelow() {
        val p = Framing.place(iw, ih, w, h, Framing.of(Fit.WHOLE))
        near(w / iw, p.scale)
        near(0f, p.left)
        near((h - ih * p.scale) / 2, p.top)
        assertTrue(Framing.gaps(p, iw, ih, w, h))
    }

    @Test
    fun aFocusNearTheEdgeStopsAtTheEdge() {
        // Asking for the far left of the picture at the centre would leave a gap; the edge holds.
        val p = Framing.place(iw, ih, w, h, Frame(x = 0f))
        near(0f, p.left)
        val q = Framing.place(iw, ih, w, h, Frame(x = 1f))
        near(w, q.left + iw * q.scale)
    }

    @Test
    fun zoomStaysWithinItsLimits() {
        val cover = h / ih
        near(cover * Framing.MAX_ZOOM, Framing.place(iw, ih, w, h, Frame(zoom = 99f)).scale)
        near(w / iw, Framing.place(iw, ih, w, h, Frame(zoom = 0f)).scale)
    }

    @Test
    fun aDragPastTheEdgeIsNotRemembered() {
        // Drag far right: the picture's left edge stops at the screen's.
        val dragged = Framing.moved(iw, ih, w, h, Frame(), dx = 10_000f, dy = 0f)
        near(0f, Framing.place(iw, ih, w, h, dragged).left)
        // So a small drag back moves it at once, by exactly that much.
        val back = Framing.moved(iw, ih, w, h, dragged, dx = -100f, dy = 0f)
        near(-100f, Framing.place(iw, ih, w, h, back).left)
    }

    @Test
    fun pinchKeepsThePointUnderTheFingers() {
        val start = Frame()
        val p = Framing.place(iw, ih, w, h, start)
        val cx = 300f
        val cy = 900f
        val u = (cx - p.left) / p.scale
        val v = (cy - p.top) / p.scale
        val q = Framing.place(iw, ih, w, h, Framing.moved(iw, ih, w, h, start, 0f, 0f, zoom = 2f, cx = cx, cy = cy))
        near(2 * p.scale, q.scale)
        near(cx, q.left + u * q.scale)
        near(cy, q.top + v * q.scale)
    }

    @Test
    fun aFrameFitsAnySizeOfTheSamePicture() {
        // The thumbnail and the full picture land on the same part of the screen.
        val f = Framing.moved(iw, ih, w, h, Frame(), dx = 400f, dy = 0f, zoom = 1.5f)
        val big = Framing.place(iw, ih, w, h, f)
        val small = Framing.place(iw / 10, ih / 10, w, h, f)
        near(big.left, small.left)
        near(big.top, small.top)
        near(big.scale * 10, small.scale)
    }

    @Test
    fun aRotationUsesItsFitUntilAPictureIsFramed() {
        val r = Rotation(fit = Fit.WHOLE)
        assertEquals(Framing.of(Fit.WHOLE), r.frame(7))
        val framed = r.framed(7, Frame(x = 0.2f))
        assertEquals(Frame(x = 0.2f), framed.frame(7))
        assertEquals(r, framed.framed(7, null))
    }
}
