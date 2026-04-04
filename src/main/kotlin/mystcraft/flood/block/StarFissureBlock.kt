package mystcraft.flood.block

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World

class StarFissureBlock(settings: Settings) : Block(settings) {

    // Make the hitbox slightly lower than a full block so the player physically falls "into" it
    override fun getCollisionShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape {
        return VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.9, 1.0)
    }

    override fun onEntityCollision(state: BlockState, world: World, pos: BlockPos, entity: Entity) {
        if (world.isClient || entity !is ServerPlayerEntity) return

        // Make sure they actually fell into it, not just brushed against the side
        if (entity.y < pos.y + 0.95) {
            val server = world.server ?: return
            val overworld = server.getWorld(World.OVERWORLD) ?: return
            
            // Get the default world spawn point
            val spawnPos = overworld.spawnPos

            // Teleport them across dimensions!
            entity.teleport(
                overworld,
                spawnPos.x.toDouble() + 0.5,
                spawnPos.y.toDouble(), // Teleport exactly to the spawn Y level
                spawnPos.z.toDouble() + 0.5,
                entity.yaw,
                entity.pitch
            )
        }
    }
}