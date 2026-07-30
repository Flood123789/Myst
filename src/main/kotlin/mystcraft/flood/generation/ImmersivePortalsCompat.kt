package mystcraft.flood.generation

import mystcraft.flood.MystcraftReforged
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.network.packet.Packet
import net.minecraft.server.world.ServerWorld
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

import net.minecraft.server.MinecraftServer
import net.minecraft.world.dimension.DimensionOptions

object ImmersivePortalsCompat {
    val isAvailable: Boolean by lazy {
        val loader = FabricLoader.getInstance()
        loader.isModLoaded("imm_ptl_core") ||
            loader.isModLoaded("imm_ptl") ||
            loader.isModLoaded("immersive_portals")
    }

    fun hasPortalAt(world: ServerWorld, pos: BlockPos): Boolean {
        if (!isAvailable) return false

        return runCatching {
            val portalClass = Class.forName("qouteall.imm_ptl.core.portal.Portal")
            world.getOtherEntities(null, Box(pos).expand(0.25)) { portalClass.isInstance(it) }.isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * Sends a world-scoped packet through IP's redirected packet envelope.
     * Directly sending a time packet through the player's network handler is
     * dimensionless; if it arrives while a portal transition is swapping the
     * active ClientWorld, an Age night can be applied to the Overworld.
     */
    fun trySendWorldPacket(player: ServerPlayerEntity, world: ServerWorld, packet: Packet<*>): Boolean {
        if (!isAvailable) return false

        return runCatching {
            val redirectionClass = Class.forName("qouteall.imm_ptl.core.network.PacketRedirection")
            val method = redirectionClass.getMethod(
                "sendRedirectedMessage",
                ServerPlayerEntity::class.java,
                RegistryKey::class.java,
                Packet::class.java
            )
            method.invoke(null, player, world.registryKey, packet)
            true
        }.onFailure {
            MystcraftReforged.LOGGER.warn(
                "Failed to send Immersive Portals world-scoped packet for {}",
                world.registryKey.value,
                it
            )
        }.getOrDefault(false)
    }

    fun trySpawnCrystalPortal(
        world: ServerWorld,
        shape: Set<BlockPos>,
        axis: Direction.Axis,
        destinationAge: String,
        targetPos: Vec3d?,
        destinationRotationQuarterTurns: Int,
        destinationYaw: Float?
    ): Boolean {
        if (!isAvailable || shape.isEmpty()) return false

        return runCatching {
            val destId = if (!destinationAge.contains(":")) "mystcraft-reforged:$destinationAge" else destinationAge
            val targetKey = RegistryKey.of(RegistryKeys.WORLD, Identifier(destId))
            val targetWorld = world.server.getWorld(targetKey) ?: return@runCatching false

            val minX = shape.minOf { it.x }
            val maxX = shape.maxOf { it.x }
            val minY = shape.minOf { it.y }
            val maxY = shape.maxOf { it.y }
            val minZ = shape.minOf { it.z }
            val maxZ = shape.maxOf { it.z }

            val center = Vec3d(
                (minX + maxX + 1) * 0.5,
                (minY + maxY + 1) * 0.5,
                (minZ + maxZ + 1) * 0.5
            )

            val plane = crystalPortalPlane(axis, minX, maxX, minY, maxY, minZ, maxZ)

            val baseArrival = targetPos ?: run {
                val profile = mystcraft.flood.generation.profile.AgeProfileManager.getOrGenerateProfile(targetWorld.server, targetWorld.registryKey.value)
                val spawnX = (profile.ageState.surfaceSpawnX ?: 0.5).toDouble()
                val spawnY = (profile.ageState.surfaceSpawnY ?: targetWorld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0)).toDouble()
                val spawnZ = (profile.ageState.surfaceSpawnZ ?: 0.5).toDouble()
                Vec3d(spawnX, spawnY, spawnZ)
            }

            // A vertical portal maps its bottom edge to the linking-book feet
            // position. A floor portal behaves like IP's End portal and maps
            // its plane directly to that position.
            val destinationCenter = if (axis == Direction.Axis.Y) {
                baseArrival
            } else {
                Vec3d(baseArrival.x, baseArrival.y + (plane.height * 0.5), baseArrival.z)
            }

            spawnReflectivePortal(
                world, targetWorld, center, destinationCenter, axis, plane,
                destinationRotationQuarterTurns, destinationYaw,
                biFacedSource = axis != Direction.Axis.Y
            )
        }.onFailure {
            MystcraftReforged.LOGGER.debug("Immersive Portals compatibility not active or failed for Crystal Portal: ${it.message}")
        }.getOrDefault(false)
    }

    fun trySpawnAgeNetherPortal(
        sourceWorld: ServerWorld,
        portalCenter: Vec3d,
        targetWorld: ServerWorld,
        targetPos: Vec3d,
        axis: Direction.Axis,
        width: Double,
        height: Double
    ): Boolean {
        if (!isAvailable) return false
        return runCatching {
            val destCenter = Vec3d(targetPos.x, targetPos.y + (height * 0.5), targetPos.z)
            spawnReflectivePortal(
                sourceWorld,
                targetWorld,
                portalCenter,
                destCenter,
                axis,
                portalPlaneForSize(axis, width, height),
                0,
                biFacedSource = true
            )
        }.getOrDefault(false)
    }

    fun trySpawnAgeEndPortal(
        sourceWorld: ServerWorld,
        portalCenter: Vec3d,
        targetWorld: ServerWorld,
        targetPos: Vec3d
    ): Boolean {
        if (!isAvailable) return false
        return runCatching {
            val destCenter = Vec3d(targetPos.x, targetPos.y + 1.5, targetPos.z)
            spawnReflectivePortal(
                sourceWorld,
                targetWorld,
                portalCenter,
                destCenter,
                Direction.Axis.Z,
                portalPlaneForSize(Direction.Axis.Z, 3.0, 3.0),
                0,
                biFacedSource = false
            )
        }.getOrDefault(false)
    }

    private fun spawnReflectivePortal(
        sourceWorld: ServerWorld,
        targetWorld: ServerWorld,
        origin: Vec3d,
        destination: Vec3d,
        axis: Direction.Axis,
        plane: CrystalPortalPlane,
        destinationRotationQuarterTurns: Int,
        destinationYaw: Float? = null,
        biFacedSource: Boolean = false
    ): Boolean {
        val portalClass = Class.forName("qouteall.imm_ptl.core.portal.Portal")
        val entityTypeField = portalClass.getField("entityType")
        val entityType = entityTypeField.get(null) as net.minecraft.entity.EntityType<*>

        val portalEntity = entityType.create(sourceWorld) ?: return false
        portalEntity.setPosition(origin.x, origin.y, origin.z)
        portalClass.getMethod("setOriginPos", Vec3d::class.java).invoke(portalEntity, origin)
        portalClass.getMethod("setDestinationDimension", RegistryKey::class.java).invoke(portalEntity, targetWorld.registryKey)
        portalClass.getMethod("setDestination", Vec3d::class.java).invoke(portalEntity, destination)

        portalClass.getMethod("setOrientationAndSize", Vec3d::class.java, Vec3d::class.java, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
            .invoke(portalEntity, plane.axisW, plane.axisH, plane.width, plane.height)

        val quarterTurns = Math.floorMod(destinationRotationQuarterTurns, 4)
        val rotationDegrees = if (destinationYaw != null && axis != Direction.Axis.Y) {
            // A linking book remembers the horizontal direction its author was
            // facing. Map the portal's inward travel direction to that saved
            // exit direction, then apply the receptacle's quarter-turn offset.
            // This stays a yaw-only rotation so VR never gains roll or pitch.
            immersivePortalYawRotation(plane, destinationYaw, quarterTurns)
        } else {
            -quarterTurns * 90.0
        }
        if (rotationDegrees != 0.0) {
            val quaternionClass = Class.forName("qouteall.q_misc_util.my_util.DQuaternion")
            val rotation = quaternionClass.getMethod(
                "rotationByDegrees",
                Vec3d::class.java,
                Double::class.javaPrimitiveType
            ).invoke(null, Vec3d(0.0, 1.0, 0.0), rotationDegrees)
            portalClass.getMethod("setRotation", quaternionClass).invoke(portalEntity, rotation)
        }

        // A lone IP Portal is visible and traversable only from its front.
        // Vertical Mystcraft frames should work from either side like a
        // Nether portal, but remain one-way like an End portal: create only
        // the flipped source face, never a reverse portal in the Age.
        val flippedPortal = if (biFacedSource) {
            val portalApiClass = Class.forName("qouteall.imm_ptl.core.api.PortalAPI")
            portalApiClass.getMethod("createFlippedPortal", portalClass)
                .invoke(null, portalEntity) as? net.minecraft.entity.Entity
        } else {
            null
        }

        if (!sourceWorld.spawnEntity(portalEntity)) return false
        if (flippedPortal != null && !sourceWorld.spawnEntity(flippedPortal)) {
            portalEntity.discard()
            return false
        }
        MystcraftReforged.LOGGER.info("Created Immersive Portal bridging ${sourceWorld.registryKey.value} -> ${targetWorld.registryKey.value}")
        return true
    }

    fun removePortalsAround(world: ServerWorld, shape: Set<BlockPos>) {
        if (!isAvailable || shape.isEmpty()) return
        runCatching {
            val minX = shape.minOf { it.x }.toDouble() - 4.0
            val maxX = shape.maxOf { it.x }.toDouble() + 4.0
            val minY = shape.minOf { it.y }.toDouble() - 4.0
            val maxY = shape.maxOf { it.y }.toDouble() + 4.0
            val minZ = shape.minOf { it.z }.toDouble() - 4.0
            val maxZ = shape.maxOf { it.z }.toDouble() + 4.0
            val box = Box(minX, minY, minZ, maxX, maxY, maxZ)

            val entities = world.getOtherEntities(null, box) { e ->
                val className = e.javaClass.name
                className.contains("imm_ptl") || className.contains("Portal") || e.type.toString().contains("portal")
            }
            entities.forEach { e ->
                e.discard()
            }
        }.onFailure {
            MystcraftReforged.LOGGER.debug("Failed removing Immersive Portal entities: ${it.message}")
        }
    }

    fun tryAddDimensionDynamically(server: MinecraftServer, ageId: Identifier, options: DimensionOptions): Boolean {
        if (!isAvailable) return false
        return runCatching {
            val apiClass = Class.forName("qouteall.q_misc_util.api.DimensionAPI")
            val method = apiClass.methods.firstOrNull { m ->
                m.name == "addDimensionDynamically" && (m.parameterCount == 2 || m.parameterCount == 3)
            } ?: apiClass.methods.firstOrNull { m ->
                m.name == "addDimension" && (m.parameterCount == 2 || m.parameterCount == 3)
            }

            if (method != null) {
                if (method.parameterCount == 2) {
                    method.invoke(null, ageId, options)
                } else {
                    method.invoke(null, server, ageId, options)
                }
                MystcraftReforged.LOGGER.info("Dynamically added dimension $ageId via Immersive Portals DimensionAPI")
                true
            } else {
                MystcraftReforged.LOGGER.warn("Immersive Portals DimensionAPI method not found.")
                false
            }
        }.onFailure {
            MystcraftReforged.LOGGER.error("Failed to add dimension via Immersive Portals DimensionAPI: ${it.message}", it)
        }.getOrDefault(false)
    }

    private fun portalPlaneForSize(axis: Direction.Axis, width: Double, height: Double): CrystalPortalPlane =
        when (axis) {
            Direction.Axis.X -> CrystalPortalPlane(
                axisW = Vec3d(0.0, 0.0, 1.0),
                axisH = Vec3d(0.0, 1.0, 0.0),
                width = width,
                height = height
            )
            Direction.Axis.Z -> CrystalPortalPlane(
                axisW = Vec3d(1.0, 0.0, 0.0),
                axisH = Vec3d(0.0, 1.0, 0.0),
                width = width,
                height = height
            )
            Direction.Axis.Y -> CrystalPortalPlane(
                axisW = Vec3d(0.0, 0.0, 1.0),
                axisH = Vec3d(1.0, 0.0, 0.0),
                width = width,
                height = height
            )
        }
}

internal data class CrystalPortalPlane(
    val axisW: Vec3d,
    val axisH: Vec3d,
    val width: Double,
    val height: Double
)

internal fun crystalPortalPlane(
    axis: Direction.Axis,
    minX: Int,
    maxX: Int,
    minY: Int,
    maxY: Int,
    minZ: Int,
    maxZ: Int
): CrystalPortalPlane = when (axis) {
    Direction.Axis.X -> CrystalPortalPlane(
        axisW = Vec3d(0.0, 0.0, 1.0),
        axisH = Vec3d(0.0, 1.0, 0.0),
        width = (maxZ - minZ + 1).toDouble(),
        height = (maxY - minY + 1).toDouble()
    )
    Direction.Axis.Z -> CrystalPortalPlane(
        axisW = Vec3d(1.0, 0.0, 0.0),
        axisH = Vec3d(0.0, 1.0, 0.0),
        width = (maxX - minX + 1).toDouble(),
        height = (maxY - minY + 1).toDouble()
    )
    Direction.Axis.Y -> CrystalPortalPlane(
        // Match IP's End portal convention: the normal points upward, so
        // entities above the frame cross downward through its active face.
        axisW = Vec3d(0.0, 0.0, 1.0),
        axisH = Vec3d(1.0, 0.0, 0.0),
        width = (maxZ - minZ + 1).toDouble(),
        height = (maxX - minX + 1).toDouble()
    )
}

internal fun immersivePortalYawRotation(
    plane: CrystalPortalPlane,
    destinationYaw: Float,
    destinationRotationQuarterTurns: Int
): Double {
    val sourceTravel = plane.axisW.crossProduct(plane.axisH).multiply(-1.0)
    // A player crosses an IP portal from its positive (front) side toward its
    // negative side, so the door's forward direction is -normal. Map that
    // travel direction onto the linking book's saved heading. Vivecraft must
    // mirror this same yaw in its independent VR world rotation.
    val sourceFacingYaw = Math.toDegrees(kotlin.math.atan2(-sourceTravel.x, sourceTravel.z)).toFloat()
    val exitYaw = MathHelper.wrapDegrees(
        destinationYaw + Math.floorMod(destinationRotationQuarterTurns, 4) * 90.0f
    )
    return -MathHelper.wrapDegrees(exitYaw - sourceFacingYaw).toDouble()
}
