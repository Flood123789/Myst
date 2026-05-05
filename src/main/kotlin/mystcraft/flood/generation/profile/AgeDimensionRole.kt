package mystcraft.flood.generation.profile

enum class AgeDimensionRole(
    val suffix: String,
    val label: String,
    val dimensionTypePath: String
) {
    OVERWORLD("", "Age", "base_age"),
    NETHER("_nether", "Nether", "age_nether"),
    END("_end", "End", "age_end")
}
