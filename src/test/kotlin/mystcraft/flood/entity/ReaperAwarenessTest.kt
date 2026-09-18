package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaperAwarenessTest {

    private fun tick(awareness: ReaperAwareness, times: Int, canSee: Boolean = false) {
        repeat(times) { awareness.tick(canSee) }
    }

    @Test
    fun `starts dormant and stays dormant while nothing happens`() {
        val awareness = ReaperAwareness()
        tick(awareness, 500)
        assertEquals(ReaperState.DORMANT, awareness.state)
        assertFalse(awareness.state.isAlerted)
    }

    @Test
    fun `sight drives pursuit and noise drives a search`() {
        val sighted = ReaperAwareness()
        sighted.onSighted()
        assertEquals(ReaperState.PURSUIT, sighted.state)

        val heard = ReaperAwareness()
        heard.onDisturbed()
        assertEquals(ReaperState.HUNTING, heard.state)
    }

    @Test
    fun `first sight reported through tick exposes the alert edge`() {
        val awareness = ReaperAwareness()

        awareness.tick(canSeeTarget = true)

        assertEquals(ReaperState.PURSUIT, awareness.state)
        assertTrue(awareness.justAlerted, "the visual wake-up must be shareable with nearby Reapers")
        awareness.tick(canSeeTarget = true)
        assertFalse(awareness.justAlerted, "an ongoing chase must not repeatedly broadcast a first alert")
    }

    @Test
    fun `losing sight drops to the search rather than standing down`() {
        val awareness = ReaperAwareness()
        awareness.onSighted()
        awareness.tick(canSeeTarget = false)
        assertEquals(ReaperState.HUNTING, awareness.state)
    }

    /** The 15-second rule: the player has to stay hidden that long to break the search. */
    @Test
    fun `search lasts fifteen seconds of unbroken concealment`() {
        val awareness = ReaperAwareness()
        awareness.onSighted()

        tick(awareness, ReaperAwareness.SEARCH_TICKS - 1)
        assertEquals(ReaperState.HUNTING, awareness.state, "gave up early")

        awareness.tick(canSeeTarget = false)
        assertEquals(ReaperState.SETTLING, awareness.state)
    }

    @Test
    fun `being seen again part way through resets the whole search`() {
        val awareness = ReaperAwareness()
        awareness.onSighted()
        tick(awareness, ReaperAwareness.SEARCH_TICKS - 10)

        awareness.tick(canSeeTarget = true)
        assertEquals(ReaperState.PURSUIT, awareness.state)
        assertEquals(0, awareness.ticksSinceSeen)

        // The clock must start over, not resume where it left off.
        tick(awareness, ReaperAwareness.SEARCH_TICKS - 1)
        assertEquals(ReaperState.HUNTING, awareness.state)
    }

    /** Noise renews the hunt, which is what keeps a hiding player pinned down. */
    @Test
    fun `noise during the search restarts the countdown`() {
        val awareness = ReaperAwareness()
        awareness.onSighted()
        tick(awareness, ReaperAwareness.SEARCH_TICKS - 5)

        awareness.onDisturbed()
        assertEquals(0, awareness.ticksSinceSeen)

        tick(awareness, ReaperAwareness.SEARCH_TICKS - 1)
        assertEquals(ReaperState.HUNTING, awareness.state, "countdown did not restart")
    }

    @Test
    fun `settling lasts a further sixty seconds before going dormant`() {
        val awareness = ReaperAwareness()
        awareness.onSighted()
        tick(awareness, ReaperAwareness.SEARCH_TICKS)
        assertEquals(ReaperState.SETTLING, awareness.state)

        tick(awareness, ReaperAwareness.SETTLE_TICKS - 1)
        assertEquals(ReaperState.SETTLING, awareness.state, "went dormant early")

        awareness.tick(canSeeTarget = false)
        assertEquals(ReaperState.DORMANT, awareness.state)
    }

    @Test
    fun `a noise while settling puts it straight back on the hunt`() {
        val awareness = ReaperAwareness()
        awareness.onSighted()
        tick(awareness, ReaperAwareness.SEARCH_TICKS)
        assertEquals(ReaperState.SETTLING, awareness.state)

        awareness.onDisturbed()
        assertEquals(ReaperState.HUNTING, awareness.state)
        assertEquals(0, awareness.settleTicksRemaining)
    }

    /** The screech fires once on waking, not on every subsequent noise. */
    @Test
    fun `just alerted fires only on the transition out of dormancy`() {
        val awareness = ReaperAwareness()

        awareness.onDisturbed()
        assertTrue(awareness.justAlerted)

        awareness.onDisturbed()
        assertFalse(awareness.justAlerted, "re-alerted an already alert Reaper")

        awareness.onSighted()
        assertFalse(awareness.justAlerted, "escalating a search to a chase is not a fresh alert")
    }

    @Test
    fun `restore clamps values loaded from disk`() {
        val awareness = ReaperAwareness()
        awareness.restore(ReaperState.SETTLING, 999_999, 999_999)

        assertEquals(ReaperState.SETTLING, awareness.state)
        assertEquals(ReaperAwareness.SEARCH_TICKS, awareness.ticksSinceSeen)
        assertEquals(ReaperAwareness.SETTLE_TICKS, awareness.settleTicksRemaining)
    }

    @Test
    fun `a full encounter runs dormant to pursuit and back to dormant`() {
        val awareness = ReaperAwareness()
        assertEquals(ReaperState.DORMANT, awareness.state)

        awareness.onSighted()
        assertEquals(ReaperState.PURSUIT, awareness.state)

        tick(awareness, ReaperAwareness.SEARCH_TICKS)
        assertEquals(ReaperState.SETTLING, awareness.state)

        tick(awareness, ReaperAwareness.SETTLE_TICKS)
        assertEquals(ReaperState.DORMANT, awareness.state)
    }
}
