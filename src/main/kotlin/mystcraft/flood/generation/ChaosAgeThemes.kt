package mystcraft.flood.generation

import kotlin.random.Random

object ChaosAgeThemes {
    const val SKY_RAINBOWS = "sky_rainbows"
    const val SKY_AURORAS = "sky_auroras"
    const val SHOOTING_STARS = "shooting_stars"
    const val COMETS = "comets"
    const val SKY_RIFTS = "sky_rifts"
    const val SKY_NEBULAE = "sky_nebulae"
    const val ECLIPSE_HALOS = "eclipse_halos"
    const val STAR_GLYPHS = "star_glyphs"
    const val HORIZON_MIRAGES = "horizon_mirages"
    const val CRYSTAL_HALOS = "crystal_halos"
    const val VOID_FLECKS = "void_flecks"
    const val SPIRAL_GALAXIES = "spiral_galaxies"
    const val FALLING_SKY_SHARDS = "falling_sky_shards"
    const val LIGHTNING_VEINS = "lightning_veins"
    const val LUMINOUS_COLUMNS = "luminous_columns"
    const val SKY_MONOLITHS = "sky_monoliths"
    const val PRISM_RINGS = "prism_rings"
    const val CHROMA_WAVES = "chroma_waves"
    const val ORBITAL_GRID = "orbital_grid"
    const val SKY_LANTERNS = "sky_lanterns"
    const val FRACTURE_WEB = "fracture_web"
    const val DREAM_VEILS = "dream_veils"
    const val SKY_BUBBLES = "sky_bubbles"
    const val STARFALL_BLOOMS = "starfall_blooms"
    const val HORIZON_CROWNS = "horizon_crowns"
    const val CELESTIAL_SCRIPT = "celestial_script"
    const val GLASS_CONSTELLATIONS = "glass_constellations"
    const val RADIANT_WHIRLPOOLS = "radiant_whirlpools"
    const val BRIGHT_SKY = "bright_sky"
    const val DARK_SKY = "dark_sky"
    const val METEOR_SHOWERS = "meteor_showers"
    const val SKY_SPHERES = "sky_spheres"
    const val PARTICLE_MOTES = "particle_motes"
    const val PARTICLE_ASH = "particle_ash"
    const val PARTICLE_SPORES = "particle_spores"
    const val PARTICLE_VOID = "particle_void"

    val SKY = listOf(
        SKY_RAINBOWS,
        SKY_AURORAS,
        SHOOTING_STARS,
        COMETS,
        SKY_RIFTS,
        SKY_NEBULAE,
        ECLIPSE_HALOS,
        STAR_GLYPHS,
        HORIZON_MIRAGES,
        CRYSTAL_HALOS,
        VOID_FLECKS,
        SPIRAL_GALAXIES,
        FALLING_SKY_SHARDS,
        LIGHTNING_VEINS,
        LUMINOUS_COLUMNS,
        SKY_MONOLITHS,
        PRISM_RINGS,
        CHROMA_WAVES,
        ORBITAL_GRID,
        SKY_LANTERNS,
        FRACTURE_WEB,
        DREAM_VEILS,
        SKY_BUBBLES,
        STARFALL_BLOOMS,
        HORIZON_CROWNS,
        CELESTIAL_SCRIPT,
        GLASS_CONSTELLATIONS,
        RADIANT_WHIRLPOOLS,
        BRIGHT_SKY,
        DARK_SKY
    )

    val TERRAIN = listOf(
        METEOR_SHOWERS,
        SKY_SPHERES
    )

    val PARTICLES = listOf(
        PARTICLE_MOTES,
        PARTICLE_ASH,
        PARTICLE_SPORES,
        PARTICLE_VOID
    )

    val ALL = SKY + TERRAIN + PARTICLES

    fun randomSky(rand: Random): String = SKY.random(rand)
    fun randomTerrain(rand: Random): String = TERRAIN.random(rand)
    fun randomParticles(rand: Random): String = PARTICLES.random(rand)
    fun random(rand: Random): String = ALL.random(rand)

    fun isSkyPage(symbol: String): Boolean = symbol in SKY
}
