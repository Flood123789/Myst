package mystcraft.flood.generation

import kotlin.random.Random

/** Pages whose result is visible in generated blocks rather than only in the renderer. */
object PhysicalAgeThemes {
    val CORE = listOf(
        "dense_ores",
        "giant_trees",
        "crystal_formations",
        "tendrils",
        "giant_obelisks"
    )

    val SKY_STRUCTURES = listOf(ChaosAgeThemes.METEOR_SHOWERS, ChaosAgeThemes.SKY_SPHERES)

    val ALL = (CORE + HistoricAgeThemes.ALL + ExoticAgeThemes.ALL + AmbientAgeThemes.ALL + SKY_STRUCTURES).distinct()

    private val FAMILIES = listOf(CORE, HistoricAgeThemes.ALL, ExoticAgeThemes.ALL, AmbientAgeThemes.ALL, SKY_STRUCTURES)

    /** Selects across families first, preventing four differently named variants of one system. */
    fun selectDistinctFamilies(rand: Random, count: Int, existing: Collection<String> = emptyList()): List<String> {
        val selected = mutableListOf<String>()
        val families = FAMILIES
            .filter { family -> family.none(existing::contains) }
            .shuffled(rand)
            .toMutableList()
        while (selected.size < count && families.isNotEmpty()) {
            val family = families.removeAt(0).filterNot { it in existing || it in selected }
            if (family.isNotEmpty()) selected += family.random(rand)
        }
        return selected
    }
}
