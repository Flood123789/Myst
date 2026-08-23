package mystcraft.flood.generation

import kotlin.random.Random

object ExoticAgeThemes {
    const val HEX = "exotic_hex"
    const val WIRE_CELLS = "exotic_wire_cells"
    const val SEPARATORS = "exotic_separators"
    const val CABLES = "exotic_cables"
    const val FRACTAL_CUBES = "exotic_fractal_cubes"
    const val LIGHT_FISSURES = "exotic_light_fissures"
    const val VIRUS = "exotic_virus"
    const val COLOSSAL_MUSHROOMS = "exotic_colossal_mushrooms"
    const val FLOATING_BOULDERS = "exotic_floating_boulders"
    const val BASALT_SPIRES = "exotic_basalt_spires"
    const val GLASS_DUNES = "exotic_glass_dunes"
    const val PETRIFIED_FORESTS = "exotic_petrified_forests"
    const val LUMINOUS_GROVES = "exotic_luminous_groves"
    const val CORAL_FIELDS = "exotic_coral_fields"
    const val ICE_NEEDLES = "exotic_ice_needles"
    const val OBSIDIAN_CRAGS = "exotic_obsidian_crags"
    const val RUNE_STONES = "exotic_rune_stones"

    val ALL = listOf(
        HEX,
        WIRE_CELLS,
        SEPARATORS,
        CABLES,
        FRACTAL_CUBES,
        LIGHT_FISSURES,
        VIRUS,
        COLOSSAL_MUSHROOMS,
        FLOATING_BOULDERS,
        BASALT_SPIRES,
        GLASS_DUNES,
        PETRIFIED_FORESTS,
        LUMINOUS_GROVES,
        CORAL_FIELDS,
        ICE_NEEDLES,
        OBSIDIAN_CRAGS,
        RUNE_STONES
    )

    fun fromModifiers(modifiers: List<String>): String? = modifiers.firstOrNull { it in ALL }

    fun random(rand: Random): String = ALL.random(rand)
}
