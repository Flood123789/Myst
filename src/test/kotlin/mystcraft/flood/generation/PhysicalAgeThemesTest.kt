package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeTerrainVariation
import mystcraft.flood.generation.profile.TerrainType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PhysicalAgeThemesTest {
    @Test
    fun `physical page catalog contains at least thirty distinct signatures`() {
        assertTrue(PhysicalAgeThemes.ALL.size >= 30)
        assertEquals(PhysicalAgeThemes.ALL.size, PhysicalAgeThemes.ALL.distinct().size)
    }

    @Test
    fun `random ages can draw one signature from every physical family`() {
        val selections = (0 until 500).map { seed ->
            PhysicalAgeThemes.selectDistinctFamilies(Random(seed), 5)
        }
        assertTrue(selections.all { it.size == 5 && it.distinct().size == 5 })

        val seenExotic = selections.flatten().filter { it in ExoticAgeThemes.ALL }.toSet()
        assertEquals(ExoticAgeThemes.ALL.toSet(), seenExotic)
    }

    @Test
    fun `unwritten noise terrain receives broad deterministic tuning`() {
        val first = AgeTerrainVariation.randomFor(TerrainType.STANDARD, Random(77))
        assertEquals(first, AgeTerrainVariation.randomFor(TerrainType.STANDARD, Random(77)))

        val profiles = (0 until 200).map { seed ->
            AgeTerrainVariation.randomFor(TerrainType.STANDARD, Random(seed))
        }.toSet()
        assertTrue(profiles.size >= 100)
        assertTrue(profiles.all {
            it.terrainTurbulence?.let { value -> value in 4..14 } == true &&
                it.seaLevel?.let { value -> value in 6..11 } == true &&
                it.caveDensity?.let { value -> value in 4..13 } == true &&
                it.biomeSize?.let { value -> value in 2..15 } == true &&
                (it.verticalRange == null || it.verticalRange?.let { value -> value in 6..12 } == true)
        })
    }
}
