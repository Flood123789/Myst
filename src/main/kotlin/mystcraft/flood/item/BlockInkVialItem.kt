package mystcraft.flood.item

import mystcraft.flood.block.ModBlocks
import mystcraft.flood.block.BookReceptacleBlock
import mystcraft.flood.block.entity.PaintedCrystalBlockEntity
import net.minecraft.client.item.TooltipContext
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.state.property.Properties
import net.minecraft.world.World

class BlockInkVialItem(settings: Settings) : Item(settings) {
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val world = context.world
        val state = world.getBlockState(context.blockPos)
        return when {
            state.isOf(ModBlocks.CRYSTAL_BLOCK) -> paintCrystalFace(context, context.blockPos, context.side)
            // The receptacle renders the paint of the crystal it is mounted against, so
            // dyeing the panel dyes that crystal face no matter which side you click.
            state.isOf(ModBlocks.BOOK_RECEPTACLE) -> {
                val facing = state.get(Properties.FACING)
                paintCrystalFace(context, context.blockPos.offset(facing.opposite), facing)
            }
            else -> ActionResult.PASS
        }
    }

    private fun paintCrystalFace(context: ItemUsageContext, crystalPos: BlockPos, face: Direction): ActionResult {
        val world = context.world
        if (!world.getBlockState(crystalPos).isOf(ModBlocks.CRYSTAL_BLOCK)) return ActionResult.PASS

        val sample = CrystalPaint.getSample(context.stack) ?: return ActionResult.FAIL
        if (!world.isClient) {
            val crystal = world.getBlockEntity(crystalPos) as? PaintedCrystalBlockEntity
                ?: return ActionResult.FAIL
            crystal.setPaintedState(face, sample)
            val receptaclePos = crystalPos.offset(face)
            val receptacleState = world.getBlockState(receptaclePos)
            if (receptacleState.isOf(ModBlocks.BOOK_RECEPTACLE) &&
                receptacleState.get(Properties.FACING) == face
            ) {
                world.setBlockState(
                    receptaclePos,
                    receptacleState.with(BookReceptacleBlock.PAINTED_BACKING, true),
                    3
                )
            }
            world.playSound(
                null,
                crystalPos,
                SoundEvents.ITEM_INK_SAC_USE,
                SoundCategory.BLOCKS,
                1.0f,
                1.0f
            )

            val player = context.player
            if (player == null || !player.abilities.creativeMode) {
                if (CrystalPaint.useOnce(context.stack) == 0) {
                    if (player != null) {
                        player.setStackInHand(context.hand, ItemStack(ModItems.INK_VIAL))
                    } else {
                        context.stack.decrement(1)
                    }
                }
            }
        }
        return ActionResult.success(world.isClient)
    }

    override fun getName(stack: ItemStack): Text {
        val sample = CrystalPaint.getSample(stack)
        return if (sample == null) super.getName(stack)
        else Text.translatable("item.mystcraft-reforged.block_ink_vial.named", sample.block.name)
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        super.appendTooltip(stack, world, tooltip, context)
        tooltip.add(
            Text.translatable("tooltip.mystcraft-reforged.block_ink_vial.uses", CrystalPaint.getUses(stack))
                .formatted(Formatting.GRAY)
        )
        tooltip.add(Text.translatable("tooltip.mystcraft-reforged.block_ink_vial.use").formatted(Formatting.DARK_GRAY))
    }
}
