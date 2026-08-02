package mystcraft.flood.registry

import mystcraft.flood.config.MystcraftConfig
import mystcraft.flood.item.ModItems
import net.fabricmc.fabric.api.loot.v2.LootTableEvents
import net.minecraft.loot.LootPool
import net.minecraft.loot.condition.RandomChanceLootCondition
import net.minecraft.loot.entry.ItemEntry
import net.minecraft.loot.provider.number.UniformLootNumberProvider
import net.minecraft.util.Identifier

object ModLoot {
    // The vanilla stronghold library
    private val STRONGHOLD_LIBRARY = Identifier("minecraft", "chests/stronghold_library")
    
    // The future custom structure we are going to build!
    private val ABANDONED_ARCHIVE = Identifier("mystcraft-reforged", "chests/abandoned_archive")

    fun register() {
        LootTableEvents.MODIFY.register { _, _, id, tableBuilder, source ->
            
            // If the game is generating one of these specific chests...
            if (id == STRONGHOLD_LIBRARY || id == ABANDONED_ARCHIVE) {
                val loot = MystcraftConfig.current.loot

                // Configurable Lost Page rolls for vanilla/data-driven chests.
                val pagePool = LootPool.builder()
                    .rolls(UniformLootNumberProvider.create(loot.vanillaChestMinPages.toFloat(), loot.vanillaChestMaxPages.toFloat()))
                    .conditionally(RandomChanceLootCondition.builder(loot.vanillaChestPageChance))
                    .with(ItemEntry.builder(ModItems.LOST_PAGE))

                // Inject our pool into the chest!
                tableBuilder.pool(pagePool)
            }
        }
    }
}
