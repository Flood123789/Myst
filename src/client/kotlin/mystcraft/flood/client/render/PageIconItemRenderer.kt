package mystcraft.flood.client.render

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.item.ModItems
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import org.joml.Matrix3f
import org.joml.Matrix4f

/**
 * Draws symbol-specific ink glyphs over the shared paper item model.
 *
 * Pages encode their symbol in NBT, so static model JSON cannot select every icon. The renderer
 * maps symbol families to small procedural glyphs and uses full light in inventory GUIs while
 * respecting world light when a page is held or displayed.
 */
object PageIconItemRenderer : BuiltinItemRendererRegistry.DynamicItemRenderer {
    private val WHITE_TEXTURE = Identifier("minecraft", "textures/misc/white.png")
    private const val PAPER = 0xFFF7EACB.toInt()
    private const val INK = 0xFF5E4732.toInt()
    private const val BLUE = 0xFF79AFFA.toInt()
    private const val GREEN = 0xFF70BE61.toInt()
    private const val TEAL = 0xFF5ACBC1.toInt()
    private const val GOLD = 0xFFF6D26D.toInt()
    private const val SILVER = 0xFFD6DEE8.toInt()
    private const val PURPLE = 0xFFB287FF.toInt()
    private const val RED = 0xFFE98787.toInt()
    private const val GRAY = 0xFFB0BAC7.toInt()
    private const val PAPER_GLOW = 0x88FFF9EE.toInt()

    override fun render(
        stack: ItemStack,
        mode: ModelTransformationMode,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int
    ) {
        val client = MinecraftClient.getInstance()
        val itemRenderer = client.itemRenderer
        val baseStack = ItemStack(ModItems.PAGE)
        val baseModel = itemRenderer.getModel(baseStack, null, null, 0)
        val renderLight = if (mode == ModelTransformationMode.GUI) LightmapTextureManager.MAX_LIGHT_COORDINATE else light

        matrices.push()
        if (mode == ModelTransformationMode.GUI) {
            matrices.translate(0.20f, 0.14f, 0.0f)
        }

        itemRenderer.renderItem(baseStack, mode, false, matrices, vertexConsumers, renderLight, overlay, baseModel)

        matrices.translate(0.0, 0.0, 0.035)
        drawPaperGlow(matrices, vertexConsumers, renderLight, overlay)
        drawBorder(matrices, vertexConsumers, renderLight, overlay)

        if (stack.item == ModItems.LOST_PAGE) {
            drawLostPage(matrices, vertexConsumers, renderLight, overlay)
        } else {
            drawSymbol(stack.nbt?.getString("Symbol"), matrices, vertexConsumers, renderLight, overlay)
        }

        matrices.pop()
    }

    private fun drawSymbol(symbolId: String?, matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        val clean = symbolId?.lowercase()?.replace("${MystcraftReforged.MOD_ID}:", "") ?: run {
            drawQuestion(matrices, providers, 8f, 8f, 1f, INK, light, overlay)
            return
        }

        when {
            clean.startsWith("terrain_") -> drawCube(matrices, providers, BLUE, light, overlay)
            clean.startsWith("biome_") -> if (clean.contains("checkerboard")) drawGrid(matrices, providers, light, overlay) else drawTree(matrices, providers, light, overlay)
            clean == "age_effect" -> drawAgeEffect(matrices, providers, light, overlay)
            clean.startsWith("sun_") -> drawSun(matrices, providers, light, overlay)
            clean.startsWith("moon_") -> drawMoon(matrices, providers, light, overlay)
            clean.startsWith("stars_") || clean == "no_stars" -> drawStars(matrices, providers, light, overlay)
            clean.startsWith("time_") -> drawClock(matrices, providers, light, overlay)
            clean.startsWith("weather_") -> drawWeather(clean, matrices, providers, light, overlay)
            clean == "low_gravity" -> drawLowGravity(matrices, providers, light, overlay)
            clean.startsWith("cloud_height_") -> drawCloudHeight(clean, matrices, providers, light, overlay)
            clean.startsWith("spawning_") -> drawPaw(matrices, providers, light, overlay)
            clean == "dense_ores" -> drawOre(matrices, providers, light, overlay)
            clean == "giant_trees" -> drawGiantTree(matrices, providers, light, overlay)
            clean == "ancient_bones" -> drawBones(matrices, providers, light, overlay)
            clean == "forgotten_ruins" -> drawRuins(matrices, providers, light, overlay)
            clean == "collapsed_observatory" -> drawObservatory(matrices, providers, light, overlay)
            clean == "ancient_aqueducts" -> drawAqueduct(matrices, providers, light, overlay)
            clean == "gateway_ruins" -> drawGateway(matrices, providers, light, overlay)
            clean == "page_storms" -> drawPageStorm(matrices, providers, light, overlay)
            clean == "memory_blooms" -> drawMemoryBloom(matrices, providers, light, overlay)
            clean == "stable_sanctuaries" -> drawSanctuary(matrices, providers, light, overlay)
            clean in setOf(
                "sky_rainbows", "sky_auroras", "shooting_stars", "comets", "sky_rifts",
                "sky_nebulae", "eclipse_halos", "star_glyphs", "horizon_mirages", "crystal_halos",
                "void_flecks", "spiral_galaxies", "falling_sky_shards", "lightning_veins",
                "luminous_columns", "sky_monoliths", "prism_rings", "chroma_waves", "orbital_grid",
                "sky_lanterns", "fracture_web", "dream_veils", "sky_bubbles", "starfall_blooms",
                "horizon_crowns", "celestial_script", "glass_constellations", "radiant_whirlpools",
                "bright_sky", "dark_sky"
            ) ->
                drawChaosSkySymbol(clean, matrices, providers, light, overlay)
            clean == "meteor_showers" -> drawMeteor(matrices, providers, light, overlay)
            clean == "sky_spheres" -> drawSkySphere(matrices, providers, light, overlay)
            clean.startsWith("particle_") -> drawParticleSymbol(clean, matrices, providers, light, overlay)
            clean == "crystal_formations" -> drawCrystals(matrices, providers, light, overlay)
            clean == "tendrils" -> drawTendrils(matrices, providers, light, overlay)
            clean == "obelisks" || clean == "giant_obelisks" -> drawObelisk(matrices, providers, light, overlay)
            clean.startsWith("exotic_") -> drawExotic(clean, matrices, providers, light, overlay)
            clean.startsWith("color_") -> drawColorSymbol(clean, matrices, providers, light, overlay)
            isStatusEffect(symbolId) -> drawPotion(matrices, providers, light, overlay)
            else -> drawTree(matrices, providers, light, overlay)
        }
    }

