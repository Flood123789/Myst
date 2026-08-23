package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import mystcraft.flood.item.ModItems
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Generates an abandoned archive landmark containing exploration space and Mystcraft page loot.
 * Placement uses [FeatureBuildHelper] so water, cave interiors, protected blocks, and terrain
 * clipping follow the same rules as the other large hand-built features.
 */
class AbandonedArchiveFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val origin = context.origin
        val serverWorld = world.toServerWorld()

        if (!AgeSubdimensionManager.isPrimaryAgeRealm(serverWorld.registryKey.value)) return false
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES) return false

        val chunkPos = ChunkPos(origin)
        val archiveRegionSize = 36
        val regionX = Math.floorDiv(chunkPos.x, archiveRegionSize)
        val regionZ = Math.floorDiv(chunkPos.z, archiveRegionSize)
        val regionSeed = profile.seed +
            regionX.toLong() * 8_851_921_307L +
            regionZ.toLong() * 4_124_918_447L +
            0xA7C417EL
        val regionRand = java.util.Random(regionSeed)
        val regionChance = when {
            profile.stability.instabilityScore >= 70 -> 0.36f
            profile.stability.instabilityScore >= 35 -> 0.24f
            else -> 0.14f
        } * AgeFeatureTuning.chanceMultiplier(profile)
        CustomStructureOverrides.generateIfPresent(context, "abandoned_archive", profile, regionChance)?.let { return it }
        if (regionRand.nextFloat() > regionChance) return false

        val ownerChunkX = regionX * archiveRegionSize + 5 + regionRand.nextInt(archiveRegionSize - 10)
        val ownerChunkZ = regionZ * archiveRegionSize + 5 + regionRand.nextInt(archiveRegionSize - 10)
        if (chunkPos.x != ownerChunkX || chunkPos.z != ownerChunkZ) return false
        if (!AgeFeatureTuning.canPlaceMajorFeature(profile, chunkPos, AgeFeatureTuning.ABANDONED_ARCHIVE, 18)) return false

        val random = net.minecraft.util.math.random.Random.create(regionSeed xor 0xB00C51BEL)

        val centerX = ownerChunkX * 16 + 8 + regionRand.nextInt(7) - 3
        val centerZ = ownerChunkZ * 16 + 8 + regionRand.nextInt(7) - 3
        val ground = FeatureBuildHelper.findGround(world, centerX, centerZ) ?: return false
        if (ground.y < 49 || ground.y > 149) return false

        val centerPos = ground.up()
        if (world.getBlockState(ground).isOf(Blocks.WATER)) return false

        val variant = ArchiveVariant.entries[random.nextInt(ArchiveVariant.entries.size)]
        val chestSpots = mutableListOf<BlockPos>()
        val supplySpots = mutableListOf<BlockPos>()

        when (variant) {
            ArchiveVariant.GRAND_HALL -> buildGrandHall(world, chunkPos, centerPos, chestSpots, supplySpots, random)
            ArchiveVariant.ROTUNDA -> buildRotunda(world, chunkPos, centerPos, chestSpots, supplySpots, random)
            ArchiveVariant.CLOISTER -> buildCloister(world, chunkPos, centerPos, chestSpots, supplySpots, random)
            ArchiveVariant.SCRIPTORIUM -> buildScriptorium(world, chunkPos, centerPos, chestSpots, supplySpots, random)
            ArchiveVariant.STACKS -> buildStacks(world, chunkPos, centerPos, chestSpots, supplySpots, random)
        }

        val pageChests = chestSpots.shuffled(random.asJavaRandom()).take((2 + random.nextInt(2)).coerceAtMost(chestSpots.size))
        for (spot in pageChests) {
            placePageChest(world, chunkPos, spot, facingForChest(spot, centerPos), random)
        }

        val supplyChests = supplySpots.filterNot { it in pageChests }
            .shuffled(random.asJavaRandom())
            .take((1 + random.nextInt(2)).coerceAtMost(supplySpots.size))
        for (spot in supplyChests) {
            placeSupplyChest(world, chunkPos, spot, facingForChest(spot, centerPos), random)
        }

        return true
    }

    private fun buildGrandHall(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        val halfX = 11
        val halfZ = 9
        val wallHeight = 10
        layFloor(world, chunkPos, center, halfX, halfZ)
        buildShell(world, chunkPos, center, halfX, halfZ, wallHeight, random)

        for (z in -6..6 step 4) {
            buildShelfRow(world, chunkPos, center.add(-8, 0, z), Direction.EAST, 10, random)
            buildShelfRow(world, chunkPos, center.add(8, 0, z), Direction.WEST, 10, random)
        }
        buildReadingTable(world, chunkPos, center.add(-2, 0, -1))
        buildReadingTable(world, chunkPos, center.add(2, 0, -1))
        buildReadingTable(world, chunkPos, center.add(-2, 0, 3))
        buildReadingTable(world, chunkPos, center.add(2, 0, 3))
        buildColumn(world, chunkPos, center.add(-9, 0, -7), 9)
        buildColumn(world, chunkPos, center.add(9, 0, -7), 9)
        buildColumn(world, chunkPos, center.add(-9, 0, 7), 9)
        buildColumn(world, chunkPos, center.add(9, 0, 7), 9)
        buildIndexDais(world, chunkPos, center.add(0, 0, -5))
        chestSpots += listOf(center.add(-8, 0, -6), center.add(8, 0, 6), center.add(0, 0, 7), center.add(0, 0, -6))
        supplySpots += listOf(center.add(-4, 0, -7), center.add(4, 0, -7), center.add(-9, 0, 0), center.add(9, 0, 0))
        scatterDebris(world, chunkPos, center, 12, random)
    }

    private fun buildRotunda(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        val radius = 10
        val wallHeight = 9
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val dist = dx * dx + dz * dz
                if (dist > radius * radius + 2) continue
                val ground = center.add(dx, -1, dz)
                setBlock(world, chunkPos, ground, floorFor(dx, dz))
                if (dist >= (radius - 1) * (radius - 1)) {
                    for (y in 0..wallHeight) {
                        val doorway = dz == radius && abs(dx) <= 1 && y <= 3
                        val clerestory = abs(dist - ((radius - 1) * (radius - 1))) <= radius && y in 3..4 && (abs(dx) + abs(dz)) % 4 == 0
                        if (doorway) {
                            setBlockState(world, chunkPos, center.add(dx, y, dz), Blocks.AIR.defaultState)
                        } else if (clerestory) {
                            setBlock(world, chunkPos, center.add(dx, y, dz), Blocks.GRAY_STAINED_GLASS_PANE)
                        } else if ((dx + dz + y) % 11 != 0) {
                            setBlock(world, chunkPos, center.add(dx, y, dz), wallFor(dx, dz, y))
                        }
                    }
                }
            }
        }

        for (angleIndex in 0 until 6) {
            val x = listOf(0, 7, 7, 0, -7, -7)[angleIndex]
            val z = listOf(8, 4, -4, -8, -4, 4)[angleIndex]
            buildColumn(world, chunkPos, center.add(x, 0, z), 8)
        }

        buildShelfRing(world, chunkPos, center, radius - 3, random)
        buildRotundaRoof(world, chunkPos, center, radius, wallHeight)
        placeDoorPair(world, chunkPos, center.add(0, 0, radius), Direction.NORTH)
        buildEntryCourt(world, chunkPos, center, radius)
        setBlock(world, chunkPos, center.up(), Blocks.LECTERN)
        chestSpots += listOf(center.add(0, 0, 0), center.add(6, 0, -2), center.add(-6, 0, 2), center.add(0, 0, -7))
        supplySpots += listOf(center.add(0, 0, 6), center.add(0, 0, -6))
        scatterDebris(world, chunkPos, center, 12, random)
    }

    private fun buildCloister(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        val half = 10
        layFloor(world, chunkPos, center, half, half)
        buildShell(world, chunkPos, center, half, half, 9, random)

        for (dx in -6..6 step 4) {
            buildColumn(world, chunkPos, center.add(dx, 0, -8), 7)
            buildColumn(world, chunkPos, center.add(dx, 0, 8), 7)
        }
        for (dz in -6..6 step 4) {
            buildColumn(world, chunkPos, center.add(-8, 0, dz), 7)
            buildColumn(world, chunkPos, center.add(8, 0, dz), 7)
        }

        buildShelfRow(world, chunkPos, center.add(-8, 0, 0), Direction.EAST, 7, random)
        buildShelfRow(world, chunkPos, center.add(8, 0, 0), Direction.WEST, 7, random)
        buildReadingTable(world, chunkPos, center.add(-2, 0, -2))
        buildReadingTable(world, chunkPos, center.add(2, 0, -2))
        buildReadingTable(world, chunkPos, center.add(-2, 0, 2))
        buildReadingTable(world, chunkPos, center.add(2, 0, 2))
        setBlock(world, chunkPos, center, Blocks.MOSS_BLOCK)
        chestSpots += listOf(center.add(-8, 0, -8), center.add(8, 0, 8), center.add(0, 0, 0), center.add(8, 0, -8))
        supplySpots += listOf(center.add(-8, 0, 8), center.add(8, 0, -8))
        scatterDebris(world, chunkPos, center, 13, random)
    }

    private fun buildScriptorium(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        layFloor(world, chunkPos, center.add(-4, 0, 0), 8, 6)
        layFloor(world, chunkPos, center.add(6, 0, -2), 5, 10)
        buildShell(world, chunkPos, center.add(-4, 0, 0), 8, 6, 8, random)
        buildShell(world, chunkPos, center.add(6, 0, -2), 5, 10, 8, random)

        buildShelfRow(world, chunkPos, center.add(-7, 0, -2), Direction.EAST, 6, random)
        buildShelfRow(world, chunkPos, center.add(6, 0, -5), Direction.WEST, 6, random)
        buildShelfRow(world, chunkPos, center.add(2, 0, 5), Direction.NORTH, 4, random)
        buildReadingTable(world, chunkPos, center.add(-2, 0, 1))
        buildReadingTable(world, chunkPos, center.add(5, 0, -1))
        setBlock(world, chunkPos, center.add(1, 0, -6), Blocks.CAULDRON)
        buildIndexDais(world, chunkPos, center.add(1, 0, 3))
        chestSpots += listOf(center.add(-8, 0, 5), center.add(8, 0, -8), center.add(6, 0, 7), center.add(-2, 0, -5))
        supplySpots += listOf(center.add(-1, 0, -5), center.add(7, 0, 1))
        scatterDebris(world, chunkPos, center, 12, random)
    }

    private fun buildStacks(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        val halfX = 10
        val halfZ = 8
        layFloor(world, chunkPos, center, halfX, halfZ)
        buildShell(world, chunkPos, center, halfX, halfZ, 10, random)
        for (x in -8..8 step 4) {
            buildShelfRow(world, chunkPos, center.add(x, 0, -4), Direction.SOUTH, 9, random)
        }
        buildReadingTable(world, chunkPos, center.add(-3, 0, 3))
        buildReadingTable(world, chunkPos, center.add(3, 0, 3))
        for (x in -4..4 step 4) {
            buildColumn(world, chunkPos, center.add(x, 0, -6), 8)
        }
        chestSpots += listOf(center.add(-8, 0, -6), center.add(8, 0, -6), center.add(0, 0, 6), center.add(0, 0, -6))
        supplySpots += listOf(center.add(-8, 0, 6), center.add(8, 0, 6))
        scatterDebris(world, chunkPos, center, 12, random)
    }

    private fun layFloor(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, halfX: Int, halfZ: Int) {
        for (dx in -halfX..halfX) {
            for (dz in -halfZ..halfZ) {
                setBlock(world, chunkPos, center.add(dx, -1, dz), floorFor(dx, dz))
            }
        }
    }

    private fun buildShell(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        halfX: Int,
        halfZ: Int,
        wallHeight: Int,
        random: net.minecraft.util.math.random.Random
    ) {
        for (dx in -halfX..halfX) {
            for (dz in -halfZ..halfZ) {
                val isWall = dx == -halfX || dx == halfX || dz == -halfZ || dz == halfZ
                if (!isWall) continue
                val doorway = dz == halfZ && abs(dx) <= 1
                val sideWindow = (abs(dx) == halfX && abs(dz) in 3 until halfZ && abs(dz) % 4 == 0)
                val rearWindow = (dz == -halfZ && abs(dx) in 3 until halfX && abs(dx) % 4 == 0)
                for (y in 0..wallHeight) {
                    val pos = center.add(dx, y, dz)
                    if (doorway && y <= 3) {
                        setBlockState(world, chunkPos, pos, Blocks.AIR.defaultState)
                    } else if ((sideWindow || rearWindow) && y in 2..3) {
                        val window = if (random.nextFloat() < 0.8f) Blocks.GRAY_STAINED_GLASS_PANE else Blocks.AIR
                        setBlock(world, chunkPos, pos, window)
                    } else if (random.nextFloat() > 0.02f + y * 0.004f) {
                        setBlock(world, chunkPos, pos, wallFor(dx, dz, y))
                    }
                }
            }
        }

        buildEntranceFrame(world, chunkPos, center, halfZ, wallHeight)
        buildGrandRoof(world, chunkPos, center, halfX, halfZ, wallHeight, random)
        buildExteriorButtresses(world, chunkPos, center, halfX, halfZ)
        placeDoorPair(world, chunkPos, center.add(0, 0, halfZ), Direction.NORTH)
        buildEntryCourt(world, chunkPos, center, halfZ)
    }

    private fun buildColumn(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, height: Int) {
        for (y in 0 until height) {
            setBlock(world, chunkPos, base.up(y), Blocks.CHISELED_STONE_BRICKS)
        }
        setBlock(world, chunkPos, base.up(height), Blocks.STONE_BRICK_SLAB)
    }

    private fun buildShelfRow(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        start: BlockPos,
        facing: Direction,
        length: Int,
        random: net.minecraft.util.math.random.Random
    ) {
        val support = facing.opposite
        for (step in 0 until length) {
            val distance = step * if (facing.direction == Direction.AxisDirection.POSITIVE) 1 else -1
            val pos = start.offset(facing, distance)
            for (y in 0..2) {
                val block = when {
                    y == 2 && random.nextFloat() < 0.22f -> Blocks.AIR
                    y == 1 && random.nextFloat() < 0.15f -> Blocks.OAK_PLANKS
                    else -> Blocks.BOOKSHELF
                }
                setBlock(world, chunkPos, pos.up(y), block)
                setBlock(world, chunkPos, pos.offset(support).up(y), if (y == 2) Blocks.DARK_OAK_PLANKS else Blocks.OAK_PLANKS)
            }
            if (random.nextFloat() < 0.22f) {
                setBlock(world, chunkPos, pos.up(3), Blocks.SPRUCE_SLAB)
                setBlock(world, chunkPos, pos.offset(support).up(3), Blocks.SPRUCE_SLAB)
            }
        }
    }

    private fun buildShelfRing(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, random: net.minecraft.util.math.random.Random) {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                if (max(abs(dx), abs(dz)) != radius) continue
                if (abs(dx) == radius && abs(dz) == radius) continue
                val pos = center.add(dx, 0, dz)
                if (random.nextFloat() < 0.18f) continue
                setBlock(world, chunkPos, pos, Blocks.BOOKSHELF)
                setBlock(world, chunkPos, pos.up(), Blocks.BOOKSHELF)
            }
        }
    }

    private fun buildReadingTable(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos) {
        setBlock(world, chunkPos, center, Blocks.OAK_PLANKS)
        setBlock(world, chunkPos, center.east(), Blocks.OAK_PLANKS)
        setBlock(world, chunkPos, center.west(), Blocks.OAK_PLANKS)
        setBlock(world, chunkPos, center.up(), Blocks.CANDLE)
        setBlockState(world, chunkPos, center.north(), Blocks.SPRUCE_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, Direction.SOUTH))
        setBlockState(world, chunkPos, center.south(), Blocks.SPRUCE_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH))
    }

    private fun buildIndexDais(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos) {
        for (dx in -2..2) {
            for (dz in -1..1) {
                val edge = abs(dx) == 2 || abs(dz) == 1
                setBlock(world, chunkPos, center.add(dx, 0, dz), if (edge) Blocks.CHISELED_STONE_BRICKS else Blocks.SMOOTH_STONE)
            }
        }
        setBlock(world, chunkPos, center.up(), Blocks.CARTOGRAPHY_TABLE)
        setBlock(world, chunkPos, center.east().up(), Blocks.LECTERN)
        setBlock(world, chunkPos, center.west().up(), Blocks.LECTERN)
        setBlock(world, chunkPos, center.north().up(), Blocks.CANDLE)
    }

    private fun buildEntryCourt(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, halfZ: Int) {
        val startZ = halfZ + 1
        for (dz in startZ..startZ + 7) {
            val width = 5 - ((dz - startZ) / 2)
            for (dx in -width..width) {
                val pos = center.add(dx, -1, dz)
                val edge = abs(dx) == width
                setBlock(world, chunkPos, pos, if (edge) Blocks.MOSSY_STONE_BRICKS else Blocks.SMOOTH_STONE)
            }
        }
        for (dx in listOf(-4, 4)) {
            setBlock(world, chunkPos, center.add(dx, 0, startZ + 1), Blocks.STONE_BRICK_WALL)
            setBlock(world, chunkPos, center.add(dx, 1, startZ + 1), Blocks.LANTERN)
            setBlock(world, chunkPos, center.add(dx, 0, startZ + 5), Blocks.STONE_BRICK_WALL)
        }
        for (dx in -2..2) {
            setBlockState(world, chunkPos, center.add(dx, 0, halfZ + 1), Blocks.STONE_BRICK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, Direction.SOUTH))
        }
    }

    private fun scatterDebris(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int, random: net.minecraft.util.math.random.Random) {
        repeat(16 + random.nextInt(12)) {
            val pos = center.add(random.nextInt(radius * 2 + 1) - radius, 0, random.nextInt(radius * 2 + 1) - radius)
            val ground = BlockPos(pos.x, center.y - 1, pos.z)
            val block = when (random.nextInt(4)) {
                0 -> Blocks.STONE_BRICK_SLAB
                1 -> Blocks.CRACKED_STONE_BRICKS
                2 -> Blocks.MOSSY_STONE_BRICKS
                else -> Blocks.OAK_PLANKS
            }
            setBlock(world, chunkPos, ground.up(random.nextInt(2)), block)
        }
    }

    private fun buildEntranceFrame(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        halfZ: Int,
        wallHeight: Int
    ) {
        val entranceZ = center.z + halfZ
        val base = BlockPos(center.x, center.y, entranceZ)
        buildColumn(world, chunkPos, base.add(-2, 0, 0), 4)
        buildColumn(world, chunkPos, base.add(2, 0, 0), 4)
        for (x in -2..2) {
            setBlock(world, chunkPos, base.add(x, 4, 0), Blocks.STONE_BRICK_SLAB)
        }
        for (x in -1..1) {
            setBlock(world, chunkPos, base.add(x, wallHeight + 1, 1), Blocks.STONE_BRICK_STAIRS)
        }
    }

    private fun buildGrandRoof(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        halfX: Int,
        halfZ: Int,
        wallHeight: Int,
        random: net.minecraft.util.math.random.Random
    ) {
        val roofY = center.y + wallHeight + 1
        val tiers = (minOf(halfX, halfZ) / 2).coerceIn(2, 4)

        for (tier in 0..tiers) {
            val tierHalfX = halfX - tier
            val tierHalfZ = halfZ - tier
            val y = roofY + tier
            for (dx in -tierHalfX..tierHalfX) {
                for (dz in -tierHalfZ..tierHalfZ) {
                    val pos = BlockPos(center.x + dx, y, center.z + dz)
                    val edge = abs(dx) == tierHalfX || abs(dz) == tierHalfZ
                    if (edge) {
                        val stairState = when {
                            abs(dx) == tierHalfX -> {
                                val facing = if (dx > 0) Direction.EAST else Direction.WEST
                                Blocks.STONE_BRICK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, facing)
                            }
                            else -> {
                                val facing = if (dz > 0) Direction.SOUTH else Direction.NORTH
                                Blocks.STONE_BRICK_STAIRS.defaultState.with(Properties.HORIZONTAL_FACING, facing)
                            }
                        }
                        setBlockState(world, chunkPos, pos, stairState)
                    } else {
                        val block = if (tier == tiers && (dx == 0 || dz == 0) && random.nextFloat() < 0.8f) {
                            Blocks.TINTED_GLASS
                        } else {
                            if (random.nextFloat() < 0.18f) Blocks.STONE_BRICK_SLAB else Blocks.DARK_OAK_PLANKS
                        }
                        setBlock(world, chunkPos, pos, block)
                    }
                }
            }
        }
    }

    private fun buildRotundaRoof(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        radius: Int,
        wallHeight: Int
    ) {
        val roofBaseY = center.y + wallHeight + 1
        for (tier in 0..3) {
            val tierRadius = radius - tier
            for (dx in -tierRadius..tierRadius) {
                for (dz in -tierRadius..tierRadius) {
                    val dist = dx * dx + dz * dz
                    if (dist > tierRadius * tierRadius) continue
                    val pos = center.add(dx, tier, dz).up(wallHeight + 1)
                    if (dist >= (tierRadius - 1) * (tierRadius - 1)) {
                        setBlock(world, chunkPos, pos, Blocks.STONE_BRICK_SLAB)
                    } else {
                        setBlock(world, chunkPos, pos, if (tier == 3 && dist <= 2) Blocks.TINTED_GLASS else Blocks.DARK_OAK_PLANKS)
                    }
                }
            }
        }
        setBlock(world, chunkPos, BlockPos(center.x, roofBaseY + 4, center.z), Blocks.CHISELED_STONE_BRICKS)
    }

    private fun buildExteriorButtresses(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        halfX: Int,
        halfZ: Int
    ) {
        for (x in -halfX..halfX step 3) {
            setBlock(world, chunkPos, center.add(x, 0, -halfZ - 1), Blocks.STONE_BRICK_WALL)
            setBlock(world, chunkPos, center.add(x, 1, -halfZ - 1), Blocks.STONE_BRICK_WALL)
            setBlock(world, chunkPos, center.add(x, 0, halfZ + 1), Blocks.STONE_BRICK_WALL)
            setBlock(world, chunkPos, center.add(x, 1, halfZ + 1), Blocks.STONE_BRICK_WALL)
        }
        for (z in -halfZ..halfZ step 3) {
            setBlock(world, chunkPos, center.add(-halfX - 1, 0, z), Blocks.STONE_BRICK_WALL)
            setBlock(world, chunkPos, center.add(-halfX - 1, 1, z), Blocks.STONE_BRICK_WALL)
            setBlock(world, chunkPos, center.add(halfX + 1, 0, z), Blocks.STONE_BRICK_WALL)
            setBlock(world, chunkPos, center.add(halfX + 1, 1, z), Blocks.STONE_BRICK_WALL)
        }
    }

    private fun placeDoorPair(world: StructureWorldAccess, chunkPos: ChunkPos, hingeBase: BlockPos, facing: Direction) {
        placeSingleDoor(world, chunkPos, hingeBase.add(-1, 0, 0), facing, true)
        placeSingleDoor(world, chunkPos, hingeBase, facing, false)
    }

    private fun placeSingleDoor(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, facing: Direction, hingeLeft: Boolean) {
        val lower = Blocks.DARK_OAK_DOOR.defaultState
            .with(Properties.HORIZONTAL_FACING, facing)
            .with(Properties.DOOR_HINGE, if (hingeLeft) net.minecraft.block.enums.DoorHinge.LEFT else net.minecraft.block.enums.DoorHinge.RIGHT)
            .with(Properties.DOUBLE_BLOCK_HALF, net.minecraft.block.enums.DoubleBlockHalf.LOWER)
        val upper = lower.with(Properties.DOUBLE_BLOCK_HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER)
        setBlockState(world, chunkPos, pos, lower)
        setBlockState(world, chunkPos, pos.up(), upper)
    }

    private fun placePageChest(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        pos: BlockPos,
        facing: Direction,
        random: net.minecraft.util.math.random.Random
    ) {
        setBlockState(world, chunkPos, pos, Blocks.CHEST.defaultState.with(ChestBlock.FACING, facing))
        val chest = world.getBlockEntity(pos) as? ChestBlockEntity ?: return
        repeat(FeatureBuildHelper.configuredLostPageCount(random, 2, 3)) {
            chest.setStack(random.nextInt(chest.size()), ItemStack(ModItems.LOST_PAGE))
        }
        if (random.nextFloat() < 0.5f) chest.setStack(random.nextInt(chest.size()), ItemStack(Blocks.BOOKSHELF.asItem()))
        if (random.nextFloat() < 0.3f) chest.setStack(random.nextInt(chest.size()), ItemStack(Blocks.LECTERN.asItem()))
        if (random.nextFloat() < 0.45f) chest.setStack(random.nextInt(chest.size()), storyBook(random))
    }

    private fun placeSupplyChest(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        pos: BlockPos,
        facing: Direction,
        random: net.minecraft.util.math.random.Random
    ) {
        setBlockState(world, chunkPos, pos, Blocks.CHEST.defaultState.with(ChestBlock.FACING, facing))
        val chest = world.getBlockEntity(pos) as? ChestBlockEntity ?: return
        val loot = listOf(
            ItemStack(net.minecraft.item.Items.BREAD, 1 + random.nextInt(3)),
            ItemStack(net.minecraft.item.Items.TORCH, 2 + random.nextInt(6)),
            ItemStack(net.minecraft.item.Items.BOOK, 1 + random.nextInt(2)),
            ItemStack(net.minecraft.item.Items.PAPER, 2 + random.nextInt(5)),
            ItemStack(net.minecraft.item.Items.INK_SAC, 1 + random.nextInt(2)),
            ItemStack(net.minecraft.item.Items.FEATHER, 1 + random.nextInt(2)),
            ItemStack(net.minecraft.item.Items.GLASS_BOTTLE, 1 + random.nextInt(2)),
            ItemStack(net.minecraft.item.Items.COAL, 1 + random.nextInt(3)),
            ItemStack(net.minecraft.item.Items.MAP),
            ItemStack(net.minecraft.item.Items.LEATHER, 1 + random.nextInt(2))
        ).shuffled(random.asJavaRandom()).take(2 + random.nextInt(2))

        loot.forEach { chest.setStack(random.nextInt(chest.size()), it) }
        if (random.nextFloat() < 0.35f) {
            chest.setStack(random.nextInt(chest.size()), storyBook(random))
        }
    }

    private fun wallFor(dx: Int, dz: Int, y: Int): Block =
        when ((dx * 31 + dz * 17 + y * 13).mod(6)) {
            0 -> Blocks.MOSSY_STONE_BRICKS
            1 -> Blocks.CRACKED_STONE_BRICKS
            2 -> Blocks.CHISELED_STONE_BRICKS
            else -> Blocks.STONE_BRICKS
        }

    private fun floorFor(dx: Int, dz: Int): Block =
        when ((dx * 19 + dz * 23).mod(4)) {
            0 -> Blocks.MOSSY_STONE_BRICKS
            1 -> Blocks.CRACKED_STONE_BRICKS
            else -> Blocks.STONE_BRICKS
        }

    private fun facingForChest(pos: BlockPos, center: BlockPos): Direction {
        val dx = pos.x - center.x
        val dz = pos.z - center.z
        return if (abs(dx) > abs(dz)) {
            if (dx > 0) Direction.WEST else Direction.EAST
        } else {
            if (dz > 0) Direction.NORTH else Direction.SOUTH
        }
    }

    private fun setBlock(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, block: Block) {
        setBlockState(world, chunkPos, pos, block.defaultState)
    }

    private fun storyBook(random: net.minecraft.util.math.random.Random): ItemStack {
        val titles = listOf(
            "Catalogue Of Ash",
            "The Last Borrower",
            "Index Without Readers",
            "Red Thread Ledger",
            "Silence Between Shelves"
        )
        val passages = listOf(
            listOf("The west stacks were sealed first.", "The doors were found open anyway."),
            listOf("We kept the lamps burning so the returners could find us.", "No one returned."),
            listOf("The catalog remains accurate.", "Only the patrons are missing."),
            listOf("Every map ends at the same margin now.", "Beyond that line there is only weather."),
            listOf("The books survived better than the stone.", "Perhaps they expected this.")
        )
        val choice = random.nextInt(passages.size)
        return ItemStack(Items.WRITTEN_BOOK).also { stack ->
            val pages = NbtList()
            passages[choice].forEach { pages.add(NbtString.of(Text.Serializer.toJson(Text.literal(it)))) }
            val nbt = stack.orCreateNbt
            nbt.putString("title", titles[choice])
            nbt.putString("author", "Archivist")
            nbt.put("pages", pages)
        }
    }

    private fun setBlockState(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, state: BlockState) {
        if ((pos.x shr 4) != chunkPos.x || (pos.z shr 4) != chunkPos.z) {
            DeferredTreePlacer.add(world.toServerWorld(), pos, state, !state.isAir)
            return
        }
        val existing = world.getBlockState(pos)
        if (existing.getHardness(world, pos) < 0.0f) return
        world.setBlockState(pos, state, 2)
    }

    private fun net.minecraft.util.math.random.Random.asJavaRandom(): java.util.Random = java.util.Random(nextLong())

    private enum class ArchiveVariant {
        GRAND_HALL,
        ROTUNDA,
        CLOISTER,
        SCRIPTORIUM,
        STACKS
    }
}
