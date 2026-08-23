package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.BiomeMode
import mystcraft.flood.generation.profile.BiomeSet
import mystcraft.flood.generation.profile.BiomeWeight
import mystcraft.flood.generation.profile.ColorSettings
import mystcraft.flood.generation.profile.SpawnSettings
import mystcraft.flood.generation.profile.StabilityProfile
import mystcraft.flood.generation.profile.TerrainType
import mystcraft.flood.generation.profile.TimeSettings
import mystcraft.flood.generation.profile.WeatherSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgeSubdimensionManagerTest {
    @Test
    fun `age nether and end unlock at score 25`() {
        assertTrue(AgeSubdimensionManager.supportsSubdimensions(profileAt(25)))
        assertFalse(AgeSubdimensionManager.supportsSubdimensions(profileAt(26)))
    }

    @Test
    fun `instability toggle remains an explicit steward bypass`() {
        val profile = profileAt(100)
        profile.stability.effectsEnabled = false

        assertTrue(AgeSubdimensionManager.supportsSubdimensions(profile))
    }

    @Test
    fun `sacrificed ages never support subdimensions`() {
        val profile = profileAt(0)
        profile.ageState.isSacrificed = true

        assertFalse(AgeSubdimensionManager.supportsSubdimensions(profile))
    }

    private fun profileAt(score: Int) = AgeProfile(
        id = "mystcraft-reforged:test_age",
        seed = 1L,
        terrainType = TerrainType.STANDARD,
        colors = ColorSettings(0, 0, 0, 0, 0),
        time = TimeSettings(),
        weather = WeatherSettings(false, false, false),
        biomes = BiomeSet(BiomeMode.SINGLE, mutableListOf(BiomeWeight("minecraft:plains", 100))),
        spawning = SpawnSettings(),
        stability = StabilityProfile(score <= 0, score)
    )
}