    private fun drawColorSymbol(clean: String, matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        when (clean) {
            "color_sky" -> drawSky(matrices, providers, light, overlay)
            "color_fog" -> drawFog(matrices, providers, light, overlay)
            "color_water" -> drawWater(matrices, providers, light, overlay)
            "color_grass" -> drawGrass(matrices, providers, light, overlay)
            "color_foliage" -> drawLeaf(matrices, providers, light, overlay)
            "color_ambient" -> drawAmbient(matrices, providers, light, overlay)
            "color_clouds" -> drawCloudTarget(matrices, providers, light, overlay)
            "color_fire_lava" -> drawFireLava(matrices, providers, light, overlay)
            "color_red" -> drawSwatch(matrices, providers, 0xFFE24D4D.toInt(), light, overlay)
            "color_blue" -> drawSwatch(matrices, providers, 0xFF4A7FDD.toInt(), light, overlay)
            "color_green" -> drawSwatch(matrices, providers, 0xFF54B859.toInt(), light, overlay)
            "color_black" -> drawSwatch(matrices, providers, 0xFF222222.toInt(), light, overlay)
            "color_white" -> drawSwatch(matrices, providers, 0xFFF2F2F2.toInt(), light, overlay)
            "color_yellow" -> drawSwatch(matrices, providers, 0xFFF0C94E.toInt(), light, overlay)
            "color_purple" -> drawSwatch(matrices, providers, 0xFF9B62E4.toInt(), light, overlay)
            "color_orange" -> drawSwatch(matrices, providers, 0xFFFF8800.toInt(), light, overlay)
            "color_cyan" -> drawSwatch(matrices, providers, 0xFF00FFFF.toInt(), light, overlay)
            "color_teal" -> drawSwatch(matrices, providers, 0xFF008080.toInt(), light, overlay)
            "color_pink" -> drawSwatch(matrices, providers, 0xFFFF69B4.toInt(), light, overlay)
            "color_magenta" -> drawSwatch(matrices, providers, 0xFFFF00FF.toInt(), light, overlay)
            "color_lime" -> drawSwatch(matrices, providers, 0xFF7FFF00.toInt(), light, overlay)
            "color_brown" -> drawSwatch(matrices, providers, 0xFF8B4513.toInt(), light, overlay)
            "color_gray" -> drawSwatch(matrices, providers, 0xFF808080.toInt(), light, overlay)
            "color_light_blue" -> drawSwatch(matrices, providers, 0xFF66CCFF.toInt(), light, overlay)
            else -> drawPalette(matrices, providers, light, overlay)
        }
    }

    private fun drawWeather(clean: String, matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        drawCloud(matrices, providers, light, overlay)
        when {
            clean.contains("thunder") || clean.contains("storm") -> drawLightning(matrices, providers, light, overlay)
            clean.contains("rain") -> drawRain(matrices, providers, light, overlay)
        }
    }

    private fun drawCloudHeight(clean: String, matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        drawCloud(matrices, providers, light, overlay)
        when {
            clean.contains("low") -> {
                line(matrices, providers, 8f, 4.4f, 8f, 11.8f, 0.5f, BLUE, light, overlay)
                line(matrices, providers, 6.9f, 10.4f, 8f, 11.8f, 0.5f, BLUE, light, overlay)
                line(matrices, providers, 9.1f, 10.4f, 8f, 11.8f, 0.5f, BLUE, light, overlay)
            }
            clean.contains("high") -> {
                line(matrices, providers, 8f, 11.6f, 8f, 4.2f, 0.5f, BLUE, light, overlay)
                line(matrices, providers, 6.9f, 5.6f, 8f, 4.2f, 0.5f, BLUE, light, overlay)
                line(matrices, providers, 9.1f, 5.6f, 8f, 4.2f, 0.5f, BLUE, light, overlay)
            }
            else -> {
                line(matrices, providers, 5.1f, 11.6f, 5.1f, 4.6f, 0.38f, BLUE, light, overlay)
                line(matrices, providers, 10.9f, 11.6f, 10.9f, 4.6f, 0.38f, BLUE, light, overlay)
            }
        }
    }

    private fun drawExotic(clean: String, matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        when {
            clean.contains("hex") -> drawHex(matrices, providers, light, overlay)
            clean.contains("wire") -> drawWire(matrices, providers, light, overlay)
            clean.contains("separator") -> drawSeparator(matrices, providers, light, overlay)
            clean.contains("cable") -> drawCable(matrices, providers, light, overlay)
            clean.contains("fractal") -> drawFractal(matrices, providers, light, overlay)
            clean.contains("virus") -> drawVirus(matrices, providers, light, overlay)
            else -> drawLightFissure(matrices, providers, light, overlay)
        }
    }

