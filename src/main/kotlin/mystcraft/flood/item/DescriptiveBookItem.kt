// src/main/kotlin/mystcraft/flood/item/DescriptiveBookItem.kt
package mystcraft.flood.item

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.DimensionInjector
import mystcraft.flood.generation.AgeTravelEffects
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
import net.minecraft.world.Heightmap
import net.minecraft.world.TeleportTarget
import net.minecraft.world.World

class DescriptiveBookItem(settings: Settings) : Item(settings) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient || user !is ServerPlayerEntity) return TypedActionResult.pass(user.getStackInHand(hand))

        val stack = user.getStackInHand(hand)
        val nbt = stack.orCreateNbt
        val server = world.server ?: return TypedActionResult.fail(stack)

        if (!nbt.contains("Age_ID")) {
            val ageName = "age_" + System.currentTimeMillis()
            val ageId = Identifier(MystcraftReforged.MOD_ID, ageName)
            
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
            user.sendMessage(Text.literal("Descriptive Book linked to $ageName.").formatted(Formatting.GREEN), true)
            
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
        val targetWorld = server.getWorld(dimKey)

        if (targetWorld != null) {
            val profile = AgeProfileManager.getOrGenerateProfile(server, ageId)
            val targetPos = when (profile.terrainType) {
                TerrainType.BIOSPHERES -> {
                    BiosphereFeature.buildOriginBiosphere(targetWorld)
                    Vec3d(0.5, BiosphereFeature.SAFE_ENTRY_Y.toDouble(), 0.5)
                }
                else -> Vec3d(0.0, computeEntryY(targetWorld).toDouble(), 0.0)
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

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt = stack.nbt
        
        if (nbt != null) {
            if (nbt.contains("Age_ID")) {
                val ageName = Identifier(nbt.getString("Age_ID")).path
                tooltip.add(Text.literal("Linked Dimension").formatted(Formatting.GOLD))
                tooltip.add(Text.literal(ageName).formatted(Formatting.DARK_GRAY))
                
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
                    tooltip.add(Text.literal("Symbols Written: ${pages.size}").formatted(Formatting.GRAY))
                    return
                }
            }
        }
        
        tooltip.add(Text.literal("Unlinked (Empty)").formatted(Formatting.DARK_RED))
    }

    override fun hasGlint(stack: ItemStack): Boolean {
        return stack.nbt?.contains("Age_ID") == true
    }
}
