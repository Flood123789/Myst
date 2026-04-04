package mystcraft.flood.block

import mystcraft.flood.generation.profile.AgeProfileManager 
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.FallingBlockEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.world.World

class DecayBlock(settings: Settings) : Block(settings) {

    override fun hasRandomTicks(state: BlockState): Boolean = true

    override fun neighborUpdate(
        state: BlockState,
        world: World,
        pos: BlockPos,
        sourceBlock: Block,
        sourcePos: BlockPos,
        notify: Boolean
    ) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
        if (world.isClient) return
        
        // Cast to ServerWorld so we can check the Age Profile
        val serverWorld = world as? ServerWorld ?: return

        // THE DORMANT CHECK: If the age is perfectly stable, do nothing!
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (profile.stability.isStable || profile.stability.instabilityScore <= 0) return

        val neighborFluid = world.getFluidState(sourcePos)
        if (!neighborFluid.isEmpty) {
            if (DecayManager.requestPermission()) {
                eatFluid(serverWorld, sourcePos, 0)
            }
        }
    }

    override fun randomTick(state: BlockState, world: ServerWorld, pos: BlockPos, random: Random) {
        if (world.isClient) return

        // THE DORMANT CHECK: If the age is perfectly stable, the rot sleeps!
        val profile = AgeProfileManager.getOrGenerateProfile(world.server, world.registryKey.value)
        if (profile.stability.isStable || profile.stability.instabilityScore <= 0) return

        // LAG SAVER
        if (!DecayManager.requestPermission()) return

        // 0. SPILL-OVER LOGIC 
        if (world.getBlockState(pos.down()).isOf(this)) {
            val horizontalDirs = Direction.Type.HORIZONTAL.toList().shuffled()
            for (dir in horizontalDirs) {
                val sidePos = pos.offset(dir)
                val sideState = world.getBlockState(sidePos)
                
                // FISSURE IMMUNITY: Don't eat the Star Fissure!
                if (!sideState.isAir && !sideState.isOf(this) && !sideState.isOf(ModBlocks.STAR_FISSURE)) {
                    world.setBlockState(sidePos, this.defaultState, 3)
                    break 
                }
            }
            world.setBlockState(pos, Blocks.AIR.defaultState, 3)
            return
        }

        // 1. DISSOLVE & CAVE-IN LOGIC 
        if (random.nextFloat() < 0.15f) {
            world.setBlockState(pos, Blocks.AIR.defaultState, 3)
            triggerCollapse(world, pos)
            return
        }

        // 2. DOWNWARD SPREAD
        if (random.nextFloat() < 0.60f) {
            val downPos = pos.down()
            val downState = world.getBlockState(downPos)
            // FISSURE IMMUNITY
            if (!downState.isAir && !downState.isOf(this) && !downState.isOf(ModBlocks.STAR_FISSURE)) {
                world.setBlockState(downPos, this.defaultState, 3)
            }
        }

        // 3. LATERAL SPREAD
        if (random.nextFloat() < 0.20f) {
            val horizontalDirs = Direction.Type.HORIZONTAL.toList().shuffled()
            for (dir in horizontalDirs) {
                val sidePos = pos.offset(dir)
                val sideState = world.getBlockState(sidePos)
                // FISSURE IMMUNITY
                if (!sideState.isAir && !sideState.isOf(this) && !sideState.isOf(ModBlocks.STAR_FISSURE)) {
                    world.setBlockState(sidePos, this.defaultState, 3)
                    break 
                }
            }
        }

        // 4. PASSIVE THIRST LOGIC
        for (dir in Direction.entries) {
            val adjPos = pos.offset(dir)
            if (!world.getFluidState(adjPos).isEmpty) {
                eatFluid(world, adjPos, 0)
            }
        }
    }

    private fun eatFluid(world: ServerWorld, pos: BlockPos, depth: Int) {
        if (depth > 12) return 
        
        val fluid = world.getFluidState(pos)

        if (!fluid.isEmpty) {
            if (fluid.isStill) {
                world.setBlockState(pos, this.defaultState, 3)
            } else {
                world.setBlockState(pos, Blocks.AIR.defaultState, 3)
                for (dir in Direction.entries) {
                    if (dir == Direction.DOWN) continue 
                    val nextPos = pos.offset(dir)
                    if (!world.getFluidState(nextPos).isEmpty) {
                        eatFluid(world, nextPos, depth + 1)
                    }
                }
            }
        }
    }

    private fun triggerCollapse(world: ServerWorld, startPos: BlockPos) {
        var currentPos = startPos.up()
        
        while (currentPos.y < world.topY) {
            val state = world.getBlockState(currentPos)
            
            // FISSURE IMMUNITY: Stop pulling blocks if we hit Decay or a Star Fissure
            if (state.isOf(this) || state.isOf(ModBlocks.STAR_FISSURE)) {
                break
            }

            if (!state.isAir) {
                if (!DecayManager.requestPermission(5)) break 

                FallingBlockEntity.spawnFromBlock(world, currentPos, state)
                world.setBlockState(currentPos, Blocks.AIR.defaultState, 3)
            }
            
            currentPos = currentPos.up()
        }
    }
}