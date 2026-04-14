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

    val ALL = listOf(
        HEX,
        WIRE_CELLS,
        SEPARATORS,
        CABLES,
        FRACTAL_CUBES,
        LIGHT_FISSURES,
        VIRUS
    )

    fun fromModifiers(modifiers: List<String>): String? = modifiers.firstOrNull { it in ALL }

    fun random(rand: Random): String = ALL.random(rand)
}
