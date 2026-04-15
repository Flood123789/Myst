package mystcraft.flood.generation

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.DimensionInjector
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.TeleportTarget
import java.nio.file.Files
import java.util.Comparator
import kotlin.io.path.exists
import kotlin.math.ceil

object AgeLifecycleManager {
    private const val DEAD_LINK_TEXT = "The ink on the pages has begun to run and the words are unreadable."
    private const val DEAD_AGE_TEXT = "This Age has been sacrificed. Only a hollow echo remains."

    fun isDeadAge(profile: AgeProfile): Boolean = profile.ageState.isSacrificed || profile.terrainType == TerrainType.VOID

    fun isDeadAge(server: MinecraftServer, ageId: Identifier): Boolean {
        if (ageId.namespace != MystcraftReforged.MOD_ID) return false
        return isDeadAge(AgeProfileManager.getOrGenerateProfile(server, ageId))
    }

    fun deadLinkMessage(): Text = Text.literal(DEAD_LINK_TEXT).formatted(Formatting.DARK_PURPLE)

    fun deadAgeMessage(): Text = Text.literal(DEAD_AGE_TEXT).formatted(Formatting.GRAY)

    fun mayEnterAge(player: ServerPlayerEntity, ageId: Identifier, linkStyleMessage: Boolean = false): Boolean {
        if (!isDeadAge(player.server, ageId)) return true
        if (player.isCreative || player.isSpectator) return true

        player.sendMessage(if (linkStyleMessage) deadLinkMessage() else deadAgeMessage(), false)
        return false
    }

    fun exileIfDeadAge(player: ServerPlayerEntity): Boolean {
        val ageId = player.serverWorld.registryKey.value
        if (ageId.namespace != MystcraftReforged.MOD_ID) return false
        if (!isDeadAge(player.server, ageId)) return false
        if (player.isCreative || player.isSpectator) return false

        warpToFallback(player, ageId, deadAgeMessage())
        return true
    }

    fun trySacrificeAges(player: ServerPlayerEntity, targetBook: ItemStack, sacrificialBook: ItemStack): Boolean {
        val targetAgeId = Identifier.tryParse(targetBook.nbt?.getString("Age_ID") ?: "") ?: run {
            player.sendMessage(Text.literal("The book in your main hand must already be linked to an Age.").formatted(Formatting.RED), false)
            return false
        }
        val sacrificeAgeId = Identifier.tryParse(sacrificialBook.nbt?.getString("Age_ID") ?: "") ?: run {
            player.sendMessage(Text.literal("The offhand descriptive book must already be linked to an Age.").formatted(Formatting.RED), false)
            return false
        }

        if (targetAgeId == sacrificeAgeId) {
            player.sendMessage(Text.literal("An Age cannot be fed to itself.").formatted(Formatting.RED), false)
            return false
        }

        val server = player.server
        val targetProfile = AgeProfileManager.getOrGenerateProfile(server, targetAgeId)
        val sacrificeProfile = AgeProfileManager.getOrGenerateProfile(server, sacrificeAgeId)

        if (isDeadAge(targetProfile)) {
            player.sendMessage(Text.literal("That target Age is already sacrificed.").formatted(Formatting.RED), false)
            return false
        }
        if (isDeadAge(sacrificeProfile)) {
            player.sendMessage(Text.literal("That sacrificial Age has already been erased.").formatted(Formatting.RED), false)
            return false
        }

        val targetScore = targetProfile.stability.instabilityScore.coerceAtLeast(0)
        val sacrificeScore = sacrificeProfile.stability.instabilityScore.coerceAtLeast(0)
        if (targetScore <= 0) {
            player.sendMessage(Text.literal("That Age is already stable.").formatted(Formatting.YELLOW), false)
            return false
        }

        val difference = targetScore - sacrificeScore
        if (difference <= 0) {
            player.sendMessage(Text.literal("The sacrificed Age must be more stable than the one you want to heal.").formatted(Formatting.RED), false)
            return false
        }

        val reduction = calculateStabilityReduction(targetScore, sacrificeScore)
        if (!sacrificeAge(server, sacrificeAgeId, player.name.string)) {
            return false
        }

        targetProfile.stability.instabilityScore = (targetProfile.stability.instabilityScore - reduction).coerceAtLeast(0)
        targetProfile.stability.isStable = targetProfile.stability.instabilityScore <= 0
        AgeProfileManager.save(server, targetAgeId)
        syncAge(server, targetAgeId, targetProfile)

        if (!player.isCreative) {
            sacrificialBook.decrement(1)
        }

        val percent = calculateReductionPercent(targetScore, sacrificeScore)
        val percentText = String.format("%.1f", percent)
        player.sendMessage(
            Text.literal("Sacrificed ${sacrificeAgeId.path} into ${targetAgeId.path}. Instability fell by $reduction ($percentText%).")
                .formatted(Formatting.AQUA),
            false
        )
        return true
    }

