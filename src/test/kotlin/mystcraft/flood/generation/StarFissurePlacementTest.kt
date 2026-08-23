package mystcraft.flood.generation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StarFissurePlacementTest {
    @Test
    fun `every age has a guaranteed fissure in its arrival region`() {
        assertTrue(StarFissurePlacement.isGuaranteedExitRegion(0, 0))
        assertFalse(StarFissurePlacement.isGuaranteedExitRegion(1, 0))
        assertFalse(StarFissurePlacement.isGuaranteedExitRegion(0, -1))
    }

    @Test
    fun `additional fissures retain instability based rarity`() {
        assertEquals(0.12f, StarFissurePlacement.chanceFor(0))
        assertEquals(0.24f, StarFissurePlacement.chanceFor(45))
        assertEquals(0.34f, StarFissurePlacement.chanceFor(80))
    }
}
