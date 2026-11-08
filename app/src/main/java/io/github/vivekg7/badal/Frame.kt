package io.github.vivekg7.badal

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How one picture sits on one screen, as the user framed it
 * (docs/plans/002_framing.md). [zoom] is relative to the size that just covers
 * the screen: 1 fills it, less shows the picture whole over a blurred copy of
 * itself, and 0 means as small as fits. The point ([x], [y]) of the picture,
 * in fractions of its width and height, sits as near the screen's centre as
 * the picture's edges allow. Fractions, not pixels, so one frame fits a
 * thumbnail and the full picture alike.
 */
@Serializable
data class Frame(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val zoom: Float = 1f,
)

/** Where a picture lands on a screen: its top-left corner and the scale it is drawn at, in screen pixels. */
data class Placement(
    val left: Float,
    val top: Float,
    val scale: Float,
)

/** The pure geometry of [Frame], shared by the wallpaper renderer and the app's previews, so both show the same crop. */
object Framing {
    const val MAX_ZOOM = 4f

    /** The frame a screen's Fit setting stands for, for a picture nobody framed by hand. */
    fun of(fit: Fit) = if (fit == Fit.FILL) Frame() else Frame(zoom = 0f)

    /** Places an [iw]×[ih] picture on a [w]×[h] screen. */
    fun place(
        iw: Int,
        ih: Int,
        w: Float,
        h: Float,
        f: Frame,
    ): Placement {
        val cover = max(w / iw, h / ih)
        val s = cover * f.zoom.coerceIn(least(iw, ih, w, h), MAX_ZOOM)

        // Larger than the screen along this axis: no gap at either edge. Smaller:
        // wholly on screen. Within that, as close to centring the focus as it gets.
        fun axis(
            screen: Float,
            size: Float,
            focus: Float,
        ) = (screen / 2 - focus * size).coerceIn(min(0f, screen - size), max(0f, screen - size))
        return Placement(axis(w, iw * s, f.x), axis(h, ih * s, f.y), s)
    }

    /** Whether the placed picture leaves part of the screen to the blurred background. */
    fun gaps(
        p: Placement,
        iw: Int,
        ih: Int,
        w: Float,
        h: Float,
    ) = p.left > 0.5f || p.top > 0.5f || p.left + iw * p.scale < w - 0.5f || p.top + ih * p.scale < h - 0.5f

    /**
     * [f] after a pinch by [zoom] around ([cx], [cy]) and a drag by ([dx], [dy]), all
     * in pixels of a [w]×[h] screen. The point under the fingers stays under them.
     */
    fun moved(
        iw: Int,
        ih: Int,
        w: Float,
        h: Float,
        f: Frame,
        dx: Float,
        dy: Float,
        zoom: Float = 1f,
        cx: Float = w / 2,
        cy: Float = h / 2,
    ): Frame {
        val p = place(iw, ih, w, h, f)
        val cover = max(w / iw, h / ih)
        val s = cover * (p.scale / cover * zoom).coerceIn(least(iw, ih, w, h), MAX_ZOOM)
        val k = s / p.scale
        val left = cx - (cx - p.left) * k + dx
        val top = cy - (cy - p.top) * k + dy
        return settled(iw, ih, w, h, Frame((w / 2 - left) / (iw * s), (h / 2 - top) / (ih * s), s / cover))
    }

    /**
     * [f] as it actually shows, edges and zoom limits applied. Stored this way, a
     * drag past an edge isn't remembered, so the next drag back moves at once.
     */
    fun settled(
        iw: Int,
        ih: Int,
        w: Float,
        h: Float,
        f: Frame,
    ): Frame {
        val p = place(iw, ih, w, h, f)
        val cover = max(w / iw, h / ih)
        return Frame(round((w / 2 - p.left) / (iw * p.scale)), round((h / 2 - p.top) / (ih * p.scale)), round(p.scale / cover))
    }

    /** The zoom at which the whole picture just fits. */
    private fun least(
        iw: Int,
        ih: Int,
        w: Float,
        h: Float,
    ) = min(w / iw, h / ih) / max(w / iw, h / ih)

    // Four places keep state.json short; a ten-thousandth of a 50 MP photo is under a pixel.
    private fun round(v: Float) = (v * 10_000).roundToInt() / 10_000f
}

/**
 * The lock screen's shade behind the clock: full strength over the top of the
 * screen, where One UI puts the clock and date, easing out to nothing by a bit
 * past a third of the way down (plan 003). Stops are (fraction of the
 * height, fraction of the strength), shared by the renderer and the previews.
 */
object Shade {
    val stops =
        listOf(
            0f to 1f,
            0.3f to 1f,
            0.36f to 0.85f,
            0.42f to 0.5f,
            0.48f to 0.15f,
            0.55f to 0f,
        )
}
