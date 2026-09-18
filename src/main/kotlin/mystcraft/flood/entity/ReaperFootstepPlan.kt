package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d

/**
 * A destination reserved by one Reaper foot while it waits for its tripod's turn.
 *
 * Foothold searches depend on an interpolated body position and measured render-frame velocity.
 * Replacing their result every frame makes a waiting foot aim at a moving point. This small latch
 * turns the first overdue, supported result into a plan: later samples cannot move it, and the rig
 * only releases it when the supporting block disappears or the step actually begins.
 */
class ReaperFootstepPlan {
    var target: Vec3d? = null
        private set

    /** Predicted rest point that made [target] a sensible terrain sample. */
    private var expectedLanding: Vec3d? = null

    /** Reserve [candidate] once this foot needs a step. An existing reservation always wins. */
    fun consider(
        foot: Vec3d,
        candidate: Vec3d?,
        footSupported: Boolean,
        threshold: Double,
        expected: Vec3d = candidate ?: foot
    ) {
        if (target != null || candidate == null) return
        if (!footSupported || foot.squaredDistanceTo(candidate) > threshold * threshold) {
            target = candidate
            expectedLanding = expected
        }
    }

    /**
     * True only when the creature's intended landing region moved materially after this plan was
     * made. Comparing intent rather than the raycast result preserves deliberate ledge/ring
     * fallbacks, which can legitimately sit well away from the ideal rest point.
     */
    fun isStale(expected: Vec3d, tolerance: Double): Boolean {
        if (target == null) return false
        val original = expectedLanding ?: return true
        return original.squaredDistanceTo(expected) > tolerance * tolerance
    }

    /** The terrain under a reserved point changed before the leg got its turn. */
    fun invalidate() {
        target = null
        expectedLanding = null
    }

    /** Transfer the reservation to the active swing, leaving this planner ready for the next one. */
    fun take(): Vec3d? {
        val result = target
        target = null
        expectedLanding = null
        return result
    }
}
