package mystcraft.flood.item

import mystcraft.flood.registry.ModSymbols
import net.minecraft.registry.Registries
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
            val randomSymbol = pickWeightedSymbol(world.random)
            
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

    private fun pickWeightedSymbol(random: net.minecraft.util.math.random.Random): net.minecraft.util.Identifier {
        val weighted = ModSymbols.availableSymbols.map { symbol -> symbol to weightFor(symbol) }
        val totalWeight = weighted.sumOf { it.second }.coerceAtLeast(1)
        var roll = random.nextInt(totalWeight)

        for ((symbol, weight) in weighted) {
            roll -= weight
            if (roll < 0) {
                return symbol
            }
        }

        return weighted.first().first
    }

    private fun weightFor(symbol: net.minecraft.util.Identifier): Int {
        if (symbol == ModSymbols.AGE_EFFECT_SYMBOL) return 1
        if (Registries.STATUS_EFFECT.containsId(symbol)) return 3

        return when (symbol.path) {
            "terrain_cities", "terrain_biospheres", "dense_ores", "spawning_no_mobs", "exotic_virus" -> 2
            "ancient_bones" -> 3
            "forgotten_ruins", "collapsed_observatory", "gateway_ruins", "page_storms", "stable_sanctuaries" -> 4
            "ancient_aqueducts", "memory_blooms" -> 5
            "terrain_caves", "terrain_floating_islands", "giant_trees", "crystal_formations", "tendrils",
            "obelisks", "terrain_amplified", "weather_endless_storm", "weather_normal", "stars_dense", "no_stars", "sun_red", "sun_blue", "low_gravity",
            "biome_checkerboard", "biome_vanilla" -> 5
            else -> if (symbol.path.startsWith("exotic_")) 4 else 14
        }
    }
}
