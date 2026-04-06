package mystcraft.flood.item

import mystcraft.flood.registry.ModSymbols
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class LostPageItem(settings: Settings) : Item(settings) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)

        if (!world.isClient) {
            // 1. Pick a completely random symbol from our master registry!
            val randomSymbol = ModSymbols.availableSymbols.random()
            
            // 2. Generate the actual, usable Symbol Page
            val identifiedPage = SymbolPageItem.createStack(randomSymbol)

            // 3. Try to put it in their inventory. If full, drop it on the floor.
            if (!user.inventory.insertStack(identifiedPage)) {
                user.dropItem(identifiedPage, false)
            }

            // 4. Consume the Lost Page
            if (!user.isCreative) {
                stack.decrement(1)
            }

            // 5. Play a satisfying magical sound effect!
            world.playSound(null, user.blockPos, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.5f, 1.5f)
        }

        return TypedActionResult.success(stack, world.isClient())
    }
}