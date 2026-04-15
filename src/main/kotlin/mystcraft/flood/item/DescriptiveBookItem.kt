// src/main/kotlin/mystcraft/flood/item/DescriptiveBookItem.kt
package mystcraft.flood.item

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.DimensionInjector
import mystcraft.flood.generation.AgeLifecycleManager
import mystcraft.flood.generation.AgeTravelEffects
import mystcraft.flood.generation.AgeTravelSafety
import mystcraft.flood.generation.BiosphereFeature
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.WorldSavePath
import net.minecraft.world.Heightmap
import net.minecraft.world.TeleportTarget
import net.minecraft.world.World
import java.nio.file.Files

class DescriptiveBookItem(settings: Settings) : Item(settings) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient || user !is ServerPlayerEntity) return TypedActionResult.pass(user.getStackInHand(hand))

        val stack = user.getStackInHand(hand)
        val nbt = stack.orCreateNbt
        val server = world.server ?: return TypedActionResult.fail(stack)

        if (hand == Hand.MAIN_HAND && user.isSneaking) {
            val offhand = user.offHandStack
            if (offhand.item === ModItems.DESCRIPTIVE_BOOK && offhand !== stack) {
                if (AgeLifecycleManager.trySacrificeAges(user, stack, offhand)) {
                    return TypedActionResult.success(stack)
                }
            }
        }

        if (!nbt.contains("Age_ID")) {
            val requestedName = if (nbt.contains("Age_Name")) nbt.getString("Age_Name") else ""
            val ageId = resolveAgeIdentifier(server, requestedName)
            
            // Extract the symbols from the book's NBT
            val symbols = mutableListOf<String>()
            if (nbt.contains("Pages")) {
                val pages = nbt.getList("Pages", 8) 
                for (i in 0 until pages.size) {
                    symbols.add(pages.getString(i))
                }
            }
            
            // Pass the symbols into the Injector!
            (server as DimensionInjector).`mystcraft$injectDimension`(ageId, symbols)
            
            nbt.putString("Age_ID", ageId.toString())
            user.sendMessage(Text.literal("Descriptive Book linked to ${ageId.path}.").formatted(Formatting.GREEN), true)
            
            teleportToAge(user, ageId)
        } else {
            val ageId = Identifier(nbt.getString("Age_ID"))
            teleportToAge(user, ageId)
        }
        return TypedActionResult.success(stack)
    }

    private fun teleportToAge(player: ServerPlayerEntity, ageId: Identifier) {
        val server = player.server ?: return
        val dimKey = RegistryKey.of(RegistryKeys.WORLD, ageId)
        var targetWorld = server.getWorld(dimKey)

        if (targetWorld == null && AgeLifecycleManager.isDeadAge(server, ageId)) {
            if (!AgeLifecycleManager.mayEnterAge(player, ageId)) {
                return
            }
            (server as DimensionInjector).`mystcraft$injectDimension`(ageId, emptyList())
            targetWorld = server.getWorld(dimKey)
        }

        if (targetWorld != null) {
            if (!AgeLifecycleManager.mayEnterAge(player, ageId)) {
                return
            }
            val profile = AgeProfileManager.getOrGenerateProfile(server, ageId)
            val targetPos = when (profile.terrainType) {
                TerrainType.BIOSPHERES -> {
                    BiosphereFeature.buildOriginBiosphere(targetWorld)
                    resolveAgeSurfaceAnchor(server, targetWorld, ageId, profile, Vec3d(0.5, BiosphereFeature.SAFE_ENTRY_Y.toDouble(), 0.5))
                }
                else -> resolveAgeSurfaceAnchor(server, targetWorld, ageId, profile, Vec3d(0.5, computeEntryY(targetWorld).toDouble(), 0.5))
            }

            val teleportTarget = TeleportTarget(
                targetPos,
                Vec3d.ZERO,
                player.yaw,
                player.pitch
            )
            
            player.addStatusEffect(StatusEffectInstance(StatusEffects.SLOW_FALLING, 300, 0, false, false))
            player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4, false, false))

            AgeTravelEffects.playDeparture(player.serverWorld, player)
            val result = FabricDimensions.teleport(player, targetWorld, teleportTarget)
            if (result != null) {
                bindPlayerRespawn(player, targetWorld, ageId, targetPos)
                AgeTravelEffects.playArrival(targetWorld, teleportTarget.position)
            }
        } else {
            player.sendMessage(Text.literal("Age dimension is not loaded.").formatted(Formatting.RED), true)
        }
    }

    private fun computeEntryY(targetWorld: net.minecraft.server.world.ServerWorld): Int {
        val surfaceY = targetWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0)
        if (surfaceY <= targetWorld.bottomY + 4) {
            return 120
        }

        return (surfaceY + 24).coerceIn(96, 160)
    }

    private fun resolveAgeSurfaceAnchor(
        server: net.minecraft.server.MinecraftServer,
        targetWorld: net.minecraft.server.world.ServerWorld,
        ageId: Identifier,
        profile: mystcraft.flood.generation.profile.AgeProfile,
        preferredPos: Vec3d
    ): Vec3d {
        val resolved = AgeTravelSafety.resolveAgeSpawn(targetWorld, profile, preferredPos)
        if (resolved.movedAnchor) {
            profile.ageState.surfaceSpawnX = resolved.anchor.x
            profile.ageState.surfaceSpawnY = resolved.anchor.y
            profile.ageState.surfaceSpawnZ = resolved.anchor.z
            AgeProfileManager.save(server, ageId)
        }
        return resolved.position
    }

    private fun bindPlayerRespawn(
        player: ServerPlayerEntity,
        targetWorld: net.minecraft.server.world.ServerWorld,
        ageId: Identifier,
        targetPos: Vec3d
    ) {
        val anchor = BlockPos.ofFloored(targetPos)
        val profile = AgeProfileManager.getOrGenerateProfile(player.server, ageId)
        if (profile.ageState.surfaceSpawnX != anchor.x || profile.ageState.surfaceSpawnY != anchor.y || profile.ageState.surfaceSpawnZ != anchor.z) {
            profile.ageState.surfaceSpawnX = anchor.x
            profile.ageState.surfaceSpawnY = anchor.y
            profile.ageState.surfaceSpawnZ = anchor.z
            AgeProfileManager.save(player.server, ageId)
        }
        player.setSpawnPoint(targetWorld.registryKey, anchor, player.yaw, true, true)
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt = stack.nbt
        val draftName = nbt?.getString("Age_Name")?.takeIf { it.isNotBlank() }
        
        if (nbt != null) {
            if (nbt.contains("Age_ID")) {
                val ageName = Identifier(nbt.getString("Age_ID")).path
                tooltip.add(Text.literal("Linked Dimension").formatted(Formatting.GOLD))
                tooltip.add(Text.literal(ageName).formatted(Formatting.DARK_GRAY))
                val id = Identifier.tryParse(nbt.getString("Age_ID"))
                val server = world?.server
                if (id != null && server != null && AgeLifecycleManager.isDeadAge(server, id)) {
                    tooltip.add(Text.literal("Sacrificed / unreadable").formatted(Formatting.DARK_PURPLE))
                }
                
                if (nbt.contains("Pages")) {
                    val pages = nbt.getList("Pages", 8)
                    tooltip.add(Text.literal("Symbols Written: ${pages.size}").formatted(Formatting.GRAY))
                }
                return
            }
            
            if (nbt.contains("Pages")) {
                val pages = nbt.getList("Pages", 8) 
                if (pages.size > 0) {
                    tooltip.add(Text.literal("Unlinked (Draft)").formatted(Formatting.YELLOW))
                    if (draftName != null) {
                        tooltip.add(Text.literal("Draft Name: $draftName").formatted(Formatting.AQUA))
                    }
                    tooltip.add(Text.literal("Symbols Written: ${pages.size}").formatted(Formatting.GRAY))
                    return
                }
            }
        }

        if (draftName != null) {
            tooltip.add(Text.literal("Draft Name: $draftName").formatted(Formatting.AQUA))
        }
        
        tooltip.add(Text.literal("Unlinked (Empty)").formatted(Formatting.DARK_RED))
    }

    override fun hasGlint(stack: ItemStack): Boolean {
        return stack.nbt?.contains("Age_ID") == true
    }

    private fun resolveAgeIdentifier(server: net.minecraft.server.MinecraftServer, requestedName: String): Identifier {
        val base = sanitizeAgePath(requestedName)
        var candidate = base
        var suffix = 2

        while (agePathExists(server, candidate)) {
            val trimmedBase = if (base.length > 55) base.take(55) else base
            candidate = "${trimmedBase}_$suffix"
            suffix++
        }

        return Identifier(MystcraftReforged.MOD_ID, candidate)
    }

    private fun agePathExists(server: net.minecraft.server.MinecraftServer, path: String): Boolean {
        val id = Identifier(MystcraftReforged.MOD_ID, path)
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, id)
        if (server.getWorld(worldKey) != null) return true

        val profileFile = server.getSavePath(WorldSavePath.ROOT)
            .resolve("mystcraft_profiles")
            .resolve("$path.json")
        return Files.exists(profileFile)
    }

    private fun sanitizeAgePath(rawName: String): String {
        val source = rawName.substringAfter(':').trim().lowercase()
        val underscored = source.replace("\\s+".toRegex(), "_")
        val safe = buildString(underscored.length) {
            for (ch in underscored) {
                if (ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '-' || ch == '/' || ch == '.') {
                    append(ch)
                } else {
                    append('_')
                }
            }
        }
            .replace("_+".toRegex(), "_")
            .trim('_', '-', '.', '/')

        if (safe.isBlank()) {
            return "age_" + System.currentTimeMillis()
        }

        val prefixed = if (safe.startsWith("age_")) safe else "age_$safe"
        return prefixed.take(63)
    }
}
