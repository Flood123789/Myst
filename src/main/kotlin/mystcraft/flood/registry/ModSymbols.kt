package mystcraft.flood.registry

import mystcraft.flood.item.ModItemGroups
import mystcraft.flood.item.SymbolPageItem
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.world.biome.BiomeKeys

object ModSymbols {
    // Using a MutableSet instead of a List prevents duplicates when you reload worlds!
    val availableSymbols = mutableSetOf<Identifier>()

    fun register() {
        // 1. Scan Status Effects / Potions (Dynamic)
        Registries.STATUS_EFFECT.ids.forEach { availableSymbols.add(it) }

        // 2. "Baked" Terrain Types
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_standard"))
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_caves"))
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_floating_islands"))
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_flat"))

        // 3. "Baked" Time Settings
        availableSymbols.add(Identifier("mystcraft-reforged", "time_day"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_night"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_noon"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_midnight"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_fast"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_slow"))

        // 4. "Baked" Weather Settings
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_clear"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_rain"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_thunder"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_endless_storm"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_no_weather"))

        // === NEW STUFF: Colors & Targets ===
        
        // Target Pages
        availableSymbols.add(Identifier("mystcraft-reforged", "color_sky"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_fog"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_water"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_grass"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_foliage"))

        // Color Modifier Pages
        availableSymbols.add(Identifier("mystcraft-reforged", "color_red"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_blue"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_green"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_black"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_white"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_yellow"))
        availableSymbols.add(Identifier("mystcraft-reforged", "color_purple"))
        
        // ===================================

        // 5. Scrape ALL Vanilla Biomes via Reflection
        BiomeKeys::class.java.fields.forEach { field ->
            if (field.type == RegistryKey::class.java) {
                val key = field.get(null) as? RegistryKey<*>
                if (key != null && key.isOf(RegistryKeys.BIOME)) {
                    availableSymbols.add(key.value)
                }
            }
        }

        // 6. Hook into Server Start to grab Modded Biomes from the Dynamic Registry
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val biomeRegistry = server.registryManager.get(RegistryKeys.BIOME)
            biomeRegistry.keys.forEach { key ->
                availableSymbols.add(key.value)
            }
        }

        // 7. Inject them into your CUSTOM Creative Tab!
        ItemGroupEvents.modifyEntriesEvent(ModItemGroups.MYSTCRAFT_PAGES_KEY).register { entries ->
            // Sorting them alphabetically by their path so the menu isn't a chaotic mess
            availableSymbols.sortedBy { it.path }.forEach { symbolId ->
                entries.add(SymbolPageItem.createStack(symbolId))
            }
        }
    }
}