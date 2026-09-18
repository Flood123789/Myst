package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d

/**
 * Critically damped springs, used everywhere the Reaper's pose follows a moving target.
 *
 * The rig previously turned every orientation at a fixed maximum rate: full speed until the
 * target was reached, then a dead stop. That is a bang-bang controller, and its output is only
 * C0 continuous — there is a corner in the velocity at both ends of every correction, which is
 * exactly the "snaps into position" quality the creature had. A hand-keyframed animation eases
 * in and eases out instead, and a critically damped spring is the closed-loop equivalent: the
 * fastest response that never overshoots, with continuous velocity throughout.
 *
 * The integrator is the implicit (backward Euler) form rather than the explicit one. Explicit
 * integration of a stiff spring diverges once `omega * delta` approaches 1, and delta here is a
 * render-frame interval that can spike arbitrarily on a chunk load or a garbage collection. The
 * implicit form is unconditionally stable at any step size, so a frame hitch produces a large
 * smooth correction rather than an explosion.
 */
object ReaperSmoothing {

    /**
     * Converts an intuitive half-life into the spring's angular frequency.
     *
     * Authoring in half-lives keeps the tuning constants readable: "this axis catches up half of
     * its error every third of a tick" is a statement about feel, whereas a raw omega is not.
     */
    fun frequencyForHalfLife(halfLifeTicks: Double): Double =
        LN_2 / halfLifeTicks.coerceAtLeast(1.0e-4)

    /** One critically damped step. Returns the new position; [velocity] is updated in place. */
    fun step(current: Double, velocity: Motion, target: Double, omega: Double, delta: Double): Double {
        if (delta <= 0.0) return current

        val f = 1.0 + 2.0 * delta * omega
        val squared = omega * omega
        val scaled = delta * squared
        val doubleScaled = delta * scaled
        val inverseDeterminant = 1.0 / (f + doubleScaled)

        val position = (f * current + delta * velocity.rate + doubleScaled * target) * inverseDeterminant
        velocity.rate = (velocity.rate + scaled * (target - current)) * inverseDeterminant
        return position
    }

    /** Mutable scalar rate, so [step] can return the position without allocating a pair. */
    class Motion(var rate: Double = 0.0)

    /**
     * A unit direction that eases toward whatever it is pointed at.
     *
     * The spring runs on the remaining angle rather than on the vector components. Interpolating
     * components fails outright for opposed vectors — their midpoint is the origin — and a
     * clinging creature reaches that case in ordinary play by dropping from a floor onto the
     * underside of an overhang. Springing the angle and rotating along the geodesic is well
     * behaved for every pair of directions, including antipodal ones.
     */
    class DampedDirection(initial: Vec3d) {
        var value: Vec3d = normalise(initial)
            private set

        private val speed = Motion()

        /** Angular speed in radians per tick, exposed for tests and for blend decisions. */
        val angularSpeed: Double get() = -speed.rate

        /** Drops any stored momentum and jumps straight to [direction]. */
        fun reset(direction: Vec3d) {
            value = normalise(direction)
            speed.rate = 0.0
        }

        /**
         * Moves the stored direction without disturbing the spring's momentum.
         *
         * Rounding a corner rotates the plane this direction is confined to, which leaves the
         * stored value pointing slightly out of it. That has to be corrected before the next
         * step or the spring turns the body about an axis it is no longer on — but correcting it
         * with [reset] would throw the accumulated rate away every single frame and flatten the
         * spring back into the plain exponential filter it replaced.
         */
        fun reproject(direction: Vec3d) {
            value = normalise(direction)
        }

        fun advance(target: Vec3d, omega: Double, delta: Double): Vec3d {
            if (delta <= 0.0) return value
            val goal = normalise(target)
            val remaining = kotlin.math.acos(value.dotProduct(goal).coerceIn(-1.0, 1.0))
            if (remaining < ANGLE_EPSILON) {
                // Bleed the stored rate off rather than zeroing it, so a target that keeps
                // drifting is tracked without the spring repeatedly restarting from rest.
                speed.rate *= REST_DECAY
                value = goal
                return value
            }

            // The spring pulls the remaining angle to zero. Position is the error itself, so its
            // velocity is the negative of how fast the direction is turning toward the target.
            val settled = ReaperSmoothing.step(remaining, speed, 0.0, omega, delta)
            val travelled = (remaining - settled).coerceIn(0.0, remaining)
            value = SurfaceCling.rotateToward(value, goal, travelled)
            return value
        }

        private fun normalise(vector: Vec3d): Vec3d =
            if (vector.lengthSquared() > 1.0e-12) vector.normalize() else Vec3d(0.0, 1.0, 0.0)

        private companion object {
            const val ANGLE_EPSILON = 1.0e-5
            const val REST_DECAY = 0.5
        }
    }

    /**
     * A point that eases toward its target, one spring per axis.
     *
     * Kept componentwise rather than springing the distance along the line between the two, so a
     * target that changes direction is followed without the value having to slow to a stop and set
     * off again. That matters where this is used: filtering a camera anchor whose owner can and
     * does reverse mid-stride.
     */
    class DampedVector(initial: Vec3d) {
        var value: Vec3d = initial
            private set

        private val speedX = Motion()
        private val speedY = Motion()
        private val speedZ = Motion()

        fun reset(to: Vec3d) {
            value = to
            speedX.rate = 0.0
            speedY.rate = 0.0
            speedZ.rate = 0.0
        }

        fun advance(target: Vec3d, omega: Double, delta: Double): Vec3d {
            if (delta <= 0.0) return value
            value = Vec3d(
                ReaperSmoothing.step(value.x, speedX, target.x, omega, delta),
                ReaperSmoothing.step(value.y, speedY, target.y, omega, delta),
                ReaperSmoothing.step(value.z, speedZ, target.z, omega, delta)
            )
            return value
        }
    }

    /** A scalar that eases toward its target, for lengths such as the body's ride height. */
    class DampedScalar(initial: Double) {
        var value: Double = initial
            private set

        private val speed = Motion()

        fun reset(to: Double) {
            value = to
            speed.rate = 0.0
        }

        fun advance(target: Double, omega: Double, delta: Double): Double {
            if (delta <= 0.0) return value
            value = ReaperSmoothing.step(value, speed, target, omega, delta)
            return value
        }
    }

    private val LN_2 = Math.log(2.0)
}
