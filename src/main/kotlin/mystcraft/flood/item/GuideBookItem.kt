package mystcraft.flood.item

import mystcraft.flood.compat.PatchouliCompat
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class GuideBookItem(settings: Settings) : Item(settings) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)

        if (world.isClient) {
            if (PatchouliCompat.openGuideGui()) {
                return TypedActionResult.success(stack, true)
            }
            return TypedActionResult.success(stack, true)
        }

        if (user is ServerPlayerEntity && !PatchouliCompat.isAvailable()) {
            user.sendMessage(
                Text.translatable("patchouli.mystcraft-reforged.guide_missing").formatted(Formatting.YELLOW),
                true
            )
        }

        return TypedActionResult.success(stack, false)
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        tooltip.add(Text.literal("A primer on pages, Ages, and instability.").formatted(Formatting.GRAY))
        if (!PatchouliCompat.isAvailable()) {
            tooltip.add(Text.literal("Install Patchouli to read it in-game.").formatted(Formatting.YELLOW))
        }
    }
}
