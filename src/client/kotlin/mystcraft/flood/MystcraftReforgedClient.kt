package mystcraft.flood.client

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.block.entity.CrystalPortalBlockEntity
import mystcraft.flood.block.entity.ModBlockEntities 
import mystcraft.flood.client.AgeTravelSoundSuppressor
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.client.gui.BookBinderScreen
import mystcraft.flood.client.gui.EditingTableScreen
import mystcraft.flood.client.gui.NotebookScreen
import mystcraft.flood.client.gui.PrintingTableScreen
import mystcraft.flood.client.gui.WritingDeskScreen
import mystcraft.flood.client.network.ClientMessages
import mystcraft.flood.client.render.AgeAmbientParticlePainter
import mystcraft.flood.client.render.AgePlantTintHelper
import mystcraft.flood.client.render.BookStandBlockEntityRenderer
import mystcraft.flood.client.render.BookReceptacleBlockEntityRenderer
import mystcraft.flood.client.render.ClientRenderCompatibility
import mystcraft.flood.client.render.CustomSkyPainter
import mystcraft.flood.client.render.DescriptiveBookEntityRenderer
import mystcraft.flood.client.render.ModEntityModelLayers
import mystcraft.flood.client.render.MystcraftDimensionEffects
import mystcraft.flood.client.render.PageIconItemRenderer
import mystcraft.flood.compat.DistantHorizonsCompat
import mystcraft.flood.entity.ModEntities
import mystcraft.flood.gui.ModScreens
import mystcraft.flood.item.NotebookItem
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.item.ModelPredicateProviderRegistry
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.block.Blocks
import net.minecraft.util.Identifier
import mystcraft.flood.item.ModItems
import org.joml.Matrix4f

class MystcraftReforgedClient : ClientModInitializer {
    
    override fun onInitializeClient() {
        ClientMessages.registerS2CPackets()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            AgeTravelSoundSuppressor.INSTANCE.tick(client)
            AgeAmbientParticlePainter.tick(client)
            DistantHorizonsCompat.tick()
        }

        WorldRenderEvents.AFTER_SETUP.register { context ->
            if (!ClientRenderCompatibility.canUseShaderFallbackSkyOverlay()) return@register
            val world = context.world() ?: return@register
            if (world.registryKey.value.namespace != MystcraftReforged.MOD_ID) return@register

            val skyView = Matrix4f(context.matrixStack().peek().positionMatrix)
            skyView.m30(0.0f)
            skyView.m31(0.0f)
            skyView.m32(0.0f)

            val skyMatrices = MatrixStack()
            skyMatrices.peek().positionMatrix.set(skyView)
            // Shaderpacks often replace the vanilla sky pass. Draw the fallback before terrain
            // and DH LOD chunks so they can occlude sky anomalies like normal distant scenery.
            skyMatrices.scale(3.0f, 3.0f, 3.0f)
            CustomSkyPainter.paintShaderFallbackSky(skyMatrices, context.projectionMatrix(), context.tickDelta())
        }
        
        HandledScreens.register(ModScreens.BOOK_BINDER_HANDLER, ::BookBinderScreen)
        HandledScreens.register(ModScreens.WRITING_DESK_HANDLER, ::WritingDeskScreen)
        HandledScreens.register(ModScreens.EDITING_TABLE_HANDLER, ::EditingTableScreen)
        HandledScreens.register(ModScreens.PRINTING_TABLE_HANDLER, ::PrintingTableScreen)
        HandledScreens.register(ModScreens.NOTEBOOK_HANDLER, ::NotebookScreen)
        ModEntityModelLayers.register()
        EntityRendererRegistry.register(ModEntities.DESCRIPTIVE_BOOK_ANCHOR, ::DescriptiveBookEntityRenderer)

        DimensionRenderingRegistry.registerDimensionEffects(
            Identifier("mystcraft-reforged", "age_effects"),
            MystcraftDimensionEffects()
        )
        
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ClientAgeCache.clear()
            DistantHorizonsCompat.clear()
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
        ModelPredicateProviderRegistry.register(ModItems.NOTEBOOK, Identifier(MystcraftReforged.MOD_ID, "filled")) { stack, _, _, _ ->
            if (NotebookItem.getSymbols(stack).isNotEmpty()) 1.0f else 0.0f
        }

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
            Blocks.OAK_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
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
            Blocks.PINK_PETALS,
            Blocks.WATER
        )
        
        MystcraftReforged.LOGGER.info("Client initialized cleanly. Render compatibility mode: ${ClientRenderCompatibility.describe()}")
    }
}
