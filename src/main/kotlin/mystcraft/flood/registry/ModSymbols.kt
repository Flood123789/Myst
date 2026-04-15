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
    val AGE_EFFECT_SYMBOL: Identifier = Identifier("mystcraft-reforged", "age_effect")

    private fun isPotionPageSymbol(symbolId: Identifier): Boolean = symbolId == AGE_EFFECT_SYMBOL || Registries.STATUS_EFFECT.containsId(symbolId)

    fun register() {
        // 1. Scan Status Effects / Potions (Dynamic)
        Registries.STATUS_EFFECT.ids
            .filter { Registries.STATUS_EFFECT.get(it)?.isInstant == false }
            .forEach { availableSymbols.add(it) }

        // 2. "Baked" Terrain Types
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_standard"))
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_amplified"))
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_caves"))
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_floating_islands"))
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_flat"))
        // === NEW TERRAIN PAGES ADDED HERE ===
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_biospheres"))
        availableSymbols.add(Identifier("mystcraft-reforged", "terrain_cities"))

        // 3. "Baked" Time Settings
        availableSymbols.add(Identifier("mystcraft-reforged", "time_day"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_night"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_noon"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_midnight"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_fast"))
        availableSymbols.add(Identifier("mystcraft-reforged", "time_slow"))

       // 4. "Baked" Weather Settings
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_clear"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_normal"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_rain"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_thunder"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_endless_storm"))
        availableSymbols.add(Identifier("mystcraft-reforged", "weather_no_weather"))
        availableSymbols.add(Identifier("mystcraft-reforged", "low_gravity"))

        // === NEW STUFF: Modifiers ===
        availableSymbols.add(Identifier("mystcraft-reforged", "dense_ores")) 
        availableSymbols.add(Identifier("mystcraft-reforged", "giant_trees"))
        availableSymbols.add(Identifier("mystcraft-reforged", "crystal_formations"))
        availableSymbols.add(Identifier("mystcraft-reforged", "tendrils"))
        availableSymbols.add(Identifier("mystcraft-reforged", "obelisks"))
        availableSymbols.add(Identifier("mystcraft-reforged", "ancient_bones"))
        availableSymbols.add(Identifier("mystcraft-reforged", "forgotten_ruins"))
        availableSymbols.add(Identifier("mystcraft-reforged", "collapsed_observatory"))
        availableSymbols.add(Identifier("mystcraft-reforged", "ancient_aqueducts"))
        availableSymbols.add(Identifier("mystcraft-reforged", "gateway_ruins"))
        availableSymbols.add(Identifier("mystcraft-reforged", "exotic_hex"))
        availableSymbols.add(Identifier("mystcraft-reforged", "exotic_wire_cells"))
        availableSymbols.add(Identifier("mystcraft-reforged", "exotic_separators"))
        availableSymbols.add(Identifier("mystcraft-reforged", "exotic_cables"))
        availableSymbols.add(Identifier("mystcraft-reforged", "exotic_fractal_cubes"))
        availableSymbols.add(Identifier("mystcraft-reforged", "exotic_light_fissures"))
        availableSymbols.add(Identifier("mystcraft-reforged", "exotic_virus"))
        availableSymbols.add(Identifier("mystcraft-reforged", "page_storms"))
        availableSymbols.add(Identifier("mystcraft-reforged", "memory_blooms"))
        availableSymbols.add(Identifier("mystcraft-reforged", "stable_sanctuaries"))

        // === NEW STUFF: Biome Controllers ===
        availableSymbols.add(Identifier("mystcraft-reforged", "biome_checkerboard"))
        availableSymbols.add(Identifier("mystcraft-reforged", "biome_vanilla"))

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
        availableSymbols.add(Identifier("mystcraft-reforged", "color_custom"))

        // === NEW STUFF: Celestial Bodies ===
        availableSymbols.add(Identifier("mystcraft-reforged", "sun_normal"))
        availableSymbols.add(Identifier("mystcraft-reforged", "sun_red"))
        availableSymbols.add(Identifier("mystcraft-reforged", "sun_blue"))
        
        availableSymbols.add(Identifier("mystcraft-reforged", "moon_normal"))
        availableSymbols.add(Identifier("mystcraft-reforged", "moon_extra")) // Stacks to increase count
        
        availableSymbols.add(Identifier("mystcraft-reforged", "stars_normal"))
        availableSymbols.add(Identifier("mystcraft-reforged", "stars_dense")) // Stacks to layer the texture
        availableSymbols.add(Identifier("mystcraft-reforged", "no_stars"))

        // === NEW STUFF: Spawning ===
        availableSymbols.add(Identifier("mystcraft-reforged", "spawning_normal"))
        availableSymbols.add(Identifier("mystcraft-reforged", "spawning_no_mobs"))
        availableSymbols.add(Identifier("mystcraft-reforged", "spawning_extra_hostile"))
        availableSymbols.add(Identifier("mystcraft-reforged", "spawning_extra_passive"))
        availableSymbols.add(AGE_EFFECT_SYMBOL)

        // The Wildcard Page
        availableSymbols.add(Identifier("mystcraft-reforged", "random"))
        
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
            availableSymbols.sortedBy { it.path }.filterNot(::isPotionPageSymbol).forEach { symbolId ->
                entries.add(SymbolPageItem.createStack(symbolId))
            }
        }

        ItemGroupEvents.modifyEntriesEvent(ModItemGroups.MYSTCRAFT_EFFECTS_KEY).register { entries ->
            availableSymbols.sortedBy { it.path }.filter(::isPotionPageSymbol).forEach { symbolId ->
                entries.add(SymbolPageItem.createStack(symbolId))
            }
        }
    }
}