    fun sacrificeAge(server: MinecraftServer, ageId: Identifier, sacrificedBy: String? = null): Boolean {
        if (ageId.namespace != MystcraftReforged.MOD_ID) return false

        val profile = AgeProfileManager.getOrGenerateProfile(server, ageId)
        if (isDeadAge(profile)) return false

        server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, ageId))?.players
            ?.toList()
            ?.forEach { player ->
                if (!player.isCreative && !player.isSpectator) {
                    warpToFallback(player, ageId, deadAgeMessage())
                }
            }

        blankProfile(profile, sacrificedBy)
        AgeProfileManager.save(server, ageId)

        deleteDimensionData(server, ageId)
        (server as DimensionInjector).`mystcraft$reloadDimension`(ageId)
        syncAge(server, ageId, profile)
        return true
    }

    private fun blankProfile(profile: AgeProfile, sacrificedBy: String?) {
        profile.ageState.isSacrificed = true
        profile.ageState.sacrificedBy = sacrificedBy
        profile.ageState.sacrificedAt = System.currentTimeMillis()
        profile.terrainType = TerrainType.VOID
        profile.colors.sky = 0x000000
        profile.colors.fog = 0x050505
        profile.colors.water = 0x030303
        profile.colors.grass = 0x141414
        profile.colors.foliage = 0x141414
        profile.time.sunNormalCount = 0
        profile.time.sunRedCount = 0
        profile.time.sunBlueCount = 0
        profile.time.moonCount = 0
        profile.time.starDensity = 0
        profile.time.fixedTime = 18000L
        profile.time.timeScale = 0.0f
        profile.weather.isEndlessRain = false
        profile.weather.isEndlessStorm = false
        profile.weather.noWeather = true
        profile.weather.currentRaining = false
        profile.weather.currentThundering = false
        profile.weather.clearTicks = 12000
        profile.weather.rainTicks = 0
        profile.weather.thunderTicks = 0
        profile.spawning.noMobs = true
        profile.spawning.hostileMultiplier = 0.0f
        profile.spawning.passiveMultiplier = 0.0f
        profile.ageEffect.effectId = null
        profile.ageEffect.enabled = false
        profile.physics.gravityScale = 1.0f
        profile.stability.effectsEnabled = false
        profile.modifiers.clear()
        profile.ageState.surfaceSpawnX = null
        profile.ageState.surfaceSpawnY = null
        profile.ageState.surfaceSpawnZ = null
    }

    private fun calculateReductionPercent(targetScore: Int, sacrificeScore: Int): Double {
        val difference = (targetScore - sacrificeScore).coerceAtLeast(0)
        return (difference / 18.0).coerceIn(1.0, 60.0)
    }

    private fun calculateStabilityReduction(targetScore: Int, sacrificeScore: Int): Int {
        val percent = calculateReductionPercent(targetScore, sacrificeScore)
        return ceil(targetScore * (percent / 100.0)).toInt().coerceAtLeast(1)
    }

    private fun warpToFallback(player: ServerPlayerEntity, blockedAgeId: Identifier, message: Text?) {
        val (targetWorld, target) = resolveFallback(player, blockedAgeId)
        AgeTravelEffects.playDeparture(player.serverWorld, player)
        val result = FabricDimensions.teleport(player, targetWorld, target)
        if (result != null) {
            AgeTravelEffects.playArrival(targetWorld, target.position)
            message?.let { player.sendMessage(it, false) }
        }
    }

    private fun resolveFallback(player: ServerPlayerEntity, blockedAgeId: Identifier): Pair<ServerWorld, TeleportTarget> {
        val server = player.server
        val spawnPos = player.spawnPointPosition
        val spawnWorld = server.getWorld(player.spawnPointDimension)
        if (spawnPos != null && spawnWorld != null && spawnWorld.registryKey.value != blockedAgeId && !isDeadAge(server, spawnWorld.registryKey.value)) {
            return spawnWorld to teleportTargetAt(spawnWorld, spawnPos, player)
        }

        val overworld = server.overworld
        return overworld to teleportTargetAt(overworld, overworld.spawnPos, player)
    }

    private fun teleportTargetAt(world: ServerWorld, pos: BlockPos, player: ServerPlayerEntity): TeleportTarget {
        val safePos = AgeTravelSafety.sanitizeArrival(world, Vec3d(pos.x + 0.5, pos.y.toDouble() + 1.0, pos.z + 0.5))
        return TeleportTarget(safePos, Vec3d.ZERO, player.yaw, player.pitch)
    }

    private fun syncAge(server: MinecraftServer, ageId: Identifier, profile: AgeProfile) {
        server.playerManager?.playerList?.forEach { player ->
            ModMessages.sendDimensionSync(player, ageId, profile)
        }
    }

    private fun deleteDimensionData(server: MinecraftServer, ageId: Identifier) {
        val dimensionRoot = server.getSavePath(WorldSavePath.ROOT)
            .resolve("dimensions")
            .resolve(ageId.namespace)
            .resolve(ageId.path)

        if (!dimensionRoot.exists()) return

        runCatching {
            Files.walk(dimensionRoot)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }.onFailure { error ->
            MystcraftReforged.LOGGER.warn("Could not fully delete dimension data for $ageId: ${error.message}")
        }
    }
}
