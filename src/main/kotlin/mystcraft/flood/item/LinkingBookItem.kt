package mystcraft.flood.item

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
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

class LinkingBookItem(settings: Settings) : Item(settings) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient || user !is ServerPlayerEntity) return TypedActionResult.pass(user.getStackInHand(hand))

        val stack = user.getStackInHand(hand)
        val nbt = stack.orCreateNbt

        if (!nbt.contains("Dimension")) {
            nbt.putString("Dimension", world.registryKey.value.toString())
            nbt.putDouble("PosX", user.x)
            nbt.putDouble("PosY", user.y)
            nbt.putDouble("PosZ", user.z)
            nbt.putFloat("Yaw", user.yaw)
            nbt.putFloat("Pitch", user.pitch)
            
            user.sendMessage(Text.literal("Linking Book bound to current location."), true)
        } else {
            val dimId = Identifier(nbt.getString("Dimension"))
            val dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId)
            val targetWorld = world.server?.getWorld(dimKey)

            if (targetWorld != null) {
                val targetPos = Vec3d(nbt.getDouble("PosX"), nbt.getDouble("PosY"), nbt.getDouble("PosZ"))
                val teleportTarget = TeleportTarget(targetPos, Vec3d.ZERO, nbt.getFloat("Yaw"), nbt.getFloat("Pitch"))
                FabricDimensions.teleport(user, targetWorld, teleportTarget)
            } else {
                user.sendMessage(Text.literal("Target dimension is unavailable."), true)
            }
        }
        return TypedActionResult.success(stack)
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
}