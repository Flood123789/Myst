package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaperTamingTest {
    @Test
    fun `lesser binding threshold remains forgiving`() {
        assertTrue(ReaperTaming.canBind(true, false, 3.5f, 10.0f))
        assertFalse(ReaperTaming.canBind(true, true, 2.0f, 8.0f))
        assertFalse(ReaperTaming.canBind(true, false, 3.6f, 10.0f))
        assertFalse(ReaperTaming.canBind(true, false, 0.0f, 10.0f))
    }

    @Test
    fun `greater can be earned as a mount only near defeat`() {
        assertTrue(ReaperTaming.canBind(false, false, 12.0f, 120.0f))
        assertFalse(ReaperTaming.canBind(false, false, 12.1f, 120.0f))
        assertTrue(ReaperTaming.requiredEchoShards(false) > ReaperTaming.requiredEchoShards(true))
    }
}
