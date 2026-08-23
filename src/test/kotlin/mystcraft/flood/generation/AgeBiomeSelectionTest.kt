package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeBiomeSelection
import mystcraft.flood.generation.profile.BiomeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AgeBiomeSelectionTest {
    @Test
    fun `vanilla distribution controller is not collapsed to single biome`() {
        val compiled = AgeCompiler.compile(listOf("biome_vanilla"))
        assertEquals("VANILLA_DISTRIBUTION", compiled.biomeController)
        assertEquals(
            BiomeMode.VANILLA_DISTRIBUTION,
            AgeBiomeSelection.selectMode(compiled.biomeController, compiled.biomes.size, Random(1))
        )
    }

    @Test
    fun `omitted biome pages produce every layout and favor multi biome ages`() {
        val modes = (0 until 1_000).map { seed ->
            AgeBiomeSelection.selectMode(null, 0, Random(seed))
        }

        assertTrue(BiomeMode.entries.all(modes::contains))
        assertTrue(modes.count { it != BiomeMode.SINGLE } >= 700)
    }

    @Test
    fun `weighted and checkerboard fallbacks choose several biomes`() {
        repeat(100) { seed ->
            assertTrue(AgeBiomeSelection.randomBiomeCount(BiomeMode.WEIGHTED, 50, Random(seed)) in 2..5)
            assertTrue(AgeBiomeSelection.randomBiomeCount(BiomeMode.CHECKERBOARD, 50, Random(seed)) in 3..6)
        }
    }
}
