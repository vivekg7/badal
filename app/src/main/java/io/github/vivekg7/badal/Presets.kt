package io.github.vivekg7.badal

import kotlin.random.Random

/**
 * The premade designs the gallery offers, built from a few tables rather than
 * listed one by one: each pattern in four palettes, plus plain colours,
 * gradients and mesh gradients (docs/plans/005_designs.md).
 */
object Presets {
    /** Background colours and pattern inks that go together. */
    class Palette(
        val bg: List<Int>,
        val inks: List<Int>,
    )

    private fun pal(
        bg: List<Long>,
        inks: List<Long>,
    ) = Palette(bg.map { it.toInt() }, inks.map { it.toInt() })

    val palettes =
        listOf(
            pal(listOf(0xFF0D0D0F), listOf(0xFFFFFFFF)),
            pal(listOf(0xFF141B33, 0xFF0A0E1E), listOf(0xFFF2C14E)),
            pal(listOf(0xFFF3EEE3), listOf(0xFF2B2B2B)),
            pal(listOf(0xFF0F3D3E, 0xFF082425), listOf(0xFFE3B505)),
            pal(listOf(0xFFFFD9C7, 0xFFFFB8A3), listOf(0xFFE5989B, 0xFFB5838D, 0xFF6D6875)),
            pal(listOf(0xFF264653), listOf(0xFF2A9D8F, 0xFFE9C46A, 0xFFF4A261, 0xFFE76F51)),
            pal(listOf(0xFF07070C), listOf(0xFF00F5D4, 0xFF9B5DE5, 0xFFF15BB5)),
            pal(listOf(0xFFE8EDF1), listOf(0xFF8DA9C4, 0xFF134074)),
            pal(listOf(0xFF2D3A3A, 0xFF1A2424), listOf(0xFF9DB4AB)),
            pal(listOf(0xFF3B0A2A, 0xFF5F0F40), listOf(0xFFFB8B24, 0xFFE36414, 0xFF9A031E)),
            pal(listOf(0xFFFAF3DD), listOf(0xFFC8D5B9, 0xFF8FC0A9, 0xFF68B0AB, 0xFF4A7C59)),
            pal(listOf(0xFF1A1A2E, 0xFF16213E), listOf(0xFFE94560)),
            pal(listOf(0xFF3D405B), listOf(0xFFF4F1DE, 0xFFE07A5F, 0xFF81B29A, 0xFFF2CC8F)),
            pal(listOf(0xFFEDE0D4, 0xFFE6CCB2), listOf(0xFFB08968, 0xFF7F5539, 0xFF9C6644)),
            pal(listOf(0xFF03045E, 0xFF023E8A), listOf(0xFF90E0EF, 0xFF48CAE4, 0xFF00B4D8)),
            pal(listOf(0xFF2B2D42), listOf(0xFF8D99AE, 0xFFEDF2F4)),
            pal(listOf(0xFFFFC8DD, 0xFFBDE0FE), listOf(0xFFFFFFFF)),
            pal(listOf(0xFF10002B, 0xFF240046), listOf(0xFFC77DFF, 0xFFE0AAFF)),
            pal(listOf(0xFF0B2B1E, 0xFF14452F), listOf(0xFF95D5B2, 0xFF52B788)),
            pal(listOf(0xFF1F1F1F), listOf(0xFFFF6B35)),
            pal(listOf(0xFFD8E2DC), listOf(0xFFFFCAD4, 0xFFF4ACB7, 0xFF9D8189)),
            pal(listOf(0xFF14213D), listOf(0xFFFCA311, 0xFFE5E5E5)),
            pal(listOf(0xFF6B705C, 0xFF555A48), listOf(0xFFFFE8D6, 0xFFDDBEA9, 0xFFCB997E)),
            pal(listOf(0xFF2B1B3D, 0xFF5C2A4E), listOf(0xFFFFB86F, 0xFFE0777D, 0xFFFFE0B5)),
            pal(listOf(0xFFFFFFFF), listOf(0xFF111111)),
            pal(listOf(0xFFC8553D, 0xFFA63F2B), listOf(0xFFF2D0A4, 0xFFFFF4E0)),
            pal(listOf(0xFFCAF0F8, 0xFFADE8F4), listOf(0xFF0077B6, 0xFF00B4D8)),
            pal(listOf(0xFF2E1F1A), listOf(0xFFA8E6CF, 0xFFDCEDC1)),
        )

