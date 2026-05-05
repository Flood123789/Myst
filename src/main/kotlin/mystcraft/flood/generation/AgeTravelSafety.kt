package mystcraft.flood.generation

import mystcraft.flood.MystcraftReforged
import net.minecraft.block.Blocks
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.TerrainType

object AgeTravelSafety {
    private const val INTERIOR_CEILING_LIMIT = 40

    data class AgeSpawnResult(val position: Vec3d, val anchor: BlockPos, val movedAnchor: Boolean)

    fun sanitizeArrival(world: ServerWorld, requestedPos: Vec3d): Vec3d {
        val exactBase = BlockPos.ofFloored(requestedPos)
        if (isSafeStand(world, exactBase)) {
            return Vec3d(exactBase.x + 0.5, exactBase.y.toDouble(), exactBase.z + 0.5)
        }

        findNearbySafeStand(world, exactBase, maxRadius = 10, preferSurface = true)?.let { safe ->
            return Vec3d(safe.x + 0.5, safe.y.toDouble(), safe.z + 0.5)
        }

        return buildEmergencyStand(world, exactBase)
    }

    fun resolveAgeSpawn(world: ServerWorld, profile: AgeProfile, preferredPos: Vec3d): AgeSpawnResult {
        val currentAnchor = profile.ageState.surfaceSpawnX?.let { x ->
            val y = profile.ageState.surfaceSpawnY ?: return@let null
            val z = profile.ageState.surfaceSpawnZ ?: return@let null
            BlockPos(x, y, z)
        }

        val preferred = currentAnchor ?: BlockPos.ofFloored(preferredPos)
        val resolved = when (profile.terrainType) {
            TerrainType.CAVES -> findNearbyInteriorStand(world, preferred, maxRadius = 48)
                ?: findNearbySafeStand(world, preferred, maxRadius = 24, preferSurface = false)
                ?: findNearbySurfaceStand(world, preferred, maxRadius = 48)
            else -> findNearbySurfaceStand(world, preferred, maxRadius = 48)
                ?: findNearbySafeStand(world, preferred, maxRadius = 24, preferSurface = false)
        } ?: if (profile.terrainType == TerrainType.CAVES) {
            findNearbyInteriorStand(world, BlockPos(0, preferred.y, 0), maxRadius = 160)
        } else {
            null
        } ?: findNearbySurfaceStand(world, BlockPos(0, preferred.y, 0), maxRadius = 160)
            ?: findNearbySafeStand(world, BlockPos(0, preferred.y, 0), maxRadius = 96, preferSurface = false)

        val finalAnchor = resolved ?: BlockPos.ofFloored(buildEmergencyStand(world, preferred))
        val moved = currentAnchor == null || currentAnchor != finalAnchor
        return AgeSpawnResult(
            Vec3d(finalAnchor.x + 0.5, finalAnchor.y.toDouble(), finalAnchor.z + 0.5),
            finalAnchor,
            moved
        )
    }

    fun resolveBoundRespawn(world: ServerWorld, requestedAnchor: BlockPos): BlockPos? {
        val resolved = PlayerEntity.findRespawnPosition(world, requestedAnchor, 0.0f, false, false)
        return resolved
            .map { BlockPos.ofFloored(it) }
            .filter { !isDecayState(world.getBlockState(it.down())) && !isDecayState(world.getBlockState(it)) }
            .orElse(null)
    }

    fun resolveCustomBoundRespawn(world: ServerWorld, requestedAnchor: BlockPos, angle: Float): BlockPos {
        val vanillaResolved = PlayerEntity.findRespawnPosition(world, requestedAnchor, angle, true, false)
            .map { BlockPos.ofFloored(it) }
            .filter { !isDecayState(world.getBlockState(it.down())) && !isDecayState(world.getBlockState(it)) }
            .orElse(null)
        if (vanillaResolved != null) {
            return vanillaResolved
        }

        val requestedStand = requestedAnchor.up()
        if (isSafeStand(world, requestedStand)) {
            return requestedStand
        }

        return findNearbySafeStand(world, requestedStand, maxRadius = 4, preferSurface = false)
            ?: findNearbySurfaceStand(world, requestedStand, maxRadius = 8)
            ?: BlockPos.ofFloored(buildEmergencyStand(world, requestedStand))
    }

