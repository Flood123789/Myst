package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaperCompanionHealingTest {
    @Test
    fun `life steal restores forty five percent of real damage`() {
        assertEquals(2.7f, ReaperCompanionHealing.lifeSteal(6.0f), 0.0001f)
        assertEquals(0.0f, ReaperCompanionHealing.lifeSteal(-2.0f), 0.0001f)
    }

    @Test
    fun `amethyst scales for greater but remains useful for lesser`() {
        assertEquals(12.0f, ReaperCompanionHealing.amethystHeal(120.0f), 0.0001f)
        assertEquals(4.0f, ReaperCompanionHealing.amethystHeal(24.0f), 0.0001f)
    }
}
