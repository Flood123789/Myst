package mystcraft.flood.item

import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtHelper
import net.minecraft.registry.Registries

object CrystalPaint {
    private const val STATE_KEY = "CrystalPaintState"
    private const val COLOR_KEY = "CrystalPaintColor"
    private const val USES_KEY = "CrystalPaintUses"

    fun setSample(stack: ItemStack, state: BlockState, color: Int) {
        stack.orCreateNbt.put(STATE_KEY, NbtHelper.fromBlockState(state))
        stack.orCreateNbt.putInt(COLOR_KEY, color and 0xFFFFFF)
        stack.orCreateNbt.putInt(USES_KEY, MAX_USES)
    }

    fun getSample(stack: ItemStack): BlockState? {
        val nbt = stack.nbt ?: return null
        if (!nbt.contains(STATE_KEY)) return null
        return NbtHelper.toBlockState(Registries.BLOCK.readOnlyWrapper, nbt.getCompound(STATE_KEY))
    }

    fun getColor(stack: ItemStack): Int = stack.nbt?.let {
        if (it.contains(COLOR_KEY)) it.getInt(COLOR_KEY) and 0xFFFFFF else DEFAULT_INK_COLOR
    } ?: DEFAULT_INK_COLOR

    fun getUses(stack: ItemStack): Int = stack.nbt?.getInt(USES_KEY) ?: 0

    fun useOnce(stack: ItemStack): Int {
        val remaining = (getUses(stack) - 1).coerceAtLeast(0)
        stack.orCreateNbt.putInt(USES_KEY, remaining)
        return remaining
    }

    fun canSample(state: BlockState): Boolean =
        !state.isAir && state.renderType == BlockRenderType.MODEL && !state.hasBlockEntity()

    const val DEFAULT_INK_COLOR: Int = 0x24141F
    const val MAX_USES: Int = 16
}