    fun resolveForcedStandRespawn(world: ServerWorld, feetPos: BlockPos): BlockPos {
        if (isSafeStand(world, feetPos)) {
            return feetPos
        }

        return findNearbySafeStand(world, feetPos, maxRadius = 4, preferSurface = false)
            ?: findNearbySurfaceStand(world, feetPos, maxRadius = 8)
            ?: BlockPos.ofFloored(buildEmergencyStand(world, feetPos))
    }

    private fun findNearbySafeStand(world: ServerWorld, origin: BlockPos, maxRadius: Int, preferSurface: Boolean): BlockPos? {
        for (radius in 0..maxRadius) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (radius > 0 && kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) continue

                    val x = origin.x + dx
                    val z = origin.z + dz

                    if (preferSurface) {
                        val topPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos(x, world.bottomY, z))
                        if (isSafeStand(world, topPos)) {
                            return topPos
                        }
                    }

                    for (dy in 12 downTo -64) {
                        val candidate = BlockPos(x, (origin.y + dy).coerceIn(world.bottomY + 1, world.topY - 3), z)
                        if (isSafeStand(world, candidate)) {
                            return candidate
                        }
                    }
                }
            }
        }
        return null
    }

    private fun findNearbyInteriorStand(world: ServerWorld, origin: BlockPos, maxRadius: Int): BlockPos? {
        for (radius in 0..maxRadius) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (radius > 0 && kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) continue

                    val x = origin.x + dx
                    val z = origin.z + dz
                    findInteriorStandInColumn(world, x, z)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findNearbySurfaceStand(world: ServerWorld, origin: BlockPos, maxRadius: Int): BlockPos? {
        for (radius in 0..maxRadius) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (radius > 0 && kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) continue

                    val x = origin.x + dx
                    val z = origin.z + dz
                    val topPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos(x, world.bottomY, z))
                    if (topPos.y <= world.bottomY + 1) continue
                    if (isSafeStand(world, topPos)) {
                        return topPos
                    }
                }
            }
        }
        return null
    }

    private fun findInteriorStandInColumn(world: ServerWorld, x: Int, z: Int): BlockPos? {
        val highestFeet = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)
            .coerceAtMost(world.topY - 3)
        if (highestFeet <= world.bottomY + 1) return null

        for (y in highestFeet downTo world.bottomY + 2) {
            val candidate = BlockPos(x, y, z)
            if (isSafeStand(world, candidate) && hasNearbyCeiling(world, candidate, INTERIOR_CEILING_LIMIT)) {
                return candidate
            }
        }
        return null
    }

    private fun isSafeStand(world: ServerWorld, feetPos: BlockPos): Boolean {
        val below = feetPos.down()
        val head = feetPos.up()
        val above = feetPos.up(2)

        val floorState = world.getBlockState(below)
        if (floorState.isAir || !floorState.isOpaqueFullCube(world, below) || isDecayState(floorState)) return false

        return !isDecayState(world.getBlockState(feetPos)) &&
            !isDecayState(world.getBlockState(head)) &&
            !isDecayState(world.getBlockState(above)) &&
            isPassableForArrival(world, feetPos) &&
            isPassableForArrival(world, head) &&
            isPassableForArrival(world, above)
    }

    private fun isPassableForArrival(world: ServerWorld, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.fluidState.isEmpty && (state.isAir || state.getCollisionShape(world, pos).isEmpty)
    }

    private fun hasNearbyCeiling(world: ServerWorld, feetPos: BlockPos, maxHeight: Int): Boolean {
        for (offset in 3..maxHeight) {
            val sample = feetPos.up(offset)
            if (sample.y >= world.topY) break
            if (!isPassableForArrival(world, sample)) {
                return true
            }
        }
        return false
    }

    private fun isDecayState(state: net.minecraft.block.BlockState): Boolean {
        val id = Registries.BLOCK.getId(state.block)
        return id.namespace == MystcraftReforged.MOD_ID &&
            (id.path == "black_decay" || id.path.startsWith("white_decay"))
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
