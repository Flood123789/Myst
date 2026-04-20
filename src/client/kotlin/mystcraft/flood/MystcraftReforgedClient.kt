package mystcraft.flood.client

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.block.entity.CrystalPortalBlockEntity
import mystcraft.flood.block.entity.ModBlockEntities 
import mystcraft.flood.client.AgeTravelSoundSuppressor
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.client.gui.BookBinderScreen
import mystcraft.flood.client.gui.NotebookScreen
import mystcraft.flood.client.network.ClientMessages
import mystcraft.flood.client.render.AgePlantTintHelper
import mystcraft.flood.client.render.BookStandBlockEntityRenderer
import mystcraft.flood.client.render.BookReceptacleBlockEntityRenderer
import mystcraft.flood.client.render.MystcraftDimensionEffects
import mystcraft.flood.client.render.PageIconItemRenderer
import mystcraft.flood.gui.ModScreens
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer
import net.minecraft.block.Blocks
import net.minecraft.util.Identifier
import mystcraft.flood.item.ModItems

class MystcraftReforgedClient : ClientModInitializer {
    
    override fun onInitializeClient() {
        ClientMessages.registerS2CPackets()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            AgeTravelSoundSuppressor.INSTANCE.tick(client)
        }
        
        HandledScreens.register(ModScreens.BOOK_BINDER_HANDLER, ::BookBinderScreen)
        HandledScreens.register(ModScreens.NOTEBOOK_HANDLER, ::NotebookScreen)

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
        BlockEntityRendererFactories.register(ModBlockEntities.BOOK_STAND) { context ->
            BookStandBlockEntityRenderer(context)
        }

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRYSTAL_BLOCK, RenderLayer.getTranslucent())
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRYSTAL_PORTAL, RenderLayer.getTranslucent())

        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.SYMBOL_PAGE, PageIconItemRenderer)
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.LOST_PAGE, PageIconItemRenderer)

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

        ColorProviderRegistry.BLOCK.register(net.minecraft.client.color.block.BlockColorProvider { state, world, pos, _ ->
            AgePlantTintHelper.getTintFor(state.block, world, pos)
        },
            Blocks.BIRCH_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.MANGROVE_LEAVES,
            Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.VINE,
            Blocks.LILY_PAD,
            Blocks.SUGAR_CANE,
            Blocks.MELON_STEM,
            Blocks.ATTACHED_MELON_STEM,
            Blocks.PUMPKIN_STEM,
            Blocks.ATTACHED_PUMPKIN_STEM,
            Blocks.GRASS_BLOCK,
            Blocks.GRASS,
            Blocks.TALL_GRASS,
            Blocks.FERN,
            Blocks.LARGE_FERN,
            Blocks.POTTED_FERN,
            Blocks.SEAGRASS,
            Blocks.TALL_SEAGRASS,
            Blocks.SMALL_DRIPLEAF,
            Blocks.BIG_DRIPLEAF,
            Blocks.BIG_DRIPLEAF_STEM,
            Blocks.MOSS_BLOCK,
            Blocks.MOSS_CARPET,
            Blocks.PINK_PETALS
        )
        
        MystcraftReforged.LOGGER.info("Client initialized cleanly.")
    }
}
