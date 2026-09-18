package mystcraft.flood.entity

/**
 * Pursuit speed arithmetic for the Paradox Reaper.
 *
 * Kept in its own object with no entity types, because merely referencing a class that extends
 * MobEntity drags the Minecraft entity hierarchy into the classloader, which throws VerifyError
 * outside a bootstrapped game. Isolating the numbers here is what lets the balance curve be unit
 * tested at all.
 */
object ReaperSpeed {

    /**
     * A sprinting player covers 5.612 blocks/second, which is 0.2806 blocks/tick. The Reaper's
     * baseline matches that exactly, so it can never simply be outrun on open ground.
     */
    const val SPRINT_BLOCKS_PER_TICK = 0.2806

    /** Searching and roosting should read as deliberate mob movement, not a permanent chase. */
    const val ROAMING_SPEED_FRACTION = 0.50

    /**
     * A last-known block is a search centre, not an exact navigation point. Greater Reapers need
     * a wider acceptance range so their 2x2 body does not circle a point beside or inside a wall.
     */
    fun searchArrivalRadiusSquared(entityWidth: Float): Double {
        val radius = maxOf(MIN_SEARCH_ARRIVAL_RADIUS, entityWidth * SEARCH_RADIUS_PER_WIDTH)
        return radius * radius
    }

    /** Vanilla generic.movement_speed for a player: the baseline ratios are taken against this. */
    const val VANILLA_WALK_SPEED = 0.1

    /** A player's sprint speed in blocks/tick, given their movement-speed attribute. */
    fun sprintSpeedFor(movementSpeedAttribute: Double): Double =
        SPRINT_BLOCKS_PER_TICK * (movementSpeedAttribute / VANILLA_WALK_SPEED)

    /**
     * Speed to chase at: never slower than [base], never more than [maxMultiplier] times it, and
     * otherwise a fraction of the quarry's own sprint.
     *
     * The fraction sits just under 1 deliberately. Matching a target exactly reads as
     * rubber-banding and makes investing in movement speed worthless; ignoring their speed
     * entirely lets a fast build walk away from the one mob whose whole identity is that it
     * cannot be outrun. Just under 1 means a fast player gains ground, but only on sustained open
     * ground, and only while the Reaper is taking walls and ceilings they have to run around.
     */
    fun pursuitSpeed(base: Double, targetSprint: Double, fraction: Double, maxMultiplier: Double): Double =
        (targetSprint * fraction).coerceIn(base, base * maxMultiplier)

    /**
     * Full speed is reserved for a chase or the committed run to a last-known position. Once the
     * Reaper reaches that position, its search and eventual roost-finding use an ordinary walk.
     */
    fun baselineSpeed(baseSprint: Double, state: ReaperState, runningToLastKnown: Boolean): Double =
        when {
            state == ReaperState.PURSUIT -> baseSprint
            state == ReaperState.HUNTING && runningToLastKnown -> baseSprint
            else -> baseSprint * ROAMING_SPEED_FRACTION
        }

    private const val MIN_SEARCH_ARRIVAL_RADIUS = 2.0
    private const val SEARCH_RADIUS_PER_WIDTH = 2.0
}
