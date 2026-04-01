package mystcraft.flood.client

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.client.gui.BookBinderScreen // ADDED
import mystcraft.flood.client.network.ClientMessages
import mystcraft.flood.client.render.MystcraftDimensionEffects
import mystcraft.flood.gui.ModScreens // ADDED
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry
import net.minecraft.client.gui.screen.ingame.HandledScreens // ADDED
import net.minecraft.util.Identifier

class MystcraftReforgedClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientMessages.registerS2CPackets()
        
        // The Brain and the Canvas can now officially meet!
        HandledScreens.register(ModScreens.BOOK_BINDER_HANDLER, ::BookBinderScreen)

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