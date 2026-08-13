package mystcraft.flood.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeasonClockTest {
    @Test
    fun `frozen and zero scale calendars do not move`() {
        assertEquals(
            SeasonClockAdvance(1234, 0.4f),
            advanceSeasonClock(1234, 0.4f, 5.0f, true, 10_000)
        )
        assertEquals(
            SeasonClockAdvance(1234, 0.4f),
            advanceSeasonClock(1234, 0.4f, 0.0f, false, 10_000)
        )
    }

    @Test
    fun `calendar advances at its dimension time scale`() {
        assertEquals(
            SeasonClockAdvance(105, 0.0f),
            advanceSeasonClock(100, 0.0f, 5.0f, false, 10_000)
        )

        var slow = SeasonClockAdvance(100, 0.0f)
        repeat(5) {
            slow = advanceSeasonClock(slow.cycleTicks, slow.accumulator, 0.2f, false, 10_000)
        }
        assertEquals(101, slow.cycleTicks)
        assertEquals(0.0f, slow.accumulator, 0.0001f)
    }

    @Test
    fun `calendar wraps at the end of the configured cycle`() {
        assertEquals(
            SeasonClockAdvance(3, 0.0f),
            advanceSeasonClock(9_998, 0.0f, 5.0f, false, 10_000)
        )
    }

    @Test
    fun `season names map to Serene Seasons subseasons`() {
        assertEquals(0, seasonStartTicks("spring", 100))
        assertEquals(400, seasonStartTicks("mid_summer", 100))
        assertEquals(900, seasonStartTicks("winter", 100))
        assertEquals(1_100, seasonStartTicks("late_winter", 100))
        assertNull(seasonStartTicks("monsoon", 100))
    }
}
