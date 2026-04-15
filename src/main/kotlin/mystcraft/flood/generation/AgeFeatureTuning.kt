package mystcraft.flood.generation

import mystcraft.flood.generation.profile.AgeProfile

object AgeFeatureTuning {
    private val FEATURE_PAGE_IDS = setOf(
        "giant_trees",
        "crystal_formations",
        "tendrils",
        "giant_obelisks",
        HistoricAgeThemes.ANCIENT_BONES,
        HistoricAgeThemes.FORGOTTEN_RUINS,
        HistoricAgeThemes.COLLAPSED_OBSERVATORY,
        HistoricAgeThemes.ANCIENT_AQUEDUCTS,
        HistoricAgeThemes.GATEWAY_RUINS,
        ExoticAgeThemes.HEX,
        ExoticAgeThemes.WIRE_CELLS,
        ExoticAgeThemes.SEPARATORS,
        ExoticAgeThemes.CABLES,
        ExoticAgeThemes.FRACTAL_CUBES,
        ExoticAgeThemes.LIGHT_FISSURES,
        ExoticAgeThemes.VIRUS,
        AmbientAgeThemes.PAGE_STORMS,
        AmbientAgeThemes.MEMORY_BLOOMS,
        AmbientAgeThemes.STABLE_SANCTUARIES
    )

    private fun extraFeatureCount(profile: AgeProfile, exempt: String? = null): Int =
        profile.modifiers.count { modifier ->
            modifier != "dense_ores" &&
                modifier != exempt &&
                modifier in FEATURE_PAGE_IDS
        }.coerceAtLeast(0)

    fun rarityRollDivisor(profile: AgeProfile, base: Int, exempt: String? = null): Int {
        val extra = extraFeatureCount(profile, exempt)
        return (base * (1.0f + extra * 0.30f)).toInt().coerceAtLeast(1)
    }

    fun chanceMultiplier(profile: AgeProfile, exempt: String? = null): Float {
        val extra = extraFeatureCount(profile, exempt)
        return (1.0f - extra * 0.10f).coerceAtLeast(0.35f)
    }
}
