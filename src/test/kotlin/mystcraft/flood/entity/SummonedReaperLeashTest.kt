package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SummonedReaperLeashTest {
    @Test
    fun `summon starts returning beyond twenty four blocks`() {
        assertFalse(SummonedReaperLeash.shouldReturn(24.0 * 24.0, alreadyReturning = false))
        assertTrue(SummonedReaperLeash.shouldReturn(24.01 * 24.01, alreadyReturning = false))
    }

    @Test
    fun `summon keeps returning until within eighteen blocks`() {
        assertTrue(SummonedReaperLeash.shouldReturn(20.0 * 20.0, alreadyReturning = true))
        assertFalse(SummonedReaperLeash.shouldReturn(18.0 * 18.0, alreadyReturning = true))
    }
}
