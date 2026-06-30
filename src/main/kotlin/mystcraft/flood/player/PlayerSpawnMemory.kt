package mystcraft.flood.player

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.PlayerSpawnMemoryAccess
import mystcraft.flood.generation.AgeSubdimensionManager
import mystcraft.flood.generation.AgeTravelSafety
import mystcraft.flood.generation.profile.AgeDimensionRole
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.mixin.PlayerEntitySpawnAccessor
import mystcraft.flood.network.ModMessages
import net.minecraft.block.BedBlock
import net.minecraft.block.RespawnAnchorBlock
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

object PlayerSpawnMemory {
    private const val POS_X = "PosX"
    private const val POS_Y = "PosY"
    private const val POS_Z = "PosZ"
    private const val ANGLE = "Angle"
    private const val FORCED = "Forced"
    private const val KIND = "Kind"

    enum class SpawnKind {
        BOUND,
        AGE_DEFAULT
    }

    data class StoredSpawn(
        val pos: BlockPos,
        val angle: Float,
        val forced: Boolean,
        val kind: SpawnKind
    )

    data class ResolvedSpawnTarget(
        val dimension: RegistryKey<World>,
        val stored: StoredSpawn?
    )

    @JvmStatic
    fun rememberFromVanillaSetSpawn(
        player: ServerPlayerEntity,
        dimension: RegistryKey<World>,
        pos: BlockPos?,
        angle: Float,
        forced: Boolean
    ) {
        if (pos == null) {
            // Vanilla clears this after a failed bed validation; keep Mystcraft's
            // persistent per-dimension spawn so the next restore can repair it.
            if (AgeSubdimensionManager.isMystcraftRealm(dimension) && getStoredSpawn(player, dimension) != null) {
                player.server.getWorld(dimension)?.let { world ->
                    val restored = resolveTargetForWorld(player, world, player.pos)
                    applyActiveSpawn(player, restored.dimension, restored.stored)
                }
                return
            }

            clearStoredSpawn(player, dimension)
            return
        }

        val world = player.server.getWorld(dimension)
        if (world != null && AgeSubdimensionManager.isMystcraftRealm(dimension)) {
            val stored = if (!forced && isVanillaRespawnBlock(world, pos)) {
                StoredSpawn(pos, angle, forced, SpawnKind.BOUND)
            } else {
                val resolved = if (forced) {
                    AgeTravelSafety.resolveForcedStandRespawn(world, pos)
                } else {
                    AgeTravelSafety.resolveBoundRespawn(world, pos)
                        ?: AgeTravelSafety.resolveCustomBoundRespawn(world, pos, angle)
                }
                StoredSpawn(resolved, angle, true, SpawnKind.BOUND)
            }
            putStoredSpawn(player, dimension, stored)
            applyActiveSpawn(player, dimension, stored)
            return
        }

        putStoredSpawn(player, dimension, StoredSpawn(pos, angle, forced, SpawnKind.BOUND))
    }

    @JvmStatic
    fun copyPersistentSpawns(from: ServerPlayerEntity, to: ServerPlayerEntity) {
        val fromMap = access(from).`mystcraft$getDimensionSpawns`()
        val toMap = access(to).`mystcraft$getDimensionSpawns`()
        toMap.clear()
        fromMap.forEach { entry -> toMap[entry.key] = entry.value.copy() }
    }

    @JvmStatic
    fun restoreForCurrentWorld(player: ServerPlayerEntity, preferredPos: Vec3d = player.pos) {
        val world = player.serverWorld
        val restored = resolveTargetForWorld(player, world, preferredPos)
        applyActiveSpawn(player, restored.dimension, restored.stored)
    }

    @JvmStatic
    fun resolveMystcraftRespawn(player: ServerPlayerEntity, world: ServerWorld, preferredPos: Vec3d): StoredSpawn =
        resolveMystcraftRespawnTarget(player, world, preferredPos).second

