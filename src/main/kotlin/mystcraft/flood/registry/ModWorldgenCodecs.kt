package mystcraft.flood.registry

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.BiosphereBiomeSource
import mystcraft.flood.generation.BiosphereChunkGenerator
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModWorldgenCodecs {
    private var hasRegistered = false

    fun register() {
        if (hasRegistered) return
        hasRegistered = true

        Registry.register(Registries.BIOME_SOURCE, Identifier(MystcraftReforged.MOD_ID, "biosphere"), BiosphereBiomeSource.CODEC)
        Registry.register(Registries.CHUNK_GENERATOR, Identifier(MystcraftReforged.MOD_ID, "biosphere"), BiosphereChunkGenerator.CODEC)
    }
}
