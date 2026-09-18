package mystcraft.flood.entity

import kotlin.math.max

object ReaperCompanionHealing {
    const val PASSIVE_DELAY_AFTER_DAMAGE_TICKS = 200
    const val PASSIVE_INTERVAL_TICKS = 100
    const val PASSIVE_AMOUNT = 1.0f
    private const val LIFE_STEAL_FRACTION = 0.45f

    fun lifeSteal(damageDealt: Float): Float = (damageDealt * LIFE_STEAL_FRACTION).coerceAtLeast(0.0f)

    fun amethystHeal(maxHealth: Float): Float = max(4.0f, maxHealth * 0.10f)
}