    @JvmStatic
    fun resolveMystcraftRespawnTarget(
        player: ServerPlayerEntity,
        world: ServerWorld,
        preferredPos: Vec3d
    ): Pair<ServerWorld, StoredSpawn> {
        val resolved = resolveTargetForWorld(player, world, preferredPos)
        val targetWorld = player.server.getWorld(resolved.dimension) ?: world
        val targetSpawn = resolveRespawnStand(player, targetWorld, resolved.stored, preferredPos)
        return targetWorld to targetSpawn
    }

    @JvmStatic
    fun findFallback(player: ServerPlayerEntity, blockedAgeId: Identifier): Pair<ServerWorld, BlockPos>? {
        val server = player.server
        val overworldKey = World.OVERWORLD
        val overworld = server.getWorld(overworldKey)
        val overworldSpawn = overworld?.let { resolveExternalSpawn(it, getStoredSpawn(player, overworldKey)) }
        if (overworld != null && overworldSpawn != null) {
            return overworld to overworldSpawn.pos
        }

        val activePos = player.spawnPointPosition
        val activeWorld = server.getWorld(player.spawnPointDimension)
        if (
            activePos != null &&
            activeWorld != null &&
            activeWorld.registryKey.value != blockedAgeId &&
            activeWorld.registryKey.value.namespace != MystcraftReforged.MOD_ID
        ) {
            val restored = resolveExternalSpawn(activeWorld, StoredSpawn(activePos, player.spawnAngle, player.isSpawnForced, SpawnKind.BOUND))
            if (restored != null) {
                return activeWorld to restored.pos
            }
        }

        return null
    }

    @JvmStatic
    fun rememberAgeDefaultSpawn(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos, angle: Float = player.yaw) {
        val stored = StoredSpawn(pos, angle, true, SpawnKind.AGE_DEFAULT)
        putStoredSpawn(player, world.registryKey, stored)
        applyActiveSpawn(player, world.registryKey, stored)
    }

    @JvmStatic
    fun rememberAgeDefaultSpawnIfAbsent(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos, angle: Float = player.yaw) {
        if (getStoredSpawn(player, world.registryKey) != null) {
            restoreForCurrentWorld(player)
            return
        }

        rememberAgeDefaultSpawn(player, world, pos, angle)
    }

    @JvmStatic
    fun setActiveSpawnToWorldDefault(player: ServerPlayerEntity, worldKey: RegistryKey<World>) {
        applyActiveSpawn(player, worldKey, null)
    }

    private fun resolveTargetForWorld(player: ServerPlayerEntity, world: ServerWorld, preferredPos: Vec3d): ResolvedSpawnTarget {
        val stored = getStoredSpawn(player, world.registryKey) ?: inferLegacySpawn(player, world)?.also {
            putStoredSpawn(player, world.registryKey, it)
        }
        return if (AgeSubdimensionManager.isMystcraftRealm(world.registryKey)) {
            resolveMystcraftTarget(player, world, stored, preferredPos)
        } else {
            ResolvedSpawnTarget(world.registryKey, resolveExternalSpawn(world, stored))
        }
    }

    private fun resolveMystcraftTarget(
        player: ServerPlayerEntity,
        world: ServerWorld,
        stored: StoredSpawn?,
        preferredPos: Vec3d
    ): ResolvedSpawnTarget {
        val ageId = world.registryKey.value
        val role = AgeSubdimensionManager.roleOf(ageId)

        val validStored = when (stored?.kind) {
            SpawnKind.BOUND -> resolveBoundActiveSpawn(world, stored)
            SpawnKind.AGE_DEFAULT -> if (role == AgeDimensionRole.OVERWORLD) stored else null
            null -> null
        }
        if (validStored != null) {
            putStoredSpawn(player, world.registryKey, validStored)
            return ResolvedSpawnTarget(world.registryKey, validStored)
        }

        if (role != AgeDimensionRole.OVERWORLD) {
            if (stored?.kind == SpawnKind.AGE_DEFAULT) {
                clearStoredSpawn(player, world.registryKey)
            }

            val parentWorldKey = RegistryKey.of(RegistryKeys.WORLD, AgeSubdimensionManager.rootIdOf(ageId))
            val parentWorld = player.server.getWorld(parentWorldKey)
            if (parentWorld != null) {
                val parentStored = getStoredSpawn(player, parentWorldKey) ?: inferLegacySpawn(player, parentWorld)?.also {
                    putStoredSpawn(player, parentWorldKey, it)
                }
                return resolveMystcraftTarget(player, parentWorld, parentStored, preferredPos)
            }
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, ageId)
        val resolved = AgeTravelSafety.resolveAgeSpawn(world, profile, preferredPos)
        persistAgeSurfaceAnchor(world, ageId, profile, resolved.anchor)
        val created = StoredSpawn(resolved.anchor, player.yaw, true, SpawnKind.AGE_DEFAULT)

        putStoredSpawn(player, world.registryKey, created)
        return ResolvedSpawnTarget(world.registryKey, created)
    }

