package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pursuit speed curve, checked against the builds this pack actually produces. Origins,
 * RPG Origins, and LevelZ all move generic.movement_speed, so the spread is wide.
 */
class ReaperSpeedTest {

    private val base = ReaperSpeed.SPRINT_BLOCKS_PER_TICK
    private val fraction = 0.93
    private val cap = 1.6

    /** A player's sprint speed for a given movement-speed attribute value. */
    private fun sprintFor(attribute: Double) = ReaperSpeed.sprintSpeedFor(attribute)

    private fun chase(attribute: Double) =
        ReaperSpeed.pursuitSpeed(base, sprintFor(attribute), fraction, cap)

    @Test
    fun `a vanilla player is matched exactly, never outrun`() {
        assertEquals(base, chase(0.1), 1.0e-9)
    }

    @Test
    fun `a slow build is still chased at full vanilla sprint`() {
        // Floor applies: being slower must not make the Reaper politely slow down too.
        assertEquals(base, chase(0.07), 1.0e-9)
        assertEquals(base, chase(0.05), 1.0e-9)
    }

    @Test
    fun `a fast build genuinely pulls away, but has to work for it`() {
        val attribute = 0.13 // a +30% origin
        val playerSprint = sprintFor(attribute)
        val reaper = chase(attribute)

        assertTrue(reaper > base, "the Reaper should speed up to follow")
        assertTrue(reaper < playerSprint, "a fast build must be able to gain ground")

        // Roughly half a block per second of gain: meaningful over the 15 second search,
        // but only if the player commits to open ground.
        val gainPerSecond = (playerSprint - reaper) * 20.0
        assertTrue(gainPerSecond in 0.2..1.5, "gain was $gainPerSecond blocks/sec")
    }

    @Test
    fun `an outlier build cannot turn a Reaper into a blur`() {
        assertEquals(base * cap, chase(1.0), 1.0e-9)
        assertEquals(base * cap, chase(5.0), 1.0e-9)
    }

    @Test
    fun `the escape gap always widens with player speed`() {
        var previous = -1.0
        for (attribute in listOf(0.10, 0.12, 0.14, 0.16, 0.18)) {
            val gap = sprintFor(attribute) - chase(attribute)
            assertTrue(gap >= previous, "faster builds must never escape more slowly")
            previous = gap
        }
    }

    @Test
    fun `only pursuit and travel to last known position use sprint speed`() {
        assertEquals(base, ReaperSpeed.baselineSpeed(base, ReaperState.PURSUIT, false), 1.0e-9)
        assertEquals(base, ReaperSpeed.baselineSpeed(base, ReaperState.HUNTING, true), 1.0e-9)
        assertEquals(base * 0.5, ReaperSpeed.baselineSpeed(base, ReaperState.HUNTING, false), 1.0e-9)
        assertEquals(base * 0.5, ReaperSpeed.baselineSpeed(base, ReaperState.SETTLING, false), 1.0e-9)
        assertEquals(base * 0.5, ReaperSpeed.baselineSpeed(base, ReaperState.DORMANT, false), 1.0e-9)
    }

    @Test
    fun `greater Reaper accepts a wider last-known search area than a lesser`() {
        assertEquals(4.0, ReaperSpeed.searchArrivalRadiusSquared(0.6f), 1.0e-6)
        assertEquals(9.61, ReaperSpeed.searchArrivalRadiusSquared(1.55f), 1.0e-5)
    }
}
