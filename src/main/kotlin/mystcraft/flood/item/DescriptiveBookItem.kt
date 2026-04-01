package mystcraft.flood.item

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.DimensionInjector
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
import net.minecraft.util.math.Vec3d
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
            
            // TODO (Next Step): Read the "Pages" array from the NBT here and pass it into the Injector 
            // so we don't just generate a random age!
            (server as DimensionInjector).`mystcraft$injectDimension`(ageId)
            
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
            
            // The Sky Drop: Let worker threads build the world beneath you
            val dropHeight = 200.0

            val teleportTarget = TeleportTarget(
                Vec3d(0.0, dropHeight, 0.0), 
                Vec3d.ZERO,
                player.yaw,
                player.pitch
            )
            
            // 15 seconds of Slow Falling to let you glide down while chunks render
            player.addStatusEffect(StatusEffectInstance(StatusEffects.SLOW_FALLING, 300, 0, false, false))
            // 20 seconds of Invincibility so you don't suffocate if a mountain spawns on you
            player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4, false, false))
            
            FabricDimensions.teleport(player, targetWorld, teleportTarget)
        } else {
            player.sendMessage(Text.literal("Age dimension is not loaded.").formatted(Formatting.RED), true)
        }
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt = stack.nbt
        
        if (nbt != null) {
            // 1. Is it already a fully linked, functional dimension book?
            if (nbt.contains("Age_ID")) {
                val ageName = Identifier(nbt.getString("Age_ID")).path
                tooltip.add(Text.literal("Linked Dimension").formatted(Formatting.GOLD))
                tooltip.add(Text.literal(ageName).formatted(Formatting.DARK_GRAY))
                
                // Show the symbol count if the book was made in the binder
                if (nbt.contains("Pages")) {
                    val pages = nbt.getList("Pages", 8)
                    tooltip.add(Text.literal("Symbols Written: ${pages.size}").formatted(Formatting.GRAY))
                }
                return
            }
            
            // 2. Is it a drafted book from the Binder waiting to be opened?
            if (nbt.contains("Pages")) {
                val pages = nbt.getList("Pages", 8) 
                if (pages.size > 0) {
                    tooltip.add(Text.literal("Unlinked (Draft)").formatted(Formatting.YELLOW))
                    tooltip.add(Text.literal("Symbols Written: ${pages.size}").formatted(Formatting.GRAY))
                    return
                }
            }
        }
        
        // 3. Fallback for completely empty books
        tooltip.add(Text.literal("Unlinked (Empty)").formatted(Formatting.DARK_RED))
    }

    override fun hasGlint(stack: ItemStack): Boolean {
        // Returns true (glows) if the book is successfully linked to an Age
        return stack.nbt?.contains("Age_ID") == true
    }
}