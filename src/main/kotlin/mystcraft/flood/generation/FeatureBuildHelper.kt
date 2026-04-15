package mystcraft.flood.generation

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.tag.BlockTags
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.Heightmap
import net.minecraft.world.StructureWorldAccess
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object FeatureBuildHelper {
    enum class WaterMode {
        AVOID,
        SEABED,
        SURFACE
    }

    fun findGround(world: StructureWorldAccess, x: Int, z: Int, waterMode: WaterMode = WaterMode.AVOID): BlockPos? {
        val topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z)
        if (topY <= world.bottomY + 1 || topY >= world.topY - 4) return null

        var y = topY - 1
        var sawWater = false
        while (y > world.bottomY + 1) {
            val pos = BlockPos(x, y, z)
            val state = world.getBlockState(pos)
            when {
                state.isOf(Blocks.WATER) -> {
                    sawWater = true
                    if (waterMode == WaterMode.SURFACE) {
                        return pos
                    }
                }
                isSolidGround(state) -> {
                    return when {
                        !sawWater -> pos
                        waterMode == WaterMode.SEABED -> pos
                        else -> null
                    }
                }
            }
            y--
        }

        return null
    }

    fun setBlock(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, block: Block, forceReplace: Boolean = false) {
        setBlockState(world, chunkPos, pos, block.defaultState, forceReplace)
    }

    fun setBlockState(world: StructureWorldAccess, chunkPos: ChunkPos, pos: BlockPos, state: BlockState, forceReplace: Boolean = false) {
        if (pos.y < world.bottomY || pos.y >= world.topY) return

        val finalState = waterlogIfNeeded(world, pos, state)
        if ((pos.x shr 4) != chunkPos.x || (pos.z shr 4) != chunkPos.z) {
            DeferredTreePlacer.add(world.toServerWorld(), pos, finalState, !finalState.isAir)
            return
        }

        val existing = world.getBlockState(pos)
        if (existing.getHardness(world, pos) < 0.0f) return
        if (!canReplace(world, pos, existing, finalState, forceReplace)) return
        world.setBlockState(pos, finalState, 2)
    }

    fun placeChest(
        world: StructureWorldAccess,
        chunkPos: ChunkPos,
        pos: BlockPos,
        facing: Direction,
        filler: (ChestBlockEntity) -> Unit
    ) {
        setBlockState(world, chunkPos, pos, Blocks.CHEST.defaultState.with(ChestBlock.FACING, facing), true)
        val chest = world.getBlockEntity(pos) as? ChestBlockEntity ?: return
        filler(chest)
    }

    fun drawLine(world: StructureWorldAccess, chunkPos: ChunkPos, start: BlockPos, end: BlockPos, state: BlockState, forceReplace: Boolean = false) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val steps = max(max(abs(dx), abs(dy)), abs(dz)).coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val pos = BlockPos(
                lerp(start.x.toDouble(), end.x.toDouble(), t).roundToInt(),
                lerp(start.y.toDouble(), end.y.toDouble(), t).roundToInt(),
                lerp(start.z.toDouble(), end.z.toDouble(), t).roundToInt()
            )
            setBlockState(world, chunkPos, pos, state, forceReplace)
        }
    }

    fun fillColumn(world: StructureWorldAccess, chunkPos: ChunkPos, base: BlockPos, height: Int, state: BlockState, forceReplace: Boolean = false) {
        for (y in 0 until height) {
            setBlockState(world, chunkPos, base.up(y), state, forceReplace)
        }
    }

    fun clearSphere(world: StructureWorldAccess, chunkPos: ChunkPos, center: BlockPos, radius: Int) {
        val radiusSq = radius * radius
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue
                    setBlockState(world, chunkPos, center.add(dx, dy, dz), Blocks.AIR.defaultState, true)
                }
            }
        }
    }

    fun cardinalFacingFrom(pos: BlockPos, center: BlockPos): Direction {
        val dx = pos.x - center.x
        val dz = pos.z - center.z
        return if (abs(dx) > abs(dz)) {
            if (dx > 0) Direction.WEST else Direction.EAST
        } else {
            if (dz > 0) Direction.NORTH else Direction.SOUTH
        }
    }

    fun randomPageLoot(random: net.minecraft.util.math.random.Random): List<ItemStack> = buildList {
        val pageCount = 1 + random.nextInt(2)
        repeat(pageCount) { add(ItemStack(mystcraft.flood.item.ModItems.LOST_PAGE)) }
        if (random.nextFloat() < 0.35f) add(ItemStack(Blocks.BOOKSHELF.asItem()))
        if (random.nextFloat() < 0.30f) add(ItemStack(net.minecraft.item.Items.MAP))
        if (random.nextFloat() < 0.30f) add(ItemStack(net.minecraft.item.Items.COMPASS))
        if (random.nextFloat() < 0.25f) add(ItemStack(net.minecraft.item.Items.SPYGLASS))
    }

    fun randomSupplyLoot(random: net.minecraft.util.math.random.Random): List<ItemStack> = listOf(
        ItemStack(net.minecraft.item.Items.BREAD, 1 + random.nextInt(3)),
        ItemStack(net.minecraft.item.Items.TORCH, 2 + random.nextInt(6)),
        ItemStack(net.minecraft.item.Items.BOOK, 1 + random.nextInt(2)),
        ItemStack(net.minecraft.item.Items.PAPER, 2 + random.nextInt(5)),
        ItemStack(net.minecraft.item.Items.INK_SAC, 1 + random.nextInt(2)),
        ItemStack(net.minecraft.item.Items.FEATHER, 1 + random.nextInt(2)),
        ItemStack(net.minecraft.item.Items.GLASS_BOTTLE, 1 + random.nextInt(2)),
        ItemStack(net.minecraft.item.Items.COAL, 1 + random.nextInt(3)),
        ItemStack(net.minecraft.item.Items.CLOCK),
        ItemStack(net.minecraft.item.Items.LEATHER, 1 + random.nextInt(2))
    ).shuffled(java.util.Random(random.nextLong())).take(2 + random.nextInt(2))

    private fun waterlogIfNeeded(world: StructureWorldAccess, pos: BlockPos, state: BlockState): BlockState {
        if (!state.contains(Properties.WATERLOGGED)) return state
        val fluidState = world.getFluidState(pos)
        return state.with(Properties.WATERLOGGED, fluidState.isStill && fluidState.fluid == net.minecraft.fluid.Fluids.WATER)
    }

    private fun canReplace(world: StructureWorldAccess, pos: BlockPos, existing: BlockState, target: BlockState, forceReplace: Boolean): Boolean {
        if (target.isAir) return true
        if (forceReplace) return existing.getHardness(world, pos) >= 0.0f
        if (existing.isAir || existing.isReplaceable) return true
        if (existing.isIn(BlockTags.LEAVES)) return true
        if (existing.isOf(Blocks.WATER) && target.contains(Properties.WATERLOGGED)) return true
        return existing.isOf(Blocks.GRASS) ||
            existing.isOf(Blocks.TALL_GRASS) ||
            existing.isOf(Blocks.FERN) ||
            existing.isOf(Blocks.LARGE_FERN) ||
            existing.isOf(Blocks.SEAGRASS) ||
            existing.isOf(Blocks.TALL_SEAGRASS) ||
            existing.isOf(Blocks.SNOW)
    }

    private fun isSolidGround(state: BlockState): Boolean =
        !state.isAir && !state.isOf(Blocks.WATER) && !state.isOf(Blocks.LAVA)

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
