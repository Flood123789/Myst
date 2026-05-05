package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs

class AncientAqueductFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES || profile.terrainType == TerrainType.CITIES || profile.terrainType == TerrainType.CAVES) return false

        val explicit = profile.modifiers.contains(HistoricAgeThemes.ANCIENT_AQUEDUCTS)
        val chance = when {
            explicit -> 0.96f
            profile.stability.instabilityScore >= 65 -> 0.34f
            profile.stability.instabilityScore >= 32 -> 0.14f
            else -> 0.0f
        } * AgeFeatureTuning.chanceMultiplier(profile, HistoricAgeThemes.ANCIENT_AQUEDUCTS)
        if (chance <= 0f) return false

        val chunkPos = ChunkPos(context.origin)
        val regionSize = if (explicit) 12 else 18
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val seed = profile.seed + regionX * 4_112_909L + regionZ * 10_119_733L + 0xA0A0E0L
        val rand = java.util.Random(seed)
        if (rand.nextFloat() > chance) return false

        val ownerChunkX = regionX * regionSize + rand.nextInt(regionSize)
        val ownerChunkZ = regionZ * regionSize + rand.nextInt(regionSize)
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, ChunkPos(ownerChunkX, ownerChunkZ), HistoricAgeThemes.ANCIENT_AQUEDUCTS, 12)) return false

        val centerX = ownerChunkX * 16 + 8
        val centerZ = ownerChunkZ * 16 + 8
        val direction = if (rand.nextBoolean()) Direction.EAST else Direction.SOUTH
        val perpendicular = if (direction.axis == Direction.Axis.X) Direction.SOUTH else Direction.EAST
        val supportLength = 26 + rand.nextInt(30)
        val spanHeight = 8 + rand.nextInt(4)
        val baseGround = FeatureBuildHelper.findGround(world, centerX, centerZ, FeatureBuildHelper.WaterMode.SEABED) ?: return false
        val channelY = baseGround.y + spanHeight
        val center = BlockPos(centerX, channelY, centerZ)
        val buildChunk = ChunkPos(center)

        for (step in -supportLength / 2..supportLength / 2) {
            if (rand.nextFloat() < 0.08f && abs(step) > 3) continue
            val walkwayCenter = center.offset(direction, step)
            buildSpan(world, buildChunk, walkwayCenter, direction, perpendicular, rand, step % 7 == 0)
            if (step % 6 == 0) {
                buildSupport(world, buildChunk, walkwayCenter, perpendicular, rand)
            }
        }

        buildBrokenCauseway(world, buildChunk, center, direction, perpendicular, rand)
        placeLoot(world, buildChunk, center, direction, rand)
        return true
    }

    private fun buildSpan(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        direction: Direction,
        perpendicular: Direction,
        rand: java.util.Random,
        flowing: Boolean
    ) {
        for (offset in -1..1) {
            val pos = center.offset(perpendicular, offset)
            val rim = abs(offset) == 1
            val block = if (rim) Blocks.STONE_BRICK_STAIRS else Blocks.SMOOTH_STONE_SLAB
            FeatureBuildHelper.setBlock(world, chunkPos, pos, block, true)
            FeatureBuildHelper.setBlock(world, chunkPos, pos.down(), if (rim) Blocks.STONE_BRICKS else Blocks.POLISHED_ANDESITE, true)
            if (flowing && offset == 0 && rand.nextFloat() < 0.78f) {
                FeatureBuildHelper.setBlock(world, chunkPos, pos.up(), Blocks.WATER, true)
            }
        }

        val parapetA = center.offset(perpendicular, 2)
        val parapetB = center.offset(perpendicular, -2)
        FeatureBuildHelper.setBlock(world, chunkPos, parapetA, Blocks.STONE_BRICK_WALL, true)
        FeatureBuildHelper.setBlock(world, chunkPos, parapetB, Blocks.STONE_BRICK_WALL, true)

        if (rand.nextFloat() < 0.18f) {
            FeatureBuildHelper.setBlock(world, chunkPos, center.offset(direction, rand.nextInt(3) - 1).up(), Blocks.AIR, true)
        }
    }

    private fun buildSupport(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        walkwayCenter: BlockPos,
        perpendicular: Direction,
        rand: java.util.Random
    ) {
        val legHeight = 5 + rand.nextInt(8)
        val leftBase = FeatureBuildHelper.findGround(world, walkwayCenter.x + perpendicular.offsetX * 2, walkwayCenter.z + perpendicular.offsetZ * 2, FeatureBuildHelper.WaterMode.SEABED)
        val rightBase = FeatureBuildHelper.findGround(world, walkwayCenter.x - perpendicular.offsetX * 2, walkwayCenter.z - perpendicular.offsetZ * 2, FeatureBuildHelper.WaterMode.SEABED)
        if (leftBase == null || rightBase == null) return

        FeatureBuildHelper.fillColumn(world, chunkPos, leftBase.up(), walkwayCenter.y - leftBase.y, Blocks.SMOOTH_SANDSTONE.defaultState, true)
        FeatureBuildHelper.fillColumn(world, chunkPos, rightBase.up(), walkwayCenter.y - rightBase.y, Blocks.SMOOTH_SANDSTONE.defaultState, true)

        val apex = walkwayCenter.down(1)
        val leftArch = leftBase.up(walkwayCenter.y - leftBase.y - 1)
        val rightArch = rightBase.up(walkwayCenter.y - rightBase.y - 1)
        FeatureBuildHelper.drawLine(world, chunkPos, leftArch, apex, Blocks.CUT_SANDSTONE.defaultState, true)
        FeatureBuildHelper.drawLine(world, chunkPos, rightArch, apex, Blocks.CUT_SANDSTONE.defaultState, true)

        if (rand.nextFloat() < 0.25f) {
            FeatureBuildHelper.setBlock(world, chunkPos, apex.down(legHeight / 2), Blocks.LANTERN, true)
        }
    }

    private fun buildBrokenCauseway(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        direction: Direction,
        perpendicular: Direction,
        rand: java.util.Random
    ) {
        val rubbleBase = center.offset(direction, -4 - rand.nextInt(5))
        for (step in 0..6) {
            val pos = rubbleBase.offset(direction, step)
            FeatureBuildHelper.setBlock(world, chunkPos, pos.down(), Blocks.MOSSY_STONE_BRICKS, true)
            if (step % 2 == 0) {
                FeatureBuildHelper.setBlock(world, chunkPos, pos.offset(perpendicular, if (rand.nextBoolean()) 1 else -1), Blocks.STONE_BRICK_SLAB, true)
            }
        }
    }

    private fun placeLoot(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        direction: Direction,
        rand: java.util.Random
    ) {
        val chestPos = center.offset(direction, -6).down()
        FeatureBuildHelper.placeChest(world, chunkPos, chestPos, direction) { chest ->
            fillChest(
                chest,
                java.util.Random(rand.nextLong()),
                FeatureBuildHelper.randomPageLoot(net.minecraft.util.math.random.Random.create(rand.nextLong())) +
                    listOf(ItemStack(Items.WATER_BUCKET))
            )
        }
    }

    private fun fillChest(chest: ChestBlockEntity, rand: java.util.Random, stacks: List<ItemStack>) {
        stacks.forEach { chest.setStack(rand.nextInt(chest.size()), it) }
    }
}
