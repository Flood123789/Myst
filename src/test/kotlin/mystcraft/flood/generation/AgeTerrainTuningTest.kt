package mystcraft.flood.generation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgeTerrainTuningTest {
    @Test
    fun `terrain turbulence preserves inherited noise cell geometry`() {
        for (turbulence in listOf(1, 4, 8, 13, 16)) {
            val tuned = AgeTerrainTuning.preservedNoiseCellSizes(1, 2, turbulence)

            assertEquals(1, tuned.first)
            assertEquals(2, tuned.second)
        }
    }
}
