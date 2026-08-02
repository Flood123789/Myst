package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import mystcraft.flood.item.ModItems
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext

class FloatingCastleFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    companion object {
        private const val REGION_SIZE_CHUNKS = 40
    }

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val random = context.random
        val origin = context.origin // The specific block being looked at
        val serverWorld = world.toServerWorld()

        if (!AgeSubdimensionManager.isPrimaryAgeRealm(serverWorld.registryKey.value)) return false
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES) return false

        val chunkPos = ChunkPos(origin)
        val structureManager = serverWorld.structureTemplateManager
        val templateOptional = structureManager.getTemplate(Identifier(MystcraftReforged.MOD_ID, "rare_floating_castle"))
        if (templateOptional.isEmpty) return false
        val template = templateOptional.get()

        var blocksPlacedInThisChunk = 0
        val regionX = Math.floorDiv(chunkPos.x, REGION_SIZE_CHUNKS)
        val regionZ = Math.floorDiv(chunkPos.z, REGION_SIZE_CHUNKS)

        for (checkRegionX in (regionX - 1)..(regionX + 1)) {
            for (checkRegionZ in (regionZ - 1)..(regionZ + 1)) {
                val castle = getCastlePlacement(serverWorld.seed, checkRegionX, checkRegionZ) ?: continue

                val placementData = net.minecraft.structure.StructurePlacementData()
                    .setMirror(castle.mirror)
                    .setRotation(castle.rotation)
                    .setIgnoreEntities(true)

                val bounds = template.calculateBoundingBox(placementData, castle.origin)
                if (!intersectsChunk(bounds, chunkPos)) continue

                val chunkBounds = BlockBox(
                    chunkPos.startX,
                    world.bottomY,
                    chunkPos.startZ,
                    chunkPos.endX,
                    world.topY - 1,
                    chunkPos.endZ
                )
                val clippedPlacementData = placementData.copy().setBoundingBox(chunkBounds)
                if (template.place(world, castle.origin, castle.origin, clippedPlacementData, random, 2 or 16)) {
                    blocksPlacedInThisChunk++
                    fillCastleChests(world, template, castle.origin, clippedPlacementData, chunkPos, random)
                }
            }
        }

        if (blocksPlacedInThisChunk > 0) {
            MystcraftReforged.LOGGER.debug("Generated floating castle section for chunk $chunkPos")
            return true
        }

        return false
    }

    private data class CastlePlacement(
        val origin: BlockPos,
        val rotation: BlockRotation,
        val mirror: BlockMirror
    )

    private fun getCastlePlacement(worldSeed: Long, regionX: Int, regionZ: Int): CastlePlacement? {
        val castleSeed = worldSeed + regionX.toLong() * 341873128712L + regionZ.toLong() * 132897987541L
        val seededRand = java.util.Random(castleSeed)

        if (seededRand.nextInt(14) != 0) return null

        val regionMinChunkX = regionX * REGION_SIZE_CHUNKS
        val regionMinChunkZ = regionZ * REGION_SIZE_CHUNKS
        val centerChunkX = regionMinChunkX + 8 + seededRand.nextInt(REGION_SIZE_CHUNKS - 16)
        val centerChunkZ = regionMinChunkZ + 8 + seededRand.nextInt(REGION_SIZE_CHUNKS - 16)
        val floatY = 152 + seededRand.nextInt(28)
        val rotation = BlockRotation.entries.toTypedArray()[seededRand.nextInt(BlockRotation.entries.size)]
        val mirror = BlockMirror.entries.toTypedArray()[seededRand.nextInt(BlockMirror.entries.size)]

        return CastlePlacement(
            origin = BlockPos(centerChunkX * 16, floatY, centerChunkZ * 16),
            rotation = rotation,
            mirror = mirror
        )
    }

    private fun isPosInCurrentChunk(pos: BlockPos, chunkPos: ChunkPos): Boolean {
        return (pos.x shr 4 == chunkPos.x) && (pos.z shr 4 == chunkPos.z)
    }

    private fun fillCastleChests(
        world: net.minecraft.world.StructureWorldAccess,
        template: net.minecraft.structure.StructureTemplate,
        origin: BlockPos,
        placementData: net.minecraft.structure.StructurePlacementData,
        chunkPos: ChunkPos,
        random: net.minecraft.util.math.random.Random
    ) {
        val chests = template.getInfosForBlock(origin, placementData, Blocks.CHEST) +
            template.getInfosForBlock(origin, placementData, Blocks.TRAPPED_CHEST)
        for (info in chests) {
            if (!isPosInCurrentChunk(info.pos, chunkPos)) continue
            val blockEntity = world.getBlockEntity(info.pos)
            if (blockEntity is ChestBlockEntity && blockEntity.isEmpty) {
                val pageCount = FeatureBuildHelper.configuredLostPageCount(random, 2, 5)
                repeat(pageCount) {
                    blockEntity.setStack(random.nextInt(blockEntity.size()), ItemStack(ModItems.LOST_PAGE))
                }
            }
        }
    }

    private fun intersectsChunk(bounds: BlockBox, chunkPos: ChunkPos): Boolean {
        val chunkMinX = chunkPos.startX
        val chunkMaxX = chunkPos.endX
        val chunkMinZ = chunkPos.startZ
        val chunkMaxZ = chunkPos.endZ

        return bounds.maxX >= chunkMinX &&
            bounds.minX <= chunkMaxX &&
            bounds.maxZ >= chunkMinZ &&
            bounds.minZ <= chunkMaxZ
    }
}
