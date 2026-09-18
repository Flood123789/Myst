package mystcraft.flood.entity

/** Arrival hysteresis for the owner-bound Reaper spawned by Little Anomaly. */
object PettyReaperFollowLeash {
    const val START_FOLLOWING_DISTANCE_SQUARED = 6.0 * 6.0
    const val STOP_FOLLOWING_DISTANCE_SQUARED = 4.0 * 4.0

    fun shouldFollow(distanceSquared: Double, alreadyFollowing: Boolean): Boolean =
        distanceSquared > if (alreadyFollowing) {
            STOP_FOLLOWING_DISTANCE_SQUARED
        } else {
            START_FOLLOWING_DISTANCE_SQUARED
        }
}
