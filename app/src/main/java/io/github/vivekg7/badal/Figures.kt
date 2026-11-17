package io.github.vivekg7.badal

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The geometry behind the curves and fractals (docs/plans/012_curves_spirographs.md):
 * plain numbers and no Android types, so FiguresTest checks it on the JVM.
 * Painter scales and colours what these return.
 */
object Figures {
    /** The space-filling curves, as L-systems: a start, rules, the turn for + and -, and which letters draw a step. */
    enum class Curve(
        val axiom: String,
        val rules: Map<Char, String>,
        val turn: Int,
        val draws: String,
        /** How much larger one more order makes it. */
        val grows: Double,
    ) {
        HILBERT("A", mapOf('A' to "+BF-AFA-FB+", 'B' to "-AF+BFB+FA-"), 90, "F", 2.0),
        MOORE("LFL+F+LFL", mapOf('L' to "-RF+LFL+FR-", 'R' to "+LF-RFR-FL+"), 90, "F", 2.0),
        PEANO("X", mapOf('X' to "XFYFX+F+YFXFY-F-XFYFX", 'Y' to "YFXFY-F-XFYFX+F+YFXFY"), 90, "F", 3.0),
        GOSPER("A", mapOf('A' to "A-B--B+A++AA+B-", 'B' to "+A-BB--B-A++A+B"), 60, "AB", sqrt(7.0)),
        DRAGON("F", mapOf('F' to "F+G", 'G' to "F-G"), 90, "FG", sqrt(2.0)),
        SIERPINSKI("F--XF--F--XF", mapOf('X' to "XF+G+XF--F--XF+G+X"), 45, "FG", 2.0),
    }

    /** [curve] at [order], as x, y pairs one step apart, from (0, 0) heading right. */
    fun curve(
        curve: Curve,
        order: Int,
    ): FloatArray {
        val n = 360 / curve.turn
        val dx = DoubleArray(n) { cos(2 * PI * it / n) }
        val dy = DoubleArray(n) { sin(2 * PI * it / n) }
        val out = Floats()
        var x = 0.0
        var y = 0.0
        var heading = 0
        out.add(0.0, 0.0)

        fun walk(
            symbol: Char,
            depth: Int,
        ) {
            val rule = curve.rules[symbol]
            if (depth > 0 && rule != null) return rule.forEach { walk(it, depth - 1) }
            when (symbol) {
                '+' -> {
                    heading = (heading + 1).mod(n)
                }

                '-' -> {
                    heading = (heading - 1).mod(n)
                }

                in curve.draws -> {
                    x += dx[heading]
                    y += dy[heading]
                    out.add(x, y)
                }
            }
        }
        curve.axiom.forEach { walk(it, order) }
        // Moore's curve ends a step from where it began: one more closes the loop.
        if (curve == Curve.MOORE) out.add(0.0, 0.0)
        return out.toArray()
    }

