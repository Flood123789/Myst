package mystcraft.flood.generation

object HistoricAgeThemes {
    const val ANCIENT_BONES = "ancient_bones"
    const val FORGOTTEN_RUINS = "forgotten_ruins"

    val ALL = listOf(
        ANCIENT_BONES,
        FORGOTTEN_RUINS
    )

    fun containedIn(modifiers: List<String>): List<String> = modifiers.filter { it in ALL }
}