    /** Colours the colour picker offers before you mix your own. */
    val swatches =
        listOf(
            0xFF000000,
            0xFF1F1F1F,
            0xFF4A4A4A,
            0xFF9E9E9E,
            0xFFE0E0E0,
            0xFFFFFFFF,
            0xFF14213D,
            0xFF023E8A,
            0xFF0077B6,
            0xFF48CAE4,
            0xFF90E0EF,
            0xFFCAF0F8,
            0xFF0B2B1E,
            0xFF2A9D8F,
            0xFF52B788,
            0xFF95D5B2,
            0xFFE9C46A,
            0xFFFCA311,
            0xFF5F0F40,
            0xFF9A031E,
            0xFFE63946,
            0xFFF4A261,
            0xFFFFB4A2,
            0xFFFFE8D6,
            0xFF240046,
            0xFF7B2CBF,
            0xFFC77DFF,
            0xFFF15BB5,
            0xFFFFC8DD,
            0xFFF3EEE3,
        ).map { it.toInt() }

    /** A pattern as it first looks: what the designer starts from when you pick it. */
    fun base(m: Motif): Design {
        val d = Design(motif = m)
        return when (m) {
            Motif.NONE -> d
            Motif.DOTS -> d.copy(scale = 40, weight = 35, opacity = 45)
            Motif.HEX_DOTS -> d.copy(scale = 40, weight = 40, opacity = 45)
            Motif.HALFTONE -> d.copy(scale = 30, weight = 60, opacity = 45)
            Motif.BURST -> d.copy(scale = 25, weight = 70, opacity = 45, y = 0.45f)
            Motif.DOT_RINGS -> d.copy(scale = 35, weight = 35, opacity = 50)
            Motif.SUNFLOWER -> d.copy(scale = 30, weight = 60, opacity = 50)
            Motif.DOT_RAYS -> d.copy(scale = 40, weight = 70, opacity = 45)
            Motif.VORTEX -> d.copy(scale = 40, weight = 70, opacity = 45)
            Motif.DOT_WAVE -> d.copy(scale = 40, weight = 45, opacity = 45)
            Motif.SCATTER -> d.copy(scale = 45, weight = 45, opacity = 50)
            Motif.STARS -> d.copy(scale = 45, weight = 40, opacity = 90)
            Motif.STRIPES -> d.copy(scale = 50, weight = 30, opacity = 15, turn = 30)
            Motif.GRID -> d.copy(scale = 50, weight = 12, opacity = 25)
            Motif.CROSSHATCH -> d.copy(scale = 40, weight = 15, opacity = 25)
            Motif.PLAID -> d.copy(scale = 70, weight = 50, opacity = 35)
            Motif.WAVES -> d.copy(scale = 40, weight = 15, opacity = 30)
            Motif.ZIGZAG -> d.copy(scale = 45, weight = 18, opacity = 30)
            Motif.RINGS -> d.copy(scale = 40, weight = 15, opacity = 25)
            Motif.SUNBURST -> d.copy(scale = 55, weight = 50, opacity = 12, y = 1f)
            Motif.SPIRAL -> d.copy(scale = 35, weight = 15, opacity = 30)
            Motif.MOIRE -> d.copy(scale = 30, weight = 30, opacity = 35)
            Motif.GRAPH -> d.copy(scale = 30, weight = 15, opacity = 30)
            Motif.ISO_GRID -> d.copy(scale = 45, weight = 12, opacity = 25)
            Motif.SQUARES -> d.copy(scale = 40, weight = 20, opacity = 30)
            Motif.WEAVE -> d.copy(scale = 55, weight = 60, opacity = 25)
            Motif.BAUHAUS -> d.copy(scale = 70, weight = 50, opacity = 100)
            Motif.ARCH -> d.copy(scale = 50, weight = 50, opacity = 100, y = 0.45f)
            Motif.PEBBLES -> d.copy(scale = 60, weight = 50, opacity = 90)
            Motif.SWIRL -> d.copy(scale = 40, weight = 20, opacity = 55)
            Motif.MARBLE -> d.copy(scale = 60, weight = 40, opacity = 70, turn = 30)
            Motif.RIDGELINES -> d.copy(scale = 40, weight = 25, opacity = 90, y = 0.2f)
            Motif.AURORA -> d.copy(scale = 60, weight = 50, opacity = 85, y = 0.35f)
            Motif.CHECKER -> d.copy(scale = 60, weight = 50, opacity = 12)
            Motif.TRIANGLES -> d.copy(scale = 60, weight = 60, opacity = 25)
            Motif.HEXAGONS -> d.copy(scale = 60, weight = 12, opacity = 30)
            Motif.CUBES -> d.copy(scale = 60, weight = 50, opacity = 22)
            Motif.DIAMONDS -> d.copy(scale = 55, weight = 40, opacity = 18)
            Motif.PLUSES -> d.copy(scale = 50, weight = 30, opacity = 40)
            Motif.CROSSES -> d.copy(scale = 50, weight = 30, opacity = 40)
            Motif.CIRCLES -> d.copy(scale = 55, weight = 60, opacity = 30)
            Motif.SCALES -> d.copy(scale = 60, weight = 30, opacity = 40)
            Motif.BRICKS -> d.copy(scale = 60, weight = 80, opacity = 18)
            Motif.TRUCHET -> d.copy(scale = 55, weight = 25, opacity = 45)
            Motif.MAZE -> d.copy(scale = 45, weight = 18, opacity = 40)
            Motif.CONFETTI -> d.copy(scale = 55, weight = 50, opacity = 90)
            Motif.BUBBLES -> d.copy(scale = 50, weight = 50, opacity = 70)
            Motif.SUN -> d.copy(scale = 50, weight = 50, opacity = 95, y = 0.38f)
            Motif.RAINBOW -> d.copy(scale = 60, weight = 60, opacity = 100, y = 0.7f)
            Motif.LAYERS -> d.copy(scale = 55, weight = 50, opacity = 100, y = 0.35f)
            Motif.RIDGES -> d.copy(scale = 55, weight = 50, opacity = 100, y = 0.4f)
            Motif.BOKEH -> d.copy(scale = 60, weight = 50, opacity = 60)
            Motif.LOW_POLY -> d.copy(scale = 60, weight = 0, opacity = 40)
            Motif.VORONOI -> d.copy(scale = 55, weight = 20, opacity = 35)
            Motif.CONTOURS -> d.copy(scale = 55, weight = 20, opacity = 40)
            Motif.FLOW -> d.copy(scale = 40, weight = 20, opacity = 50)
            Motif.BLOBS -> d.copy(scale = 60, weight = 50, opacity = 70)
        }
    }

