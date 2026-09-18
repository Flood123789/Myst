package mystcraft.flood.entity

/** Pure eligibility rule for binding a Greater Reaper's weakened lesser summon. */
object ReaperTaming {
    const val LESSER_MAX_HEALTH_FRACTION = 0.35f
    const val GREATER_MAX_HEALTH_FRACTION = 0.10f

    fun canBind(isLesser: Boolean, isPetty: Boolean, health: Float, maxHealth: Float): Boolean =
        !isPetty && maxHealth > 0.0f && health > 0.0f && health <= maxHealth *
            if (isLesser) LESSER_MAX_HEALTH_FRACTION else GREATER_MAX_HEALTH_FRACTION

    fun requiredEchoShards(isLesser: Boolean): Int = if (isLesser) 1 else 4
}
