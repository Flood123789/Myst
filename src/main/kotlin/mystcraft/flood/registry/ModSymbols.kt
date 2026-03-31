package mystcraft.flood.registry

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.symbol.AgeSymbol
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.SimpleRegistry
import net.minecraft.util.Identifier

object ModSymbols {
    val KEY: RegistryKey<Registry<AgeSymbol>> = RegistryKey.ofRegistry(Identifier(MystcraftReforged.MOD_ID, "symbols"))
    val REGISTRY: SimpleRegistry<AgeSymbol> = SimpleRegistry(KEY, com.mojang.serialization.Lifecycle.stable())

    fun register() {
        add("flat_terrain", 0, AgeSymbol.Category.TERRAIN)
        add("void_terrain", 0, AgeSymbol.Category.TERRAIN)
    }

    private fun add(name: String, instability: Int, cat: AgeSymbol.Category) {
        val id = Identifier(MystcraftReforged.MOD_ID, name)
        Registry.register(REGISTRY, id, AgeSymbol(id, instability, cat))
    }
}