    private fun resolveRespawnStand(
        player: ServerPlayerEntity,
        world: ServerWorld,
        stored: StoredSpawn?,
        preferredPos: Vec3d
    ): StoredSpawn {
        stored?.let {
            return when (it.kind) {
                SpawnKind.BOUND -> {
                    val resolved = if (it.forced) {
                        AgeTravelSafety.resolveForcedStandRespawn(world, it.pos)
                    } else {
                        AgeTravelSafety.resolveBoundRespawn(world, it.pos)
                            ?: AgeTravelSafety.resolveCustomBoundRespawn(world, it.pos, it.angle)
                    }
                    it.copy(pos = resolved, forced = true)
                }
                SpawnKind.AGE_DEFAULT -> it.copy(
                    pos = AgeTravelSafety.resolveForcedStandRespawn(world, it.pos),
                    forced = true
                )
            }
        }

        val target = resolveMystcraftTarget(player, world, getStoredSpawn(player, world.registryKey), preferredPos)
        return target.stored?.let { resolveRespawnStand(player, world, it, preferredPos) }
            ?: StoredSpawn(BlockPos.ofFloored(preferredPos), player.yaw, true, SpawnKind.AGE_DEFAULT)
    }

    private fun resolveExternalSpawn(world: ServerWorld, stored: StoredSpawn?): StoredSpawn? {
        stored ?: return null
        return when (stored.kind) {
            SpawnKind.BOUND -> if (stored.forced || isVanillaRespawnBlock(world, stored.pos)) stored else null
            SpawnKind.AGE_DEFAULT -> stored
        }
    }

    private fun inferLegacySpawn(player: ServerPlayerEntity, world: ServerWorld): StoredSpawn? {
        val activePos = player.spawnPointPosition ?: return null
        if (player.spawnPointDimension != world.registryKey) return null

        return if (AgeSubdimensionManager.isMystcraftRealm(world.registryKey)) {
            if (!player.isSpawnForced && isVanillaRespawnBlock(world, activePos)) {
                StoredSpawn(activePos, player.spawnAngle, false, SpawnKind.BOUND)
            } else if (player.isSpawnForced) {
                val resolved = AgeTravelSafety.resolveForcedStandRespawn(world, activePos)
                StoredSpawn(resolved, player.spawnAngle, true, SpawnKind.BOUND)
            } else {
                StoredSpawn(activePos, player.spawnAngle, true, SpawnKind.AGE_DEFAULT)
            }
        } else {
            StoredSpawn(activePos, player.spawnAngle, player.isSpawnForced, SpawnKind.BOUND)
        }
    }

    private fun persistAgeSurfaceAnchor(world: ServerWorld, ageId: Identifier, profile: AgeProfile, anchor: BlockPos) {
        if (
            profile.ageState.surfaceSpawnX == anchor.x &&
            profile.ageState.surfaceSpawnY == anchor.y &&
            profile.ageState.surfaceSpawnZ == anchor.z
        ) {
            return
        }

        profile.ageState.surfaceSpawnX = anchor.x
        profile.ageState.surfaceSpawnY = anchor.y
        profile.ageState.surfaceSpawnZ = anchor.z
        AgeProfileManager.save(world.server, ageId)
        world.server.playerManager.playerList.forEach { player ->
            ModMessages.sendDimensionSync(player, ageId, profile)
        }
    }

