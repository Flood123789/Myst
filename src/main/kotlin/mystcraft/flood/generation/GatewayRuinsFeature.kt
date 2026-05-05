package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs

class GatewayRuinsFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES || profile.terrainType == TerrainType.CAVES) return false

        val explicit = profile.modifiers.contains(HistoricAgeThemes.GATEWAY_RUINS)
        val chance = when {
            explicit -> 0.95f
            profile.stability.instabilityScore >= 60 -> 0.32f
            profile.stability.instabilityScore >= 30 -> 0.14f
            else -> 0.0f
        } * AgeFeatureTuning.chanceMultiplier(profile, HistoricAgeThemes.GATEWAY_RUINS)
        if (chance <= 0f) return false

        val chunkPos = ChunkPos(context.origin)
        val regionSize = if (explicit) 10 else 14
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        val seed = profile.seed + regionX * 2_911_021L + regionZ * 8_991_191L + 0x6A7E0AL
        val rand = java.util.Random(seed)
        if (rand.nextFloat() > chance) return false

        val ownerChunkX = regionX * regionSize + rand.nextInt(regionSize)
        val ownerChunkZ = regionZ * regionSize + rand.nextInt(regionSize)
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, ChunkPos(ownerChunkX, ownerChunkZ), HistoricAgeThemes.GATEWAY_RUINS, 10)) return false

        val centerX = ownerChunkX * 16 + 8
        val centerZ = ownerChunkZ * 16 + 8
        val ground = FeatureBuildHelper.findGround(world, centerX, centerZ) ?: return false
        val center = ground.up()
        val axis = if (rand.nextBoolean()) Direction.Axis.X else Direction.Axis.Z
        val chunk = ChunkPos(center)

        buildCourtyard(world, chunk, center, rand)
        for (index in -1..1) {
            val offset = if (axis == Direction.Axis.X) BlockPos(index * 6, 0, 0) else BlockPos(0, 0, index * 6)
            buildGateway(world, chunk, center.add(offset), axis, 7 + rand.nextInt(6), rand)
        }
        if (rand.nextBoolean()) {
            buildGateway(world, chunk, center.add(if (axis == Direction.Axis.X) BlockPos(0, 0, 9) else BlockPos(9, 0, 0)), perpendicular(axis), 9 + rand.nextInt(4), rand)
        }
        scatterFragments(world, chunk, center, 10, rand)
        placeLoot(world, chunk, center, axis, rand)
        return true
    }

    private fun buildCourtyard(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, rand: java.util.Random) {
        for (dx in -7..7) {
            for (dz in -7..7) {
                val dist = abs(dx) + abs(dz)
                if (dist > 12) continue
                val block = when {
                    dist > 10 -> Blocks.CRACKED_STONE_BRICKS
                    rand.nextFloat() < 0.18f -> Blocks.CHISELED_STONE_BRICKS
                    else -> Blocks.STONE_BRICKS
                }
                FeatureBuildHelper.setBlock(world, chunkPos, center.add(dx, -1, dz), block, true)
            }
        }
    }

    private fun buildGateway(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, axis: Direction.Axis, height: Int, rand: java.util.Random) {
        val lateral = if (axis == Direction.Axis.X) Direction.SOUTH else Direction.EAST
        val frameBlock = if (rand.nextFloat() < 0.3f) Blocks.POLISHED_BLACKSTONE_BRICKS else Blocks.STONE_BRICKS
        val capBlock = if (rand.nextBoolean()) Blocks.CRYING_OBSIDIAN else Blocks.CHISELED_STONE_BRICKS

        val leftBase = center.offset(lateral, -2)
        val rightBase = center.offset(lateral, 2)
        buildPier(world, chunkPos, leftBase, height, frameBlock)
        buildPier(world, chunkPos, rightBase, height - rand.nextInt(3), frameBlock)

        val topY = center.y + height
        val spanStart = BlockPos(leftBase.x, topY, leftBase.z)
        val spanEnd = BlockPos(rightBase.x, topY - rand.nextInt(2), rightBase.z)
        FeatureBuildHelper.drawLine(world, chunkPos, spanStart, spanEnd, capBlock.defaultState, true)

        if (rand.nextFloat() < 0.6f) {
            FeatureBuildHelper.drawLine(world, chunkPos, center.up(2), center.up(height - 1), Blocks.END_ROD.defaultState, true)
        }
        if (rand.nextFloat() < 0.45f) {
            FeatureBuildHelper.drawLine(world, chunkPos, center.offset(lateral, -1).up(3), center.offset(lateral, 1).up(height - 2), Blocks.TINTED_GLASS.defaultState, true)
        }
    }

    private fun buildPier(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, height: Int, block: Block) {
        for (y in 0..height) {
            val state = if (y == height) {
                Blocks.STONE_BRICK_SLAB.defaultState
            } else {
                block.defaultState
            }
            FeatureBuildHelper.setBlockState(world, chunkPos, base.up(y), state, true)
            if (y in 1 until height && y % 3 == 0) {
                FeatureBuildHelper.setBlock(world, chunkPos, base.up(y).north(), Blocks.STONE_BRICK_WALL, true)
                FeatureBuildHelper.setBlock(world, chunkPos, base.up(y).south(), Blocks.STONE_BRICK_WALL, true)
            }
        }
    }

    private fun scatterFragments(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, rand: java.util.Random) {
        repeat(28 + rand.nextInt(10)) {
            val pos = center.add(rand.nextInt(radius * 2 + 1) - radius, rand.nextInt(2), rand.nextInt(radius * 2 + 1) - radius)
            val state = when (rand.nextInt(6)) {
                0 -> Blocks.CRYING_OBSIDIAN.defaultState
                1 -> Blocks.TINTED_GLASS.defaultState
                2 -> {
                    val facing = when (rand.nextInt(4)) {
                        0 -> Direction.NORTH
                        1 -> Direction.SOUTH
                        2 -> Direction.EAST
                        else -> Direction.WEST
                    }
                    Blocks.STONE_BRICK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, facing)
                }
                3 -> Blocks.STONE_BRICK_SLAB.defaultState
                4 -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultState
                else -> Blocks.CHISELED_STONE_BRICKS.defaultState
            }
            FeatureBuildHelper.setBlockState(world, chunkPos, pos, state, true)
        }
    }

    private fun placeLoot(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, axis: Direction.Axis, rand: java.util.Random) {
        val chestPos = center.add(if (axis == Direction.Axis.X) 0 else 4, 0, if (axis == Direction.Axis.X) 4 else 0)
        FeatureBuildHelper.placeChest(world, chunkPos, chestPos, FeatureBuildHelper.cardinalFacingFrom(chestPos, center)) { chest ->
            fillChest(
                chest,
                java.util.Random(rand.nextLong()),
                FeatureBuildHelper.randomPageLoot(net.minecraft.util.math.random.Random.create(rand.nextLong())) +
                    listOf(ItemStack(Items.ENDER_PEARL, 1 + rand.nextInt(2)))
            )
        }
    }

    private fun fillChest(chest: ChestBlockEntity, rand: java.util.Random, stacks: List<ItemStack>) {
        stacks.forEach { chest.setStack(rand.nextInt(chest.size()), it) }
    }

    private fun perpendicular(axis: Direction.Axis): Direction.Axis =
        if (axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X
}
