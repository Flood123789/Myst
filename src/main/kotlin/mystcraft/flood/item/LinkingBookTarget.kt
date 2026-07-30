package mystcraft.flood.item

import net.minecraft.entity.Entity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import kotlin.math.floor
import kotlin.math.round

object LinkingBookTarget {
    private const val SURFACE_Y = "StandingSurfaceY"
    private const val FACING = "Facing"

    fun writeStandingAnchor(world: World, entity: Entity, nbt: NbtCompound) {
        val standingBlock = entity.steppingPos
        val surfaceY = collisionSurfaceY(world, standingBlock) ?: entity.y
        val facing = Direction.getFacing(
            entity.rotationVector.x,
            entity.rotationVector.y,
            entity.rotationVector.z
        )

        nbt.putDouble("PosY", surfaceY)
        nbt.putDouble(SURFACE_Y, surfaceY)
        nbt.putString(FACING, facing.asString())
        nbt.putFloat("Yaw", snapCardinalYaw(entity.yaw))
    }

    /**
     * Resolves old books against the closest collision surface at or below
     * their recorded feet position. New books carry the exact surface height.
     */
    fun resolveArrivalY(world: ServerWorld, nbt: NbtCompound): Double {
        if (nbt.contains(SURFACE_Y)) return nbt.getDouble(SURFACE_Y)

        val rawY = nbt.getDouble("PosY")
        val x = floor(nbt.getDouble("PosX")).toInt()
        val z = floor(nbt.getDouble("PosZ")).toInt()
        val startY = floor(rawY + 1.0E-5).toInt()

        for (y in startY downTo (startY - 4)) {
            val surface = collisionSurfaceY(world, BlockPos(x, y, z)) ?: continue
            if (surface <= rawY + 0.125 && rawY - surface <= 2.0) {
                return surface
            }
        }
        return rawY
    }

    fun resolveCardinalYaw(nbt: NbtCompound): Float = snapCardinalYaw(nbt.getFloat("Yaw"))

    private fun collisionSurfaceY(world: World, pos: BlockPos): Double? {
        val shape = world.getBlockState(pos).getCollisionShape(world, pos)
        if (shape.isEmpty) return null
        return pos.y + shape.getMax(Direction.Axis.Y)
    }
}

internal fun snapCardinalYaw(yaw: Float): Float {
    val snapped = MathHelper.wrapDegrees(round(yaw / 90.0f) * 90.0f)
    return if (snapped == 0.0f) 0.0f else snapped
}
