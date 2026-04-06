package mystcraft.flood.client

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.block.entity.CrystalPortalBlockEntity
import mystcraft.flood.block.entity.ModBlockEntities 
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.client.gui.BookBinderScreen
import mystcraft.flood.client.network.ClientMessages
import mystcraft.flood.client.render.BookReceptacleBlockEntityRenderer
import mystcraft.flood.client.render.MystcraftDimensionEffects
import mystcraft.flood.gui.ModScreens
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer
import net.minecraft.util.Identifier

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

        BlockEntityRendererFactories.register(ModBlockEntities.BOOK_RECEPTACLE) { context ->
            BookReceptacleBlockEntityRenderer(context)
        }

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRYSTAL_BLOCK, RenderLayer.getTranslucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRYSTAL_PORTAL, RenderLayer.getTranslucent())

        // === RANDOMIZED PORTAL COLOR PROVIDER ===
        ColorProviderRegistry.BLOCK.register(net.minecraft.client.color.block.BlockColorProvider { state, world, pos, tintIndex ->
            if (tintIndex == 0 && pos != null && world != null) {
                
                val be = world.getBlockEntity(pos) as? CrystalPortalBlockEntity
                
                // If the block has a color assigned by the server, use it!
                if (be != null && be.portalColor != -1) {
                    // Mask it with 0xFFFFFF so Minecraft never sees a negative number!
                    return@BlockColorProvider be.portalColor and 0xFFFFFF
                }
            }
            
            // Return -1 (Minecraft's default "Do Not Tint" code) if no color is found
            return@BlockColorProvider -1 
            
        }, ModBlocks.CRYSTAL_PORTAL)
        
        MystcraftReforged.LOGGER.info("Client initialized cleanly.")
    }
}