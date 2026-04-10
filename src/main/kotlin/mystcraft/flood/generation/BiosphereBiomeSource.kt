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
                RegistryCodecs.entryList(RegistryKeys.BIOME).fieldOf("surface_biomes").forGetter(BiosphereBiomeSource::surfaceBiomes),
                RegistryCodecs.entryList(RegistryKeys.BIOME).fieldOf("cave_biomes").forGetter(BiosphereBiomeSource::caveBiomes)
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
    }

    private val surfaceEntries = surfaceBiomes.stream().toList()
    private val caveEntries = caveBiomes.stream().toList()
    private val surfaceIds = surfaceEntries.map { it.getKey().orElse(RegistryKey.of(RegistryKeys.BIOME, Identifier("minecraft", "plains"))).value }
    private val caveIds = caveEntries.map { it.getKey().orElse(RegistryKey.of(RegistryKeys.BIOME, Identifier("minecraft", "plains"))).value }
    private val surfaceById = surfaceIds.zip(surfaceEntries).toMap()
    private val caveById = caveIds.zip(caveEntries).toMap()

    override fun getCodec(): Codec<out BiomeSource> = CODEC

    override fun biomeStream(): Stream<RegistryEntry<Biome>> {
        return Stream.concat(surfaceBiomes.stream(), caveBiomes.stream()).distinct()
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
