package io.github.vivekg7.badal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Draws a [Design] (docs/plans/005_designs.md). Every size is a fraction of
 * the width, so the same design drawn small for a thumbnail is the wallpaper
 * in miniature, and the same seed always gives the same picture.
 */
object Painter {
    fun render(
        d: Design,
        w: Int,
        h: Int,
    ): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        background(c, d, w.toFloat(), h.toFloat())
        if (d.motif != Motif.NONE) Pen(c, d, w.toFloat(), h.toFloat(), out).draw()
        if (d.vignette > 0) {
            val r = hypot(w / 2f, h / 2f)
            val dark = Color.argb(d.vignette * 255 / 100, 0, 0, 0)
            val p = Paint().apply { shader = RadialGradient(w / 2f, h / 2f, r, intArrayOf(0, 0, dark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP) }
            c.drawPaint(p)
        }
        if (d.grain > 0) grain(out, d.grain, d.seed)
        return out
    }

    private fun background(
        c: Canvas,
        d: Design,
        w: Float,
        h: Float,
    ) {
        val cols = d.colors.ifEmpty { listOf(INK_BLACK) }.toIntArray()
        val ox = d.x * w
        val oy = d.y * h
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        if (cols.size == 1 || d.fill == Fill.SOLID) return c.drawColor(cols[0])
        when (d.fill) {
            Fill.LINEAR -> {
                val a = Math.toRadians(d.angle.toDouble())
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                // Long enough that the first and last colours just reach opposite corners.
                val half = abs(w / 2 * dx) + abs(h / 2 * dy)
                p.shader = LinearGradient(w / 2 - dx * half, h / 2 - dy * half, w / 2 + dx * half, h / 2 + dy * half, cols, null, Shader.TileMode.CLAMP)
            }

            Fill.RADIAL -> {
                p.shader = RadialGradient(ox, oy, max(1f, farthest(ox, oy, w, h)), cols, null, Shader.TileMode.CLAMP)
            }

            Fill.SWEEP -> {
                p.shader =
                    SweepGradient(ox, oy, cols + cols[0], null).apply {
                        setLocalMatrix(Matrix().apply { setRotate(d.angle.toFloat(), ox, oy) })
                    }
            }

            else -> {
                return mesh(c, cols, d.seed, w, h)
            }
        }
        c.drawPaint(p)
    }

