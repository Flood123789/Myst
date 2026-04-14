package mystcraft.flood.block

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import net.minecraft.world.World

class WhiteDecayBlock(settings: Settings) : Block(settings) {
    companion object {
        val ACTIVE = net.minecraft.state.property.BooleanProperty.of("active")
    }

    init {
        defaultState = stateManager.defaultState.with(ACTIVE, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(ACTIVE)
    }

    override fun hasRandomTicks(state: BlockState): Boolean = state[ACTIVE]

    override fun neighborUpdate(
        state: BlockState,
        world: World,
        pos: BlockPos,
        sourceBlock: Block,
        sourcePos: BlockPos,
        notify: Boolean
    ) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        WhiteDecayLogic.neighborUpdate(this, state, world, pos)
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        WhiteDecayLogic.randomTick(this, state, world, pos, random)
    }
}
