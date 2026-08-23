package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeDimensionRole
import mystcraft.flood.generation.profile.TerrainType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgeBiomeDistributionTest {
    @Test
    fun `alpha and beta terrain retain the complete overworld biome distribution`() {
        assertEquals(AgeDimensionRole.OVERWORLD, AgeBiomeDistribution.sourceRole(AgeDimensionRole.OVERWORLD, TerrainType.ALPHA))
        assertEquals(AgeDimensionRole.OVERWORLD, AgeBiomeDistribution.sourceRole(AgeDimensionRole.OVERWORLD, TerrainType.BETA))
    }

    @Test
    fun `native realm terrain inherits its matching biome distribution`() {
        assertEquals(AgeDimensionRole.NETHER, AgeBiomeDistribution.sourceRole(AgeDimensionRole.OVERWORLD, TerrainType.NETHER))
        assertEquals(AgeDimensionRole.END, AgeBiomeDistribution.sourceRole(AgeDimensionRole.OVERWORLD, TerrainType.END))
    }

    @Test
    fun `derived realm role overrides the root terrain type`() {
        assertEquals(AgeDimensionRole.NETHER, AgeBiomeDistribution.sourceRole(AgeDimensionRole.NETHER, TerrainType.STANDARD))
        assertEquals(AgeDimensionRole.END, AgeBiomeDistribution.sourceRole(AgeDimensionRole.END, TerrainType.STANDARD))
    }
}
