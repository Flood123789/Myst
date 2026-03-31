package mystcraft.flood.item

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.DimensionInjector
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
import net.minecraft.world.Heightmap
import net.minecraft.world.TeleportTarget
import net.minecraft.world.World
import net.minecraft.world.chunk.ChunkStatus

class DescriptiveBookItem(settings: Settings) : Item(settings) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient || user !is ServerPlayerEntity) return TypedActionResult.pass(user.getStackInHand(hand))

        val stack = user.getStackInHand(hand)
        val nbt = stack.orCreateNbt
        val server = world.server ?: return TypedActionResult.fail(stack)

        if (!nbt.contains("Age_ID")) {
            val ageName = "age_" + System.currentTimeMillis()
            val ageId = Identifier(MystcraftReforged.MOD_ID, ageName)
            
            (server as DimensionInjector).`mystcraft$injectDimension`(ageId)
            
            nbt.putString("Age_ID", ageId.toString())
            user.sendMessage(Text.literal("Descriptive Book linked to $ageName."), true)
            
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
            targetWorld.getChunk(0, 0, ChunkStatus.FULL, true)
            val surfaceY = targetWorld.getTopY(Heightmap.Type.WORLD_SURFACE, 0, 0).toDouble()

            val teleportTarget = TeleportTarget(
                Vec3d(0.0, surfaceY + 1.0, 0.0),
                Vec3d.ZERO,
                player.yaw,
                player.pitch
            )
            FabricDimensions.teleport(player, targetWorld, teleportTarget)
        } else {
            player.sendMessage(Text.literal("Age dimension is not loaded."), true)
        }
    }
}