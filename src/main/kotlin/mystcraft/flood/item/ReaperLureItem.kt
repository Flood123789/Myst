package mystcraft.flood.item

import mystcraft.flood.entity.ReaperDebugLure
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext
import net.minecraft.world.World

/**
 * Debug tool: right-click to send drone Reapers to whatever the crosshair is pointing at.
 *
 * The pick is a full raycast from the eyes rather than a block interaction, so it reaches far
 * past normal arm's length. That matters for what this is for: standing back to watch a Reaper
 * cross a cliff face or a cave roof means aiming at geometry tens of blocks away, which a
 * reach-limited block click cannot address at all.
 */
class ReaperLureItem(settings: Settings) : Item(settings) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (world.isClient) return TypedActionResult.success(stack, true)

        val start = user.getCameraPosVec(1.0f)
        val end = start.add(user.getRotationVec(1.0f).multiply(RANGE))
        val hit = world.raycast(
            RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user)
        )

        if (hit == null || hit.type != HitResult.Type.BLOCK) {
            user.sendMessage(Text.literal("Nothing in sight within $RANGE blocks.").formatted(Formatting.RED), true)
            return TypedActionResult.fail(stack)
        }

        val blockHit = hit as BlockHitResult
        // Offset onto the face that was hit, so the lure sits in the air a Reaper can occupy
        // rather than inside the block itself.
        val target = blockHit.blockPos.offset(blockHit.side)
        ReaperDebugLure.set(world, target)

        val distance = Math.sqrt(start.squaredDistanceTo(hit.pos)).toInt()
        user.sendMessage(
            Text.literal("Lure set to ${target.x}, ${target.y}, ${target.z}  (${distance}m)")
                .formatted(Formatting.AQUA),
            true
        )
        world.playSound(
            null, target, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.8f
        )
        return TypedActionResult.consume(stack)
    }

    /**
     * Keeps the lure alive only while the tool is actually in hand, so stowing it releases the
     * drones without any explicit "off" step.
     */
    override fun inventoryTick(stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean) {
        if (world.isClient || !selected) return
        if (entity !is PlayerEntity) return
        ReaperDebugLure.refresh(world)
    }

    private companion object {
        /** Long enough to aim at the far side of a ravine or the roof of a large cave. */
        const val RANGE = 160.0
    }
}
