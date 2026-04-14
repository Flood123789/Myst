package mystcraft.flood.block

import net.minecraft.block.*
import net.minecraft.block.enums.BlockHalf
import net.minecraft.block.enums.DoorHinge
import net.minecraft.block.enums.SlabType
import net.minecraft.block.enums.StairShape
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.fluid.Fluids
import net.minecraft.item.ItemPlacementContext
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.world.BlockView
import net.minecraft.world.World
import net.minecraft.world.WorldAccess

class WhiteDecaySlabBlock(settings: Settings) : SlabBlock(settings) {
    init {
        defaultState = stateManager.defaultState
            .with(TYPE, SlabType.BOTTOM)
            .with(WATERLOGGED, false)
            .with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }
}

class WhiteDecayStairsBlock(baseState: BlockState, settings: Settings) : StairsBlock(baseState, settings) {
    init {
        defaultState = stateManager.defaultState
            .with(FACING, net.minecraft.util.math.Direction.NORTH)
            .with(HALF, BlockHalf.BOTTOM)
            .with(SHAPE, StairShape.STRAIGHT)
            .with(WATERLOGGED, false)
            .with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }
}

class WhiteDecayFenceBlock(settings: Settings) : FenceBlock(settings) {
    init {
        defaultState = stateManager.defaultState
            .with(NORTH, false)
            .with(EAST, false)
            .with(SOUTH, false)
            .with(WEST, false)
            .with(WATERLOGGED, false)
            .with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState {
        val placed = super.getPlacementState(ctx) ?: defaultState
        return updateConnections(ctx.world, ctx.blockPos, placed)
    }

    override fun getStateForNeighborUpdate(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: WorldAccess,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        if (state[WATERLOGGED]) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world))
        }

        return if (direction.axis.isHorizontal) {
            updateConnections(world, pos, state)
        } else {
            super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos)
        }
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }

    private fun updateConnections(world: BlockView, pos: BlockPos, state: BlockState): BlockState {
        return state
            .with(NORTH, shouldConnect(world, pos.north(), Direction.SOUTH))
            .with(EAST, shouldConnect(world, pos.east(), Direction.WEST))
            .with(SOUTH, shouldConnect(world, pos.south(), Direction.NORTH))
            .with(WEST, shouldConnect(world, pos.west(), Direction.EAST))
    }

    private fun shouldConnect(world: BlockView, neighborPos: BlockPos, facingTowardSelf: Direction): Boolean {
        val neighborState = world.getBlockState(neighborPos)
        val neighborBlock = neighborState.block

        return when {
            neighborBlock is WhiteDecayFenceBlock -> true
            neighborBlock is FenceBlock -> true
            neighborBlock is WallBlock -> true
            neighborBlock is FenceGateBlock -> true
            else -> neighborState.isSideSolidFullSquare(world, neighborPos, facingTowardSelf)
        }
    }
}

class WhiteDecayPaneBlock(settings: Settings) : PaneBlock(settings) {
    init {
        defaultState = stateManager.defaultState
            .with(NORTH, false)
            .with(EAST, false)
            .with(SOUTH, false)
            .with(WEST, false)
            .with(WATERLOGGED, false)
            .with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }
}

class WhiteDecayTrapdoorBlock(settings: Settings, blockSetType: BlockSetType) : TrapdoorBlock(settings, blockSetType) {
    init {
        defaultState = stateManager.defaultState
            .with(FACING, Direction.NORTH)
            .with(OPEN, false)
            .with(HALF, BlockHalf.BOTTOM)
            .with(POWERED, false)
            .with(WATERLOGGED, false)
            .with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }
}

class WhiteDecayDoorBlock(settings: Settings, blockSetType: BlockSetType) : DoorBlock(settings, blockSetType) {
    init {
        defaultState = stateManager.defaultState
            .with(FACING, Direction.NORTH)
            .with(OPEN, false)
            .with(HINGE, DoorHinge.LEFT)
            .with(POWERED, false)
            .with(HALF, DoubleBlockHalf.LOWER)
            .with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }
}

class WhiteDecayTorchBlock(settings: Settings) : TorchBlock(settings, ParticleTypes.ASH) {
    init {
        defaultState = stateManager.defaultState.with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }

    override fun randomDisplayTick(state: BlockState, world: World, pos: BlockPos, random: Random) {}
}

class WhiteDecayWallTorchBlock(settings: Settings) : WallTorchBlock(settings, ParticleTypes.ASH) {
    init {
        defaultState = stateManager.defaultState
            .with(FACING, Direction.NORTH)
            .with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }

    override fun randomDisplayTick(state: BlockState, world: World, pos: BlockPos, random: Random) {}
}

class WhiteDecayLanternBlock(settings: Settings) : LanternBlock(settings) {
    init {
        defaultState = stateManager.defaultState
            .with(HANGING, false)
            .with(WATERLOGGED, false)
            .with(WhiteDecayBlock.ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(WhiteDecayBlock.ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[WhiteDecayBlock.ACTIVE]

    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }
}
