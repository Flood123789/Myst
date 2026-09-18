package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * The contact weight is what removes the twice-per-cycle jolt from the body's posture, so what
 * matters about it is continuity, not any particular value: it must leave and return to full
 * contact smoothly and never jump.
 */
class ReaperFootstepContactTest {

    @Test
    fun `a planted foot carries the whole weight`() {
        assertEquals(1.0, ReaperFootstepMotion.contactWeight(1.0), 1.0e-12)
        assertEquals(1.0, ReaperFootstepMotion.contactWeight(1.5), 1.0e-12)
    }

    @Test
    fun `contact is full at lift-off and again at touch-down`() {
        assertEquals(1.0, ReaperFootstepMotion.contactWeight(0.0), 1.0e-12)
        assertTrue(ReaperFootstepMotion.contactWeight(0.999) > 0.99)
    }

    @Test
    fun `a foot at full clearance carries nothing`() {
        assertEquals(0.0, ReaperFootstepMotion.contactWeight(0.5), 1.0e-12)
    }

    @Test
    fun `contact never jumps across the swing`() {
        // Any step in this curve is a step in the fitted body plane, which is exactly the pop the
        // weighting exists to remove. Sampled far finer than a frame ever lands.
        val steps = 4000
        var previous = ReaperFootstepMotion.contactWeight(0.0)
        var largest = 0.0
        for (index in 1..steps) {
            val current = ReaperFootstepMotion.contactWeight(index.toDouble() / steps)
            largest = maxOf(largest, abs(current - previous))
            previous = current
        }
        assertTrue(largest < 0.01, "largest single-sample change was $largest")
    }

    @Test
    fun `contact and clearance always account for exactly one foot`() {
        for (index in 0..100) {
            val t = index / 100.0
            val total = ReaperFootstepMotion.contactWeight(t) + ReaperFootstepMotion.clearanceFraction(t)
            assertEquals(1.0, total, 1.0e-12)
        }
    }

    @Test
    fun `progress outside the swing is clamped`() {
        assertEquals(1.0, ReaperFootstepMotion.contactWeight(-1.0), 1.0e-12)
        assertEquals(1.0, ReaperFootstepMotion.contactWeight(2.0), 1.0e-12)
    }
}
