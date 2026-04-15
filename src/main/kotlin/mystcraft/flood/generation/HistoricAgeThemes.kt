package mystcraft.flood.generation

object HistoricAgeThemes {
    const val ANCIENT_BONES = "ancient_bones"
    const val FORGOTTEN_RUINS = "forgotten_ruins"
    const val COLLAPSED_OBSERVATORY = "collapsed_observatory"
    const val ANCIENT_AQUEDUCTS = "ancient_aqueducts"
    const val GATEWAY_RUINS = "gateway_ruins"

    val ALL = listOf(
        ANCIENT_BONES,
        FORGOTTEN_RUINS,
        COLLAPSED_OBSERVATORY,
        ANCIENT_AQUEDUCTS,
        GATEWAY_RUINS
    )

    fun containedIn(modifiers: List<String>): List<String> = modifiers.filter { it in ALL }
}
