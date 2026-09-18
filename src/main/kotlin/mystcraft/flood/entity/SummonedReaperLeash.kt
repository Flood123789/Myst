package mystcraft.flood.entity

/** Hysteresis for lesser Reapers that a Greater called in with its screech. */
object SummonedReaperLeash {
    const val START_RETURNING_DISTANCE_SQUARED = 24.0 * 24.0
    const val STOP_RETURNING_DISTANCE_SQUARED = 18.0 * 18.0

    fun shouldReturn(distanceSquared: Double, alreadyReturning: Boolean): Boolean =
        distanceSquared > if (alreadyReturning) {
            STOP_RETURNING_DISTANCE_SQUARED
        } else {
            START_RETURNING_DISTANCE_SQUARED
        }
}
