package mystcraft.flood.generation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.world.ChunkRegion
import net.minecraft.world.HeightLimitView
import net.minecraft.world.biome.source.BiomeAccess
import net.minecraft.world.biome.source.BiomeSource
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.gen.GenerationStep
import net.minecraft.world.gen.StructureAccessor
import net.minecraft.world.gen.chunk.Blender
import net.minecraft.world.gen.chunk.ChunkGenerator
import net.minecraft.world.gen.chunk.VerticalBlockSample
import net.minecraft.world.gen.noise.NoiseConfig
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class BlankAgeChunkGenerator(
    private val blankBiomeSource: BiomeSource
) : ChunkGenerator(blankBiomeSource) {

    companion object {
        val CODEC: Codec<BlankAgeChunkGenerator> = RecordCodecBuilder.create { instance ->
            instance.group(
                BiomeSource.CODEC.fieldOf("biome_source").forGetter(BlankAgeChunkGenerator::blankBiomeSource)
            ).apply(instance, ::BlankAgeChunkGenerator)
        }
    }

    override fun getCodec(): Codec<out ChunkGenerator> = CODEC

    override fun carve(
        region: ChunkRegion,
        seed: Long,
        noiseConfig: NoiseConfig,
        biomeAccess: BiomeAccess,
        structureAccessor: StructureAccessor,
        chunk: Chunk,
        carverStep: GenerationStep.Carver
    ) {
    }

    override fun buildSurface(
        region: ChunkRegion,
        structures: StructureAccessor,
        noiseConfig: NoiseConfig,
        chunk: Chunk
    ) {
    }

    override fun populateEntities(region: ChunkRegion) {
    }

    override fun populateNoise(
        executor: Executor,
        blender: Blender,
        noiseConfig: NoiseConfig,
        structureAccessor: StructureAccessor,
        chunk: Chunk
    ): CompletableFuture<Chunk> = CompletableFuture.completedFuture(chunk)

    override fun getWorldHeight(): Int = 384

    override fun getSeaLevel(): Int = -64

    override fun getMinimumY(): Int = -64

    override fun getHeight(
        x: Int,
        z: Int,
        heightmap: net.minecraft.world.Heightmap.Type,
        world: HeightLimitView,
        noiseConfig: NoiseConfig
    ): Int = getMinimumY()

    override fun getColumnSample(
        x: Int,
        z: Int,
        world: HeightLimitView,
        noiseConfig: NoiseConfig
    ): VerticalBlockSample =
        VerticalBlockSample(getMinimumY(), Array(getWorldHeight()) { Blocks.AIR.defaultState })

    override fun getDebugHudText(text: MutableList<String>, noiseConfig: NoiseConfig, pos: BlockPos) {
        text.add("Sacrificed Age Void")
    }

    override fun getSpawnHeight(world: HeightLimitView): Int = 80
}
