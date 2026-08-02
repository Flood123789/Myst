package mystcraft.flood.generation

import mystcraft.flood.config.MystcraftConfig
import mystcraft.flood.generation.profile.AgeProfile
import net.minecraft.util.math.ChunkPos
import kotlin.math.roundToInt

object AgeFeatureTuning {
    const val ABANDONED_ARCHIVE = "abandoned_archive"
    const val STAR_FISSURE = "star_fissure"

    private val FEATURE_PAGE_IDS = setOf(
        "giant_trees",
        "crystal_formations",
        "tendrils",
        "giant_obelisks",
        HistoricAgeThemes.ANCIENT_BONES,
        HistoricAgeThemes.FORGOTTEN_RUINS,
        HistoricAgeThemes.COLLAPSED_OBSERVATORY,
        HistoricAgeThemes.ANCIENT_AQUEDUCTS,
        HistoricAgeThemes.GATEWAY_RUINS,
        ExoticAgeThemes.HEX,
        ExoticAgeThemes.WIRE_CELLS,
        ExoticAgeThemes.SEPARATORS,
        ExoticAgeThemes.CABLES,
        ExoticAgeThemes.FRACTAL_CUBES,
        ExoticAgeThemes.LIGHT_FISSURES,
        ExoticAgeThemes.VIRUS,
        AmbientAgeThemes.PAGE_STORMS,
        AmbientAgeThemes.MEMORY_BLOOMS,
        AmbientAgeThemes.STABLE_SANCTUARIES,
        ChaosAgeThemes.SKY_RAINBOWS,
        ChaosAgeThemes.SKY_AURORAS,
        ChaosAgeThemes.SHOOTING_STARS,
        ChaosAgeThemes.COMETS,
        ChaosAgeThemes.SKY_RIFTS,
        ChaosAgeThemes.SKY_NEBULAE,
        ChaosAgeThemes.ECLIPSE_HALOS,
        ChaosAgeThemes.STAR_GLYPHS,
        ChaosAgeThemes.HORIZON_MIRAGES,
        ChaosAgeThemes.CRYSTAL_HALOS,
        ChaosAgeThemes.VOID_FLECKS,
        ChaosAgeThemes.SPIRAL_GALAXIES,
        ChaosAgeThemes.FALLING_SKY_SHARDS,
        ChaosAgeThemes.LIGHTNING_VEINS,
        ChaosAgeThemes.LUMINOUS_COLUMNS,
        ChaosAgeThemes.SKY_MONOLITHS,
        ChaosAgeThemes.PRISM_RINGS,
        ChaosAgeThemes.CHROMA_WAVES,
        ChaosAgeThemes.ORBITAL_GRID,
        ChaosAgeThemes.SKY_LANTERNS,
        ChaosAgeThemes.FRACTURE_WEB,
        ChaosAgeThemes.DREAM_VEILS,
        ChaosAgeThemes.SKY_BUBBLES,
        ChaosAgeThemes.STARFALL_BLOOMS,
        ChaosAgeThemes.HORIZON_CROWNS,
        ChaosAgeThemes.CELESTIAL_SCRIPT,
        ChaosAgeThemes.GLASS_CONSTELLATIONS,
        ChaosAgeThemes.RADIANT_WHIRLPOOLS,
        ChaosAgeThemes.BRIGHT_SKY,
        ChaosAgeThemes.DARK_SKY,
        ChaosAgeThemes.METEOR_SHOWERS,
        ChaosAgeThemes.SKY_SPHERES,
        ChaosAgeThemes.PARTICLE_MOTES,
        ChaosAgeThemes.PARTICLE_ASH,
        ChaosAgeThemes.PARTICLE_SPORES,
        ChaosAgeThemes.PARTICLE_VOID
    )

    private val MAJOR_FEATURE_IDS = listOf(
        ABANDONED_ARCHIVE,
        HistoricAgeThemes.FORGOTTEN_RUINS,
        HistoricAgeThemes.ANCIENT_BONES,
        HistoricAgeThemes.COLLAPSED_OBSERVATORY,
        HistoricAgeThemes.ANCIENT_AQUEDUCTS,
        HistoricAgeThemes.GATEWAY_RUINS,
        AmbientAgeThemes.PAGE_STORMS,
        AmbientAgeThemes.MEMORY_BLOOMS,
        AmbientAgeThemes.STABLE_SANCTUARIES,
        STAR_FISSURE
    )

    private val PAGE_LOOT_FEATURE_IDS = setOf(
        ABANDONED_ARCHIVE,
        HistoricAgeThemes.FORGOTTEN_RUINS,
        HistoricAgeThemes.COLLAPSED_OBSERVATORY,
        HistoricAgeThemes.ANCIENT_AQUEDUCTS,
        HistoricAgeThemes.GATEWAY_RUINS,
        AmbientAgeThemes.PAGE_STORMS,
        AmbientAgeThemes.STABLE_SANCTUARIES
    )

