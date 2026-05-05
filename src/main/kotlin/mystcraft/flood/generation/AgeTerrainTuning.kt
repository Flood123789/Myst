package mystcraft.flood.generation

import mystcraft.flood.generation.profile.TerrainTuningProfile
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings
import net.minecraft.world.gen.chunk.GenerationShapeConfig
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes
import net.minecraft.world.gen.noise.NoiseRouter
import kotlin.math.roundToInt

object AgeTerrainTuning {
    private const val LEVEL_MIN = 1
    private const val LEVEL_MAX = 16

    private const val SEA_LEVEL_MIN = -64
    private const val SEA_LEVEL_MAX = 160

    private const val RANGE_MIN_Y_MIN = -64
    private const val RANGE_MIN_Y_MAX = -128
    private const val RANGE_HEIGHT_MIN = 128
    private const val RANGE_HEIGHT_MAX = 384

    private const val TURBULENCE_BIAS_MIN = -0.18
    private const val TURBULENCE_BIAS_MAX = 0.26

    private const val CAVE_DENSITY_MULTIPLIER_MIN = 1.45
    private const val CAVE_DENSITY_MULTIPLIER_MAX = 0.55
    private const val CAVE_WORLD_CAVE_MULTI = 0.42

    fun withTuning(base: RegistryEntry<ChunkGeneratorSettings>, tuning: TerrainTuningProfile): RegistryEntry<ChunkGeneratorSettings> {
        val normalized = tuning.normalized()
        if (!normalized.isEdited()) return base

        val original = base.value()
        val shape = tunedShape(original.generationShapeConfig(), normalized)
        val router = tunedRouter(original.noiseRouter(), normalized)
        val seaLevel = normalized.seaLevel?.let(::seaLevel) ?: original.seaLevel()
        val mobGenerationDisabled = original.mobGenerationDisabled() || normalized.noMobs
        val aquifers = if (normalized.noAquifers) false else original.aquifers()

        return RegistryEntry.of(
            ChunkGeneratorSettings(
                shape,
                original.defaultBlock(),
                original.defaultFluid(),
                router,
                original.surfaceRule(),
                original.spawnTarget(),
                seaLevel,
                mobGenerationDisabled,
                aquifers,
                original.oreVeins(),
                original.usesLegacyRandom()
            )
        )
    }

    fun checkerboardScale(level: Int?): Int {
        val normalized = level?.coerceIn(LEVEL_MIN, LEVEL_MAX) ?: return 3
        return when {
            normalized <= 4 -> 1
            normalized <= 8 -> 2
            normalized <= 12 -> 3
            else -> 4
        }
    }

    private fun tunedShape(base: GenerationShapeConfig, tuning: TerrainTuningProfile): GenerationShapeConfig {
        val verticalLevel = tuning.verticalRange
        val turbulenceLevel = tuning.terrainTurbulence

        var minimumY = base.minimumY()
        var height = base.height()
        if (verticalLevel != null) {
            minimumY = aligned16(lerp(verticalLevel, RANGE_MIN_Y_MIN.toDouble(), RANGE_MIN_Y_MAX.toDouble()).roundToInt())
            height = aligned16(lerp(verticalLevel, RANGE_HEIGHT_MIN.toDouble(), RANGE_HEIGHT_MAX.toDouble()).roundToInt()).coerceAtLeast(16)
        }

        val horizontalSize = when {
            turbulenceLevel == null -> base.horizontalSize()
            turbulenceLevel <= 4 -> (base.horizontalSize() + 1).coerceAtMost(4)
            turbulenceLevel >= 13 -> (base.horizontalSize() - 1).coerceAtLeast(1)
            else -> base.horizontalSize()
        }

        val verticalSize = when {
            turbulenceLevel == null -> base.verticalSize()
            turbulenceLevel <= 4 -> (base.verticalSize() + 1).coerceAtMost(4)
            turbulenceLevel >= 13 -> (base.verticalSize() - 1).coerceAtLeast(1)
            else -> base.verticalSize()
        }

        return GenerationShapeConfig.create(minimumY, height, horizontalSize, verticalSize)
    }

    private fun tunedRouter(base: NoiseRouter, tuning: TerrainTuningProfile): NoiseRouter {
        var initialDensity = base.initialDensityWithoutJaggedness()
        var finalDensity = base.finalDensity()

        tuning.terrainTurbulence?.let { level ->
            val bias = DensityFunctionTypes.constant(lerp(level, TURBULENCE_BIAS_MIN, TURBULENCE_BIAS_MAX))
            initialDensity = DensityFunctionTypes.add(initialDensity, bias)
            finalDensity = DensityFunctionTypes.add(finalDensity, bias)
        }

        val caveDensityLevel = tuning.caveDensity
        val caveMultiplier = when {
            tuning.caveWorld -> CAVE_WORLD_CAVE_MULTI
            caveDensityLevel != null -> lerp(caveDensityLevel, CAVE_DENSITY_MULTIPLIER_MIN, CAVE_DENSITY_MULTIPLIER_MAX)
            else -> 1.0
        }
        if (caveMultiplier != 1.0) {
            finalDensity = DensityFunctionTypes.mul(finalDensity, DensityFunctionTypes.constant(caveMultiplier))
        }

        return NoiseRouter(
            base.barrierNoise(),
            base.fluidLevelFloodednessNoise(),
            base.fluidLevelSpreadNoise(),
            base.lavaNoise(),
            base.temperature(),
            base.vegetation(),
            base.continents(),
            base.erosion(),
            base.depth(),
            base.ridges(),
            initialDensity,
            finalDensity,
            base.veinToggle(),
            base.veinRidged(),
            base.veinGap()
        )
    }

    private fun seaLevel(level: Int): Int = lerp(level, SEA_LEVEL_MIN.toDouble(), SEA_LEVEL_MAX.toDouble()).roundToInt()

    private fun lerp(level: Int, min: Double, max: Double): Double {
        val t = ((level.coerceIn(LEVEL_MIN, LEVEL_MAX) - LEVEL_MIN).toDouble() / (LEVEL_MAX - LEVEL_MIN).toDouble())
        return min + (max - min) * t
    }

    private fun aligned16(value: Int): Int = Math.floorDiv(value, 16) * 16
}
