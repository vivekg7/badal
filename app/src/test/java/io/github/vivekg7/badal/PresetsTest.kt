package io.github.vivekg7.badal

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {
    @Test
    fun everyPatternIsOffered() {
        val offered = Presets.all.map { it.motif }.toSet()
        assertEquals(Motif.entries.toSet(), offered)
    }

    @Test
    fun noTwoPresetsAreTheSame() {
        // The gallery selects by value: two equal presets would select together.
        assertEquals(Presets.all.size, Presets.all.toSet().size)
        assertTrue(Presets.all.size >= 250)
    }

    @Test
    fun everyPresetHasColoursAndValidSettings() {
        for (d in Presets.all + (1..200).map(Presets::random)) {
            assertTrue(d.colors.isNotEmpty() && d.colors.size <= 5)
            assertTrue(d.inks.isNotEmpty() && d.inks.size <= 5)
            for (v in listOf(d.opacity, d.scale, d.weight, d.grain, d.vignette)) assertTrue(v in 0..100)
        }
    }

    @Test
    fun designsSurviveJson() {
        val json = Json { ignoreUnknownKeys = true }
        for (d in Presets.all) assertEquals(d, json.decodeFromString(Design.serializer(), json.encodeToString(Design.serializer(), d)))
    }

    @Test
    fun anOldRecipeStillLoads() {
        // A field added later must have a default, so recipes saved before it still load.
        val d = Json { ignoreUnknownKeys = true }.decodeFromString(Design.serializer(), """{"motif":"DOTS","future":1}""")
        assertEquals(Motif.DOTS, d.motif)
    }

    @Test
    fun theSameSeedMakesTheSameSurprise() {
        assertEquals(Presets.random(42), Presets.random(42))
    }
}