    /** [m] in palette [p]: over its gradient if it has one, or for a pattern that takes its colours from the background, a gradient of all of them. */
    fun styled(
        m: Motif,
        p: Palette,
        seed: Int = 1,
    ): Design {
        val d = base(m).copy(inks = p.inks, seed = seed)
        return when {
            m.samples -> d.copy(fill = Fill.LINEAR, angle = 120, colors = (p.bg + p.inks).distinct().take(4), inks = listOf(p.bg.first()))
            p.bg.size > 1 -> d.copy(fill = if (m.centred) Fill.RADIAL else Fill.LINEAR, colors = p.bg)
            else -> d.copy(colors = p.bg)
        }
    }

    private val solids =
        listOf(
            0xFF000000,
            0xFF121212,
            0xFF1C2333,
            0xFF0B3D2E,
            0xFF5C1A1B,
            0xFF2E294E,
            0xFF264653,
            0xFF3A3A3A,
            0xFFE9E4D8,
            0xFFF4A261,
            0xFF8FB8A8,
            0xFFB5838D,
            0xFF6D597A,
            0xFF457B9D,
            0xFFD9C5B2,
            0xFFFFFFFF,
        )

    private val gradients =
        listOf(
            listOf(0xFFFF5F6D, 0xFFFFC371),
            listOf(0xFF2193B0, 0xFF6DD5ED),
            listOf(0xFF8E2DE2, 0xFF4A00E0),
            listOf(0xFFED4264, 0xFFFFEDBC),
            listOf(0xFF00B09B, 0xFF96C93D),
            listOf(0xFF2C3E50, 0xFFFD746C),
            listOf(0xFF0F2027, 0xFF203A43, 0xFF2C5364),
            listOf(0xFFFF9A9E, 0xFFFECFEF),
            listOf(0xFFF7971E, 0xFFFFD200),
            listOf(0xFF00C9FF, 0xFF92FE9D),
            listOf(0xFF141E30, 0xFF243B55),
            listOf(0xFFFFAFBD, 0xFFFFC3A0),
            listOf(0xFF83A4D4, 0xFFB6FBFF),
            listOf(0xFFC471F5, 0xFFFA71CD),
            listOf(0xFFFF00CC, 0xFF333399),
            listOf(0xFFFFE259, 0xFFFFA751),
            listOf(0xFFBDC3C7, 0xFF2C3E50),
            listOf(0xFF1D2B64, 0xFFF8CDDA),
            listOf(0xFF0B486B, 0xFFF56217),
            listOf(0xFF000000, 0xFF434343),
            listOf(0xFF355C7D, 0xFF6C5B7B, 0xFFC06C84),
            listOf(0xFFFC5C7D, 0xFF6A82FB),
            listOf(0xFF12C2E9, 0xFFC471ED, 0xFFF64F59),
            listOf(0xFF0F0C29, 0xFF302B63, 0xFF24243E),
        )

