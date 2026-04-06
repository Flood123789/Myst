package mystcraft.flood.client

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.block.entity.ModBlockEntities 
import mystcraft.flood.block.entity.CrystalPortalBlockEntity
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.client.gui.BookBinderScreen
import mystcraft.flood.client.network.ClientMessages
import mystcraft.flood.client.render.MystcraftDimensionEffects
import mystcraft.flood.client.render.BookReceptacleBlockEntityRenderer
import mystcraft.flood.gui.ModScreens
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer
import net.minecraft.util.Identifier
import java.awt.Color
import kotlin.math.abs

class MystcraftReforgedClient : ClientModInitializer {
    
    override fun onInitializeClient() {
        ClientMessages.registerS2CPackets()
        
        HandledScreens.register(ModScreens.BOOK_BINDER_HANDLER, ::BookBinderScreen)

        DimensionRenderingRegistry.registerDimensionEffects(
            Identifier("mystcraft-reforged", "age_effects"),
            MystcraftDimensionEffects()
        )
        
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ClientAgeCache.clear()
            MystcraftReforged.LOGGER.info("Cleared Age Cache on disconnect.")
        }
        
        BlockEntityRendererFactories.register(ModBlockEntities.STAR_FISSURE) { context ->
            EndPortalBlockEntityRenderer(context)
        }

        // === NEW: Register the 3D Book Renderer! ===
        BlockEntityRendererFactories.register(ModBlockEntities.BOOK_RECEPTACLE) { context ->
            BookReceptacleBlockEntityRenderer(context)
        }

        // Tell the client that Crystal Blocks are see-through
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRYSTAL_BLOCK, RenderLayer.getTranslucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRYSTAL_PORTAL, RenderLayer.getTranslucent())

        // === UPDATED: Signature Portal Colors! ===
        ColorProviderRegistry.BLOCK.register({ state, world, pos, tintIndex ->
            if (world != null && pos != null && tintIndex == 0) {
                
                // Grab the portal block and read its destination
                val be = world.getBlockEntity(pos) as? CrystalPortalBlockEntity
                val ageName = be?.destinationAge ?: "unlinked"
                
                // Turn the age name into a unique, stable color! 
                // Every time you go to Age 5, the portal will be the exact same color.
                val hash = ageName.hashCode().toFloat()
                val hue = (abs(hash) % 360.0f) / 360.0f
                
                return@register Color.HSBtoRGB(hue, 0.8f, 0.9f)
            }
            0x8800FF 
        }, ModBlocks.CRYSTAL_PORTAL)
        
        MystcraftReforged.LOGGER.info("Client initialized cleanly.")
    }
}