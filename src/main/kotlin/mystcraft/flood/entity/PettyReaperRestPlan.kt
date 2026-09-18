package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d

enum class PettyReaperRestMode { FOLLOWING, SEEKING_SHELTER, DORMANT }

/** Idle shelter latch belonging only to the owner-bound Reaper spawned by Little Anomaly. */
class PettyReaperRestPlan {
    var mode = PettyReaperRestMode.FOLLOWING
        private set
    var stillTicks = 0
        private set
    private var lastOwnerPosition: Vec3d? = null

    fun observeOwner(ownerPosition: Vec3d) {
        if (mode != PettyReaperRestMode.FOLLOWING) return
        val previous = lastOwnerPosition
        lastOwnerPosition = ownerPosition
        if (previous != null && previous.squaredDistanceTo(ownerPosition) > MOTION_EPSILON_SQUARED) {
            stillTicks = 0
            return
        }
        stillTicks++
        if (stillTicks >= STILL_TICKS_BEFORE_SHELTER) mode = PettyReaperRestMode.SEEKING_SHELTER
    }

    fun reachedShelter() {
        if (mode == PettyReaperRestMode.SEEKING_SHELTER) mode = PettyReaperRestMode.DORMANT
    }

    fun wakeIfOwnerFar(ownerPosition: Vec3d, reaperPosition: Vec3d): Boolean {
        if (mode != PettyReaperRestMode.DORMANT ||
            ownerPosition.squaredDistanceTo(reaperPosition) <= WAKE_DISTANCE_SQUARED
        ) return false
        reset()
        return true
    }

    fun reset() {
        mode = PettyReaperRestMode.FOLLOWING
        stillTicks = 0
        lastOwnerPosition = null
    }

    fun restore(restored: PettyReaperRestMode) {
        mode = restored
        stillTicks = if (restored == PettyReaperRestMode.FOLLOWING) 0 else STILL_TICKS_BEFORE_SHELTER
        lastOwnerPosition = null
    }

    companion object {
        const val STILL_TICKS_BEFORE_SHELTER = 6 * 20
        const val WAKE_DISTANCE_SQUARED = 10.0 * 10.0
        const val SHELTER_ARRIVAL_RADIUS_SQUARED = 1.0
        private const val MOTION_EPSILON_SQUARED = 0.025 * 0.025
    }
}
