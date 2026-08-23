package mystcraft.flood.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MystcraftSeasonsTest {
    @Test
    fun `season names and aliases map to Serene Seasons order`() {
        assertEquals(0, MystcraftSeasons.indexOf("spring"))
        assertEquals(3, MystcraftSeasons.indexOf("summer"))
        assertEquals(6, MystcraftSeasons.indexOf("fall"))
        assertEquals(8, MystcraftSeasons.indexOf("late-autumn"))
        assertEquals(10, MystcraftSeasons.indexOf("mid_winter"))
        assertNull(MystcraftSeasons.indexOf("monsoon"))
    }

    @Test
    fun `season indices wrap across the annual cycle`() {
        assertEquals("early_spring", MystcraftSeasons.nameOf(12))
        assertEquals("late_winter", MystcraftSeasons.nameOf(-1))
    }

    @Test
    fun `random starts are deterministic per world and dimension`() {
        val first = MystcraftSeasons.randomIndex(42L, "mystcraft-reforged:age_1")
        assertEquals(first, MystcraftSeasons.randomIndex(42L, "mystcraft-reforged:age_1"))
        assertTrue(first in 0..11)
        assertNotEquals(first, MystcraftSeasons.randomIndex(42L, "mystcraft-reforged:age_2"))
    }
}
