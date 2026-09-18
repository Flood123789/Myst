package mystcraft.flood.entity

/**
 * Pure ranking for settling destinations.
 *
 * The order of preference is darkness first and everything else afterwards. That is a stronger
 * statement than it sounds: a dark corner behind a desk beats a ceiling next to glowstone, and it
 * has to beat it outright rather than on points, because the alternative is a creature that
 * drifts toward the brightest high ground in the room and roosts in plain sight. Height is a
 * tie-breaker among places that are already dark, not a rival consideration.
 *
 * Enclosure is the other half of it. Solved limbs let the creature fold into whatever it can fit
 * inside, so the places it likes are the ones with geometry close on several sides — a corner, an
 * alcove, the gap behind something — rather than the open middle of a flat ceiling.
 */
object ReaperRoostScoring {

    /**
     * @param lightLevel 0 to 15 at the candidate cell.
     * @param heightDelta blocks above the creature's current position, negative for below.
     * @param openSky whether the cell can see the sky, which disqualifies it in daylight.
     * @param enclosingFaces how many of the six neighbouring cells are solid, 0 to 6.
     */
    fun score(
        lightLevel: Int,
        heightDelta: Int,
        openSky: Boolean,
        enclosingFaces: Int = 1
    ): Double {
        val darkness = 15 - lightLevel.coerceIn(0, 15)
        val height = heightDelta.coerceIn(-16, 16)
        val enclosure = enclosingFaces.coerceIn(0, 6)

        // Darkness is scaled so that one level of it outweighs the entire span of every other
        // term combined. Anything less and a bright cell high enough, or tucked away enough, can
        // still win, which is precisely the outcome this ordering exists to forbid.
        return darkness * DARKNESS_WEIGHT +
            enclosure * ENCLOSURE_WEIGHT +
            height * HEIGHT_WEIGHT -
            if (openSky) OPEN_SKY_PENALTY else 0.0
    }

    fun arrivalRadiusSquared(entityWidth: Float): Double {
        val radius = maxOf(MIN_ARRIVAL_RADIUS, entityWidth * ARRIVAL_RADIUS_PER_WIDTH)
        return radius * radius
    }

    /**
     * True when a candidate is dark enough to be worth settling in at all.
     *
     * Below this a Reaper keeps looking rather than committing, which is what stops one giving up
     * and roosting under a torch merely because it sampled nothing better this second.
     */
    fun isDarkEnough(lightLevel: Int): Boolean = lightLevel <= ROOST_LIGHT_CEILING

    /** Light level at or below which a spot counts as properly dark. */
    const val ROOST_LIGHT_CEILING = 7

    // One light level has to be worth more than everything else put together, or the ordering is
    // a points system after all. The other terms span 36 for enclosure, 32 across the full height
    // range, and 24 for open sky: 92 in total, so a single level is priced well above that.
    private const val DARKNESS_WEIGHT = 128.0
    private const val ENCLOSURE_WEIGHT = 6.0
    private const val HEIGHT_WEIGHT = 1.0
    private const val OPEN_SKY_PENALTY = 24.0
    private const val MIN_ARRIVAL_RADIUS = 2.0
    private const val ARRIVAL_RADIUS_PER_WIDTH = 1.75
}
