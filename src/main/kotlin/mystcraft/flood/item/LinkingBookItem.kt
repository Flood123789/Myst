package mystcraft.flood.item

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import mystcraft.flood.generation.AgeLifecycleManager
import mystcraft.flood.generation.AgeTravelEffects
import mystcraft.flood.network.ModMessages
import net.minecraft.client.item.TooltipContext
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

/**
 * A return link bound to an existing dimension and exact position.
 *
 * Unlike a [DescriptiveBookItem], this item never authors or creates a world. Its NBT is a travel
 * target captured from the server, and activation resolves that target through
 * [LinkingBookTarget] before teleporting.
 */
class LinkingBookItem(settings: Settings) : Item(settings) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient) return TypedActionResult.success(user.getStackInHand(hand))
        if (user !is ServerPlayerEntity) return TypedActionResult.pass(user.getStackInHand(hand))

        val stack = user.getStackInHand(hand)
        if (!stack.orCreateNbt.contains("Dimension")) {
            bindCurrentLocation(world, user, stack)
        }

        if (user.isSneaking && stack.orCreateNbt.contains("Dimension")) {
            activate(world, user, stack)
        } else {
            ModMessages.sendOpenLinkingBook(user, stack, hand)
        }
        return TypedActionResult.success(stack)
    }

    fun activate(world: World, user: ServerPlayerEntity, stack: ItemStack) {
        val nbt = stack.orCreateNbt

        if (!nbt.contains("Dimension")) {
            bindCurrentLocation(world, user, stack)
        } else {
            val dimId = Identifier(nbt.getString("Dimension"))
            val dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId)
            val targetWorld = world.server?.getWorld(dimKey)

            if (targetWorld != null) {
                if (!AgeLifecycleManager.mayEnterAge(user, dimId, linkStyleMessage = true)) {
                    return
                }
                val targetPos = Vec3d(
                    nbt.getDouble("PosX"),
                    LinkingBookTarget.resolveArrivalY(targetWorld, nbt),
                    nbt.getDouble("PosZ")
                )
                val teleportTarget = TeleportTarget(
                    targetPos,
                    Vec3d.ZERO,
                    LinkingBookTarget.resolveCardinalYaw(nbt),
                    nbt.getFloat("Pitch")
                )
                AgeTravelEffects.playDeparture(user.serverWorld, user)
                val result = FabricDimensions.teleport(user, targetWorld, teleportTarget)
                if (result != null) {
                    AgeTravelEffects.playArrival(targetWorld, teleportTarget.position)
                }
            } else if (world.server != null && AgeLifecycleManager.isDeadAge(world.server!!, dimId)) {
                user.sendMessage(AgeLifecycleManager.deadLinkMessage(), false)
            } else {
                user.sendMessage(Text.literal("Target dimension is unavailable."), true)
            }
        }
    }

    // === NEW: Adds the hover text ===
    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        val nbt = stack.nbt
        if (nbt != null && nbt.contains("Dimension")) {
            val dim = nbt.getString("Dimension")
            val x = nbt.getDouble("PosX").toInt()
            val y = nbt.getDouble("PosY").toInt()
            val z = nbt.getDouble("PosZ").toInt()
            
            tooltip.add(Text.literal("Linked to: ").formatted(Formatting.GRAY).append(Text.literal(dim).formatted(Formatting.GOLD)))
            tooltip.add(Text.literal("Location: $x, $y, $z").formatted(Formatting.DARK_GRAY))
        } else {
            tooltip.add(Text.literal("Unlinked").formatted(Formatting.DARK_RED))
            tooltip.add(Text.literal("Right-click to bind to current location.").formatted(Formatting.GRAY))
        }
    }

    // === NEW: Makes the item glow if it is linked ===
    override fun hasGlint(stack: ItemStack): Boolean {
        return stack.hasNbt() && stack.nbt!!.contains("Dimension")
    }

    private fun bindCurrentLocation(world: World, user: ServerPlayerEntity, stack: ItemStack) {
        val nbt = stack.orCreateNbt
        nbt.putString("Dimension", world.registryKey.value.toString())
        nbt.putDouble("PosX", user.x)
        LinkingBookTarget.writeStandingAnchor(world, user, nbt)
        nbt.putDouble("PosZ", user.z)
        nbt.putFloat("Pitch", user.pitch)
        world.server?.let { BookPreviewData.refreshForStack(it, stack) }
        user.sendMessage(Text.literal("Linking Book bound to current location.").formatted(Formatting.GREEN), true)
    }
}
