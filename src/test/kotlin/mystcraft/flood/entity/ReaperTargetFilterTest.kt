package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaperTargetFilterTest {

    @Test
    fun `both Whackdolls corpse types are ignored`() {
        assertTrue(ReaperTargetFilter.isIgnoredEntityType("whackdolls:ragdoll"))
        assertTrue(ReaperTargetFilter.isIgnoredEntityType("whackdolls:mob_ragdoll"))
    }

    @Test
    fun `ordinary entities and similarly named types remain valid`() {
        assertFalse(ReaperTargetFilter.isIgnoredEntityType("minecraft:player"))
        assertFalse(ReaperTargetFilter.isIgnoredEntityType("minecraft:zombie"))
        assertFalse(ReaperTargetFilter.isIgnoredEntityType("another_mod:ragdoll"))
        assertFalse(ReaperTargetFilter.isIgnoredEntityType("malformed"))
    }
}