    private fun drawBorder(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 2f, 2f, 14f, 2.7f, INK, light, overlay)
        rect(matrices, providers, 2f, 13.3f, 14f, 14f, INK, light, overlay)
        rect(matrices, providers, 2f, 2f, 2.7f, 14f, INK, light, overlay)
        rect(matrices, providers, 13.3f, 2f, 14f, 14f, INK, light, overlay)
    }

    private fun drawPaperGlow(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 3f, 3f, 13f, 13f, PAPER_GLOW, light, overlay)
    }

    private fun drawLostPage(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        drawQuestion(matrices, providers, 8f, 7.8f, 1f, PURPLE, light, overlay)
        star(matrices, providers, 4.8f, 4.7f, 0.8f, GRAY, light, overlay)
        star(matrices, providers, 11.4f, 5.6f, 0.6f, GOLD, light, overlay)
        star(matrices, providers, 10.6f, 11.2f, 0.6f, GRAY, light, overlay)
    }

    private fun drawCube(matrices: MatrixStack, providers: VertexConsumerProvider, color: Int, light: Int, overlay: Int) {
        rect(matrices, providers, 4f, 6f, 10f, 12f, color, light, overlay)
        line(matrices, providers, 4f, 6f, 6.2f, 4f, 0.7f, INK, light, overlay)
        line(matrices, providers, 10f, 6f, 12.2f, 4f, 0.7f, INK, light, overlay)
        line(matrices, providers, 6.2f, 4f, 12.2f, 4f, 0.7f, INK, light, overlay)
        line(matrices, providers, 12.2f, 4f, 12.2f, 10f, 0.7f, INK, light, overlay)
        line(matrices, providers, 10f, 12f, 12.2f, 10f, 0.7f, INK, light, overlay)
    }

    private fun drawTree(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 7.2f, 9f, 8.8f, 12.3f, 0xFF7A5234.toInt(), light, overlay)
        rect(matrices, providers, 4.6f, 4.5f, 11.4f, 9.2f, GREEN, light, overlay)
        rect(matrices, providers, 5.6f, 3.2f, 10.4f, 5f, GREEN, light, overlay)
    }

    private fun drawGiantTree(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6.8f, 7.8f, 9.2f, 12.6f, 0xFF6D472C.toInt(), light, overlay)
        rect(matrices, providers, 4.1f, 4.8f, 11.9f, 8.6f, GREEN, light, overlay)
        rect(matrices, providers, 5.1f, 3.1f, 10.9f, 5.2f, GREEN, light, overlay)
        rect(matrices, providers, 3.4f, 6.2f, 5.2f, 8.3f, 0xFF4E9F54.toInt(), light, overlay)
        rect(matrices, providers, 10.8f, 5.8f, 12.6f, 8.1f, 0xFF4E9F54.toInt(), light, overlay)
        line(matrices, providers, 7.3f, 9.1f, 5.3f, 6.6f, 0.5f, 0xFF7A5234.toInt(), light, overlay)
        line(matrices, providers, 8.7f, 9.0f, 10.7f, 6.4f, 0.5f, 0xFF7A5234.toInt(), light, overlay)
    }

    private fun drawSky(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        sunCore(matrices, providers, 11.3f, 4.8f, 1.2f, GOLD, light, overlay)
        drawCloud(matrices, providers, light, overlay)
        line(matrices, providers, 4.1f, 10.7f, 11.9f, 10.7f, 0.6f, BLUE, light, overlay)
    }

    private fun drawFog(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4f, 5.2f, 12f, 6.2f, 0x88A6B2BF.toInt(), light, overlay)
        rect(matrices, providers, 3.4f, 7.6f, 12.6f, 8.7f, 0x88A6B2BF.toInt(), light, overlay)
        rect(matrices, providers, 4.4f, 10f, 11.6f, 11.1f, 0x88A6B2BF.toInt(), light, overlay)
    }

    private fun drawWater(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 4f, 6f, 7f, 5.3f, 0.7f, TEAL, light, overlay)
        line(matrices, providers, 7f, 5.3f, 10f, 6f, 0.7f, TEAL, light, overlay)
        line(matrices, providers, 4f, 8.5f, 7f, 7.8f, 0.7f, TEAL, light, overlay)
        line(matrices, providers, 7f, 7.8f, 10f, 8.5f, 0.7f, TEAL, light, overlay)
    }

    private fun drawGrass(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4f, 11.2f, 12f, 11.8f, GREEN, light, overlay)
        line(matrices, providers, 5f, 11.2f, 6.1f, 6.2f, 0.6f, GREEN, light, overlay)
        line(matrices, providers, 7.2f, 11.2f, 7.8f, 5.2f, 0.6f, GREEN, light, overlay)
        line(matrices, providers, 9f, 11.2f, 10.5f, 6.8f, 0.6f, GREEN, light, overlay)
    }

    private fun drawLeaf(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 5f, 5f, 11f, 10.2f, 0xFF5AA95F.toInt(), light, overlay)
        line(matrices, providers, 8f, 10.8f, 8f, 6f, 0.5f, 0xFF2B7B3A.toInt(), light, overlay)
        line(matrices, providers, 8f, 8f, 10.3f, 6.2f, 0.5f, 0xFF2B7B3A.toInt(), light, overlay)
        line(matrices, providers, 8f, 8f, 5.7f, 6.7f, 0.5f, 0xFF2B7B3A.toInt(), light, overlay)
    }

    private fun drawAmbient(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        star(matrices, providers, 5.3f, 5.2f, 0.55f, GOLD, light, overlay)
        star(matrices, providers, 10.9f, 6.0f, 0.45f, SILVER, light, overlay)
        rect(matrices, providers, 4.4f, 8.0f, 11.6f, 10.6f, 0x88A48CFF.toInt(), light, overlay)
        rect(matrices, providers, 5.3f, 6.7f, 10.7f, 8.4f, 0x6678C8FF, light, overlay)
    }

    private fun drawCloudTarget(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        drawCloud(matrices, providers, light, overlay)
        line(matrices, providers, 4.7f, 11.5f, 11.3f, 11.5f, 0.55f, BLUE, light, overlay)
        star(matrices, providers, 10.9f, 4.6f, 0.45f, SILVER, light, overlay)
    }

    private fun drawFireLava(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.8f, 9.8f, 11.2f, 11.2f, 0xFFE25322.toInt(), light, overlay)
        line(matrices, providers, 6.0f, 11.1f, 6.7f, 7.0f, 0.65f, 0xFFFFC04D.toInt(), light, overlay)
        line(matrices, providers, 8.0f, 11.1f, 8.8f, 5.6f, 0.75f, 0xFFFF7B2F.toInt(), light, overlay)
        line(matrices, providers, 10.0f, 11.1f, 9.3f, 7.4f, 0.65f, 0xFFFFC04D.toInt(), light, overlay)
        rect(matrices, providers, 5.1f, 4.4f, 10.9f, 6.0f, 0xFFFF6A00.toInt(), light, overlay)
    }

    private fun drawSun(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        sunCore(matrices, providers, 8f, 8f, 1.7f, GOLD, light, overlay)
        line(matrices, providers, 8f, 3.2f, 8f, 5.1f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 8f, 10.9f, 8f, 12.8f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 3.2f, 8f, 5.1f, 8f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 10.9f, 8f, 12.8f, 8f, 0.6f, GOLD, light, overlay)
    }

    private fun drawMoon(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 5f, 4.5f, 10.5f, 11f, SILVER, light, overlay)
        rect(matrices, providers, 7f, 5.2f, 11f, 10.3f, PAPER, light, overlay)
        star(matrices, providers, 11.4f, 5.2f, 0.5f, GOLD, light, overlay)
    }

    private fun drawStars(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        star(matrices, providers, 6f, 5.5f, 0.85f, GOLD, light, overlay)
        star(matrices, providers, 10.3f, 7.1f, 0.65f, SILVER, light, overlay)
        star(matrices, providers, 7.5f, 10.5f, 0.75f, GOLD, light, overlay)
    }

    private fun drawClock(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.8f, 4.8f, 11.2f, 11.2f, SILVER, light, overlay)
        rect(matrices, providers, 5.8f, 5.8f, 10.2f, 10.2f, PAPER, light, overlay)
        line(matrices, providers, 8f, 8f, 8f, 5.9f, 0.5f, INK, light, overlay)
        line(matrices, providers, 8f, 8f, 9.7f, 8.8f, 0.5f, INK, light, overlay)
    }

    private fun drawCloud(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.2f, 7.2f, 11.8f, 10f, GRAY, light, overlay)
        rect(matrices, providers, 5.2f, 5.8f, 8.2f, 7.7f, GRAY, light, overlay)
        rect(matrices, providers, 8f, 5.1f, 10.6f, 7.7f, GRAY, light, overlay)
    }

    private fun drawRain(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 5.8f, 10.5f, 5.1f, 12.4f, 0.45f, BLUE, light, overlay)
        line(matrices, providers, 8f, 10.5f, 7.3f, 12.4f, 0.45f, BLUE, light, overlay)
        line(matrices, providers, 10.2f, 10.5f, 9.5f, 12.4f, 0.45f, BLUE, light, overlay)
    }

    private fun drawLightning(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 8.8f, 9.8f, 7.6f, 11.2f, 0.7f, GOLD, light, overlay)
        line(matrices, providers, 7.6f, 11.2f, 8.6f, 11.2f, 0.7f, GOLD, light, overlay)
        line(matrices, providers, 8.6f, 11.2f, 7.2f, 13.1f, 0.7f, GOLD, light, overlay)
    }

    private fun drawPotion(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6.9f, 4f, 9.1f, 5.3f, INK, light, overlay)
        rect(matrices, providers, 5.2f, 5.2f, 10.8f, 11.7f, PURPLE, light, overlay)
        rect(matrices, providers, 6.1f, 6.2f, 9.9f, 10.7f, PAPER, light, overlay)
        rect(matrices, providers, 6.5f, 8.4f, 9.5f, 10.7f, TEAL, light, overlay)
    }

    private fun drawAgeEffect(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        drawPotion(matrices, providers, light, overlay)
        star(matrices, providers, 11.1f, 5.2f, 0.7f, GOLD, light, overlay)
        line(matrices, providers, 10.2f, 7.3f, 11.8f, 6.1f, 0.45f, GOLD, light, overlay)
        line(matrices, providers, 10.1f, 9.6f, 11.8f, 10.8f, 0.45f, GOLD, light, overlay)
    }

    private fun drawOre(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        diamond(matrices, providers, 5.6f, 8.8f, 1.2f, GOLD, light, overlay)
        diamond(matrices, providers, 8f, 6.8f, 1.35f, SILVER, light, overlay)
        diamond(matrices, providers, 10.5f, 9.2f, 1.1f, RED, light, overlay)
    }

    private fun drawBones(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 4.4f, 10.9f, 11.7f, 5.1f, 0.9f, SILVER, light, overlay)
        line(matrices, providers, 5.2f, 11.8f, 12.5f, 6f, 0.5f, PAPER, light, overlay)
        line(matrices, providers, 6f, 10.2f, 6f, 5.6f, 0.55f, SILVER, light, overlay)
        line(matrices, providers, 8f, 9.1f, 8f, 4.8f, 0.55f, SILVER, light, overlay)
        line(matrices, providers, 10f, 7.7f, 10f, 4.5f, 0.55f, SILVER, light, overlay)
        sunCore(matrices, providers, 4.6f, 11.1f, 0.8f, PAPER, light, overlay)
        sunCore(matrices, providers, 11.8f, 5.2f, 0.8f, PAPER, light, overlay)
    }

    private fun drawRuins(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.3f, 10.9f, 11.7f, 11.7f, GRAY, light, overlay)
        rect(matrices, providers, 4.8f, 6.2f, 5.8f, 10.9f, SILVER, light, overlay)
        rect(matrices, providers, 7.5f, 5.1f, 8.5f, 10.9f, SILVER, light, overlay)
        rect(matrices, providers, 10.2f, 7.1f, 11.2f, 10.9f, SILVER, light, overlay)
        rect(matrices, providers, 4.8f, 5.4f, 8.5f, 6.4f, SILVER, light, overlay)
        line(matrices, providers, 8.5f, 6.4f, 10.5f, 4.4f, 0.55f, GOLD, light, overlay)
        rect(matrices, providers, 6.1f, 8.1f, 6.9f, 8.9f, GREEN, light, overlay)
    }

    private fun drawObservatory(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.4f, 10.8f, 11.6f, 11.8f, GRAY, light, overlay)
        line(matrices, providers, 4.8f, 10.8f, 8f, 5.1f, 0.5f, SILVER, light, overlay)
        line(matrices, providers, 8f, 5.1f, 11.2f, 10.8f, 0.5f, SILVER, light, overlay)
        line(matrices, providers, 5.7f, 7.7f, 10.8f, 4.8f, 0.8f, BLUE, light, overlay)
        star(matrices, providers, 11.2f, 4.3f, 0.55f, GOLD, light, overlay)
    }

    private fun drawAqueduct(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.2f, 5.2f, 11.8f, 6.2f, SILVER, light, overlay)
        rect(matrices, providers, 5f, 6.2f, 5.9f, 11.8f, GRAY, light, overlay)
        rect(matrices, providers, 7.6f, 6.2f, 8.4f, 10.8f, GRAY, light, overlay)
        rect(matrices, providers, 10f, 6.2f, 10.9f, 11.8f, GRAY, light, overlay)
        line(matrices, providers, 5.1f, 11.4f, 8f, 8.5f, 0.45f, GOLD, light, overlay)
        line(matrices, providers, 8f, 8.5f, 10.7f, 11.4f, 0.45f, GOLD, light, overlay)
    }

    private fun drawGateway(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 5f, 4.8f, 6.2f, 11.8f, INK, light, overlay)
        rect(matrices, providers, 9.8f, 4.8f, 11f, 11.8f, INK, light, overlay)
        rect(matrices, providers, 5f, 4.2f, 11f, 5.4f, PURPLE, light, overlay)
        rect(matrices, providers, 6.5f, 6.1f, 9.5f, 10.6f, PAPER, light, overlay)
        line(matrices, providers, 6.7f, 10.3f, 9.2f, 6.6f, 0.45f, BLUE, light, overlay)
    }

    private fun drawPageStorm(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 8f, 3.4f, 8f, 12.3f, 0.45f, GRAY, light, overlay)
        line(matrices, providers, 4.8f, 10.8f, 7.1f, 8.4f, 0.55f, PAPER, light, overlay)
        line(matrices, providers, 7.1f, 8.4f, 10.6f, 6.2f, 0.55f, PAPER, light, overlay)
        line(matrices, providers, 10.6f, 6.2f, 8.9f, 4.2f, 0.55f, PAPER, light, overlay)
        line(matrices, providers, 5.4f, 6.1f, 7.6f, 5.1f, 0.55f, SILVER, light, overlay)
        star(matrices, providers, 8.1f, 12.5f, 0.6f, PURPLE, light, overlay)
    }

    private fun drawMemoryBloom(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6.9f, 7.1f, 9.1f, 9.4f, TEAL, light, overlay)
        line(matrices, providers, 8f, 8.3f, 5.4f, 5.4f, 0.55f, PURPLE, light, overlay)
        line(matrices, providers, 8f, 8.3f, 10.6f, 5.4f, 0.55f, PURPLE, light, overlay)
        line(matrices, providers, 8f, 8.3f, 5.3f, 11.2f, 0.55f, GREEN, light, overlay)
        line(matrices, providers, 8f, 8.3f, 10.7f, 11.2f, 0.55f, GREEN, light, overlay)
        star(matrices, providers, 8f, 4.4f, 0.5f, GOLD, light, overlay)
    }

    private fun drawSanctuary(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.6f, 11.2f, 11.4f, 12f, SILVER, light, overlay)
        rect(matrices, providers, 5.1f, 7.5f, 6.1f, 11.2f, SILVER, light, overlay)
        rect(matrices, providers, 9.9f, 7.5f, 10.9f, 11.2f, SILVER, light, overlay)
        rect(matrices, providers, 5.1f, 6.8f, 10.9f, 7.7f, SILVER, light, overlay)
        rect(matrices, providers, 7.2f, 8.8f, 8.8f, 10.5f, BLUE, light, overlay)
        star(matrices, providers, 5.4f, 6.5f, 0.45f, GOLD, light, overlay)
        star(matrices, providers, 10.6f, 6.5f, 0.45f, GOLD, light, overlay)
    }

    private fun drawChaosSkySymbol(clean: String, matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        when (clean) {
            "bright_sky" -> {
                rect(matrices, providers, 3.8f, 3.8f, 12.2f, 12.2f, 0xAAFFF4A8.toInt(), light, overlay)
                sunCore(matrices, providers, 8f, 8f, 2.2f, GOLD, light, overlay)
                star(matrices, providers, 4.7f, 4.9f, 0.55f, PAPER, light, overlay)
                star(matrices, providers, 11.3f, 10.8f, 0.55f, PAPER, light, overlay)
            }
            "dark_sky" -> {
                rect(matrices, providers, 3.8f, 3.8f, 12.2f, 12.2f, 0xDD16182A.toInt(), light, overlay)
                star(matrices, providers, 5.5f, 5.4f, 0.65f, SILVER, light, overlay)
                star(matrices, providers, 10.6f, 6.7f, 0.45f, GOLD, light, overlay)
                star(matrices, providers, 8.0f, 10.8f, 0.55f, SILVER, light, overlay)
            }
            "sky_rainbows" -> {
                line(matrices, providers, 3.8f, 10.8f, 7.8f, 4.0f, 0.65f, RED, light, overlay)
                line(matrices, providers, 5.0f, 11.0f, 8.0f, 5.0f, 0.65f, GOLD, light, overlay)
                line(matrices, providers, 6.2f, 11.0f, 8.2f, 6.0f, 0.65f, GREEN, light, overlay)
                line(matrices, providers, 7.4f, 11.0f, 8.6f, 7.0f, 0.65f, BLUE, light, overlay)
                drawCloud(matrices, providers, light, overlay)
            }
            "sky_auroras" -> {
                line(matrices, providers, 4.2f, 5.0f, 6.4f, 10.8f, 0.9f, 0x9968FFD0.toInt(), light, overlay)
                line(matrices, providers, 7.2f, 4.3f, 8.1f, 11.4f, 0.9f, 0x998DC7FF.toInt(), light, overlay)
                line(matrices, providers, 10.4f, 5.2f, 9.2f, 11.2f, 0.9f, 0x99B58DFF.toInt(), light, overlay)
                star(matrices, providers, 5.1f, 4.1f, 0.45f, SILVER, light, overlay)
            }
            "shooting_stars" -> {
                line(matrices, providers, 4.2f, 5.0f, 11.8f, 8.4f, 0.55f, SILVER, light, overlay)
                line(matrices, providers, 5.7f, 9.2f, 11.0f, 11.6f, 0.45f, GOLD, light, overlay)
                star(matrices, providers, 11.9f, 8.5f, 0.65f, PAPER, light, overlay)
                star(matrices, providers, 11.1f, 11.6f, 0.45f, PAPER, light, overlay)
            }
            "comets" -> {
                line(matrices, providers, 4.0f, 11.6f, 10.8f, 5.2f, 0.85f, 0xFFBDEBFF.toInt(), light, overlay)
                sunCore(matrices, providers, 11.3f, 4.8f, 1.1f, PAPER, light, overlay)
            }
            else -> {
                line(matrices, providers, 4.4f, 11.5f, 11.6f, 4.3f, 1.0f, PURPLE, light, overlay)
                line(matrices, providers, 5.2f, 11.8f, 12.4f, 4.6f, 0.45f, BLUE, light, overlay)
                star(matrices, providers, 10.5f, 5.2f, 0.55f, GOLD, light, overlay)
            }
        }
    }

    private fun drawMeteor(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 4.2f, 4.3f, 9.6f, 9.6f, 1.0f, RED, light, overlay)
        line(matrices, providers, 5.4f, 4.0f, 10.2f, 8.8f, 0.55f, GOLD, light, overlay)
        sunCore(matrices, providers, 10.3f, 10.2f, 1.4f, 0xFF222222.toInt(), light, overlay)
        rect(matrices, providers, 4.4f, 12.0f, 11.6f, 12.7f, GRAY, light, overlay)
    }

    private fun drawSkySphere(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        sunCore(matrices, providers, 8f, 7.4f, 3.1f, 0xFF7FCB7A.toInt(), light, overlay)
        rect(matrices, providers, 5.2f, 7.6f, 10.8f, 10.1f, 0xFF805F3A.toInt(), light, overlay)
        line(matrices, providers, 8f, 10.5f, 8f, 12.7f, 0.45f, SILVER, light, overlay)
        star(matrices, providers, 10.8f, 4.7f, 0.45f, GOLD, light, overlay)
    }

    private fun drawParticleSymbol(clean: String, matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        val color = when (clean) {
            "particle_ash" -> GRAY
            "particle_spores" -> GREEN
            "particle_void" -> PURPLE
            else -> GOLD
        }
        star(matrices, providers, 5.2f, 5.5f, 0.55f, color, light, overlay)
        star(matrices, providers, 8.8f, 4.4f, 0.45f, color, light, overlay)
        star(matrices, providers, 10.8f, 7.2f, 0.65f, color, light, overlay)
        star(matrices, providers, 6.9f, 10.3f, 0.5f, color, light, overlay)
        line(matrices, providers, 4.6f, 11.8f, 11.3f, 3.8f, 0.28f, 0x66FFFFFF, light, overlay)
    }

    private fun drawCrystals(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        crystal(matrices, providers, 6f, 12f, 2f, 6f, 0xFF8FD7FF.toInt(), light, overlay)
        crystal(matrices, providers, 8f, 12f, 2.4f, 8f, 0xFF70B5FF.toInt(), light, overlay)
        crystal(matrices, providers, 10.2f, 12f, 1.8f, 5f, 0xFFB8E6FF.toInt(), light, overlay)
    }

    private fun drawTendrils(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 5f, 11.8f, 6.4f, 8.2f, 0.75f, PURPLE, light, overlay)
        line(matrices, providers, 6.4f, 8.2f, 5.6f, 5.2f, 0.75f, PURPLE, light, overlay)
        line(matrices, providers, 8f, 12f, 8.4f, 7.5f, 0.75f, GREEN, light, overlay)
        line(matrices, providers, 8.4f, 7.5f, 10.4f, 4.7f, 0.75f, GREEN, light, overlay)
        line(matrices, providers, 10.8f, 11.6f, 9.8f, 8.2f, 0.75f, PURPLE, light, overlay)
    }

    private fun drawObelisk(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6.5f, 4f, 9.5f, 12f, INK, light, overlay)
        line(matrices, providers, 6.5f, 4f, 8f, 2.8f, 0.7f, SILVER, light, overlay)
        line(matrices, providers, 9.5f, 4f, 8f, 2.8f, 0.7f, SILVER, light, overlay)
    }

    private fun drawHex(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 5.2f, 5.5f, 7f, 4.1f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 7f, 4.1f, 9f, 4.1f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 9f, 4.1f, 10.8f, 5.5f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 10.8f, 5.5f, 10.8f, 8.1f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 10.8f, 8.1f, 9f, 9.5f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 9f, 9.5f, 7f, 9.5f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 7f, 9.5f, 5.2f, 8.1f, 0.6f, GOLD, light, overlay)
        line(matrices, providers, 5.2f, 8.1f, 5.2f, 5.5f, 0.6f, GOLD, light, overlay)
    }

    private fun drawWire(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6f, 6f, 10f, 10f, 0xFF70E0FF.toInt(), light, overlay)
        rect(matrices, providers, 7f, 7f, 9f, 9f, PAPER, light, overlay)
        line(matrices, providers, 4.3f, 4.5f, 6f, 6f, 0.45f, PURPLE, light, overlay)
        line(matrices, providers, 11.7f, 4.5f, 10f, 6f, 0.45f, PURPLE, light, overlay)
        line(matrices, providers, 4.3f, 11.5f, 6f, 10f, 0.45f, PURPLE, light, overlay)
        line(matrices, providers, 11.7f, 11.5f, 10f, 10f, 0.45f, PURPLE, light, overlay)
    }

    private fun drawSeparator(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6.2f, 3.4f, 9.8f, 12.4f, INK, light, overlay)
        rect(matrices, providers, 7.7f, 4.2f, 8.3f, 11.6f, GOLD, light, overlay)
    }

    private fun drawCable(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 4.5f, 11.2f, 6.5f, 5.2f, 0.95f, GRAY, light, overlay)
        line(matrices, providers, 6.5f, 5.2f, 9.5f, 10.8f, 0.95f, GRAY, light, overlay)
        line(matrices, providers, 9.5f, 10.8f, 11.5f, 4.8f, 0.95f, GRAY, light, overlay)
        rect(matrices, providers, 8.8f, 7.8f, 10.4f, 9.4f, TEAL, light, overlay)
    }

    private fun drawFractal(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.2f, 4.2f, 11.8f, 11.8f, SILVER, light, overlay)
        rect(matrices, providers, 5.4f, 5.4f, 10.6f, 10.6f, PAPER, light, overlay)
        rect(matrices, providers, 6f, 6f, 7.6f, 7.6f, SILVER, light, overlay)
        rect(matrices, providers, 8.4f, 6f, 10f, 7.6f, SILVER, light, overlay)
        rect(matrices, providers, 6f, 8.4f, 7.6f, 10f, SILVER, light, overlay)
        rect(matrices, providers, 8.4f, 8.4f, 10f, 10f, SILVER, light, overlay)
    }

    private fun drawLightFissure(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6.8f, 4f, 9.2f, 12f, GRAY, light, overlay)
        rect(matrices, providers, 7.6f, 3f, 8.4f, 13f, GOLD, light, overlay)
    }

    private fun drawVirus(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6.6f, 3.8f, 9.4f, 7.6f, PURPLE, light, overlay)
        line(matrices, providers, 6.6f, 3.8f, 8f, 2.4f, 0.45f, BLUE, light, overlay)
        line(matrices, providers, 9.4f, 3.8f, 8f, 2.4f, 0.45f, BLUE, light, overlay)
        line(matrices, providers, 6.6f, 7.6f, 8f, 9.1f, 0.45f, BLUE, light, overlay)
        line(matrices, providers, 9.4f, 7.6f, 8f, 9.1f, 0.45f, BLUE, light, overlay)
        rect(matrices, providers, 7.45f, 7.9f, 8.55f, 11.8f, SILVER, light, overlay)
        line(matrices, providers, 4.3f, 11.2f, 7.1f, 9.3f, 0.45f, PURPLE, light, overlay)
        line(matrices, providers, 11.7f, 11.2f, 8.9f, 9.3f, 0.45f, PURPLE, light, overlay)
    }

    private fun drawGrid(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 4.3f, 4.3f, 7.5f, 7.5f, GREEN, light, overlay)
        rect(matrices, providers, 8.5f, 4.3f, 11.7f, 7.5f, BLUE, light, overlay)
        rect(matrices, providers, 4.3f, 8.5f, 7.5f, 11.7f, GOLD, light, overlay)
        rect(matrices, providers, 8.5f, 8.5f, 11.7f, 11.7f, PURPLE, light, overlay)
    }

    private fun drawPalette(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        drawSwatch(matrices, providers, 0xFFEA6464.toInt(), light, overlay, 4.2f, 4.2f, 7.5f, 7.5f)
        drawSwatch(matrices, providers, 0xFF5F89EA.toInt(), light, overlay, 8.5f, 4.2f, 11.8f, 7.5f)
        drawSwatch(matrices, providers, 0xFF6DC56D.toInt(), light, overlay, 4.2f, 8.5f, 7.5f, 11.8f)
        drawSwatch(matrices, providers, 0xFFDFBF58.toInt(), light, overlay, 8.5f, 8.5f, 11.8f, 11.8f)
    }

    private fun drawPaw(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        rect(matrices, providers, 6.2f, 8.4f, 9.8f, 11.3f, INK, light, overlay)
        rect(matrices, providers, 4.6f, 5.5f, 5.8f, 7.1f, INK, light, overlay)
        rect(matrices, providers, 6.4f, 4.5f, 7.6f, 6.4f, INK, light, overlay)
        rect(matrices, providers, 8.4f, 4.5f, 9.6f, 6.4f, INK, light, overlay)
        rect(matrices, providers, 10.2f, 5.5f, 11.4f, 7.1f, INK, light, overlay)
    }

    private fun drawLowGravity(matrices: MatrixStack, providers: VertexConsumerProvider, light: Int, overlay: Int) {
        line(matrices, providers, 4.8f, 11.2f, 8f, 5.3f, 0.55f, BLUE, light, overlay)
        line(matrices, providers, 8f, 5.3f, 11.2f, 11.2f, 0.55f, BLUE, light, overlay)
        star(matrices, providers, 8f, 4.2f, 0.7f, GOLD, light, overlay)
        line(matrices, providers, 5.5f, 8.8f, 6.5f, 8.1f, 0.4f, SILVER, light, overlay)
        line(matrices, providers, 9.5f, 8.8f, 10.5f, 8.1f, 0.4f, SILVER, light, overlay)
    }

    private fun drawSwatch(matrices: MatrixStack, providers: VertexConsumerProvider, color: Int, light: Int, overlay: Int, x1: Float = 4f, y1: Float = 4f, x2: Float = 12f, y2: Float = 12f) {
        rect(matrices, providers, x1, y1, x2, y2, color, light, overlay)
        rect(matrices, providers, x1 + 0.8f, y1 + 0.8f, x2 - 0.8f, y2 - 0.8f, color and 0xAAFFFFFF.toInt(), light, overlay)
    }

    private fun sunCore(matrices: MatrixStack, providers: VertexConsumerProvider, cx: Float, cy: Float, radius: Float, color: Int, light: Int, overlay: Int) =
        rect(matrices, providers, cx - radius, cy - radius, cx + radius, cy + radius, color, light, overlay)

    private fun star(matrices: MatrixStack, providers: VertexConsumerProvider, cx: Float, cy: Float, size: Float, color: Int, light: Int, overlay: Int) {
        rect(matrices, providers, cx - size * 0.18f, cy - size, cx + size * 0.18f, cy + size, color, light, overlay)
        rect(matrices, providers, cx - size, cy - size * 0.18f, cx + size, cy + size * 0.18f, color, light, overlay)
    }

    private fun diamond(matrices: MatrixStack, providers: VertexConsumerProvider, cx: Float, cy: Float, radius: Float, color: Int, light: Int, overlay: Int) {
        line(matrices, providers, cx, cy - radius, cx + radius, cy, 0.5f, color, light, overlay)
        line(matrices, providers, cx + radius, cy, cx, cy + radius, 0.5f, color, light, overlay)
        line(matrices, providers, cx, cy + radius, cx - radius, cy, 0.5f, color, light, overlay)
        line(matrices, providers, cx - radius, cy, cx, cy - radius, 0.5f, color, light, overlay)
    }

    private fun crystal(matrices: MatrixStack, providers: VertexConsumerProvider, cx: Float, bottom: Float, width: Float, height: Float, color: Int, light: Int, overlay: Int) {
        rect(matrices, providers, cx - width / 2f, bottom - height + 1.3f, cx + width / 2f, bottom, color, light, overlay)
        line(matrices, providers, cx - width / 2f, bottom - height + 1.3f, cx, bottom - height, 0.5f, color, light, overlay)
        line(matrices, providers, cx + width / 2f, bottom - height + 1.3f, cx, bottom - height, 0.5f, color, light, overlay)
    }

    private fun drawQuestion(matrices: MatrixStack, providers: VertexConsumerProvider, cx: Float, cy: Float, scale: Float, color: Int, light: Int, overlay: Int) {
        line(matrices, providers, cx - 1.5f * scale, cy - 2.0f * scale, cx, cy - 3.1f * scale, 0.6f * scale, color, light, overlay)
        line(matrices, providers, cx, cy - 3.1f * scale, cx + 1.5f * scale, cy - 2.0f * scale, 0.6f * scale, color, light, overlay)
        line(matrices, providers, cx + 1.5f * scale, cy - 2.0f * scale, cx, cy - 0.5f * scale, 0.6f * scale, color, light, overlay)
        rect(matrices, providers, cx - 0.3f * scale, cy + 1.2f * scale, cx + 0.3f * scale, cy + 1.8f * scale, color, light, overlay)
    }

    private fun line(matrices: MatrixStack, providers: VertexConsumerProvider, x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int, light: Int, overlay: Int) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(0.001f)
        val nx = -dy / length * thickness / 2f
        val ny = dx / length * thickness / 2f
        quad(matrices, providers, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, color, light, overlay)
    }

    private fun rect(matrices: MatrixStack, providers: VertexConsumerProvider, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, light: Int, overlay: Int) =
        quad(matrices, providers, x1, y1, x2, y1, x2, y2, x1, y2, color, light, overlay)

    private fun quad(matrices: MatrixStack, providers: VertexConsumerProvider, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, color: Int, light: Int, overlay: Int) {
        val entry = matrices.peek()
        val consumer = providers.getBuffer(RenderLayer.getEntityTranslucent(WHITE_TEXTURE))
        val r = color shr 16 and 255
        val g = color shr 8 and 255
        val b = color and 255
        val a = color ushr 24 and 255
        val z = 0.0025f

        vertex(consumer, entry.positionMatrix, entry.normalMatrix, x1, y1, z, r, g, b, a, 0f, 0f, light, overlay)
        vertex(consumer, entry.positionMatrix, entry.normalMatrix, x2, y2, z, r, g, b, a, 1f, 0f, light, overlay)
        vertex(consumer, entry.positionMatrix, entry.normalMatrix, x3, y3, z, r, g, b, a, 1f, 1f, light, overlay)
        vertex(consumer, entry.positionMatrix, entry.normalMatrix, x4, y4, z, r, g, b, a, 0f, 1f, light, overlay)
    }

    private fun vertex(consumer: VertexConsumer, matrix: Matrix4f, normalMatrix: Matrix3f, px: Float, py: Float, pz: Float, r: Int, g: Int, b: Int, a: Int, u: Float, v: Float, light: Int, overlay: Int) {
        consumer.vertex(matrix, mapX(px), mapY(py), pz)
            .color(r, g, b, a)
            .texture(u, v)
            .overlay(overlay)
            .light(light)
            .normal(normalMatrix, 0f, 0f, 1f)
            .next()
    }

    private fun isStatusEffect(symbolId: String?): Boolean {
        val id = symbolId?.let(Identifier::tryParse) ?: return false
        return Registries.STATUS_EFFECT.ids.any { it == id }
    }

    private fun mapX(px: Float): Float = px / 16f - 0.5f
    private fun mapY(py: Float): Float = 0.5f - py / 16f
}
