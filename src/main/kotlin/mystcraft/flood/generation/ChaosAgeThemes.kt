package mystcraft.flood.generation

import kotlin.random.Random

object ChaosAgeThemes {
    const val SKY_RAINBOWS = "sky_rainbows"
    const val SKY_AURORAS = "sky_auroras"
    const val SHOOTING_STARS = "shooting_stars"
    const val COMETS = "comets"
    const val SKY_RIFTS = "sky_rifts"
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
}