    private fun extraFeatureCount(profile: AgeProfile, exempt: String? = null): Int =
        profile.modifiers.toList().count { modifier ->
            modifier != "dense_ores" &&
                modifier != exempt &&
                modifier in FEATURE_PAGE_IDS
        }.coerceAtLeast(0)

    fun rarityRollDivisor(profile: AgeProfile, base: Int, exempt: String? = null): Int {
        val extra = extraFeatureCount(profile, exempt)
        val multiplier = configuredSpawnMultiplier(exempt)
        if (multiplier <= 0f) return Int.MAX_VALUE
        return (base * (1.0f + extra * 0.30f) / multiplier).roundToInt().coerceAtLeast(1)
    }

    fun chanceMultiplier(profile: AgeProfile, exempt: String? = null): Float {
        val extra = extraFeatureCount(profile, exempt)
        val crowding = (1.0f - extra * 0.10f).coerceAtLeast(0.35f)
        return (crowding * configuredSpawnMultiplier(exempt)).coerceIn(0f, 1f)
    }

    private fun configuredSpawnMultiplier(featureId: String?): Float {
        val config = MystcraftConfig.current.worldGeneration
        val pageMultiplier = if (featureId in PAGE_LOOT_FEATURE_IDS) config.pageFeatureSpawnRateMultiplier else 1.0f
        return config.featureSpawnRateMultiplier * pageMultiplier
    }

    fun canPlaceMajorFeature(
        profile: AgeProfile,
        candidateChunk: ChunkPos,
        featureId: String,
        minDistanceChunks: Int
    ): Boolean {
        if (!featureCanAppear(profile, featureId)) return false

        val cellSize = minDistanceChunks.coerceAtLeast(8)
        val cellX = Math.floorDiv(candidateChunk.x, cellSize)
        val cellZ = Math.floorDiv(candidateChunk.z, cellSize)
        val selfScore = reservationScore(profile.seed, featureId, cellX, cellZ)

        for (nearCellX in cellX - 1..cellX + 1) {
            for (nearCellZ in cellZ - 1..cellZ + 1) {
                for (otherFeature in MAJOR_FEATURE_IDS) {
                    if (otherFeature == featureId && nearCellX == cellX && nearCellZ == cellZ) continue
                    if (!featureCanAppear(profile, otherFeature)) continue

                    val otherAnchor = reservedChunk(profile.seed, otherFeature, nearCellX, nearCellZ, cellSize)
                    val dx = otherAnchor.x - candidateChunk.x
                    val dz = otherAnchor.z - candidateChunk.z
                    if (dx * dx + dz * dz >= minDistanceChunks * minDistanceChunks) continue

                    val otherScore = reservationScore(profile.seed, otherFeature, nearCellX, nearCellZ)
                    if (otherScore > selfScore || (otherScore == selfScore && otherFeature < featureId)) {
                        return false
                    }
                }
            }
        }

        return true
    }

    private fun reservedChunk(seed: Long, featureId: String, cellX: Int, cellZ: Int, cellSize: Int): ChunkPos {
        val random = java.util.Random(reservationSeed(seed, featureId, cellX, cellZ))
        val margin = (cellSize / 5).coerceIn(1, 4)
        val span = (cellSize - margin * 2).coerceAtLeast(1)
        return ChunkPos(
            cellX * cellSize + margin + random.nextInt(span),
            cellZ * cellSize + margin + random.nextInt(span)
        )
    }

    private fun reservationScore(seed: Long, featureId: String, cellX: Int, cellZ: Int): Int =
        java.util.Random(reservationSeed(seed, featureId, cellX, cellZ) xor 0x5EED5EEDL).nextInt()

    private fun reservationSeed(seed: Long, featureId: String, cellX: Int, cellZ: Int): Long =
        seed xor
            (featureId.hashCode().toLong() * 6_364_136_223_846_793_005L) xor
            (cellX.toLong() * 1_442_695_040_888_963_407L) xor
            (cellZ.toLong() * 2_853_942_125_093_471_117L)

    private fun featureCanAppear(profile: AgeProfile, featureId: String): Boolean {
        val instability = profile.stability.instabilityScore
        return when (featureId) {
            ABANDONED_ARCHIVE -> true
            HistoricAgeThemes.FORGOTTEN_RUINS -> profile.modifiers.contains(featureId) || instability >= 18
            HistoricAgeThemes.ANCIENT_BONES -> profile.modifiers.contains(featureId) || instability >= 28
            HistoricAgeThemes.COLLAPSED_OBSERVATORY -> profile.modifiers.contains(featureId) || instability >= 40
            HistoricAgeThemes.ANCIENT_AQUEDUCTS -> profile.modifiers.contains(featureId) || instability >= 32
            HistoricAgeThemes.GATEWAY_RUINS -> profile.modifiers.contains(featureId) || instability >= 30
            AmbientAgeThemes.PAGE_STORMS -> profile.modifiers.contains(featureId) || instability >= 45
            AmbientAgeThemes.MEMORY_BLOOMS -> true
            AmbientAgeThemes.STABLE_SANCTUARIES -> true
            STAR_FISSURE -> true
            else -> profile.modifiers.contains(featureId)
        }
    }
}
