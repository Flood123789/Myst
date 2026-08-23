package mystcraft.flood.generation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomStructureOverridesTest {
    @Test
    fun `override catalog is unique and exposes every exotic physical theme`() {
        val ids = CustomStructureOverrides.TYPES.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.size >= 30)
        assertTrue(ids.containsAll(ExoticAgeThemes.ALL))
    }
}
