package mystcraft.flood.compat

import kotlin.math.floor

data class SeasonClockAdvance(
    val cycleTicks: Int,
    val accumulator: Float
)

val MYSTCRAFT_SEASON_NAMES: List<String> = listOf(
    "spring",
    "summer",
    "autumn",
    "winter",
    "early_spring",
    "mid_spring",
    "late_spring",
    "early_summer",
    "mid_summer",
    "late_summer",
    "early_autumn",
    "mid_autumn",
    "late_autumn",
    "early_winter",
    "mid_winter",
    "late_winter"
)

fun seasonOrdinal(name: String): Int? = when (name.lowercase()) {
    "spring", "early_spring" -> 0
    "mid_spring" -> 1
    "late_spring" -> 2
    "summer", "early_summer" -> 3
    "mid_summer" -> 4
    "late_summer" -> 5
    "autumn", "early_autumn" -> 6
    "mid_autumn" -> 7
    "late_autumn" -> 8
    "winter", "early_winter" -> 9
    "mid_winter" -> 10
    "late_winter" -> 11
    else -> null
}

fun seasonStartTicks(name: String, subSeasonDuration: Int): Int? {
    if (subSeasonDuration <= 0) return null
    return seasonOrdinal(name)?.times(subSeasonDuration)
}

fun advanceSeasonClock(
    cycleTicks: Int,
    accumulator: Float,
    timeScale: Float,
    frozen: Boolean,
    cycleDuration: Int
): SeasonClockAdvance {
    require(cycleDuration > 0) { "Season cycle duration must be positive" }
    if (frozen || timeScale <= 0f) {
        return SeasonClockAdvance(Math.floorMod(cycleTicks, cycleDuration), accumulator.coerceAtLeast(0f))
    }

    val accumulated = accumulator.coerceAtLeast(0f) + timeScale
    val wholeTicks = floor(accumulated.toDouble()).toLong()
    val normalizedTicks = Math.floorMod(cycleTicks.toLong() + wholeTicks, cycleDuration.toLong()).toInt()
    return SeasonClockAdvance(normalizedTicks, (accumulated - wholeTicks.toFloat()).coerceAtLeast(0f))
}
