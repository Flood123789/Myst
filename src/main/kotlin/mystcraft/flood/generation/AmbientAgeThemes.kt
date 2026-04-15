package mystcraft.flood.generation

object AmbientAgeThemes {
    const val PAGE_STORMS = "page_storms"
    const val MEMORY_BLOOMS = "memory_blooms"
    const val STABLE_SANCTUARIES = "stable_sanctuaries"

    val ALL = listOf(
        PAGE_STORMS,
        MEMORY_BLOOMS,
        STABLE_SANCTUARIES
    )

    fun containedIn(modifiers: List<String>): List<String> = modifiers.filter { it in ALL }
}