    /**
     * One closed line through every point of a [cols]*2 by [rows]*2 grid,
     * winding at random: it runs round a random spanning tree of the
     * [cols] by [rows] grid, the way a hand traces round a maze's walls.
     */
    fun meander(
        cols: Int,
        rows: Int,
        seed: Int,
    ): FloatArray {
        val rnd = Random(seed)
        // The tree: random edges joined unless they'd close a loop (Kruskal's).
        val parent = IntArray(cols * rows) { it }

        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r].also { parent[r] = parent[parent[r]] }
            return r
        }
        val right = BooleanArray(cols * rows)
        val down = BooleanArray(cols * rows)
        val edges = IntArray(cols * rows * 2) { it }
        edges.shuffle(rnd)
        for (e in edges) {
            val cell = e / 2
            val i = cell % cols
            val j = cell / cols
            val horizontal = e % 2 == 0
            if (if (horizontal) i + 1 >= cols else j + 1 >= rows) continue
            val other = if (horizontal) cell + 1 else cell + cols
            val a = root(cell)
            val b = root(other)
            if (a == b) continue
            parent[a] = b
            if (horizontal) right[cell] = true else down[cell] = true
        }
        // Every point of the fine grid gets exactly two neighbours.
        val fw = cols * 2
        val fh = rows * 2
        val nb = IntArray(fw * fh * 2) { -1 }

        fun link(
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
        ) {
            val a = y1 * fw + x1
            val b = y2 * fw + x2
            nb[a * 2 + if (nb[a * 2] < 0) 0 else 1] = b
            nb[b * 2 + if (nb[b * 2] < 0) 0 else 1] = a
        }
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val cell = j * cols + i
                val x = i * 2
                val y = j * 2
                if (j == 0 || !down[cell - cols]) link(x, y, x + 1, y)
                if (!right[cell]) link(x + 1, y, x + 1, y + 1)
                if (!down[cell]) link(x, y + 1, x + 1, y + 1)
                if (i == 0 || !right[cell - 1]) link(x, y, x, y + 1)
                if (right[cell]) {
                    link(x + 1, y, x + 2, y)
                    link(x + 1, y + 1, x + 2, y + 1)
                }
                if (down[cell]) {
                    link(x, y + 1, x, y + 2)
                    link(x + 1, y + 1, x + 1, y + 2)
                }
            }
        }
        val out = Floats()
        var prev = -1
        var at = 0
        do {
            out.add((at % fw).toDouble(), (at / fw).toDouble())
            val next = if (nb[at * 2] != prev) nb[at * 2] else nb[at * 2 + 1]
            prev = at
            at = next
        } while (at != 0)
        out.add(0.0, 0.0)
        return out.toArray()
    }

    class Circle(
        val x: Double,
        val y: Double,
        val r: Double,
        /** How many circles were drawn before this one could be: the first ones are 0. */
        val depth: Int,
    )

    /**
     * An Apollonian gasket in a circle of radius 1 at (0, 0): two circles side
     * by side, [split] and 1 - [split] across, then in every gap between three
     * circles the one that touches all three, down to radius [smallest].
     */
    fun apollonian(
        split: Double,
        smallest: Double,
        most: Int = 30_000,
    ): List<Circle> {
        // A circle as its curvature k (negative for the outer one) and k times its centre.
        class C(
            val k: Double,
            val kx: Double,
            val ky: Double,
            val depth: Int,
        )
        val r1 = split
        val r2 = 1 - split
        val c0 = C(-1.0, 0.0, 0.0, 0)
        val c1 = C(1 / r1, (r1 - 1) / r1, 0.0, 0)
        val c2 = C(1 / r2, r1 / r2, 0.0, 0)
        // The two circles touching all three (Descartes' theorem), above and below.
        // Descartes' square root is of 0 here, which rounding can push below it: leave it out.
        val k3 = c0.k + c1.k + c2.k
        val c3 = C(k3, c1.kx + c2.kx, 2.0, 0)
        val c4 = C(k3, c1.kx + c2.kx, -2.0, 0)
        val out = mutableListOf(c1, c2, c3, c4)
        // Each gap: three circles round it, and the fourth that touches them on its other side.
        val gaps = ArrayDeque<Array<C>>()
        for (t in listOf(c3, c4)) {
            gaps += arrayOf(c0, c1, t, c2)
            gaps += arrayOf(c0, c2, t, c1)
            gaps += arrayOf(c1, c2, t, c0)
        }
        while (gaps.isNotEmpty() && out.size < most) {
            val (a, b, c, away) = gaps.removeFirst()
            val k = 2 * (a.k + b.k + c.k) - away.k
            if (1 / k < smallest) continue
            val d = C(k, 2 * (a.kx + b.kx + c.kx) - away.kx, 2 * (a.ky + b.ky + c.ky) - away.ky, maxOf(a.depth, b.depth, c.depth) + 1)
            out += d
            gaps += arrayOf(a, b, d, c)
            gaps += arrayOf(a, c, d, b)
            gaps += arrayOf(b, c, d, a)
        }
        return out.map { Circle(it.kx / it.k, it.ky / it.k, 1 / it.k, it.depth) }
    }

    /**
     * Circles packed into [w] by [h] without touching: random points, each
     * as large as fits up to [largest], skipped when under [smallest].
     * Large ones are tried first, so small ones fill between them.
     */
    fun packing(
        w: Double,
        h: Double,
        largest: Double,
        smallest: Double,
        gap: Double,
        seed: Int,
    ): List<Circle> {
        val rnd = Random(seed)
        val cell = largest * 2 + gap
        val gx = (w / cell).toInt() + 1
        val gy = (h / cell).toInt() + 1
        val grid = Array(gx * gy) { mutableListOf<Circle>() }
        val out = mutableListOf<Circle>()
        // Each pass allows smaller circles than the last.
        var cap = largest
        while (cap >= smallest) {
            val tries = (w * h / (cap * cap) * 3).toInt().coerceAtMost(40_000)
            repeat(tries) {
                val x = rnd.nextDouble() * w
                val y = rnd.nextDouble() * h
                val i = (x / cell).toInt()
                val j = (y / cell).toInt()
                var r = cap
                for (jj in maxOf(0, j - 1)..minOf(gy - 1, j + 1)) {
                    for (ii in maxOf(0, i - 1)..minOf(gx - 1, i + 1)) {
                        for (o in grid[jj * gx + ii]) {
                            val dx = o.x - x
                            val dy = o.y - y
                            r = minOf(r, sqrt(dx * dx + dy * dy) - o.r - gap)
                        }
                    }
                }
                if (r >= smallest) {
                    val c = Circle(x, y, r, out.size)
                    out += c
                    grid[j * gx + i] += c
                }
            }
            cap *= 0.6
        }
        return out
    }

    class Rhomb(
        /** The corners, x, y in turn. */
        val p: DoubleArray,
        /** The fat one (72°); the thin one is 36°. */
        val thick: Boolean,
        /** The two grid directions it was made from, 0 to 4: they say which way it lies. */
        val a: Int,
        val b: Int,
    )

    /**
     * A Penrose tiling of rhombs with sides 1, by de Bruijn's pentagrid: five
     * sets of parallel lines, a rhomb wherever two lines cross. [seed] shifts
     * the lines, which picks another of the tilings; [reach] is how far from
     * (0, 0) it must cover.
     */
    fun penrose(
        seed: Int,
        reach: Double,
    ): List<Rhomb> {
        val rnd = Random(seed)
        val ex = DoubleArray(5) { cos(2 * PI * it / 5) }
        val ey = DoubleArray(5) { sin(2 * PI * it / 5) }
        // Offsets that add up to 0 make it a Penrose tiling.
        val g = DoubleArray(5) { rnd.nextDouble() * 0.6 + 0.2 }
        g[4] = -(g[0] + g[1] + g[2] + g[3])
        // A tile lies about 2.5 times as far out as the crossing it comes from.
        val n = ceil(reach / 2.5).toInt() + 2
        val out = mutableListOf<Rhomb>()
        val k = IntArray(5)
        for (r in 0 until 5) {
            for (s in r + 1 until 5) {
                val det = ex[r] * ey[s] - ey[r] * ex[s]
                for (kr in -n..n) {
                    for (ks in -n..n) {
                        // Where line kr of set r crosses line ks of set s.
                        val ar = kr - g[r]
                        val bs = ks - g[s]
                        val x = (ar * ey[s] - bs * ey[r]) / det
                        val y = (bs * ex[r] - ar * ex[s]) / det
                        if (x * x + y * y > (reach / 2.5 + 2) * (reach / 2.5 + 2)) continue
                        for (j in 0 until 5) k[j] = ceil(x * ex[j] + y * ey[j] + g[j]).toInt()
                        val p = DoubleArray(8)
                        for ((q, step) in listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1).withIndex()) {
                            k[r] = kr + step.first
                            k[s] = ks + step.second
                            var px = 0.0
                            var py = 0.0
                            for (j in 0 until 5) {
                                px += k[j] * ex[j]
                                py += k[j] * ey[j]
                            }
                            p[q * 2] = px
                            p[q * 2 + 1] = py
                        }
                        out += Rhomb(p, s - r == 1 || s - r == 4, r, s)
                    }
                }
            }
        }
        return out
    }
}

/** A growing list of x, y pairs, so a curve of a hundred thousand steps doesn't box every number. */
class Floats {
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

    fun add(
        x: Double,
        y: Double,
    ) = add(x.toFloat(), y.toFloat())

    fun toArray() = a.copyOf(n)
}
