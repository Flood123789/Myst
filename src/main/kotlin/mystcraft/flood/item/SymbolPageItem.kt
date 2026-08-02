package mystcraft.flood.item

import mystcraft.flood.generation.ChaosAgeThemes
import mystcraft.flood.registry.ModSymbols
import net.minecraft.registry.Registries
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class SymbolPageItem(settings: Settings) : Item(settings) {
    override fun getName(stack: ItemStack): Text {
        val symbolId = stack.nbt?.getString("Symbol") ?: return super.getName(stack)
        val id = Identifier.tryParse(symbolId) ?: return Text.literal("Symbol: ${prettify(symbolId)}")
        if (id.path == "color_custom") {
            val rawDisplay = stack.nbt?.getCompound("display")?.getString("Name").orEmpty()
            val hex = Regex("#[0-9A-Fa-f]{6}").find(rawDisplay)?.value?.uppercase()
            if (hex != null) {
                val rgb = hex.substring(1).toInt(16)
                return Text.literal("Color: $hex").styled { it.withColor(rgb) }
            }
        }
        val descriptor = catalogDescriptor(id)
        return Text.literal("${descriptor.category}: ${descriptor.label}")
    }
    
    companion object {
        private data class CatalogDescriptor(val order: Int, val category: String, val label: String)

        fun catalogSortKey(symbolId: Identifier): String {
            val descriptor = catalogDescriptor(symbolId)
            return "%02d|%s|%s".format(descriptor.order, descriptor.category, descriptor.label)
        }

        private fun catalogDescriptor(id: Identifier): CatalogDescriptor {
            val path = id.path
            val pretty = prettify(path)
            return when {
                ModSymbols.isBiomeSymbol(id) -> CatalogDescriptor(10, "Biome", pretty)
                path.startsWith("terrain_") -> CatalogDescriptor(0, "Terrain", prettify(path.removePrefix("terrain_")))
                path in setOf("color_sky", "color_fog", "color_water", "color_grass", "color_foliage", "color_ambient", "color_clouds", "color_fire_lava") ->
                    CatalogDescriptor(20, "Environment", "${prettify(path.removePrefix("color_"))} Color")
                path.startsWith("color_") -> CatalogDescriptor(30, "Color", prettify(path.removePrefix("color_")))
                path.startsWith("time_") || path.startsWith("weather_") || path.startsWith("cloud_height_") ||
                    path.startsWith("sun_") || path.startsWith("moon_") || path.startsWith("stars_") || path == "no_stars" ||
                    path.startsWith("spawning_") || path == "low_gravity" -> CatalogDescriptor(20, "Environment", pretty)
                path in ChaosAgeThemes.SKY || path.startsWith("particle_") -> CatalogDescriptor(40, "Sky", pretty)
                id == ModSymbols.AGE_EFFECT_SYMBOL || Registries.STATUS_EFFECT.containsId(id) -> CatalogDescriptor(60, "Effect", pretty)
                path.startsWith("biome_") -> CatalogDescriptor(11, "Biome Layout", prettify(path.removePrefix("biome_")))
                else -> CatalogDescriptor(50, "Feature", pretty)
            }
        }

        private fun prettify(raw: String): String = raw.substringAfter(':')
            .replace('_', ' ')
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }

        fun createStack(symbolId: Identifier): ItemStack {
            val stack = ItemStack(ModItems.SYMBOL_PAGE)
            stack.orCreateNbt.putString("Symbol", symbolId.toString())
            return stack
        }
    }
}
