package mystcraft.flood.generation.instability

import mystcraft.flood.config.MystcraftConfig

/**
 * Runtime hazard thresholds calibrated against generated Age profiles.
 *
 * Low non-zero scores still describe an imperfect Age, but they no longer need
 * to produce direct player hazards. This lets cleansing make an Age livable
 * before the much harder task of reducing its instability all the way to zero.
 */
object InstabilityThresholds {
    /** An Age can sustain its own Nether and End once it reaches this score. */
    val OVERWORLD_FUNCTIONS: Int get() = MystcraftConfig.current.instability.overworldFunctionsMaxScore

    val MILD_DECAY: Int get() = MystcraftConfig.current.instability.mildThreshold
    val MODERATE_DECAY: Int get() = MystcraftConfig.current.instability.moderateThreshold
    val SEVERE_DECAY: Int get() = MystcraftConfig.current.instability.severeThreshold
    val WORLD_EATER: Int get() = MystcraftConfig.current.instability.worldEaterThreshold
    val CRITICAL_COLLAPSE: Int get() = MystcraftConfig.current.instability.criticalThreshold

    fun tierFor(score: Int): Int = when {
        score >= CRITICAL_COLLAPSE -> 5
        score >= WORLD_EATER -> 4
        score >= SEVERE_DECAY -> 3
        score >= MODERATE_DECAY -> 2
        score >= MILD_DECAY -> 1
        else -> 0
    }
}