    private fun putStoredSpawn(player: ServerPlayerEntity, dimension: RegistryKey<World>, stored: StoredSpawn) {
        access(player).`mystcraft$getDimensionSpawns`()[dimension.value.toString()] = encode(stored)
    }

    private fun clearStoredSpawn(player: ServerPlayerEntity, dimension: RegistryKey<World>) {
        access(player).`mystcraft$getDimensionSpawns`().remove(dimension.value.toString())
    }

    private fun getStoredSpawn(player: ServerPlayerEntity, dimension: RegistryKey<World>): StoredSpawn? =
        access(player).`mystcraft$getDimensionSpawns`()[dimension.value.toString()]?.let(::decode)

    private fun resolveBoundActiveSpawn(world: ServerWorld, stored: StoredSpawn): StoredSpawn? {
        if (!stored.forced && isVanillaRespawnBlock(world, stored.pos)) {
            return stored
        }

        findNearbyVanillaRespawnBlock(world, stored.pos)?.let { recovered ->
            return stored.copy(pos = recovered, forced = false)
        }

        if (stored.forced) {
            return stored.copy(pos = AgeTravelSafety.resolveForcedStandRespawn(world, stored.pos), forced = true)
        }

        val resolved = AgeTravelSafety.resolveBoundRespawn(world, stored.pos) ?: return null
        return stored.copy(pos = resolved, forced = true)
    }

    private fun isVanillaRespawnBlock(world: ServerWorld, pos: BlockPos): Boolean {
        val blockState = world.getBlockState(pos)
        val block = blockState.block
        return (block is BedBlock && BedBlock.isBedWorking(world)) ||
            (block is RespawnAnchorBlock && RespawnAnchorBlock.isNether(world))
    }

    private fun findNearbyVanillaRespawnBlock(world: ServerWorld, pos: BlockPos): BlockPos? {
        for (radius in 0..4) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (radius > 0 && kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) continue

                    for (dy in -2..2) {
                        val candidate = pos.add(dx, dy, dz)
                        if (!isVanillaRespawnBlock(world, candidate)) continue

                        val respawnStand = AgeTravelSafety.resolveBoundRespawn(world, candidate) ?: continue
                        if (isNear(respawnStand, pos, maxDistanceSquared = 16)) {
                            return candidate
                        }
                    }
                }
            }
        }

        return null
    }

    private fun isNear(a: BlockPos, b: BlockPos, maxDistanceSquared: Int): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return dx * dx + dy * dy + dz * dz <= maxDistanceSquared
    }

    private fun encode(stored: StoredSpawn): NbtCompound = NbtCompound().apply {
        putInt(POS_X, stored.pos.x)
        putInt(POS_Y, stored.pos.y)
        putInt(POS_Z, stored.pos.z)
        putFloat(ANGLE, stored.angle)
        putBoolean(FORCED, stored.forced)
        putString(KIND, stored.kind.name)
    }

    private fun decode(tag: NbtCompound): StoredSpawn {
        val kind = runCatching { SpawnKind.valueOf(tag.getString(KIND)) }.getOrElse { SpawnKind.BOUND }
        return StoredSpawn(
            BlockPos(tag.getInt(POS_X), tag.getInt(POS_Y), tag.getInt(POS_Z)),
            tag.getFloat(ANGLE),
            tag.getBoolean(FORCED),
            kind
        )
    }

    private fun applyActiveSpawn(player: ServerPlayerEntity, worldKey: RegistryKey<World>, stored: StoredSpawn?) {
        val accessor = player as PlayerEntitySpawnAccessor
        accessor.`mystcraft$setSpawnPointDimension`(worldKey)
        accessor.`mystcraft$setSpawnPointPosition`(stored?.pos)
        accessor.`mystcraft$setSpawnAngle`(stored?.angle ?: 0.0f)
        accessor.`mystcraft$setSpawnForced`(stored?.forced ?: false)
    }

    private fun access(player: ServerPlayerEntity): PlayerSpawnMemoryAccess =
        player as PlayerSpawnMemoryAccess
}
