package mystcraft.flood.item

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
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
}