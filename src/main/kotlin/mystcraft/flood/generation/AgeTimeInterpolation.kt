package mystcraft.flood.generation

import kotlin.math.floor

/** Pure client-side prediction math for a dimension-scoped Age clock. */
fun interpolateAgeTime(
    synchronizedTime: Long,
    timeScale: Float,
    frozen: Boolean,
    elapsedClientTicks: Long
): Long {
    if (frozen) return synchronizedTime

    val elapsed = elapsedClientTicks.coerceAtLeast(0L)
    val scale = timeScale.coerceAtLeast(0.0f)
    return synchronizedTime + floor(elapsed.toDouble() * scale.toDouble()).toLong()
}
