package mystcraft.flood.generation

import com.mojang.datafixers.util.Pair as DFPair
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.FixedBiomeSource
import net.minecraft.world.biome.source.MultiNoiseBiomeSource
import net.minecraft.world.biome.source.util.MultiNoiseUtil

object ClassicBiomeSources {
    private data class ClassicBiomeBand(val biomeId: String, val weight: Float)

    private val betaBands = listOf(
        ClassicBiomeBand("minecraft:snowy_plains", 0.14f),
        ClassicBiomeBand("minecraft:taiga", 0.18f),
        ClassicBiomeBand("minecraft:forest", 0.24f),
        ClassicBiomeBand("minecraft:plains", 0.18f),
        ClassicBiomeBand("minecraft:swamp", 0.10f),
        ClassicBiomeBand("minecraft:desert", 0.16f)
    )

    private val alphaBands = listOf(
        ClassicBiomeBand("minecraft:snowy_plains", 0.12f),
        ClassicBiomeBand("minecraft:taiga", 0.20f),
        ClassicBiomeBand("minecraft:windswept_hills", 0.12f),
        ClassicBiomeBand("minecraft:forest", 0.26f),
        ClassicBiomeBand("minecraft:plains", 0.12f),
        ClassicBiomeBand("minecraft:swamp", 0.06f),
        ClassicBiomeBand("minecraft:desert", 0.12f)
    )

    fun betaSource(biomeRegistry: Registry<Biome>): net.minecraft.world.biome.source.BiomeSource =
        buildClassicSource(biomeRegistry, betaBands, "minecraft:forest")

    fun alphaSource(biomeRegistry: Registry<Biome>): net.minecraft.world.biome.source.BiomeSource =
        buildClassicSource(biomeRegistry, alphaBands, "minecraft:forest")

    private fun buildClassicSource(
        biomeRegistry: Registry<Biome>,
        bands: List<ClassicBiomeBand>,
        fallbackBiomeId: String
    ): net.minecraft.world.biome.source.BiomeSource {
        val fallback = lookupBiome(biomeRegistry, fallbackBiomeId) ?: biomeRegistry.streamEntries().findFirst().orElse(null)
        if (fallback == null) {
            throw IllegalStateException("No biomes available to build classic biome source")
        }

        val entries = mutableListOf<DFPair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>>()
        var currentMin = -1.0f

        for (band in bands) {
            val biomeEntry = lookupBiome(biomeRegistry, band.biomeId) ?: continue
            val nextMax = (currentMin + band.weight * 2.0f).coerceAtMost(1.0f)
            val hypercube = MultiNoiseUtil.NoiseHypercube(
                MultiNoiseUtil.ParameterRange.of(currentMin, nextMax),
                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                MultiNoiseUtil.ParameterRange.of(-1.0f, 1.0f),
                0L
            )
            entries.add(DFPair.of(hypercube, biomeEntry))
            currentMin = nextMax
        }

        return if (entries.isEmpty()) {
            FixedBiomeSource(fallback)
        } else {
            MultiNoiseBiomeSource.create(MultiNoiseUtil.Entries(entries))
        }
    }

    private fun lookupBiome(biomeRegistry: Registry<Biome>, biomeId: String): RegistryEntry<Biome>? {
        val id = Identifier.tryParse(biomeId) ?: return null
        val key = RegistryKey.of(RegistryKeys.BIOME, id)
        return biomeRegistry.getEntry(key).orElse(null)
    }
}
