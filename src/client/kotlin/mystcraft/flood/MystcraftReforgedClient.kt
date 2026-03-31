package mystcraft.flood.client

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.cache.ClientAgeCache
import mystcraft.flood.client.network.ClientMessages
import mystcraft.flood.client.render.MystcraftDimensionEffects
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry
import net.minecraft.util.Identifier

class MystcraftReforgedClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientMessages.registerS2CPackets()

        DimensionRenderingRegistry.registerDimensionEffects(
            Identifier("mystcraft-reforged", "age_effects"),
            MystcraftDimensionEffects()
        )
        
        // Wipe the RAM ghosts when quitting to the main menu
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ClientAgeCache.clear()
            MystcraftReforged.LOGGER.info("Cleared Age Cache on disconnect.")
        }
        
        
        MystcraftReforged.LOGGER.info("Client initialized cleanly.")
    }
}