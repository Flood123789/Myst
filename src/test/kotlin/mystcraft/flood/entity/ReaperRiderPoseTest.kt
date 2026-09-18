package mystcraft.flood.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pose is a shape rather than a set of numbers worth pinning exactly, so these check the
 * properties that make it read as riding: it leans forward, it leans further at speed, the legs
 * straddle rather than sit, and nothing is mirrored the wrong way round.
 */
class ReaperRiderPoseTest {

    private fun resting() = ReaperRiderPose.forSpeed(0.0, lesser = false)
    private fun sprinting() = ReaperRiderPose.forSpeed(0.245, lesser = false)

    @Test
    fun `the torso leans forward even at rest`() {
        // Positive body pitch is forward in Minecraft's model convention.
        assertTrue(resting().bodyPitch > 0.0f, "expected a forward lean, got ${resting().bodyPitch}")
    }

    @Test
    fun `the rider folds further down as the creature speeds up`() {
        assertTrue(
            sprinting().bodyPitch > resting().bodyPitch,
            "sprint lean ${sprinting().bodyPitch} should exceed rest lean ${resting().bodyPitch}"
        )
    }

    @Test
    fun `the lean is bounded rather than growing without limit`() {
        // Speed is clamped, so an implausible reading cannot fold the rider through their own legs.
        val absurd = ReaperRiderPose.forSpeed(50.0, lesser = false)
        assertEquals(sprinting().bodyPitch, absurd.bodyPitch, 1.0e-6f)
    }

    @Test
    fun `the neck and hips are closed in proportion to the lean`() {
        // Head and body are siblings in the biped model, so a leaning torso leaves a gap unless
        // the pivots move with it. Both must scale with the lean or the seam opens at speed.
        assertTrue(sprinting().headPivotY > resting().headPivotY)
        assertTrue(sprinting().bodyPivotY > resting().bodyPivotY)
        assertTrue(sprinting().armPivotY > resting().armPivotY)
    }

    @Test
    fun `the head drops further than the torso`() {
        // Vanilla's own sneak ratios: the head has further to travel than the chest does.
        assertTrue(resting().headPivotY > resting().bodyPivotY)
    }

    @Test
    fun `limbs reach forward rather than hanging`() {
        // Negative pitch swings a limb forward; both arms and legs must, or the rider dangles.
        assertTrue(resting().armPitch < 0.0f, "arms should reach forward")
        assertTrue(resting().legPitch < 0.0f, "legs should come forward around the body")
    }

    @Test
    fun `the hands follow the torso down`() {
        assertTrue(
            sprinting().armPitch < resting().armPitch,
            "arms should reach further as the rider folds"
        )
    }

    @Test
    fun `legs straddle rather than sit together`() {
        assertTrue(resting().legYaw > 0.0f, "legs need to open outward")
        assertTrue(resting().legRoll > 0.0f, "legs need to splay outward")
    }

    @Test
    fun `a smaller creature is straddled less widely`() {
        val onLesser = ReaperRiderPose.forSpeed(0.0, lesser = true)
        assertTrue(onLesser.legYaw < resting().legYaw)
        assertTrue(onLesser.legRoll < resting().legRoll)
        assertTrue(onLesser.legYaw > 0.0f, "still a straddle, only a narrower one")
    }

    @Test
    fun `the straddle does not change with speed`() {
        // Only the fold responds to pace; legs gripping the sides have nowhere else to be.
        assertEquals(resting().legYaw, sprinting().legYaw, 1.0e-6f)
        assertEquals(resting().legRoll, sprinting().legRoll, 1.0e-6f)
    }

    @Test
    fun `a negative speed reading is treated as stationary`() {
        assertEquals(resting().bodyPitch, ReaperRiderPose.forSpeed(-1.0, lesser = false).bodyPitch, 1.0e-6f)
    }
}
