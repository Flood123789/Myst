package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d

/** Continuous three-phase trajectory for one procedural Reaper footstep. */
object ReaperFootstepMotion {

    /**
     * Lift at the old plant, travel at full clearance, then lower onto the new plant.
     *
     * Using one lerp plus a sine arc moves sideways before the foot has visibly broken contact;
     * over a short insect step that reads as a teleport. Each phase here uses quintic easing, so
     * position, velocity, and acceleration remain continuous at lift-off, phase changes, and
     * touch-down.
     */
    fun position(
        from: Vec3d,
        to: Vec3d,
        up: Vec3d,
        progress: Double,
        clearance: Double
    ): Vec3d {
        val t = progress.coerceIn(0.0, 1.0)
        return when {
            t <= LIFT_END -> {
                val lift = smooth(t / LIFT_END)
                from.add(up.multiply(clearance * lift))
            }

            t < LOWER_START -> {
                val travel = smooth((t - LIFT_END) / (LOWER_START - LIFT_END))
                from.lerp(to, travel).add(up.multiply(clearance))
            }

            else -> {
                val lower = smooth((t - LOWER_START) / (1.0 - LOWER_START))
                to.add(up.multiply(clearance * (1.0 - lower)))
            }
        }
    }

    /**
     * How far off the surface this foot is riding, as a fraction of full clearance.
     *
     * Everything the body's posture is fitted from — its tilt, its ride height, its heading — is
     * derived from where the feet are. Deciding which feet count with a boolean makes that set
     * change all at once the instant a tripod leaves the ground, so the fitted plane jumps, and
     * the body visibly ticks over twice per gait cycle no matter how smoothly the filter behind
     * it is run. Weighting each foot by how much of its weight it is actually still carrying
     * turns that discontinuity into a crossfade, which is what stops the body snapping between
     * stances.
     */
    fun clearanceFraction(progress: Double): Double {
        val t = progress.coerceIn(0.0, 1.0)
        return when {
            t >= 1.0 -> 0.0
            t <= LIFT_END -> smooth(t / LIFT_END)
            t < LOWER_START -> 1.0
            else -> 1.0 - smooth((t - LOWER_START) / (1.0 - LOWER_START))
        }
    }

    /** The share of the body this foot still supports: 1 while planted, 0 at full clearance. */
    fun contactWeight(progress: Double): Double = 1.0 - clearanceFraction(progress)

    /** Quintic smoothstep with zero first and second derivatives at both ends. */
    private fun smooth(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0)
    }

    private const val LIFT_END = 0.28
    private const val LOWER_START = 0.72
}
