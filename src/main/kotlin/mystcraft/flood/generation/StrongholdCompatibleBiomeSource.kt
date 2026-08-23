package mystcraft.flood.generation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.registry.RegistryCodecs
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.BiomeSource
import net.minecraft.world.biome.source.util.MultiNoiseUtil
import java.util.stream.Stream

/**
 * Reports a stronghold-valid anchor biome as possible without ever selecting it
 * for terrain. The chunk-generator mixin then permits only the stronghold's
 * final biome predicate, leaving every other structure's biome rules intact.
 */
class StrongholdCompatibleBiomeSource(
    val delegate: BiomeSource,
    private val anchors: RegistryEntryList<Biome>
) : BiomeSource() {
    companion object {
        val CODEC: Codec<StrongholdCompatibleBiomeSource> = RecordCodecBuilder.create { instance ->
            instance.group(
                BiomeSource.CODEC.fieldOf("delegate").forGetter(StrongholdCompatibleBiomeSource::delegate),
                RegistryCodecs.entryList(RegistryKeys.BIOME).fieldOf("anchors").forGetter(StrongholdCompatibleBiomeSource::anchors)
            ).apply(instance, ::StrongholdCompatibleBiomeSource)
        }
    }

    override fun getCodec(): Codec<out BiomeSource> = CODEC

    override fun biomeStream(): Stream<RegistryEntry<Biome>> = Stream.concat(delegate.biomes.stream(), anchors.stream()).distinct()

    override fun getBiome(x: Int, y: Int, z: Int, noise: MultiNoiseUtil.MultiNoiseSampler): RegistryEntry<Biome> =
        delegate.getBiome(x, y, z, noise)
}
