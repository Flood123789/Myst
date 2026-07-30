package mystcraft.flood.generation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgeTimeInterpolationTest {
    @Test
    fun `each time scale advances only its own synchronized clock`() {
        assertEquals(6_100L, interpolateAgeTime(6_000L, 1.0f, false, 100L))
        assertEquals(6_500L, interpolateAgeTime(6_000L, 5.0f, false, 100L))
        assertEquals(6_020L, interpolateAgeTime(6_000L, 0.2f, false, 100L))
    }

    @Test
    fun `fixed clocks do not advance`() {
        assertEquals(18_000L, interpolateAgeTime(18_000L, 5.0f, true, 100L))
    }

    @Test
    fun `late packets cannot extrapolate backwards`() {
        assertEquals(12_000L, interpolateAgeTime(12_000L, 1.0f, false, -20L))
    }
}