    private val meshes =
        listOf(
            listOf(0xFFFF9A8B, 0xFFFF6A88, 0xFFFF99AC, 0xFFFAD0C4),
            listOf(0xFF4158D0, 0xFFC850C0, 0xFFFFCC70),
            listOf(0xFF0093E9, 0xFF80D0C7, 0xFF13547A),
            listOf(0xFF8EC5FC, 0xFFE0C3FC, 0xFFF9F9F9),
            listOf(0xFF21D4FD, 0xFFB721FF, 0xFF1A1A40),
            listOf(0xFFFBAB7E, 0xFFF7CE68, 0xFFFF8A65),
            listOf(0xFF0F2027, 0xFF2C5364, 0xFF1CB5E0, 0xFF000046),
            listOf(0xFFA9C9FF, 0xFFFFBBEC, 0xFFFFFFFF),
            listOf(0xFF2E3192, 0xFF1BFFFF, 0xFF0E1A40),
            listOf(0xFFD4FC79, 0xFF96E6A1, 0xFF2E8B57),
            listOf(0xFFFF3CAC, 0xFF784BA0, 0xFF2B86C5),
            listOf(0xFF0B0B0F, 0xFF3A1C71, 0xFFD76D77, 0xFFFFAF7B),
            listOf(0xFFF6D365, 0xFFFDA085, 0xFFFFF1E0),
            listOf(0xFF134E5E, 0xFF71B280, 0xFF0B2E2E),
            listOf(0xFF7F7FD5, 0xFF86A8E7, 0xFF91EAE4),
            listOf(0xFF232526, 0xFF414345, 0xFF6B6B83),
        )

    /** The gallery: sections in order, each a title and its designs. */
    val groups: List<Pair<String, List<Design>>> by lazy {
        val fills = listOf(Fill.LINEAR to 90, Fill.LINEAR to 135, Fill.RADIAL to 0, Fill.LINEAR to 60, Fill.LINEAR to 160, Fill.RADIAL to 0)
        val plain =
            listOf(
                "Colours" to solids.map { Design(colors = listOf(it.toInt())) },
                "Gradients" to
                    gradients.mapIndexed { i, cols ->
                        val (fill, angle) = fills[i % fills.size]
                        Design(fill = fill, angle = angle, colors = cols.map { it.toInt() }, y = 0.3f)
                    },
                "Mesh" to
                    meshes.mapIndexed { i, cols ->
                        Design(
                            fill = Fill.MESH,
                            colors = cols.map { it.toInt() },
                            seed = i + 3,
                            grain =
                                if (i % 3 ==
                                    0
                                ) {
                                    12
                                } else {
                                    0
                                },
                        )
                    },
            )
        val patterned =
            Motif.entries.filter { it != Motif.NONE }.groupBy { it.group }.map { (group, motifs) ->
                group to
                    motifs.flatMap { m ->
                        val fit = if (m == Motif.STARS || m == Motif.AURORA) palettes.filter { dark(it.bg.first()) } else palettes
                        (0 until 4).map { q -> styled(m, fit[(m.ordinal * 5 + q * 7) % fit.size], seed = q + 1) }
                    }
            }
        plain + patterned
    }

    /** Dark enough for stars: luminance under a third. */
    private fun dark(c: Int) = (0.2126 * (c shr 16 and 0xFF) + 0.7152 * (c shr 8 and 0xFF) + 0.0722 * (c and 0xFF)) / 255 < 0.33

    val all: List<Design> by lazy { groups.flatMap { it.second } }

    /** Something new from the parts above: a pattern, a palette, sizes nudged, sometimes grain. The same [seed] makes the same design. */
    fun random(seed: Int): Design {
        val r = Random(seed)
        val motifs = Motif.entries.filter { it != Motif.NONE }
        val m = if (r.nextInt(8) == 0) Motif.NONE else motifs[r.nextInt(motifs.size)]
        val p = palettes[r.nextInt(palettes.size)]
        var d = styled(m, p, seed = r.nextInt(1, 10_000))
        if (m == Motif.NONE) d = d.copy(fill = Fill.MESH, colors = (p.bg + p.inks).distinct().take(4))

        fun nudge(
            v: Int,
            by: Int,
        ) = (v + r.nextInt(-by, by + 1)).coerceIn(0, 100)
        d = d.copy(scale = nudge(d.scale, 15), weight = nudge(d.weight, 20))
        if (m.turns) d = d.copy(turn = listOf(0, 0, 15, 30, 45, 60, 90)[r.nextInt(7)])
        if (r.nextInt(4) == 0) d = d.copy(grain = 10)
        if (r.nextInt(5) == 0) d = d.copy(vignette = 25)
        return d
    }
}
