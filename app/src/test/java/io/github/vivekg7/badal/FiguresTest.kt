package io.github.vivekg7.badal

import io.github.vivekg7.badal.Figures.Curve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class FiguresTest {
    private fun steps(p: FloatArray) = (2 until p.size step 2).map { hypot(p[it] - p[it - 2], p[it + 1] - p[it - 1]) }

    private fun cells(p: FloatArray) = (p.indices step 2).map { p[it].roundToInt() to p[it + 1].roundToInt() }

    @Test
    fun gridCurvesVisitEveryPointOnce() {
        for ((curve, order, side) in listOf(Triple(Curve.HILBERT, 4, 16), Triple(Curve.PEANO, 3, 27))) {
            val p = Figures.curve(curve, order)
            assertTrue(steps(p).all { abs(it - 1) < 1e-4 })
            val seen = cells(p)
            assertEquals(side * side, seen.size)
            assertEquals(seen.size, seen.toSet().size)
        }
    }

    @Test
    fun mooreIsALoop() {
        val p = Figures.curve(Curve.MOORE, 4)
        val seen = cells(p)
        assertEquals(seen.first(), seen.last())
        assertEquals(32 * 32, seen.toSet().size)
    }

    @Test
    fun gosperAndDragonNeverRetraceAStep() {
        for (curve in listOf(Curve.GOSPER, Curve.DRAGON)) {
            val p = Figures.curve(curve, 6)
            val edges =
                (2 until p.size step 2).map {
                    // An edge by its midpoint, rounded past float noise.
                    ((p[it] + p[it - 2]) * 50).roundToInt() to ((p[it + 1] + p[it - 1]) * 50).roundToInt()
                }
            assertEquals(edges.size, edges.toSet().size)
        }
    }

    @Test
    fun aMeanderIsOneLoopThroughEveryPoint() {
        for (seed in 1..5) {
            val p = Figures.meander(7, 5, seed)
            assertTrue(steps(p).all { abs(it - 1) < 1e-4 })
            val seen = cells(p)
            assertEquals(seen.first(), seen.last())
            assertEquals(14 * 10, seen.size - 1)
            assertEquals(14 * 10, seen.toSet().size)
        }
        assertTrue(!Figures.meander(7, 5, 1).contentEquals(Figures.meander(7, 5, 2)))
    }

    @Test
    fun apollonianCirclesTouchButNeverOverlap() {
        for (split in listOf(0.5, 0.4, 0.3, 0.25)) apollonian(split)
    }

    private fun apollonian(split: Double) {
        val cs = Figures.apollonian(split, 0.01)
        assertTrue(cs.size > 100)
        var touching = 0
        for (c in cs) {
            val gap = 1 - hypot(c.x, c.y) - c.r
            assertTrue(gap > -1e-6)
            if (gap < 1e-6) touching++
        }
        for (i in cs.indices) {
            for (j in i + 1 until cs.size) {
                val gap = hypot(cs[i].x - cs[j].x, cs[i].y - cs[j].y) - cs[i].r - cs[j].r
                assertTrue(gap > -1e-6)
                if (gap < 1e-6) touching++
            }
        }
        // Each new circle touches the three round its gap, the outer one among them.
        assertTrue(touching >= (cs.size - 4) * 3)
    }

    @Test
    fun packedCirclesKeepTheirGap() {
        val cs = Figures.packing(100.0, 200.0, 10.0, 1.0, 0.5, 3)
        assertTrue(cs.size > 200)
        for (i in cs.indices) {
            for (j in i + 1 until cs.size) {
                assertTrue(hypot(cs[i].x - cs[j].x, cs[i].y - cs[j].y) >= cs[i].r + cs[j].r + 0.5 - 1e-9)
            }
        }
    }

    @Test
    fun penroseRhombsHaveUnitSidesAndTheGoldenRatio() {
        val tiles = Figures.penrose(7, 20.0)
        for (t in tiles) {
            for (q in 0 until 4) {
                val n = (q + 1) % 4
                assertEquals(1.0, hypot(t.p[n * 2] - t.p[q * 2], t.p[n * 2 + 1] - t.p[q * 2 + 1]), 1e-9)
            }
        }
        // Fat to thin is the golden ratio over any large patch.
        val near = tiles.filter { hypot(it.p[0], it.p[1]) < 15 }
        val ratio = near.count { it.thick }.toDouble() / near.count { !it.thick }
        assertEquals(1.618, ratio, 0.1)
        // No two rhombs in the same place.
        val centres = tiles.map { ((it.p[0] + it.p[4]) * 50).roundToInt() to ((it.p[1] + it.p[5]) * 50).roundToInt() }
        assertEquals(centres.size, centres.toSet().size)
    }
}
