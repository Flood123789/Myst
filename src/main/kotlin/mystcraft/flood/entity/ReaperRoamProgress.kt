package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d

/**
 * Detects a genuinely stalled roaming Reaper from position progress. Surface crawlers touch
 * walls and ceilings as part of ordinary travel, so vanilla collision flags cannot distinguish
 * "following the surface" from "wedged in place" and caused a new heading every tick.
 */
class ReaperRoamProgress(
    private val minimumProgressSquared: Double = 0.0004,
    private val stalledTicksBeforeTurn: Int = 8
) {
    private var previousPosition: Vec3d? = null
    private var stalledTicks = 0

    fun reset() {
        previousPosition = null
        stalledTicks = 0
    }

    fun observe(position: Vec3d, requestedMove: Vec3d): Boolean {
        if (requestedMove.lengthSquared() < 1.0e-6) {
            previousPosition = position
            stalledTicks = 0
            return false
        }

        val previous = previousPosition
        previousPosition = position
        if (previous == null) return false

        stalledTicks = if (previous.squaredDistanceTo(position) < minimumProgressSquared) {
            stalledTicks + 1
        } else {
            0
        }
        return stalledTicks >= stalledTicksBeforeTurn
    }
}
