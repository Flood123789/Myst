package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.acos

class ReaperSmoothingTest {

    private fun angleBetween(a: Vec3d, b: Vec3d): Double =
        acos(a.normalize().dotProduct(b.normalize()).coerceIn(-1.0, 1.0))

    @Test
    fun `scalar spring converges on its target`() {
        val value = ReaperSmoothing.DampedScalar(0.0)
        val omega = ReaperSmoothing.frequencyForHalfLife(2.0)
        repeat(200) { value.advance(1.0, omega, 1.0) }
        assertTrue(abs(value.value - 1.0) < 1.0e-3, "settled at ${value.value}")
    }

    @Test
    fun `critically damped scalar never overshoots`() {
        val value = ReaperSmoothing.DampedScalar(0.0)
        val omega = ReaperSmoothing.frequencyForHalfLife(1.5)
        repeat(400) {
            value.advance(1.0, omega, 0.25)
            assertTrue(value.value <= 1.0 + 1.0e-9, "overshot to ${value.value}")
        }
    }

    @Test
    fun `spring eases in rather than starting at full speed`() {
        // The whole point of replacing the fixed-rate turn: the first step of a correction has to
        // be small, or the motion begins with the same visible jerk the rate limiter produced.
        val eased = ReaperSmoothing.DampedScalar(0.0)
        val omega = ReaperSmoothing.frequencyForHalfLife(2.0)
        val first = eased.advance(1.0, omega, 1.0)
        val second = eased.advance(1.0, omega, 1.0) - first
        assertTrue(second > first, "expected acceleration, got $first then $second")
    }

    @Test
    fun `direction spring reaches its target and stops there`() {
        val direction = ReaperSmoothing.DampedDirection(Vec3d(0.0, 1.0, 0.0))
        val omega = ReaperSmoothing.frequencyForHalfLife(1.6)
        val target = Vec3d(1.0, 0.0, 0.0)
        repeat(200) { direction.advance(target, omega, 1.0) }
        assertTrue(angleBetween(direction.value, target) < 1.0e-3)
    }

    @Test
    fun `direction spring handles an exactly opposed target`() {
        // Floor to ceiling with no wall between is reachable in ordinary play, and a component
        // interpolation would be stuck at the start point forever.
        val direction = ReaperSmoothing.DampedDirection(Vec3d(0.0, 1.0, 0.0))
        val omega = ReaperSmoothing.frequencyForHalfLife(1.6)
        val target = Vec3d(0.0, -1.0, 0.0)
        repeat(300) { direction.advance(target, omega, 1.0) }
        assertTrue(angleBetween(direction.value, target) < 1.0e-2, "stalled at ${direction.value}")
    }

    @Test
    fun `direction spring never rotates past its target`() {
        val direction = ReaperSmoothing.DampedDirection(Vec3d(0.0, 1.0, 0.0))
        val omega = ReaperSmoothing.frequencyForHalfLife(1.2)
        val target = Vec3d(1.0, 0.0, 0.0)
        var previous = angleBetween(direction.value, target)
        repeat(300) {
            direction.advance(target, omega, 0.5)
            val remaining = angleBetween(direction.value, target)
            assertTrue(remaining <= previous + 1.0e-9, "angle grew from $previous to $remaining")
            previous = remaining
        }
    }

    @Test
    fun `a large frame gap stays stable instead of exploding`() {
        // Implicit integration is chosen precisely so a chunk-load hitch cannot diverge.
        val value = ReaperSmoothing.DampedScalar(0.0)
        val omega = ReaperSmoothing.frequencyForHalfLife(0.25)
        repeat(20) { value.advance(1.0, omega, 4.0) }
        assertTrue(value.value.isFinite() && value.value <= 1.0 + 1.0e-9, "diverged to ${value.value}")
    }

    @Test
    fun `reproject moves the value without discarding momentum`() {
        val direction = ReaperSmoothing.DampedDirection(Vec3d(0.0, 0.0, 1.0))
        val omega = ReaperSmoothing.frequencyForHalfLife(2.0)
        repeat(4) { direction.advance(Vec3d(1.0, 0.0, 0.0), omega, 1.0) }
        val moving = direction.angularSpeed
        assertTrue(moving > 0.0, "expected stored angular speed, got $moving")

        direction.reproject(direction.value)
        assertEquals(moving, direction.angularSpeed, 1.0e-12)

        direction.reset(direction.value)
        assertEquals(0.0, direction.angularSpeed, 1.0e-12)
    }

    @Test
    fun `vector spring converges without overshooting any axis`() {
        val point = ReaperSmoothing.DampedVector(Vec3d.ZERO)
        val omega = ReaperSmoothing.frequencyForHalfLife(2.8)
        val target = Vec3d(1.0, -2.0, 0.5)
        repeat(400) {
            val current = point.advance(target, omega, 0.5)
            assertTrue(current.x <= target.x + 1.0e-9, "x overshot to ${current.x}")
            assertTrue(current.y >= target.y - 1.0e-9, "y overshot to ${current.y}")
            assertTrue(current.z <= target.z + 1.0e-9, "z overshot to ${current.z}")
        }
        assertTrue(point.value.squaredDistanceTo(target) < 1.0e-6, "settled at ${point.value}")
    }

    @Test
    fun `vector spring follows a target that reverses`() {
        // The camera anchor this filters belongs to something that can turn around mid-stride, so
        // a formulation that has to slow to a stop before setting off again would lag visibly.
        val point = ReaperSmoothing.DampedVector(Vec3d.ZERO)
        val omega = ReaperSmoothing.frequencyForHalfLife(1.5)
        repeat(20) { point.advance(Vec3d(1.0, 0.0, 0.0), omega, 1.0) }
        repeat(60) { point.advance(Vec3d(-1.0, 0.0, 0.0), omega, 1.0) }
        assertTrue(point.value.x < -0.99, "failed to reverse, reached ${point.value.x}")
    }

    @Test
    fun `vector spring reset drops momentum`() {
        val point = ReaperSmoothing.DampedVector(Vec3d.ZERO)
        val omega = ReaperSmoothing.frequencyForHalfLife(2.0)
        repeat(5) { point.advance(Vec3d(5.0, 0.0, 0.0), omega, 1.0) }
        point.reset(Vec3d.ZERO)
        assertEquals(0.0, point.advance(Vec3d.ZERO, omega, 1.0).x, 1.0e-12)
    }

    @Test
    fun `zero and negative deltas are inert`() {
        val value = ReaperSmoothing.DampedScalar(0.5)
        val omega = ReaperSmoothing.frequencyForHalfLife(1.0)
        assertEquals(0.5, value.advance(1.0, omega, 0.0), 1.0e-12)
        assertEquals(0.5, value.advance(1.0, omega, -1.0), 1.0e-12)
    }
}
