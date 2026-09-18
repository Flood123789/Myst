package mystcraft.flood.generation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryCodecs
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.util.Identifier
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.BiomeSource
import net.minecraft.world.biome.source.util.MultiNoiseUtil
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer
import java.util.stream.Stream
import kotlin.math.sqrt

class BiosphereBiomeSource(
    private val seed: Long,
    private val surfaceBiomes: RegistryEntryList<Biome>,
    private val caveBiomes: RegistryEntryList<Biome>
) : BiomeSource() {

    companion object {
        val CODEC: Codec<BiosphereBiomeSource> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.LONG.fieldOf("seed").forGetter(BiosphereBiomeSource::seed),
                RegistryCodecs.entryList(RegistryKeys.BIOME).fieldOf("surface_biomes").forGetter(BiosphereBiomeSource::effectiveSurfaceBiomes),
                RegistryCodecs.entryList(RegistryKeys.BIOME).fieldOf("cave_biomes").forGetter(BiosphereBiomeSource::effectiveCaveBiomes)
            ).apply(instance, ::BiosphereBiomeSource)
        }

        fun fromRegistry(seed: Long, biomeRegistry: Registry<Biome>): BiosphereBiomeSource {
            val surfaceEntries = BiosphereLayout.collectBiomeIds(biomeRegistry, caveOnly = false).mapNotNull { id ->
                biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, id)).orElse(null)
            }
            val caveEntries = BiosphereLayout.collectBiomeIds(biomeRegistry, caveOnly = true).mapNotNull { id ->
                biomeRegistry.getEntry(RegistryKey.of(RegistryKeys.BIOME, id)).orElse(null)
            }
            val safeSurface = if (surfaceEntries.isNotEmpty()) surfaceEntries else biomeRegistry.streamEntries().toList()
            val safeCave = if (caveEntries.isNotEmpty()) caveEntries else safeSurface
            return BiosphereBiomeSource(seed, RegistryEntryList.of(safeSurface), RegistryEntryList.of(safeCave))
        }

        private fun biomeId(entry: RegistryEntry<Biome>): Identifier =
            entry.getKey().orElse(RegistryKey.of(RegistryKeys.BIOME, Identifier("minecraft", "plains"))).value

        private fun featureOrderCompatible(entries: List<RegistryEntry<Biome>>): Boolean = try {
            PlacedFeatureIndexer.collectIndexedFeatures(
                entries,
                { entry -> entry.value().generationSettings.features },
                false
            )
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    /*
     * ChunkGenerator validates the placed-feature order of every biome advertised by a biome
     * source. A large modpack can contain individually valid biomes which cannot coexist in one
     * source (for example vanilla desert and Modern Beta's beta_desert). Biospheres deliberately
     * sample the whole biome registry, so validate that combined graph here and omit only entries
     * which would introduce a cycle. Vanilla entries are considered first to keep the baseline
     * biome set deterministic. This also runs when an existing Age is decoded from disk, repairing
     * sources written by older versions rather than leaving the world permanently unloadable.
     */
    private val compatibleEntries: List<RegistryEntry<Biome>> = run {
        val candidates = Stream.concat(surfaceBiomes.stream(), caveBiomes.stream())
            .distinct()
            .sorted(
                compareBy<RegistryEntry<Biome>>(
                    { if (biomeId(it).namespace == "minecraft") 0 else 1 },
                    { biomeId(it).toString() }
                )
            )
            .toList()
        val accepted = mutableListOf<RegistryEntry<Biome>>()
        val rejected = mutableListOf<Identifier>()

        for (candidate in candidates) {
            if (featureOrderCompatible(accepted + candidate)) {
                accepted += candidate
            } else {
                rejected += biomeId(candidate)
            }
        }

        if (rejected.isNotEmpty()) {
            mystcraft.flood.MystcraftReforged.LOGGER.warn(
                "Excluded {} biome(s) from Biosphere Ages because their placed-feature order conflicts with the accepted biome set: {}",
                rejected.size,
                rejected.joinToString()
            )
        }
        accepted
    }

    private val surfaceEntries = surfaceBiomes.stream().filter(compatibleEntries::contains).toList()
        .ifEmpty { compatibleEntries.take(1) }
    private val caveEntries = caveBiomes.stream().filter(compatibleEntries::contains).toList()
        .ifEmpty { surfaceEntries }
    private val effectiveSurfaceBiomes = RegistryEntryList.of(surfaceEntries)
    private val effectiveCaveBiomes = RegistryEntryList.of(caveEntries)
    private val surfaceIds = surfaceEntries.map(::biomeId)
    private val caveIds = caveEntries.map(::biomeId)
    private val surfaceById = surfaceIds.zip(surfaceEntries).toMap()
    private val caveById = caveIds.zip(caveEntries).toMap()

    override fun getCodec(): Codec<out BiomeSource> = CODEC

    override fun biomeStream(): Stream<RegistryEntry<Biome>> {
        return compatibleEntries.stream()
    }

    override fun getBiome(x: Int, y: Int, z: Int, noise: MultiNoiseUtil.MultiNoiseSampler): RegistryEntry<Biome> {
        val blockX = x shl 2
        val blockY = y shl 2
        val blockZ = z shl 2
        val nearest = BiosphereLayout.nearestSphere(seed, surfaceIds, caveIds, blockX, blockZ).minByOrNull { sphere ->
            val dx = (blockX - sphere.centerX).toDouble()
            val dy = (blockY - sphere.centerY).toDouble()
            val dz = (blockZ - sphere.centerZ).toDouble()
            val verticalWeight = if (sphere.cave) 1.0 else 0.35
            sqrt(dx * dx + dz * dz + dy * dy * verticalWeight * verticalWeight) / sphere.radius.toDouble()
        }

        if (nearest != null) {
            val source = if (nearest.cave) caveById else surfaceById
            return source[nearest.biomeId] ?: surfaceEntries.first()
        }

        return surfaceEntries.first()
    }
}
