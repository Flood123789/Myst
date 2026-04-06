package mystcraft.flood.block

import mystcraft.flood.block.entity.BookReceptacleBlockEntity
import mystcraft.flood.block.entity.CrystalPortalBlockEntity
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.world.BlockView
import net.minecraft.world.TeleportTarget
import net.minecraft.world.World
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

class CrystalPortalBlock(settings: Settings) : BlockWithEntity(settings) {

    init {
        defaultState = stateManager.defaultState.with(Properties.AXIS, Direction.Axis.Z)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(Properties.AXIS)
    }

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return CrystalPortalBlockEntity(pos, state)
    }

    @Deprecated("Deprecated in Java")
    override fun getRenderType(state: BlockState): BlockRenderType {
        return BlockRenderType.MODEL
    }

    // === NEW: Makes the hitbox a thin slice based on the Axis! ===
    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape {
        return when (state.get(Properties.AXIS)) {
            Direction.Axis.X -> Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0)
            Direction.Axis.Y -> Block.createCuboidShape(0.0, 6.0, 0.0, 16.0, 10.0, 16.0)
            Direction.Axis.Z -> Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0)
            null -> VoxelShapes.empty()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getCollisionShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape {
        return VoxelShapes.empty()
    }

    @Deprecated("Deprecated in Java")
    override fun onStateReplaced(state: BlockState, world: World, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.isOf(newState.block) && !world.isClient) {
            val be = world.getBlockEntity(pos) as? CrystalPortalBlockEntity
            val recPos = be?.receptaclePos
            super.onStateReplaced(state, world, pos, newState, moved)
            
            if (recPos != null) {
                val recBe = world.getBlockEntity(recPos) as? BookReceptacleBlockEntity
                recBe?.extinguishPortal()
            }
        } else {
            super.onStateReplaced(state, world, pos, newState, moved)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean) {
        if (!world.isClient) {
            val be = world.getBlockEntity(pos) as? CrystalPortalBlockEntity
            if (be != null) {
                val recPos = be.receptaclePos
                if (recPos != null) {
                    val recBe = world.getBlockEntity(recPos) as? BookReceptacleBlockEntity
                    if (recBe != null && !recBe.verifyPortalIntegrity()) {
                        recBe.extinguishPortal()
                    }
                }
            }
        }
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify)
    }

    @Deprecated("Deprecated in Java")
    override fun onEntityCollision(state: BlockState, world: World, pos: BlockPos, entity: Entity) {
        if (!world.isClient && !entity.hasVehicle() && !entity.hasPassengers()) {
            if (entity.portalCooldown > 0) return

            val be = world.getBlockEntity(pos) as? CrystalPortalBlockEntity ?: return
            if (be.destinationAge.isEmpty()) return

            val targetKey = RegistryKey.of(RegistryKeys.WORLD, Identifier(be.destinationAge))
            val targetWorld = world.server!!.getWorld(targetKey)
            
            if (targetWorld != null) {
                entity.portalCooldown = 100 
                
                val destVec = if (be.targetX != null && be.targetY != null && be.targetZ != null) {
                    Vec3d(be.targetX!!, be.targetY!!, be.targetZ!!)
                } else {
                    if (entity is LivingEntity) {
                        entity.addStatusEffect(net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOW_FALLING, 300, 0, false, false))
                        entity.addStatusEffect(net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 400, 4, false, false))
                    }
                    Vec3d(0.0, 200.0, 0.0)
                }

                FabricDimensions.teleport(entity, targetWorld, TeleportTarget(
                    destVec, 
                    entity.velocity, entity.yaw, entity.pitch
                ))
            }
        }
    }
}