package mystcraft.flood.generation

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import mystcraft.flood.generation.profile.AgeDimensionRole
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Blocks
import net.minecraft.block.BlockState
import net.minecraft.block.NetherPortalBlock
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.BlockLocating
import net.minecraft.world.dimension.NetherPortal
import net.minecraft.world.TeleportTarget
import net.minecraft.world.World

object AgePortalRouting {
    private val AGE_END_SPAWN = BlockPos(100, 50, 0)

    @JvmStatic
    fun tryCreateNetherPortal(world: World, pos: BlockPos): Boolean {
        val serverWorld = world as? ServerWorld ?: return false
        val currentAgeId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isMystcraftRealm(serverWorld.registryKey)) return false
        if (AgeSubdimensionManager.routeNetherPortal(serverWorld.server, currentAgeId) == null) return false

        val portal =
            NetherPortal.getNewPortal(world, pos, Direction.Axis.X)
                .orElseGet { NetherPortal.getNewPortal(world, pos, Direction.Axis.Z).orElse(null) }
                ?: return false

        portal.createPortal()
        return true
    }

    @JvmStatic
    fun handleNetherPortalCollision(world: World, pos: BlockPos, state: BlockState, entity: Entity): Boolean {
        val serverWorld = world as? ServerWorld ?: return false
        if (!shouldHijackPortal(entity, serverWorld)) return false

        val currentAgeId = serverWorld.registryKey.value
        val targetAgeId = AgeSubdimensionManager.routeNetherPortal(serverWorld.server, currentAgeId) ?: return false
        if (entity.portalCooldown > 0) return true

        val targetWorld = AgeSubdimensionManager.getWorld(serverWorld.server, targetAgeId) ?: return false
        val sourceRole = AgeSubdimensionManager.roleOf(currentAgeId)
        val targetRole = AgeSubdimensionManager.roleOf(targetAgeId)
        val scale = AgeSubdimensionManager.portalCoordinateScale(sourceRole, targetRole)
        val sourceAxis = state.getOrEmpty(NetherPortalBlock.AXIS).orElse(Direction.Axis.X)
        val preferredPos = BlockPos.ofFloored(
            pos.x * scale,
            entity.y.coerceIn(targetWorld.bottomY + 16.0, (targetWorld.topY - 16).toDouble()),
            pos.z * scale
        )
        val teleportTarget = resolveNetherTeleportTarget(entity, serverWorld, targetWorld, pos, state, preferredPos, sourceAxis, targetRole == AgeDimensionRole.NETHER)
            ?: return false

        ImmersivePortalsCompat.trySpawnAgeNetherPortal(
            serverWorld,
            Vec3d(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5),
            targetWorld,
            teleportTarget.position,
            sourceAxis,
            2.0,
            3.0
        )

        return performPortalTeleport(entity, serverWorld, targetWorld, teleportTarget)
    }

    @JvmStatic
    fun handleEndPortalCollision(world: World, entity: Entity): Boolean {
        val serverWorld = world as? ServerWorld ?: return false
        if (!shouldHijackPortal(entity, serverWorld)) return false

        val currentAgeId = serverWorld.registryKey.value
        val targetAgeId = AgeSubdimensionManager.routeEndPortal(serverWorld.server, currentAgeId) ?: return false
        if (entity.portalCooldown > 0) return true

        val targetWorld = AgeSubdimensionManager.getWorld(serverWorld.server, targetAgeId) ?: return false
        val targetRole = AgeSubdimensionManager.roleOf(targetAgeId)
        val teleportTarget = when (targetRole) {
            AgeDimensionRole.END -> {
                ensureEndArrivalPlatform(targetWorld, AGE_END_SPAWN)
                TeleportTarget(Vec3d(AGE_END_SPAWN.x + 0.5, AGE_END_SPAWN.y.toDouble(), AGE_END_SPAWN.z + 0.5), entity.velocity, entity.yaw, entity.pitch)
            }
            AgeDimensionRole.OVERWORLD -> {
                val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, targetAgeId)
                val preferredPos = if (profile.terrainType == TerrainType.BIOSPHERES) {
                    Vec3d(0.5, BiosphereFeature.SAFE_ENTRY_Y.toDouble(), 0.5)
                } else {
                    Vec3d(0.5, 96.0, 0.5)
                }
                val resolved = AgeTravelSafety.resolveAgeSpawn(targetWorld, profile, preferredPos)
                if (resolved.movedAnchor) {
                    profile.ageState.surfaceSpawnX = resolved.anchor.x
                    profile.ageState.surfaceSpawnY = resolved.anchor.y
                    profile.ageState.surfaceSpawnZ = resolved.anchor.z
                    AgeProfileManager.save(serverWorld.server, targetAgeId)
                }
                TeleportTarget(resolved.position, entity.velocity, entity.yaw, entity.pitch)
            }
            AgeDimensionRole.NETHER -> {
                val preferredPos = Vec3d(0.5, 80.0, 0.5)
                TeleportTarget(AgeTravelSafety.sanitizeArrival(targetWorld, preferredPos), entity.velocity, entity.yaw, entity.pitch)
            }
        }
        ImmersivePortalsCompat.trySpawnAgeEndPortal(
            serverWorld,
            entity.pos,
            targetWorld,
            teleportTarget.position
        )
        return performPortalTeleport(entity, serverWorld, targetWorld, teleportTarget)
    }

    private fun shouldHijackPortal(entity: Entity, world: ServerWorld): Boolean {
        if (entity.hasVehicle() || entity.hasPassengers()) return false
        val worldKey: RegistryKey<World> = world.registryKey
        return AgeSubdimensionManager.isMystcraftRealm(worldKey)
    }

    private fun resolveNetherTeleportTarget(
        entity: Entity,
        sourceWorld: ServerWorld,
        targetWorld: ServerWorld,
        sourcePortalPos: BlockPos,
        sourcePortalState: BlockState,
        preferredTargetPos: BlockPos,
        sourceAxis: Direction.Axis,
        destinationIsNether: Boolean
    ): TeleportTarget? {
        val sourceRect = BlockLocating.getLargestRectangle(
            sourcePortalPos,
            sourceAxis,
            21,
            Direction.Axis.Y,
            21
        ) { testPos ->
            val testState = sourceWorld.getBlockState(testPos)
            testState.isOf(sourcePortalState.block) && testState.getOrEmpty(NetherPortalBlock.AXIS).orElse(sourceAxis) == sourceAxis
        }
        val portalOffset = NetherPortal.entityPosInPortal(
            sourceRect,
            sourceAxis,
            entity.pos,
            entity.getDimensions(entity.pose)
        )
        val targetRect = targetWorld.portalForcer
            .getPortalRect(preferredTargetPos, destinationIsNether, targetWorld.worldBorder)
            .orElseGet { targetWorld.portalForcer.createPortal(preferredTargetPos, sourceAxis).orElse(null) }
            ?: return null

        return NetherPortal.getNetherTeleportTarget(
            targetWorld,
            targetRect,
            sourceAxis,
            portalOffset,
            entity,
            entity.velocity,
            entity.yaw,
            entity.pitch
        )
    }

    private fun performPortalTeleport(entity: Entity, sourceWorld: ServerWorld, targetWorld: ServerWorld, destination: TeleportTarget): Boolean {
        entity.portalCooldown = 100
        AgeTravelEffects.playDeparture(sourceWorld, entity.pos)
        val result = FabricDimensions.teleport(entity, targetWorld, destination)
        if (result != null) {
            AgeTravelEffects.playArrival(targetWorld, destination.position)
            return true
        }
        return false
    }

    private fun ensureEndArrivalPlatform(world: ServerWorld, center: BlockPos) {
        for (dx in -2..2) {
            for (dz in -2..2) {
                world.setBlockState(center.add(dx, -1, dz), Blocks.OBSIDIAN.defaultState, 3)
                for (dy in 0..3) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.AIR.defaultState, 3)
                }
            }
        }
    }
}
