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
import net.minecraft.world.Heightmap
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.max

class AbandonedArchiveFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val random = context.random
        val origin = context.origin
        val serverWorld = world.toServerWorld()

        if (serverWorld.registryKey.value.namespace != MystcraftReforged.MOD_ID) return false
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES) return false

        if (random.nextInt(AgeFeatureTuning.rarityRollDivisor(profile, 420)) != 0) return false

        val centerX = origin.x + 8
        val centerZ = origin.z + 8
        val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, centerX, centerZ)
        if (topY < 50 || topY > 150) return false

        val centerPos = BlockPos(centerX, topY, centerZ)
        if (world.getBlockState(centerPos.down()).isOf(Blocks.WATER)) return false

        val chunkPos = ChunkPos(origin)
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
        val halfX = 8
        val halfZ = 7
        val wallHeight = 8
        layFloor(world, chunkPos, center, halfX, halfZ)
        buildShell(world, chunkPos, center, halfX, halfZ, wallHeight, random)

        for (z in -4..4 step 4) {
            buildShelfRow(world, chunkPos, center.add(-5, 0, z), Direction.EAST, 7, random)
            buildShelfRow(world, chunkPos, center.add(5, 0, z), Direction.WEST, 7, random)
        }
        buildReadingTable(world, chunkPos, center.add(-2, 0, -1))
        buildReadingTable(world, chunkPos, center.add(2, 0, -1))
        buildReadingTable(world, chunkPos, center.add(-2, 0, 3))
        buildReadingTable(world, chunkPos, center.add(2, 0, 3))
        buildColumn(world, chunkPos, center.add(-7, 0, -5), 7)
        buildColumn(world, chunkPos, center.add(7, 0, -5), 7)
        buildColumn(world, chunkPos, center.add(-7, 0, 5), 7)
        buildColumn(world, chunkPos, center.add(7, 0, 5), 7)
        chestSpots += listOf(center.add(-6, 0, -4), center.add(6, 0, 4), center.add(0, 0, 5))
        supplySpots += listOf(center.add(-3, 0, -5), center.add(3, 0, -5))
        scatterDebris(world, chunkPos, center, 10, random)
    }

    private fun buildRotunda(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        val radius = 7
        val wallHeight = 7
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
            val x = listOf(0, 5, 5, 0, -5, -5)[angleIndex]
            val z = listOf(6, 3, -3, -6, -3, 3)[angleIndex]
            buildColumn(world, chunkPos, center.add(x, 0, z), 6)
        }

        buildShelfRing(world, chunkPos, center, radius - 3, random)
        buildRotundaRoof(world, chunkPos, center, radius, wallHeight)
        placeDoorPair(world, chunkPos, center.add(0, 0, radius), Direction.NORTH)
        setBlock(world, chunkPos, center.up(), Blocks.LECTERN)
        chestSpots += listOf(center.add(0, 0, 0), center.add(4, 0, -1), center.add(-4, 0, 1))
        supplySpots += listOf(center.add(0, 0, 4), center.add(0, 0, -4))
        scatterDebris(world, chunkPos, center, 9, random)
    }

    private fun buildCloister(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        val half = 8
        layFloor(world, chunkPos, center, half, half)
        buildShell(world, chunkPos, center, half, half, 7, random)

        for (dx in -6..6 step 4) {
            buildColumn(world, chunkPos, center.add(dx, 0, -6), 6)
            buildColumn(world, chunkPos, center.add(dx, 0, 6), 6)
        }
        for (dz in -6..6 step 4) {
            buildColumn(world, chunkPos, center.add(-6, 0, dz), 6)
            buildColumn(world, chunkPos, center.add(6, 0, dz), 6)
        }

        buildShelfRow(world, chunkPos, center.add(-6, 0, 0), Direction.EAST, 5, random)
        buildShelfRow(world, chunkPos, center.add(6, 0, 0), Direction.WEST, 5, random)
        buildReadingTable(world, chunkPos, center.add(-2, 0, -2))
        buildReadingTable(world, chunkPos, center.add(2, 0, -2))
        buildReadingTable(world, chunkPos, center.add(-2, 0, 2))
        buildReadingTable(world, chunkPos, center.add(2, 0, 2))
        setBlock(world, chunkPos, center, Blocks.MOSS_BLOCK)
        chestSpots += listOf(center.add(-6, 0, -6), center.add(6, 0, 6), center.add(0, 0, 0))
        supplySpots += listOf(center.add(-6, 0, 6), center.add(6, 0, -6))
        scatterDebris(world, chunkPos, center, 11, random)
    }

    private fun buildScriptorium(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        layFloor(world, chunkPos, center.add(-3, 0, 0), 6, 5)
        layFloor(world, chunkPos, center.add(5, 0, -2), 4, 8)
        buildShell(world, chunkPos, center.add(-3, 0, 0), 6, 5, 7, random)
        buildShell(world, chunkPos, center.add(5, 0, -2), 4, 8, 7, random)

        buildShelfRow(world, chunkPos, center.add(-7, 0, -2), Direction.EAST, 6, random)
        buildShelfRow(world, chunkPos, center.add(6, 0, -5), Direction.WEST, 6, random)
        buildShelfRow(world, chunkPos, center.add(2, 0, 5), Direction.NORTH, 4, random)
        buildReadingTable(world, chunkPos, center.add(-2, 0, 1))
        buildReadingTable(world, chunkPos, center.add(5, 0, -1))
        setBlock(world, chunkPos, center.add(1, 0, -6), Blocks.CAULDRON)
        chestSpots += listOf(center.add(-6, 0, 4), center.add(7, 0, -6), center.add(5, 0, 5))
        supplySpots += listOf(center.add(-1, 0, -4), center.add(6, 0, 1))
        scatterDebris(world, chunkPos, center, 10, random)
    }

    private fun buildStacks(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        center: BlockPos,
        chestSpots: MutableList<BlockPos>,
        supplySpots: MutableList<BlockPos>,
        random: net.minecraft.util.math.random.Random
    ) {
        val halfX = 8
        val halfZ = 6
        layFloor(world, chunkPos, center, halfX, halfZ)
        buildShell(world, chunkPos, center, halfX, halfZ, 8, random)
        for (x in -6..6 step 3) {
            buildShelfRow(world, chunkPos, center.add(x, 0, -3), Direction.SOUTH, 7, random)
        }
        buildReadingTable(world, chunkPos, center.add(-3, 0, 3))
        buildReadingTable(world, chunkPos, center.add(3, 0, 3))
        for (x in -4..4 step 4) {
            buildColumn(world, chunkPos, center.add(x, 0, -4), 6)
        }
        chestSpots += listOf(center.add(-6, 0, -4), center.add(6, 0, -4), center.add(0, 0, 4))
        supplySpots += listOf(center.add(-6, 0, 4), center.add(6, 0, 4))
        scatterDebris(world, chunkPos, center, 10, random)
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
        repeat(2 + random.nextInt(2)) {
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
