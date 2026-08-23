package mystcraft.flood.compat

import java.util.Random

/** Loader-neutral season naming and deterministic initialization rules. */
object MystcraftSeasons {
    val subSeasonNames: List<String> = listOf(
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

    private val aliases = mapOf(
        "spring" to 0,
        "summer" to 3,
        "autumn" to 6,
        "fall" to 6,
        "winter" to 9
    )

    fun indexOf(name: String): Int? {
        val normalized = name.lowercase().replace('-', '_')
        return aliases[normalized] ?: subSeasonNames.indexOf(normalized).takeIf { it >= 0 }
    }

    fun nameOf(index: Int): String = subSeasonNames[Math.floorMod(index, subSeasonNames.size)]

    fun randomIndex(seed: Long, dimensionId: String): Int {
        val mixedSeed = seed xor (dimensionId.hashCode().toLong() * -7046029254386353131L)
        return Random(mixedSeed).nextInt(subSeasonNames.size)
    }
}
