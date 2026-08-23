package mystcraft.flood.generation.instability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InstabilityThresholdsTest {
    @Test
    fun `overworld functions require a direct-hazard-free score`() {
        assertEquals(25, InstabilityThresholds.OVERWORLD_FUNCTIONS)
        assertEquals(0, InstabilityThresholds.tierFor(InstabilityThresholds.OVERWORLD_FUNCTIONS))
    }

    @Test
    fun `low non-zero instability can be free of direct hazards`() {
        assertEquals(0, InstabilityThresholds.tierFor(0))
        assertEquals(0, InstabilityThresholds.tierFor(1))
        assertEquals(0, InstabilityThresholds.tierFor(44))
    }

    @Test
    fun `each observed score band enters one additional hazard tier`() {
        assertEquals(1, InstabilityThresholds.tierFor(45))
        assertEquals(2, InstabilityThresholds.tierFor(65))
        assertEquals(3, InstabilityThresholds.tierFor(80))
        assertEquals(4, InstabilityThresholds.tierFor(100))
        assertEquals(5, InstabilityThresholds.tierFor(115))
    }

    @Test
    fun `scores immediately below boundaries stay in the previous tier`() {
        assertEquals(1, InstabilityThresholds.tierFor(64))
        assertEquals(2, InstabilityThresholds.tierFor(79))
        assertEquals(3, InstabilityThresholds.tierFor(99))
        assertEquals(4, InstabilityThresholds.tierFor(114))
    }
}
