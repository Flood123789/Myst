package mystcraft.flood.generation

import net.minecraft.block.Blocks
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap

object AgeTravelSafety {
    fun sanitizeArrival(world: ServerWorld, requestedPos: Vec3d): Vec3d {
        val exactBase = BlockPos.ofFloored(requestedPos)
        if (isSafeStand(world, exactBase)) {
            return Vec3d(exactBase.x + 0.5, exactBase.y.toDouble(), exactBase.z + 0.5)
        }

        findNearbySafeStand(world, exactBase)?.let { safe ->
            return Vec3d(safe.x + 0.5, safe.y.toDouble(), safe.z + 0.5)
        }

        return buildEmergencyStand(world, exactBase)
    }

    private fun findNearbySafeStand(world: ServerWorld, origin: BlockPos): BlockPos? {
        for (radius in 0..10) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (radius > 0 && kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) continue

                    val x = origin.x + dx
                    val z = origin.z + dz
                    val surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)
                    val candidate = BlockPos(x, surfaceY + 1, z)
                    if (isSafeStand(world, candidate)) {
                        return candidate
                    }
                }
            }
        }
        return null
    }

    private fun isSafeStand(world: ServerWorld, feetPos: BlockPos): Boolean {
        val below = feetPos.down()
        val head = feetPos.up()
        val above = feetPos.up(2)

        val floorState = world.getBlockState(below)
        if (floorState.isAir || !floorState.isOpaqueFullCube(world, below)) return false

        return isPassableForArrival(world, feetPos) &&
            isPassableForArrival(world, head) &&
            isPassableForArrival(world, above)
    }

    private fun isPassableForArrival(world: ServerWorld, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.isAir || state.fluidState.isEmpty.not() || state.getCollisionShape(world, pos).isEmpty
    }

    private fun buildEmergencyStand(world: ServerWorld, origin: BlockPos): Vec3d {
        val feet = BlockPos(origin.x, origin.y.coerceIn(world.bottomY + 2, world.topY - 3), origin.z)
        val below = feet.down()

        if (!world.getBlockState(below).isOpaqueFullCube(world, below)) {
            world.setBlockState(below, Blocks.SMOOTH_STONE.defaultState, 3)
        }

        for (clearPos in listOf(feet, feet.up(), feet.up(2))) {
            val state = world.getBlockState(clearPos)
            if (state.getHardness(world, clearPos) >= 0.0f) {
                world.setBlockState(clearPos, Blocks.AIR.defaultState, 3)
            }
        }

        return Vec3d(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
    }
}