    /**
     * Soft blobs: a few points, each a colour, blended by distance. Worked out on
     * a grid a sixth of the size and smoothed on the way up; the field is smooth
     * enough that nothing shows of the grid.
     */
    private fun mesh(
        c: Canvas,
        cols: IntArray,
        seed: Int,
        w: Float,
        h: Float,
    ) {
        val rnd = Random(seed)
        val n = max(4, cols.size + 2)
        val aspect = h / w
        val px = FloatArray(n) { rnd.nextFloat() * 1.1f - 0.05f }
        val py = FloatArray(n) { (rnd.nextFloat() * 1.1f - 0.05f) * aspect }
        val pc = IntArray(n) { cols[(it + seed).mod(cols.size)] }
        val gw = max(8, (w / 6).toInt())
        val gh = max(8, (h / 6).toInt())
        val out = IntArray(gw * gh)
        val sigma2 = 2 * 0.4f * 0.4f
        for (j in 0 until gh) {
            for (i in 0 until gw) {
                val x = i / (gw - 1f)
                val y = j / (gh - 1f) * aspect
                var r = 0f
                var g = 0f
                var b = 0f
                var sum = 1e-6f
                for (q in 0 until n) {
                    val dx = x - px[q]
                    val dy = y - py[q]
                    val wt = exp(-(dx * dx + dy * dy) / sigma2)
                    r += wt * Color.red(pc[q])
                    g += wt * Color.green(pc[q])
                    b += wt * Color.blue(pc[q])
                    sum += wt
                }
                out[j * gw + i] = Color.rgb((r / sum).toInt(), (g / sum).toInt(), (b / sum).toInt())
            }
        }
        val small = Bitmap.createBitmap(out, gw, gh, Bitmap.Config.ARGB_8888)
        c.drawBitmap(small, null, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        small.recycle()
    }

    /** Monochrome noise, as much lighter as darker, so the picture's brightness doesn't move. */
    private fun grain(
        b: Bitmap,
        amount: Int,
        seed: Int,
    ) {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val rnd = Random(seed * 31 + 7)
        val a = amount * 0.8f
        for (i in px.indices) {
            val n = ((rnd.nextFloat() - 0.5f) * a).toInt()
            val c = px[i]
            val r = ((c shr 16 and 0xFF) + n).coerceIn(0, 255)
            val g = ((c shr 8 and 0xFF) + n).coerceIn(0, 255)
            val bl = ((c and 0xFF) + n).coerceIn(0, 255)
            px[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
    }

    fun farthest(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
    ) = maxOf(hypot(x, y), hypot(w - x, y), hypot(x, h - y), hypot(w - x, h - y))
}

/** One pattern on one canvas. */
private class Pen(
    val c: Canvas,
    val d: Design,
    val w: Float,
    val h: Float,
    val bg: Bitmap,
) {
    /** A pixel of a 1080-wide screen. */
    val u = w / 1080f

    /** The pattern's spacing: 2% of the width at Size 0, 24% at 100. */
    val s = w * 0.02f * 12f.pow(d.scale / 100f)

    /** Weight, 0 to 1. */
    val k = d.weight / 100f
    val ox = d.x * w
    val oy = d.y * h
    val rnd = Random(d.seed)
    val inks = d.inks.ifEmpty { listOf(INK_WHITE) }

    /** From the point to the farthest corner, and a step more: drawing this far out covers the screen at any rotation. */
    val reach = Painter.farthest(ox, oy, w, h) + s * 2
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    /** Ink number [i], going round them. */
    fun ink(i: Int) = inks[i.mod(inks.size)]

    /** An ink at random. */
    fun anyInk() = if (inks.size == 1) inks[0] else inks[rnd.nextInt(inks.size)]

    fun Paint.ink(
        color: Int,
        alpha: Float = 1f,
    ) = apply {
        this.color = color
        this.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
    }

    fun draw() {
        // The pattern is drawn whole and then laid over at its opacity, so where
        // its own marks overlap they don't add up.
        val layered = !d.motif.samples
        if (layered) c.saveLayerAlpha(null, d.opacity.coerceIn(0, 100) * 255 / 100)
        c.save()
        if (d.motif.turns) c.rotate(d.turn.toFloat(), ox, oy)
        when (d.motif) {
            Motif.NONE -> Unit
            Motif.DOTS -> lattice(s, s) { x, y, _, _ -> dot(x, y, s * (0.05f + 0.4f * k)) }
            Motif.HEX_DOTS -> lattice(s, s * 0.866f, stagger = true) { x, y, _, _ -> dot(x, y, s * (0.05f + 0.4f * k)) }
            Motif.HALFTONE -> halftone()
            Motif.BURST -> burst()
            Motif.DOT_RINGS -> dotRings()
            Motif.SUNFLOWER -> sunflower()
            Motif.DOT_RAYS -> rays(twist = false)
            Motif.VORTEX -> rays(twist = true)
            Motif.DOT_WAVE -> dotWave()
            Motif.SCATTER -> scatter()
            Motif.STARS -> stars()
            Motif.STRIPES -> stripes()
            Motif.GRID -> grid()
            Motif.CROSSHATCH -> crosshatch()
            Motif.PLAID -> plaid()
            Motif.WAVES -> waves()
            Motif.ZIGZAG -> zigzag()
            Motif.RINGS -> rings()
            Motif.SUNBURST -> sunburst()
            Motif.SPIRAL -> spiral()
            Motif.MOIRE -> moire()
            Motif.CHECKER -> checker()
            Motif.TRIANGLES -> triangles()
            Motif.HEXAGONS -> hexagons()
            Motif.CUBES -> cubes()
            Motif.DIAMONDS -> diamonds()
            Motif.PLUSES -> pluses(0f)
            Motif.CROSSES -> pluses(45f)
            Motif.CIRCLES -> circles()
            Motif.SCALES -> scales()
            Motif.BRICKS -> bricks()
            Motif.TRUCHET -> truchet()
            Motif.MAZE -> maze()
            Motif.CONFETTI -> confetti()
            Motif.BUBBLES -> bubbles()
            Motif.SUN -> sun()
            Motif.RAINBOW -> rainbow()
            Motif.LAYERS -> layers(ridged = false)
            Motif.RIDGES -> layers(ridged = true)
            Motif.BOKEH -> bokeh()
            Motif.LOW_POLY -> lowPoly()
            Motif.VORONOI -> voronoi()
            Motif.CONTOURS -> contours()
            Motif.FLOW -> flow(swirl = false)
            Motif.BLOBS -> blobs()
            Motif.GRAPH -> graph()
            Motif.ISO_GRID -> isoGrid()
            Motif.SQUARES -> squares()
            Motif.WEAVE -> weave()
            Motif.BAUHAUS -> bauhaus()
            Motif.ARCH -> arches()
            Motif.PEBBLES -> pebbles()
            Motif.SWIRL -> flow(swirl = true)
            Motif.MARBLE -> marble()
            Motif.RIDGELINES -> ridgelines()
            Motif.AURORA -> aurora()
        }
        c.restore()
        if (layered) c.restore()
    }

    // --- helpers

    /** Every point of a grid [dx] by [dy] through the design's point, out to [reach]; [stagger] shifts odd rows by half. */
    inline fun lattice(
        dx: Float,
        dy: Float,
        stagger: Boolean = false,
        f: (x: Float, y: Float, i: Int, j: Int) -> Unit,
    ) {
        val nx = (reach / dx).toInt() + 1
        val ny = (reach / dy).toInt() + 1
        for (j in -ny..ny) {
            val shift = if (stagger && j % 2 != 0) dx / 2 else 0f
            for (i in -nx..nx) f(ox + i * dx + shift, oy + j * dy, i, j)
        }
    }

    /** Random points about [cell] apart that cover the screen, never two in a clump. */
    inline fun jittered(
        cell: Float,
        f: (x: Float, y: Float) -> Unit,
    ) {
        var y = -cell
        while (y < h + cell) {
            var x = -cell
            while (x < w + cell) {
                f(x + rnd.nextFloat() * cell, y + rnd.nextFloat() * cell)
                x += cell
            }
            y += cell
        }
    }

    fun dot(
        x: Float,
        y: Float,
        r: Float,
        color: Int = anyInk(),
    ) {
        if (r > 0.25f * u) c.drawCircle(x, y, r, fill.ink(color))
    }

    fun poly(vararg p: Float) =
        Path().apply {
            moveTo(p[0], p[1])
            for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
            close()
        }

    fun stroke(width: Float) = line.apply { strokeWidth = max(u * 0.75f, width) }

    // --- dots

    /** Dots that shrink away down the screen, from full at the top to none at the bottom. */
    fun halftone() =
        lattice(s, s * 0.866f, stagger = true) { x, y, _, _ ->
            val t = ((y - oy) / h + 0.5f).coerceIn(0f, 1f)
            dot(x, y, s * (0.08f + 0.5f * k) * (1 - t), ink(0))
        }

    /** Dots largest at the point, shrinking outward. */
    fun burst() =
        lattice(s, s * 0.866f, stagger = true) { x, y, _, _ ->
            val t = (hypot(x - ox, y - oy) / (max(w, h) * 0.7f)).coerceIn(0f, 1f)
            dot(x, y, s * (0.08f + 0.5f * k) * (1 - t), ink(0))
        }

    fun dotRings() {
        val r = s * (0.05f + 0.35f * k)
        dot(ox, oy, r, ink(0))
        var n = 1
        while (n * s < reach) {
            val rho = n * s
            val m = max(6, (2 * PI * rho / s).roundToInt())
            for (q in 0 until m) {
                val a = 2 * PI * q / m
                dot(ox + rho * cos(a).toFloat(), oy + rho * sin(a).toFloat(), r, ink(n))
            }
            n++
        }
    }

    /** The golden-angle spiral of a sunflower's seeds, growing outward. */
    fun sunflower() {
        val step = s * 0.5f
        var n = 0
        while (true) {
            val rho = step * sqrt(n.toFloat())
            if (rho > reach) break
            val a = n * 2.399963
            val grow = 0.35f + 0.65f * min(1f, rho / (w * 0.7f))
            dot(ox + rho * cos(a).toFloat(), oy + rho * sin(a).toFloat(), s * (0.06f + 0.3f * k) * grow)
            n++
        }
    }

    /** Dots running out from the point along rays, growing as they go; [twist] turns each ring half a step, into spiral arms. */
    fun rays(twist: Boolean) {
        val m = (2 * PI * w * 0.35f / s).roundToInt().coerceIn(8, 160)
        val q = sin(PI / m).toFloat()
        var rho = s * 0.8f
        var ring = 0
        while (rho < reach) {
            val phase = if (twist) ring * PI / m else 0.0
            for (i in 0 until m) {
                val a = 2 * PI * i / m + phase
                dot(ox + rho * cos(a).toFloat(), oy + rho * sin(a).toFloat(), rho * q * (0.2f + 0.75f * k), ink(ring))
            }
            rho *= 1 + 2.1f * q
            ring++
        }
    }

    fun dotWave() =
        lattice(s, s) { x, y, _, j ->
            val yy = y + s * 1.1f * sin(2 * PI * x / (s * 9) + j * 0.35).toFloat()
            val r = s * (0.05f + 0.35f * k) * (0.65f + 0.35f * sin(2 * PI * (x + y) / (s * 14)).toFloat())
            dot(x, yy, r)
        }

    fun scatter() =
        jittered(s * 1.2f) { x, y ->
            if (rnd.nextFloat() < 0.8f) dot(x, y, s * (0.05f + 0.35f * k) * (0.3f + 0.7f * rnd.nextFloat()))
        }

    fun stars() {
        val n = (w * h / (s * s) * 2).toInt()
        repeat(n) {
            val r = u * (0.7f + 2.4f * rnd.nextFloat().pow(3)) * (0.5f + k)
            fill.ink(anyInk(), 0.25f + 0.75f * rnd.nextFloat())
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r, fill)
        }
        repeat(max(3, n / 60)) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = u * (2.5f + 3f * rnd.nextFloat()) * (0.5f + k)
            val col = anyInk()
            val glow = Paint(Paint.ANTI_ALIAS_FLAG)
            glow.shader = RadialGradient(x, y, r * 7, intArrayOf(withAlpha(col, 0.45f), withAlpha(col, 0f)), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r * 7, glow)
            c.drawCircle(x, y, r, fill.ink(col))
        }
    }

    // --- lines

    fun stripes() {
        val band = s * (0.08f + 0.84f * k)
        val n = (reach / s).toInt() + 1
        for (j in -n..n) {
            val y = oy + j * s
            c.drawRect(ox - reach, y, ox + reach, y + band, fill.ink(ink(j)))
        }
    }

    fun grid() {
        val p = stroke(s * (0.015f + 0.12f * k)).ink(ink(0))
        p.strokeCap = Paint.Cap.BUTT
        val n = (reach / s).toInt() + 1
        for (i in -n..n) {
            c.drawLine(ox + i * s, oy - reach, ox + i * s, oy + reach, p.ink(ink(i)))
            c.drawLine(ox - reach, oy + i * s, ox + reach, oy + i * s, p.ink(ink(i + 1)))
        }
        p.strokeCap = Paint.Cap.ROUND
    }

    fun crosshatch() {
        val gap = s * 0.6f
        val p = stroke(gap * (0.04f + 0.3f * k))
        val n = (reach / gap).toInt() + 1
        for (turn in listOf(45f, -90f)) {
            c.rotate(turn, ox, oy)
            for (i in -n..n) c.drawLine(ox - reach, oy + i * gap, ox + reach, oy + i * gap, p.ink(ink(i)))
        }
    }

    /** Tartan: bands of several widths both ways, half see-through, so they weave where they cross. */
    fun plaid() {
        val period = s * 4
        val bands = listOf(0f to 0.28f, 0.38f to 0.07f, 0.5f to 0.025f, 0.56f to 0.025f, 0.75f to 0.12f)
        val n = (reach / period).toInt() + 1
        for (vertical in listOf(false, true)) {
            for (j in -n..n) {
                bands.forEachIndexed { b, (at, wide) ->
                    val a = (j * period) + at * period
                    val bw = wide * period * (0.4f + 1.2f * k)
                    fill.ink(ink(b), 0.5f)
                    if (vertical) {
                        c.drawRect(ox + a, oy - reach, ox + a + bw, oy + reach, fill)
                    } else {
                        c.drawRect(ox - reach, oy + a, ox + reach, oy + a + bw, fill)
                    }
                }
            }
        }
    }

    fun waves() {
        val p = stroke(s * (0.02f + 0.35f * k))
        val amp = s * 0.45f
        val len = s * 7
        val n = (reach / s).toInt() + 1
        for (j in -n..n) {
            val path = Path()
            var x = ox - reach
            path.moveTo(x, oy + j * s + amp * sin(2 * PI * x / len + j * 0.6).toFloat())
            while (x < ox + reach) {
                x += u * 6
                path.lineTo(x, oy + j * s + amp * sin(2 * PI * x / len + j * 0.6).toFloat())
            }
            c.drawPath(path, p.ink(ink(j)))
        }
    }

    fun zigzag() {
        val p = stroke(s * (0.03f + 0.3f * k))
        p.strokeJoin = Paint.Join.MITER
        p.strokeCap = Paint.Cap.BUTT
        val half = s * 0.6f
        val amp = s * 0.32f
        val n = (reach / s).toInt() + 1
        for (j in -n..n) {
            val path = Path()
            var i = -((reach / half).toInt() + 1)
            path.moveTo(ox + i * half, oy + j * s)
            while (i * half < reach) {
                i++
                path.lineTo(ox + i * half, oy + j * s + if (i % 2 == 0) 0f else -amp * 2)
            }
            c.drawPath(path, p.ink(ink(j)))
        }
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.ROUND
    }

    fun rings() {
        val p = stroke(s * (0.03f + 0.45f * k))
        var n = 1
        while (n * s < reach) {
            c.drawCircle(ox, oy, n * s, p.ink(ink(n)))
            n++
        }
    }

    fun sunburst() {
        val m = (2 * PI * w * 0.25f / s).roundToInt().coerceIn(6, 120)
        val each = 360f / m
        val oval = RectF(ox - reach, oy - reach, ox + reach, oy + reach)
        for (q in 0 until m) c.drawArc(oval, q * each, each * (0.08f + 0.6f * k), true, fill.ink(ink(q)))
    }

    /** One line wound out from the point, a turn every [s]. */
    fun spiral() {
        val b = s / (2 * PI.toFloat())
        val path = Path()
        path.moveTo(ox, oy)
        var a = 0f
        while (b * a < reach) {
            a += min(0.25f, u * 5 / (b * a + u))
            path.lineTo(ox + b * a * cos(a), oy + b * a * sin(a))
        }
        c.drawPath(path, stroke(s * (0.03f + 0.45f * k)).ink(ink(0)))
    }

    /** Two sets of rings a little apart: where they cross, they make the moiré's shimmering bands. */
    fun moire() {
        val gap = s * 0.35f
        val p = stroke(gap * (0.15f + 0.4f * k))
        for ((side, cx) in listOf(ox - s * 0.9f, ox + s * 0.9f).withIndex()) {
            var n = 1
            while (n * gap < reach + s) {
                c.drawCircle(cx, oy, n * gap, p.ink(ink(side)))
                n++
            }
        }
    }

    // --- shapes

    fun checker() =
        lattice(s, s) { x, y, i, j ->
            if ((i + j) % 2 == 0) c.drawRect(x, y, x + s, y + s, fill.ink(ink(i)))
        }

    /** A tiling of triangles, some filled, each at its own strength. */
    fun triangles() {
        val a = s * 1.2f
        val th = a * 0.866f
        val ny = (reach / th).toInt() + 1
        val nx = (reach / (a / 2)).toInt() + 2
        for (j in -ny..ny) {
            val y = oy + j * th
            for (i in -nx..nx) {
                val x = ox + i * a / 2
                val up = (i + j) % 2 == 0
                if (rnd.nextFloat() > 0.2f + 0.8f * k) continue
                fill.ink(anyInk(), 0.2f + 0.8f * rnd.nextFloat())
                val path = if (up) poly(x, y + th, x + a / 2, y, x + a, y + th) else poly(x, y, x + a, y, x + a / 2, y + th)
                c.drawPath(path, fill)
            }
        }
    }

    fun hexagon(
        cx: Float,
        cy: Float,
        r: Float,
    ) = Path().apply {
        for (q in 0 until 6) {
            val a = PI / 3 * q - PI / 2
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (q == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun hexagons() {
        val r = s * 0.6f
        val p = stroke(s * (0.015f + 0.12f * k))
        lattice(r * sqrt(3f), r * 1.5f, stagger = true) { x, y, i, _ -> c.drawPath(hexagon(x, y, r), p.ink(ink(i))) }
    }

    /** Isometric cubes: each hexagon split into a light top and two shaded sides. */
    fun cubes() {
        val r = s * 0.6f
        val cx = r * 0.866f
        lattice(r * sqrt(3f), r * 1.5f, stagger = true) { x, y, i, _ ->
            val col = ink(i)
            c.drawPath(poly(x, y - r, x + cx, y - r / 2, x, y, x - cx, y - r / 2), fill.ink(col, 1f))
            c.drawPath(poly(x, y, x + cx, y - r / 2, x + cx, y + r / 2, x, y + r), fill.ink(col, 0.25f + 0.3f * k))
            c.drawPath(poly(x, y, x, y + r, x - cx, y + r / 2, x - cx, y - r / 2), fill.ink(col, 0.5f + 0.3f * k))
        }
    }

    /** Harlequin: a lattice of diamonds, alternately strong and faint. */
    fun diamonds() {
        val hw = s * 0.5f
        val hh = s * 0.8f
        lattice(s, hh, stagger = true) { x, y, i, j ->
            val strong = j % 2 == 0
            fill.ink(ink(i), if (strong) 1f else 0.25f + 0.5f * k)
            c.drawPath(poly(x, y - hh, x + hw, y, x, y + hh, x - hw, y), fill)
        }
    }

    fun pluses(turn: Float) {
        val arm = s * 0.2f
        val p = stroke(s * (0.03f + 0.12f * k))
        lattice(s, s) { x, y, _, _ ->
            c.save()
            c.rotate(turn, x, y)
            p.ink(anyInk())
            c.drawLine(x - arm, y, x + arm, y, p)
            c.drawLine(x, y - arm, x, y + arm, p)
            c.restore()
        }
    }

    /** Rings on a grid; heavy enough and they overlap into chains. */
    fun circles() {
        val p = stroke(s * 0.035f + u)
        val r = s * 0.5f * (0.45f + 1.1f * k)
        lattice(s, s) { x, y, _, _ -> c.drawCircle(x, y, r, p.ink(anyInk())) }
    }

    /** Seigaiha: rows of fans, each lower row over the one above it. Each fan first clears what it covers. */
    fun scales() {
        val r = s * 0.6f
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val p = stroke(r * (0.04f + 0.1f * k))
        val ny = (reach / (r / 2)).toInt() + 2
        val nx = (reach / (2 * r)).toInt() + 2
        for (j in -ny..ny) {
            val y = oy + j * r / 2
            val shift = if (j % 2 != 0) r else 0f
            for (i in -nx..nx) {
                val x = ox + i * 2 * r + shift
                c.drawCircle(x, y, r, clear)
                val col = ink(j)
                for (q in 0 until 4) c.drawCircle(x, y, r * (1 - q * 0.24f) - p.strokeWidth / 2, p.ink(col))
            }
        }
    }

    fun bricks() {
        val bw = s * 1.2f
        val bh = s * 0.5f
        val gap = s * (0.02f + 0.12f * (1 - k))
        lattice(bw, bh, stagger = true) { x, y, _, _ ->
            fill.ink(anyInk(), 0.35f + 0.65f * rnd.nextFloat())
            c.drawRect(x + gap / 2, y + gap / 2, x + bw - gap / 2, y + bh - gap / 2, fill)
        }
    }

    /** Truchet tiles: quarter circles in opposite corners, each tile turned at random, joining into curves. */
    fun truchet() {
        val p = stroke(s * (0.04f + 0.3f * k))
        p.strokeCap = Paint.Cap.BUTT
        val half = s / 2
        lattice(s, s) { x, y, _, _ ->
            p.ink(ink(0))
            if (rnd.nextBoolean()) {
                c.drawArc(RectF(x - half, y - half, x + half, y + half), 0f, 90f, false, p)
                c.drawArc(RectF(x + s - half, y + s - half, x + s + half, y + s + half), 180f, 90f, false, p)
            } else {
                c.drawArc(RectF(x + s - half, y - half, x + s + half, y + half), 90f, 90f, false, p)
                c.drawArc(RectF(x - half, y + s - half, x + half, y + s + half), 270f, 90f, false, p)
            }
        }
        p.strokeCap = Paint.Cap.ROUND
    }

    /** One diagonal per tile, either way at random: the "10 PRINT" maze. */
    fun maze() {
        val p = stroke(s * (0.04f + 0.25f * k))
        lattice(s, s) { x, y, _, _ ->
            p.ink(anyInk())
            if (rnd.nextBoolean()) c.drawLine(x, y, x + s, y + s, p) else c.drawLine(x + s, y, x, y + s, p)
        }
    }

    fun confetti() {
        val size = s * (0.15f + 0.35f * k)
        jittered(s * 1.4f) { x, y ->
            if (rnd.nextFloat() > 0.8f) return@jittered
            val col = anyInk()
            c.save()
            c.rotate(rnd.nextFloat() * 360, x, y)
            val p = stroke(size * 0.28f).ink(col)
            when (rnd.nextInt(7)) {
                0 -> {
                    c.drawCircle(x, y, size * 0.5f, fill.ink(col))
                }

                1 -> {
                    c.drawCircle(x, y, size * 0.45f, p)
                }

                2 -> {
                    c.drawPath(poly(x, y - size * 0.6f, x + size * 0.55f, y + size * 0.4f, x - size * 0.55f, y + size * 0.4f), fill.ink(col))
                }

                3 -> {
                    c.drawRect(x - size * 0.4f, y - size * 0.4f, x + size * 0.4f, y + size * 0.4f, fill.ink(col))
                }

                4 -> {
                    val path = Path()
                    path.moveTo(x - size, y)
                    var t = -size
                    while (t < size) {
                        t += size / 8
                        path.lineTo(x + t, y + size * 0.25f * sin(t / size * 2 * PI * 1.5).toFloat())
                    }
                    c.drawPath(path, stroke(size * 0.2f).ink(col))
                }

                5 -> {
                    c.drawLine(x - size * 0.6f, y, x + size * 0.6f, y, p)
                }

                else -> {
                    c.drawLine(x - size * 0.45f, y, x + size * 0.45f, y, p)
                    c.drawLine(x, y - size * 0.45f, x, y + size * 0.45f, p)
                }
            }
            c.restore()
        }
    }

    fun bubbles() {
        val n = (w * h / (s * s) * 0.5f).toInt().coerceAtMost(4000)
        repeat(n) {
            val r = s * (0.15f + 2.2f * rnd.nextFloat().pow(2)) * (0.4f + 1.2f * k)
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            if (rnd.nextFloat() < 0.3f) {
                c.drawCircle(x, y, r, stroke(u * 2 + r * 0.04f).ink(anyInk(), 0.6f))
            } else {
                c.drawCircle(x, y, r, fill.ink(anyInk(), 0.12f + 0.4f * rnd.nextFloat()))
            }
        }
    }

    // --- art

    /** A disc at the point with a glow around it; Weight is how far the glow reaches. */
    fun sun() {
        val r = s * 3
        val col = ink(0)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        val far = r * (1.3f + 2.5f * k)
        glow.shader =
            RadialGradient(
                ox,
                oy,
                far,
                intArrayOf(withAlpha(col, 0.5f), withAlpha(col, 0.12f), withAlpha(col, 0f)),
                floatArrayOf(r / far, (r / far + 1) / 2, 1f),
                Shader.TileMode.CLAMP,
            )
        c.drawCircle(ox, oy, far, glow)
        c.drawCircle(ox, oy, r, fill.ink(col))
        // More inks: rings inside the disc, like a setting sun's bands.
        for (q in 1 until inks.size) c.drawCircle(ox, oy, r * (1 - q / (inks.size + 0.5f)), fill.ink(ink(q)))
    }

    /** Concentric arches over the point, one band per ink and round again. */
    fun rainbow() {
        val band = s * 0.7f
        val gap = band * 0.5f * (1 - k)
        val n = max(5, inks.size)
        val p = stroke(band - gap)
        p.strokeCap = Paint.Cap.BUTT
        for (q in 0 until n) {
            val r = s * 1.5f + q * band + band / 2
            c.drawArc(RectF(ox - r, oy - r, ox + r, oy + r), 180f, 180f, false, p.ink(ink(n - 1 - q)))
        }
        p.strokeCap = Paint.Cap.ROUND
    }

    /** Hills, one behind the other from the point's height down, each a little stronger: the last nearly solid. */
    fun layers(ridged: Boolean) {
        val noise = Noise(d.seed)
        val top = oy
        val n = ((h - top) / (s * 1.4f)).toInt().coerceIn(3, 30)
        val step = (h - top) / n
        val alpha = 1 - 0.08f.pow(1f / n)
        val amp = s * (0.3f + 1.5f * k)
        for (i in 0 until n) {
            val base = top + i * step
            val len = w * (0.6f + rnd.nextFloat())
            val phase = rnd.nextFloat() * 6.28f
            val path = Path()
            var x = -u * 8
            path.moveTo(x, h + 1)
            while (x < w + u * 8) {
                val y =
                    if (ridged) {
                        base - amp * 1.6f * (1 - abs(noise.fbm(x / (w * 0.35f), i * 3.1f, 4))) + amp * 0.6f
                    } else {
                        base + amp * sin(2 * PI * x / len + phase).toFloat() + amp * 0.35f * sin(2 * PI * x / (len / 2.3f) + phase * 1.7f).toFloat()
                    }
                path.lineTo(x, y)
                x += u * 6
            }
            path.lineTo(w + u * 8, h + 1)
            path.close()
            c.drawPath(path, fill.ink(ink(i), alpha))
        }
    }

    fun bokeh() {
        val n = (w * h / (s * s) * 0.3f).toInt().coerceIn(6, 1500)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(n) {
            val r = s * (0.3f + 2.5f * rnd.nextFloat().pow(2)) * (0.4f + 1.2f * k)
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val col = anyInk()
            val a = 0.15f + 0.45f * rnd.nextFloat()
            p.shader =
                RadialGradient(
                    x,
                    y,
                    r,
                    intArrayOf(withAlpha(col, a), withAlpha(col, a * 0.75f), withAlpha(col, 0f)),
                    floatArrayOf(0f, 0.8f, 1f),
                    Shader.TileMode.CLAMP,
                )
            c.drawCircle(x, y, r, p)
        }
    }

    /** The background under ([x], [y]), lighter or darker by [by], from -1 to 1. */
    fun shaded(
        x: Float,
        y: Float,
        by: Float,
    ): Int {
        val col = bg.getPixel(x.toInt().coerceIn(0, bg.width - 1), y.toInt().coerceIn(0, bg.height - 1))
        val toward = if (by > 0) 255 else 0
        val t = abs(by)

        fun mix(v: Int) = (v + (toward - v) * t).roundToInt()
        return Color.rgb(mix(Color.red(col)), mix(Color.green(col)), mix(Color.blue(col)))
    }

    /** The background cut into triangles, each one flat, a little lighter or darker; Weight draws their edges. */
    fun lowPoly() {
        val cell = s * 1.6f
        val nx = (w / cell).toInt() + 3
        val ny = (h / cell).toInt() + 3
        val px = Array(ny) { j -> FloatArray(nx) { i -> (i - 1) * cell + (rnd.nextFloat() - 0.5f) * cell * 0.8f } }
        val py = Array(ny) { j -> FloatArray(nx) { (j - 1) * cell + (rnd.nextFloat() - 0.5f) * cell * 0.8f } }
        val vary = d.opacity / 100f * 0.35f
        val tris = mutableListOf<Pair<Path, Int>>()
        for (j in 0 until ny - 1) {
            for (i in 0 until nx - 1) {
                val a = floatArrayOf(px[j][i], py[j][i])
                val b = floatArrayOf(px[j][i + 1], py[j][i + 1])
                val cc = floatArrayOf(px[j + 1][i + 1], py[j + 1][i + 1])
                val dd = floatArrayOf(px[j + 1][i], py[j + 1][i])
                val halves = if (rnd.nextBoolean()) listOf(listOf(a, b, cc), listOf(a, cc, dd)) else listOf(listOf(a, b, dd), listOf(b, cc, dd))
                for (t in halves) {
                    val mx = (t[0][0] + t[1][0] + t[2][0]) / 3
                    val my = (t[0][1] + t[1][1] + t[2][1]) / 3
                    tris += poly(t[0][0], t[0][1], t[1][0], t[1][1], t[2][0], t[2][1]) to shaded(mx, my, (rnd.nextFloat() * 2 - 1) * vary)
                }
            }
        }
        // A hairline in the same colour closes the seams that anti-aliasing leaves between triangles.
        val seam = stroke(u * 1.2f)
        for ((path, col) in tris) {
            c.drawPath(path, fill.ink(col))
            c.drawPath(path, seam.ink(col))
        }
        if (k > 0.02f) {
            val edge = stroke(u * (0.5f + 2.5f * k))
            for ((path, _) in tris) c.drawPath(path, edge.ink(ink(0), 0.15f + 0.5f * k))
        }
    }

    /** Stained glass: the background cut into cells around random points, each flat and shaded, with ink in the gaps. */
    fun voronoi() {
        val cell = s * 1.4f
        val nx = (w / cell).toInt() + 3
        val ny = (h / cell).toInt() + 3
        val sx = Array(ny) { FloatArray(nx) }
        val sy = Array(ny) { FloatArray(nx) }
        for (j in 0 until ny) {
            for (i in 0 until nx) {
                sx[j][i] = (i - 1 + rnd.nextFloat()) * cell
                sy[j][i] = (j - 1 + rnd.nextFloat()) * cell
            }
        }
        val gap = s * 0.12f * k
        val vary = d.opacity / 100f * 0.35f
        if (gap > 0) c.drawColor(ink(0))
        for (j in 1 until ny - 1) {
            for (i in 1 until nx - 1) {
                val x0 = sx[j][i]
                val y0 = sy[j][i]
                var poly =
                    listOf(x0 - 3 * cell to y0 - 3 * cell, x0 + 3 * cell to y0 - 3 * cell, x0 + 3 * cell to y0 + 3 * cell, x0 - 3 * cell to y0 + 3 * cell)
                for (jj in max(0, j - 2)..min(ny - 1, j + 2)) {
                    for (ii in max(0, i - 2)..min(nx - 1, i + 2)) {
                        if (ii == i && jj == j) continue
                        poly = clip(poly, x0, y0, sx[jj][ii], sy[jj][ii], gap / 2)
                    }
                }
                if (poly.size < 3) continue
                val path = Path()
                poly.forEachIndexed { q, (x, y) -> if (q == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                path.close()
                val col = shaded(x0, y0, (rnd.nextFloat() * 2 - 1) * vary)
                c.drawPath(path, fill.ink(col))
                if (gap == 0f) c.drawPath(path, stroke(u * 1.2f).ink(col))
            }
        }
    }

    /** [poly] cut to the side of the bisector nearer ([x0], [y0]) than ([x1], [y1]), less [inset] (Sutherland–Hodgman). */
    fun clip(
        poly: List<Pair<Float, Float>>,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        inset: Float,
    ): List<Pair<Float, Float>> {
        val nx = x1 - x0
        val ny = y1 - y0
        val len = hypot(nx, ny)
        val mx = (x0 + x1) / 2 - nx / len * inset
        val my = (y0 + y1) / 2 - ny / len * inset

        fun side(p: Pair<Float, Float>) = (p.first - mx) * nx + (p.second - my) * ny
        val out = mutableListOf<Pair<Float, Float>>()
        for (q in poly.indices) {
            val a = poly[q]
            val b = poly[(q + 1) % poly.size]
            val sa = side(a)
            val sb = side(b)
            if (sa <= 0) out += a
            if ((sa < 0) != (sb < 0) && sa != sb) {
                val t = sa / (sa - sb)
                out += (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
            }
        }
        return out
    }

    /** Topographic lines through a noise field (marching squares), every fifth one bolder. */
    fun contours() {
        val noise = Noise(d.seed)
        val g = max(2f, w / 160f)
        val gx = (w / g).toInt() + 2
        val gy = (h / g).toInt() + 2
        val f = s * 7
        val v = FloatArray(gx * gy) { noise.fbm((it % gx) * g / f, (it / gx) * g / f, 3) }
        val step = 0.045f
        val thin = stroke(u * (1f + 5f * k))
        val segs = HashMap<Int, Floats>()
        for (j in 0 until gy - 1) {
            for (i in 0 until gx - 1) {
                val a = v[j * gx + i]
                val b = v[j * gx + i + 1]
                val cc = v[(j + 1) * gx + i + 1]
                val dd = v[(j + 1) * gx + i]
                val lo = floor(minOf(a, b, cc, dd) / step).toInt() + 1
                val hi = floor(maxOf(a, b, cc, dd) / step).toInt()
                for (l in lo..hi) {
                    val lv = l * step
                    val list = segs.getOrPut(l) { Floats() }
                    march(a, b, cc, dd, lv, i * g, j * g, g, list)
                }
            }
        }
        for ((l, list) in segs) {
            val bold = l.mod(5) == 0
            thin.strokeWidth = u * (1f + 5f * k) * if (bold) 2.2f else 1f
            c.drawLines(list.a, 0, list.n, thin.ink(ink(l), if (bold) 1f else 0.7f))
        }
    }

    /** The segments where one square cell, corners [a] [b] (top) [cc] [dd] (bottom, right to left), crosses level [lv]. */
    fun march(
        a: Float,
        b: Float,
        cc: Float,
        dd: Float,
        lv: Float,
        x: Float,
        y: Float,
        g: Float,
        out: Floats,
    ) {
        val case = (if (a > lv) 1 else 0) or (if (b > lv) 2 else 0) or (if (cc > lv) 4 else 0) or (if (dd > lv) 8 else 0)

        fun t(
            p: Float,
            q: Float,
        ) = if (p == q) 0.5f else ((lv - p) / (q - p)).coerceIn(0f, 1f)

        fun edge(e: Int) {
            when (e) {
                0 -> out.add(x + t(a, b) * g, y)
                1 -> out.add(x + g, y + t(b, cc) * g)
                2 -> out.add(x + t(dd, cc) * g, y + g)
                else -> out.add(x, y + t(a, dd) * g)
            }
        }

        fun seg(
            e1: Int,
            e2: Int,
        ) {
            edge(e1)
            edge(e2)
        }
        when (case) {
            1, 14 -> {
                seg(3, 0)
            }

            2, 13 -> {
                seg(0, 1)
            }

            3, 12 -> {
                seg(3, 1)
            }

            4, 11 -> {
                seg(1, 2)
            }

            5 -> {
                seg(3, 0)
                seg(1, 2)
            }

            6, 9 -> {
                seg(0, 2)
            }

            7, 8 -> {
                seg(2, 3)
            }

            10 -> {
                seg(0, 1)
                seg(2, 3)
            }
        }
    }

    /** Lines that drift along a noise field, like hair or wind; [swirl] winds them round the point instead. */
    fun flow(swirl: Boolean) {
        val noise = Noise(d.seed)
        val f = s * 6
        val len = s * 0.12f
        val p = stroke(u * (1f + 5f * k))
        jittered(s * 0.8f) { x0, y0 ->
            val path = Path()
            var x = x0
            var y = y0
            path.moveTo(x, y)
            repeat(60) {
                val n = noise.fbm(x / f, y / f, 2)
                val a =
                    if (swirl) {
                        // Round the point, drifting a little inward, wavering with the noise.
                        kotlin.math.atan2(y - oy, x - ox) + PI.toFloat() * 0.56f + n * 0.7f
                    } else {
                        n * 2 * PI.toFloat() * 1.5f
                    }
                x += cos(a) * len
                y += sin(a) * len
                path.lineTo(x, y)
            }
            c.drawPath(path, p.ink(anyInk(), 0.5f + 0.5f * rnd.nextFloat()))
        }
    }

    /** Graph paper: fine lines, every fifth one bolder. */
    fun graph() {
        val p = stroke(s * (0.01f + 0.06f * k))
        p.strokeCap = Paint.Cap.BUTT
        val n = (reach / s).toInt() + 1
        for (i in -n..n) {
            val major = i.mod(5) == 0
            p.strokeWidth = max(u * 0.75f, s * (0.01f + 0.06f * k)) * if (major) 2.5f else 1f
            p.ink(ink(if (major) 0 else 1), if (major) 1f else 0.5f)
            c.drawLine(ox + i * s, oy - reach, ox + i * s, oy + reach, p)
            c.drawLine(ox - reach, oy + i * s, ox + reach, oy + i * s, p)
        }
        p.strokeCap = Paint.Cap.ROUND
    }

    /** Three sets of lines at sixty degrees: the triangular grid of isometric drawings. */
    fun isoGrid() {
        val gap = s * 0.866f
        val p = stroke(s * (0.01f + 0.08f * k))
        val n = (reach / gap).toInt() + 1
        for ((q, turn) in listOf(0f, 60f, 60f).withIndex()) {
            c.rotate(turn, ox, oy)
            for (i in -n..n) c.drawLine(ox - reach, oy + i * gap, ox + reach, oy + i * gap, p.ink(ink(q)))
        }
    }

    fun squares() {
        val p = stroke(s * (0.03f + 0.45f * k))
        p.strokeJoin = Paint.Join.MITER
        var n = 1
        while (n * s / 2 < reach * 1.5f) {
            val r = n * s / 2
            c.drawRect(ox - r, oy - r, ox + r, oy + r, p.ink(ink(n)))
            n++
        }
        p.strokeJoin = Paint.Join.ROUND
    }

    /** Basket weave: three bars to a square, across and down in turn. */
    fun weave() {
        val cell = s * 1.2f
        val bar = cell / 3
        val gap = bar * (0.08f + 0.5f * (1 - k))
        lattice(cell, cell) { x, y, i, j ->
            fill.ink(ink(i + j))
            for (q in 0 until 3) {
                if ((i + j) % 2 == 0) {
                    c.drawRect(x + gap / 2, y + q * bar + gap / 2, x + cell - gap / 2, y + (q + 1) * bar - gap / 2, fill)
                } else {
                    c.drawRect(x + q * bar + gap / 2, y + gap / 2, x + (q + 1) * bar - gap / 2, y + cell - gap / 2, fill)
                }
            }
        }
    }

    /**
     * Bauhaus tiles: each square a block of colour with a quarter circle, half
     * circle, circle or triangle on it in another. The background's colour is
     * one of the choices, so some shapes read as holes.
     */
    fun bauhaus() {
        val cell = s * 1.6f
        val choices = inks + d.colors.take(1)

        fun pick() = choices[rnd.nextInt(choices.size)]
        lattice(cell, cell) { x, y, _, _ ->
            val back = pick()
            var front = pick()
            if (front == back) front = choices[(choices.indexOf(back) + 1) % choices.size]
            if (rnd.nextFloat() < 0.3f + 0.7f * k) c.drawRect(x, y, x + cell, y + cell, fill.ink(back))
            fill.ink(front)
            c.save()
            c.clipRect(x, y, x + cell, y + cell)
            c.rotate(90f * rnd.nextInt(4), x + cell / 2, y + cell / 2)
            when (rnd.nextInt(5)) {
                0 -> c.drawCircle(x, y, cell, fill)
                1 -> c.drawCircle(x + cell / 2, y, cell / 2, fill)
                2 -> c.drawCircle(x + cell / 2, y + cell / 2, cell * 0.36f, fill)
                3 -> c.drawPath(poly(x, y, x + cell, y, x, y + cell), fill)
                else -> c.drawRect(x, y, x + cell / 2, y + cell, fill)
            }
            c.restore()
        }
    }

    /** Nested arches standing on the bottom edge, their tops near the point. */
    fun arches() {
        val n = max(4, inks.size)
        val outer = s * 8f
        val band = outer / (n + 1) * (0.5f + 0.5f * k)
        for (q in 0 until n) {
            val half = outer / 2 - q * band
            if (half <= 0) break
            val top = oy + q * band
            val path = Path()
            path.moveTo(ox - half, h + 1)
            path.lineTo(ox - half, top + half)
            path.arcTo(RectF(ox - half, top, ox + half, top + 2 * half), 180f, 180f)
            path.lineTo(ox + half, h + 1)
            path.close()
            c.drawPath(path, fill.ink(ink(q)))
        }
    }

    /** A few large, soft-edged organic shapes. */
    fun pebbles() {
        val noise = Noise(d.seed)
        val n = 3 + rnd.nextInt(4)
        repeat(n) { q ->
            val cx = rnd.nextFloat() * w
            val cy = rnd.nextFloat() * h
            val r = s * (1.5f + 3f * rnd.nextFloat()) * (0.5f + k)
            val pts = 24
            val xs = FloatArray(pts)
            val ys = FloatArray(pts)
            for (i in 0 until pts) {
                val a = 2 * PI * i / pts
                val rr = r * (1 + 0.35f * noise.at(cos(a).toFloat() * 1.2f + q * 7, sin(a).toFloat() * 1.2f + q * 3))
                xs[i] = cx + rr * cos(a).toFloat()
                ys[i] = cy + rr * sin(a).toFloat() * 0.85f
            }
            // Through the midpoints, with the points as control points: a closed curve with no corners.
            val path = Path()
            path.moveTo((xs[0] + xs[1]) / 2, (ys[0] + ys[1]) / 2)
            for (i in 1..pts) {
                val a = i % pts
                val b = (i + 1) % pts
                path.quadTo(xs[a], ys[a], (xs[a] + xs[b]) / 2, (ys[a] + ys[b]) / 2)
            }
            path.close()
            c.drawPath(path, fill.ink(ink(q)))
        }
    }

    /** Veins through stone: thin bright lines along a warped wave. Weight thickens them. */
    fun marble() {
        val noise = Noise(d.seed)
        val gw = max(8, (w / 2).toInt())
        val gh = max(8, (h / 2).toInt())
        val cw = w / gw
        val f = s * 4
        val sharp = 3f + 20f * (1 - k)
        val dst = Rect(0, 0, w.roundToInt(), h.roundToInt())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        inks.forEachIndexed { q, col ->
            val freq = 1f + q * 0.7f
            val px =
                IntArray(gw * gh) { idx ->
                    val x = (idx % gw) * cw
                    val y = (idx / gw) * cw
                    val warp = noise.fbm(x / (f * 2.5f) + q * 5, y / (f * 2.5f), 3) * 3f
                    val v = abs(sin((x + y * 0.3f) / (f * 1.5f) * freq + warp))
                    withAlpha(col, (1 - v).pow(sharp) * (0.6f + 0.4f / (q + 1)))
                }
            val b = Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
            c.drawBitmap(b, null, dst, paint)
            b.recycle()
        }
    }

    /** Lines stacked down the screen, each lifted by peaks near the middle, each hiding the ones behind it. */
    fun ridgelines() {
        val noise = Noise(d.seed)
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val p = stroke(u * (1.5f + 5f * k))
        val gap = s * 0.5f
        val top = oy
        val n = ((h - top) / gap).toInt()
        val amp = s * 3
        for (i in 0..n) {
            val base = top + i * gap
            val path = Path()
            var x = -u * 4
            var first = true
            while (x < w + u * 8) {
                val env = exp(-((x - ox) / (w * 0.2f)).pow(2))
                val y = base - amp * env * max(0f, noise.fbm(x / (s * 1.6f), i * 0.37f, 3) + 0.25f)
                if (first) path.moveTo(x, y) else path.lineTo(x, y)
                first = false
                x += u * 4
            }
            val fillPath = Path(path)
            fillPath.lineTo(w + u * 8, h + 1)
            fillPath.lineTo(-u * 4, h + 1)
            fillPath.close()
            c.drawPath(fillPath, clear)
            c.drawPath(path, p.ink(ink(i)))
        }
    }

    /** Curtains of light hanging from wavy lines across the sky, streaked downward. */
    fun aurora() {
        val noise = Noise(d.seed)
        val gw = max(8, (w / 4).toInt())
        val gh = max(8, (h / 4).toInt())
        val cw = w / gw
        val dst = Rect(0, 0, w.roundToInt(), h.roundToInt())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val tall = s * (2f + 4f * k)
        inks.forEachIndexed { q, col ->
            val base = oy + q * s * 1.2f
            val edge = FloatArray(gw) { i -> base + s * 2.5f * noise.fbm(i * cw / (w * 0.5f), q * 4.1f, 3) }
            val streak = FloatArray(gw) { i -> 0.55f + 0.45f * noise.at(i * cw / (s * 0.35f), q * 9.7f) }
            val px =
                IntArray(gw * gh) { idx ->
                    val i = idx % gw
                    val y = (idx / gw) * cw
                    val dy = y - edge[i]
                    // Bright along the lower edge, fading up into the curtain, sharp below it.
                    val a = if (dy < 0) exp(dy / tall) else exp(-(dy / (s * 0.5f)).pow(2))
                    withAlpha(col, a * streak[i] * 0.9f)
                }
            val b = Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
            c.drawBitmap(b, null, dst, paint)
            b.recycle()
        }
    }

    /** Organic shapes where a noise field is high; with several inks, stacked like camouflage. Weight is how much they cover. */
    fun blobs() {
        val noise = Noise(d.seed)
        val gw = max(8, (w / 3).toInt())
        val gh = max(8, (h / 3).toInt())
        val cw = w / gw
        val f = s * 5
        val v = FloatArray(gw * gh) { noise.fbm((it % gw) * cw / f, (it / gw) * cw / f, 3) }
        val dst = Rect(0, 0, w.roundToInt(), h.roundToInt())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val base = 0.3f - 0.6f * k
        inks.forEachIndexed { q, col ->
            val at = base + q * 0.14f
            // Each edge a pixel wide however steep the field is there, so it doesn't step when drawn larger.
            val px =
                IntArray(gw * gh) { idx ->
                    val e = max(abs(v[min(idx + 1, v.size - 1)] - v[idx]), abs(v[min(idx + gw, v.size - 1)] - v[idx])) * 0.7f + 1e-4f
                    withAlpha(col, smooth(at - e, at + e, v[idx]))
                }
            val b = Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
            c.drawBitmap(b, null, dst, paint)
            b.recycle()
        }
    }

    fun smooth(
        e0: Float,
        e1: Float,
        x: Float,
    ): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    fun withAlpha(
        col: Int,
        a: Float,
    ) = (col and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * (col ushr 24)).roundToInt() shl 24)
}

/** A growing list of floats, without boxing each one. */
private class Floats {
    var a = FloatArray(1024)
    var n = 0

    fun add(
        x: Float,
        y: Float,
    ) {
        if (n + 2 > a.size) a = a.copyOf(a.size * 2)
        a[n++] = x
        a[n++] = y
    }
}

/** Perlin's improved gradient noise in two dimensions, about -1 to 1, the same for the same seed. */
private class Noise(
    seed: Int,
) {
    private val p = IntArray(512)

    init {
        val perm = (0 until 256).shuffled(Random(seed))
        for (i in 0 until 512) p[i] = perm[i and 255]
    }

    fun at(
        x: Float,
        y: Float,
    ): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = fade(xf)
        val v = fade(yf)
        val x0 = xi and 255
        val y0 = yi and 255
        val aa = p[p[x0] + y0]
        val ab = p[p[x0] + y0 + 1]
        val ba = p[p[x0 + 1] + y0]
        val bb = p[p[x0 + 1] + y0 + 1]
        val top = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
        val bottom = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)
        return lerp(top, bottom, v)
    }

    /** Several octaves, each half as strong and twice as fine. */
    fun fbm(
        x: Float,
        y: Float,
        octaves: Int,
    ): Float {
        var sum = 0f
        var amp = 1f
        var f = 1f
        var norm = 0f
        repeat(octaves) {
            sum += amp * at(x * f + it * 17.3f, y * f + it * 9.1f)
            norm += amp
            amp *= 0.5f
            f *= 2f
        }
        return sum / norm * 1.4f
    }

    private fun fade(t: Float) = t * t * t * (t * (t * 6 - 15) + 10)

    private fun lerp(
        a: Float,
        b: Float,
        t: Float,
    ) = a + (b - a) * t

    private fun grad(
        hash: Int,
        x: Float,
        y: Float,
    ) = when (hash and 7) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }
}
