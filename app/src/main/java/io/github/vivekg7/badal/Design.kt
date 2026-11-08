package io.github.vivekg7.badal

import kotlinx.serialization.Serializable

/*
 * A wallpaper Badal draws itself instead of copying from the gallery
 * (docs/plans/005_designs.md): a background, a pattern over it, and a finish.
 * Plain values and no Android types, so the presets are tested on the JVM;
 * Painter draws one.
 */

@Serializable
enum class Fill(
    val label: String,
) {
    SOLID("Solid"),
    LINEAR("Linear"),
    RADIAL("Radial"),
    SWEEP("Sweep"),

    /** Soft blobs of each colour, blended: a "mesh gradient". */
    MESH("Mesh"),
}

/**
 * A pattern. [turns]: Rotation means something for it. [centred]: it grows
 * from the design's point (x, y), which the designer lets you drag. [seeded]:
 * it is scattered at random, so Shuffle gives another of it.
 */
@Serializable
enum class Motif(
    val label: String,
    val group: String,
    val turns: Boolean = false,
    val centred: Boolean = false,
    val seeded: Boolean = false,
) {
    NONE("None", ""),

    DOTS("Dots", "Dots", turns = true),
    HEX_DOTS("Offset dots", "Dots", turns = true),
    HALFTONE("Halftone", "Dots", turns = true, centred = true),
    BURST("Dot burst", "Dots", turns = true, centred = true),
    DOT_RINGS("Dot rings", "Dots", centred = true),
    SUNFLOWER("Sunflower", "Dots", centred = true),
    DOT_RAYS("Dot rays", "Dots", turns = true, centred = true),
    VORTEX("Vortex", "Dots", turns = true, centred = true),
    DOT_WAVE("Dot wave", "Dots", turns = true),
    SCATTER("Scatter", "Dots", seeded = true),
    STARS("Stars", "Dots", seeded = true),

    STRIPES("Stripes", "Lines", turns = true),
    GRID("Grid", "Lines", turns = true),
    CROSSHATCH("Crosshatch", "Lines", turns = true),
    PLAID("Plaid", "Lines", turns = true),
    WAVES("Waves", "Lines", turns = true),
    ZIGZAG("Zigzag", "Lines", turns = true),
    RINGS("Rings", "Lines", centred = true),
    SUNBURST("Sunburst", "Lines", turns = true, centred = true),
    SPIRAL("Spiral", "Lines", turns = true, centred = true),
    MOIRE("Moiré", "Lines", turns = true, centred = true),
    GRAPH("Graph paper", "Lines", turns = true),
    ISO_GRID("Isometric", "Lines", turns = true),
    SQUARES("Squares", "Lines", turns = true, centred = true),

    CHECKER("Checks", "Shapes", turns = true),
    TRIANGLES("Triangles", "Shapes", turns = true, seeded = true),
    HEXAGONS("Honeycomb", "Shapes", turns = true),
    CUBES("Cubes", "Shapes", turns = true),
    DIAMONDS("Diamonds", "Shapes", turns = true),
    PLUSES("Pluses", "Shapes", turns = true),
    CROSSES("Crosses", "Shapes", turns = true),
    CIRCLES("Circles", "Shapes", turns = true),
    SCALES("Scales", "Shapes", turns = true),
    BRICKS("Bricks", "Shapes", turns = true, seeded = true),
    TRUCHET("Arcs", "Shapes", turns = true, seeded = true),
    MAZE("Maze", "Shapes", turns = true, seeded = true),
    WEAVE("Weave", "Shapes", turns = true),
    BAUHAUS("Bauhaus", "Shapes", turns = true, seeded = true),
    CONFETTI("Confetti", "Shapes", seeded = true),
    BUBBLES("Bubbles", "Shapes", seeded = true),

    SUN("Sun", "Art", centred = true),
    RAINBOW("Rainbow", "Art", turns = true, centred = true),
    ARCH("Arches", "Art", centred = true),
    PEBBLES("Pebbles", "Art", seeded = true),
    LAYERS("Dunes", "Art", centred = true, seeded = true),
    RIDGES("Ridges", "Art", centred = true, seeded = true),
    BOKEH("Bokeh", "Art", seeded = true),
    LOW_POLY("Low poly", "Art", seeded = true),
    VORONOI("Cells", "Art", seeded = true),
    CONTOURS("Contours", "Art", seeded = true),
    FLOW("Flow", "Art", seeded = true),
    SWIRL("Swirl", "Art", centred = true, seeded = true),
    MARBLE("Marble", "Art", turns = true, seeded = true),
    RIDGELINES("Ridgelines", "Art", centred = true, seeded = true),
    AURORA("Aurora", "Art", centred = true, seeded = true),
    BLOBS("Blobs", "Art", seeded = true),
    ;

    /** Colours its marks from the background under them, instead of in an ink: opacity is how much they vary. */
    val samples get() = this == LOW_POLY || this == VORONOI
}

/**
 * Everything that makes one design. Colours are ARGB. Sizes are relative to
 * the screen's width, so a thumbnail looks like the wallpaper, only smaller.
 */
@Serializable
data class Design(
    val fill: Fill = Fill.SOLID,
    /** One to five background colours; Solid uses the first. */
    val colors: List<Int> = listOf(INK_BLACK),
    /** Linear: the direction it runs, in degrees, 90 being top to bottom. Sweep: where it starts. */
    val angle: Int = 90,
    val motif: Motif = Motif.NONE,
    /** One to five pattern colours; a pattern of separate marks picks among them. */
    val inks: List<Int> = listOf(INK_WHITE),
    /** The pattern's opacity, in percent. */
    val opacity: Int = 50,
    /** How large the pattern is: its spacing, from 2% to 24% of the screen's width. */
    val scale: Int = 50,
    /** How heavy its marks are: dot size, line width, coverage. */
    val weight: Int = 50,
    /** Rotation in degrees, for patterns that [Motif.turns]. */
    val turn: Int = 0,
    /** The point a centred pattern, a radial or sweep fill grows from, in fractions of the screen. */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** For Shuffle: picks the random scatter of seeded patterns and of Mesh. */
    val seed: Int = 1,
    /** Film grain, in percent. */
    val grain: Int = 0,
    /** Darkened corners, in percent. */
    val vignette: Int = 0,
)

const val INK_BLACK = 0xFF000000.toInt()
const val INK_WHITE = 0xFFFFFFFF.toInt()
