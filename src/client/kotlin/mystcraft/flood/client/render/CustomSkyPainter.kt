package mystcraft.flood.client.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.generation.ChaosAgeThemes
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.VertexBuffer
import net.minecraft.client.render.*
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Util
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.random.Random as MCRandom
import org.joml.Matrix4f
import kotlin.random.Random

object CustomSkyPainter {
    private val SUN_TEXTURE = Identifier("textures/environment/sun.png")
    private val MOON_PHASES = Identifier("textures/environment/moon_phases.png")
    private val AURORA_TEXTURE = Identifier("mystcraft-reforged", "textures/environment/aurora.png")
    private val RIFT_TEXTURE = Identifier("mystcraft-reforged", "textures/environment/rift.png")
    private val STREAK_TEXTURE = Identifier("mystcraft-reforged", "textures/environment/streak.png")
    private var starBuffer: VertexBuffer? = null

    private data class SkyBasis(
        val centerX: Float,
        val centerY: Float,
        val centerZ: Float,
        val rightX: Float,
        val rightY: Float,
        val rightZ: Float,
        val upX: Float,
        val upY: Float,
        val upZ: Float
    )

    private fun initStars() {
        if (starBuffer != null) return
        starBuffer = VertexBuffer(VertexBuffer.Usage.STATIC)
        val tessellator = Tessellator.getInstance()
        val bufferBuilder = tessellator.buffer
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION)
        val random = MCRandom.create(10842L)
        for (i in 0 until 1500) {
            var d = random.nextFloat() * 2.0f - 1.0f
            var e = random.nextFloat() * 2.0f - 1.0f
            var f = random.nextFloat() * 2.0f - 1.0f
            val g = 0.15f + random.nextFloat() * 0.1f
            val h = d * d + e * e + f * f
            if (h >= 1.0f || h <= 0.01f) continue
            val j = 1.0f / kotlin.math.sqrt(h.toDouble()).toFloat()
            d *= j; e *= j; f *= j
            val k = d * 100.0f; val l = e * 100.0f; val m = f * 100.0f
            val n = kotlin.math.atan2(d.toDouble(), f.toDouble()).toFloat()
            val o = kotlin.math.asin(e.toDouble()).toFloat()
            val p = kotlin.math.cos(n.toDouble()).toFloat()
            val q = kotlin.math.sin(n.toDouble()).toFloat()
            val r = kotlin.math.cos(o.toDouble()).toFloat()
            val s = kotlin.math.sin(o.toDouble()).toFloat()
            val t = random.nextFloat() * kotlin.math.PI.toFloat() * 2.0f
            val u = kotlin.math.cos(t.toDouble()).toFloat()
            val v = kotlin.math.sin(t.toDouble()).toFloat()
            for (w in 0..3) {
                val x = ((w and 2) - 1).toFloat() * g
                val y = (((w + 1) and 2) - 1).toFloat() * g
                val z = x * u - y * v
                val aa = y * u + x * v
                val ab = z * r + 0.0f * s
                val ac = 0.0f * r - z * s
                val ad = ac * q - aa * p
                val ae = aa * q + ac * p
                bufferBuilder.vertex((k + ad).toDouble(), (l + ab).toDouble(), (m + ae).toDouble()).next()
            }
        }
        starBuffer!!.bind()
        starBuffer!!.upload(bufferBuilder.end())
        VertexBuffer.unbind()
    }

    fun paintSkyTint(matrices: MatrixStack, projectionMatrix: Matrix4f) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val ageId = world.registryKey.value
        if (ageId.namespace != "mystcraft-reforged") return

        val profile = ClientAgeCache.getProperties(ageId) ?: return
        val sky = profile.colors.sky
        val red = ((sky shr 16) and 0xFF) / 255.0f
        val green = ((sky shr 8) and 0xFF) / 255.0f
        val blue = (sky and 0xFF) / 255.0f
        val alpha = 0.72f

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        val matrix = matrices.peek().positionMatrix
        val size = 100.0f
        val bottom = -100.0f
        val top = 100.0f

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
        tintQuad(buffer, matrix, -size, top, -size, size, top, -size, size, top, size, -size, top, size, red, green, blue, alpha)
        tintQuad(buffer, matrix, -size, bottom, -size, -size, top, -size, -size, top, size, -size, bottom, size, red, green, blue, alpha)
        tintQuad(buffer, matrix, size, bottom, size, size, top, size, size, top, -size, size, bottom, -size, red, green, blue, alpha)
        tintQuad(buffer, matrix, -size, bottom, size, -size, top, size, size, top, size, size, bottom, size, red, green, blue, alpha)
        tintQuad(buffer, matrix, size, bottom, -size, size, top, -size, -size, top, -size, -size, bottom, -size, red, green, blue, alpha)
        tessellator.draw()

        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    fun paintExtraSky(matrices: MatrixStack, projectionMatrix: Matrix4f, tickDelta: Float) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val ageId = world.registryKey.value
        if (ageId.namespace != "mystcraft-reforged") return
        val profile = ClientAgeCache.getProperties(ageId) ?: return
        val rand = Random(profile.seed)

        RenderSystem.enableBlend()
        RenderSystem.depthMask(false)
        RenderSystem.disableDepthTest()
        try {
            // KILL THE BOXES: Use Additive Blending for everything!
            // This makes black pixels in sun/moon textures transparent.
            RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE, 
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO
            )

            // 1. STARS
            val skyAngle = world.getSkyAngle(tickDelta)
            var brightness = 1.0f - (kotlin.math.cos(skyAngle * (kotlin.math.PI.toFloat() * 2.0f)) * 2.0f + 0.25f)
            brightness = brightness.coerceIn(0.0f, 1.0f)
            val starAlpha = brightness * brightness * 0.5f

            if (starAlpha > 0.0f && profile.time.starDensity > 1) {
                initStars()
                RenderSystem.setShader(GameRenderer::getPositionProgram)
                RenderSystem.setShaderColor(starAlpha, starAlpha, starAlpha, starAlpha)
                for (i in 1 until profile.time.starDensity) {
                    matrices.push()
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rand.nextFloat() * 360f))
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rand.nextFloat() * 360f))
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rand.nextFloat() * 360f))
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(world.getSkyAngle(tickDelta) * 360.0f))
                    starBuffer?.bind()
                    starBuffer?.draw(matrices.peek().positionMatrix, projectionMatrix, GameRenderer.getPositionProgram())
                    VertexBuffer.unbind()
                    matrices.pop()
                }
            }

            // 2. CELESTIAL BODIES (Suns then Moons)
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram)
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)

            val tessellator = Tessellator.getInstance()
            val buffer = tessellator.buffer

            matrices.push()
            try {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f))

                // Draw Red Suns
                for (i in 0 until profile.time.sunRedCount) {
                    drawCelestialBody(matrices, buffer, tessellator, SUN_TEXTURE,
                        profile.time.sunSize * (rand.nextFloat() * 1.5f + 0.8f), world.getSkyAngle(tickDelta),
                        rand.nextFloat() * 360f, rand.nextFloat() * 360f, 1.0f, 0.2f, 0.2f, 0.9f)
                }
                // Draw Blue Suns
                for (i in 0 until profile.time.sunBlueCount) {
                    drawCelestialBody(matrices, buffer, tessellator, SUN_TEXTURE,
                        profile.time.sunSize * (rand.nextFloat() * 0.8f + 0.4f), world.getSkyAngle(tickDelta),
                        rand.nextFloat() * 360f, rand.nextFloat() * 360f, 0.2f, 0.5f, 1.0f, 0.9f)
                }
                // Draw Extra Moons (Drawn last = appears on top!)
                for (i in 1 until profile.time.moonCount) {
                    drawCelestialBody(matrices, buffer, tessellator, MOON_PHASES,
                        profile.time.moonSize * (rand.nextFloat() * 1.5f + 0.5f), world.getSkyAngle(tickDelta) + 0.5f,
                        rand.nextFloat() * 360f, rand.nextFloat() * 360f, 1.0f, 1.0f, 1.0f, 0.8f, true)
                }
            } finally {
                matrices.pop()
            }

            RenderSystem.defaultBlendFunc()
            RenderSystem.disableCull()
            try {
                val effectTicks = (Util.getMeasuringTimeMs().toDouble() / 50.0).toFloat()
                drawChaosSky(matrices, buffer, tessellator, profile.modifiers, profile.seed, effectTicks)
            } finally {
                RenderSystem.enableCull()
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        }
    }

    private fun drawChaosSky(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        modifiers: List<String>,
        seed: Long,
        effectTicks: Float
    ) {
        if (modifiers.contains(ChaosAgeThemes.BRIGHT_SKY)) {
            drawSkyBand(matrices, buffer, tessellator, 0f, 95f, 72f, 0xFFF9B8, 0.15f)
        }
        if (modifiers.contains(ChaosAgeThemes.DARK_SKY)) {
            drawSkyBand(matrices, buffer, tessellator, 0f, 96f, 88f, 0x050713, 0.36f)
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_RAINBOWS)) {
            val offset = (seed and 255L).toFloat()
            drawRainbowArc(matrices, buffer, tessellator, offset)
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_AURORAS)) {
            repeat(6) { index ->
                val hue = ((seed shr (index * 10)).toInt() and 255) / 255.0f
                val color = java.awt.Color.HSBtoRGB(hue, 0.72f, 1.0f) and 0xFFFFFF
                drawAurora(matrices, buffer, tessellator, index * 60f + hue * 28f, color, 0.78f, index)
            }
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_RIFTS)) {
            repeat(3) { index ->
                val yaw = ((seed ushr ((index * 11) % 48)).toInt() and 359).toFloat() + index * 83f
                val altitude = 32f + (((seed ushr ((index * 7 + 5) % 48)).toInt() and 31).toFloat())
                val roll = -34f + (((seed ushr ((index * 5 + 9) % 48)).toInt() and 68).toFloat())
                drawFallenSkyRift(
                    matrices,
                    buffer,
                    tessellator,
                    yaw,
                    altitude,
                    roll,
                    34f + index * 8f,
                    17f + index * 4f,
                    seed + index * 7919L,
                    effectTicks
                )
            }
            repeat(2) { index ->
                drawTexturedSkySprite(
                    matrices,
                    buffer,
                    tessellator,
                    RIFT_TEXTURE,
                    45f + index * 130f + (seed % 37).toFloat(),
                    48f,
                    -18f + index * 8f,
                    24f,
                    11f,
                    0xB287FF,
                    0.48f
                )
            }
        }
        if (modifiers.contains(ChaosAgeThemes.SHOOTING_STARS)) {
            repeat(9) { index ->
                val offset = ((seed ushr ((index * 7) % 48)).toInt() and 2047).toFloat()
                val cycle = 2100f
                val window = 0.052f
                val phase = ((effectTicks + offset + index * 173f) % cycle) / cycle
                if (phase < window) {
                    val progress = phase / window
                    val alpha = kotlin.math.sin(progress * kotlin.math.PI).toFloat() * 0.82f
                    drawMovingSkyStreak(
                        matrices,
                        buffer,
                        tessellator,
                        ((seed ushr ((index * 9) % 52)).toInt() and 359).toFloat() + index * 37f,
                        28f + (((seed ushr ((index * 5) % 44)).toInt() and 31).toFloat()),
                        -28f + index * 13f,
                        progress,
                        74f,
                        18f + index % 4,
                        2.5f,
                        0xF9FFFF,
                        alpha
                    )
                }
            }
        }
        if (modifiers.contains(ChaosAgeThemes.COMETS)) {
            repeat(2) { index ->
                val cycle = 9600f
                val phase = ((effectTicks + index * 3300f + (seed and 1023L)) % cycle) / cycle
                if (phase < 0.36f) {
                    val progress = phase / 0.36f
                    drawMovingSkyStreak(
                        matrices,
                        buffer,
                        tessellator,
                        25f + index * 160f + progress * 90f,
                        52f - progress * 18f,
                        -18f,
                        0.45f,
                        32f,
                        38f,
                        5.4f,
                        0xBDEBFF,
                        kotlin.math.sin(progress * kotlin.math.PI).toFloat() * 0.88f
                    )
                }
            }
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_NEBULAE)) {
            drawSkyNebulae(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.ECLIPSE_HALOS)) {
            drawEclipseHalos(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.STAR_GLYPHS)) {
            drawStarGlyphs(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.HORIZON_MIRAGES)) {
            drawHorizonMirages(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.CRYSTAL_HALOS)) {
            drawCrystalHalos(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.VOID_FLECKS)) {
            drawVoidFlecks(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.SPIRAL_GALAXIES)) {
            drawSpiralGalaxies(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.FALLING_SKY_SHARDS)) {
            drawFallingSkyShards(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.LIGHTNING_VEINS)) {
            drawLightningVeins(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.LUMINOUS_COLUMNS)) {
            drawLuminousColumns(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_MONOLITHS)) {
            drawSkyMonoliths(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.PRISM_RINGS)) {
            drawPrismRings(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.CHROMA_WAVES)) {
            drawChromaWaves(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.ORBITAL_GRID)) {
            drawOrbitalGrid(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_LANTERNS)) {
            drawSkyLanterns(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.FRACTURE_WEB)) {
            drawFractureWeb(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.DREAM_VEILS)) {
            drawDreamVeils(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_BUBBLES)) {
            drawSkyBubbles(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.STARFALL_BLOOMS)) {
            drawStarfallBlooms(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.HORIZON_CROWNS)) {
            drawHorizonCrowns(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.CELESTIAL_SCRIPT)) {
            drawCelestialScript(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.GLASS_CONSTELLATIONS)) {
            drawGlassConstellations(matrices, buffer, tessellator, seed, effectTicks)
        }
        if (modifiers.contains(ChaosAgeThemes.RADIANT_WHIRLPOOLS)) {
            drawRadiantWhirlpools(matrices, buffer, tessellator, seed, effectTicks)
        }
    }

    private fun drawSkyBand(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        yaw: Float,
        height: Float,
        width: Float,
        color: Int,
        alpha: Float
    ) {
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((height - 82f) * 0.45f))
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        val matrix = matrices.peek().positionMatrix
        val red = ((color shr 16) and 0xFF) / 255.0f
        val green = ((color shr 8) and 0xFF) / 255.0f
        val blue = (color and 0xFF) / 255.0f
        val depth = when {
            width <= 8f -> width.coerceAtLeast(2.5f)
            width > 70f -> 22f
            else -> 3.8f
        }
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
        buffer.vertex(matrix, -width, 100f, -depth).color(red, green, blue, alpha * 0.12f).next()
        buffer.vertex(matrix, width, 100f, -depth).color(red, green, blue, alpha * 0.12f).next()
        buffer.vertex(matrix, width, 100f, depth).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, -width, 100f, depth).color(red, green, blue, alpha).next()
        tessellator.draw()
        matrices.pop()
    }

    private fun drawRainbowArc(matrices: MatrixStack, buffer: BufferBuilder, tessellator: Tessellator, yaw: Float) {
        val colors = intArrayOf(0xFF5E5E, 0xFFB84A, 0xFFE96A, 0x62D66B, 0x56A8FF, 0x9A78FF)
        colors.forEachIndexed { index, color ->
            drawHorizonRainbowBand(
                matrices,
                buffer,
                tessellator,
                yaw,
                -168f,
                168f,
                -12.0f + index * 1.15f,
                18.0f + index * 0.85f,
                1.65f,
                color,
                0.62f,
                2.5f
            )
        }
    }

    private fun drawSkyNebulae(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x4E4555424C41L)
            repeat(4 + (kotlin.math.abs(seed.toInt()) % 3)) { cluster ->
                val hue = (rand.nextFloat() + cluster * 0.173f) % 1.0f
                val colorA = java.awt.Color.HSBtoRGB(hue, 0.78f, 1.0f) and 0xFFFFFF
                val colorB = java.awt.Color.HSBtoRGB((hue + 0.12f + rand.nextFloat() * 0.24f) % 1.0f, 0.64f, 1.0f) and 0xFFFFFF
                val colorC = java.awt.Color.HSBtoRGB((hue + 0.48f + rand.nextFloat() * 0.16f) % 1.0f, 0.42f, 1.0f) and 0xFFFFFF
                val palette = arrayOf(colorComponents(colorA), colorComponents(colorB), colorComponents(colorC))
                val basis = skyBasis(
                    cluster * (62f + rand.nextFloat() * 32f) + (seed % 47).toFloat(),
                    24f + rand.nextFloat() * 48f,
                    -36f + rand.nextFloat() * 72f,
                    98f
                )
                val pulse = 0.82f + 0.18f * kotlin.math.sin((effectTicks * 0.01f + cluster * 1.7f).toDouble()).toFloat()
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                repeat(14 + rand.nextInt(10)) { puff ->
                    val c = palette[puff % palette.size]
                    val x = (rand.nextFloat() * 2f - 1f) * (24f + rand.nextFloat() * 22f)
                    val y = (rand.nextFloat() * 2f - 1f) * (10f + rand.nextFloat() * 12f)
                    val width = 9f + rand.nextFloat() * 34f
                    val height = 4f + rand.nextFloat() * 16f
                    val alpha = (0.045f + rand.nextFloat() * 0.075f) * pulse
                    drawSkyLocalQuad(buffer, matrix, basis, x, y, width, height, c[0], c[1], c[2], alpha)
                }
                repeat(5) { mote ->
                    val c = palette[(mote + 1) % palette.size]
                    val x = (rand.nextFloat() * 2f - 1f) * 42f
                    val y = (rand.nextFloat() * 2f - 1f) * 18f
                    val size = 0.5f + rand.nextFloat() * 1.4f
                    drawSkyLocalQuad(buffer, matrix, basis, x, y, size, size, c[0], c[1], c[2], 0.28f * pulse)
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawEclipseHalos(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            repeat(2) { index ->
                val basis = skyBasis(
                    55f + index * 142f + (seed % 61).toFloat(),
                    44f + index * 13f,
                    effectTicks * 0.05f + index * 17f,
                    98f
                )
                val pulse = 0.82f + 0.18f * kotlin.math.sin((effectTicks * 0.018f + index).toDouble()).toFloat()
                buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR)
                drawSkyDisc(buffer, matrix, basis, 12.5f + index * 2f, 0.0f, 0.0f, 0.015f, 0.72f)
                tessellator.draw()

                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                drawSkyRing(buffer, matrix, basis, 12.5f + index * 2f, 17.8f + index * 2.4f, 0.84f, 0.72f, 1.0f, 0.24f * pulse)
                drawSkyRing(buffer, matrix, basis, 19.2f + index * 2.6f, 21.8f + index * 3f, 0.28f, 0.95f, 1.0f, 0.14f * pulse)
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawStarGlyphs(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x474C595048L)
            val glyphShapes = arrayOf(
                floatArrayOf(-12f, -5f, -4f, 8f, 8f, 6f, 14f, -3f, 2f, -10f, -10f, -12f),
                floatArrayOf(-10f, 0f, -4f, 9f, 5f, 10f, 12f, 2f, 6f, -8f, -6f, -10f),
                floatArrayOf(-13f, -7f, -1f, 11f, 11f, -7f, 0f, -2f, -8f, 8f, 8f, 8f),
                floatArrayOf(-12f, 8f, -7f, -8f, 0f, 2f, 7f, -8f, 12f, 8f, 0f, -12f),
                floatArrayOf(-14f, 0f, -7f, 8f, 0f, 0f, 7f, 8f, 14f, 0f, 7f, -8f, 0f, 0f, -7f, -8f)
            )
            repeat(5) { glyph ->
                val points = glyphShapes[(glyph + rand.nextInt(glyphShapes.size)) % glyphShapes.size]
                val scale = 0.62f + rand.nextFloat() * 0.96f
                val hue = (rand.nextFloat() + glyph * 0.21f) % 1.0f
                val lineColor = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.48f + rand.nextFloat() * 0.38f, 1.0f) and 0xFFFFFF)
                val accentColor = colorComponents(java.awt.Color.HSBtoRGB((hue + 0.31f) % 1.0f, 0.64f, 1.0f) and 0xFFFFFF)
                val basis = skyBasis(
                    glyph * 71f + (seed % 79).toFloat() + rand.nextFloat() * 32f,
                    26f + rand.nextFloat() * 48f,
                    -34f + rand.nextFloat() * 68f + effectTicks * 0.01f,
                    98f
                )
                val pulse = 0.62f + 0.38f * kotlin.math.sin((effectTicks * 0.026f + glyph * 1.9f).toDouble()).toFloat().coerceAtLeast(0f)
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                val pointCount = points.size / 2
                for (i in 0 until pointCount) {
                    val next = (i + 1) % pointCount
                    val x0 = points[i * 2] * scale
                    val y0 = points[i * 2 + 1] * scale
                    val x1 = points[next * 2] * scale
                    val y1 = points[next * 2 + 1] * scale
                    drawSkyLocalLine(buffer, matrix, basis, x0, y0, x1, y1, 0.22f + scale * 0.13f, lineColor[0], lineColor[1], lineColor[2], 0.42f * pulse)
                    if (i % 2 == glyph % 2) {
                        drawSkyLocalLine(buffer, matrix, basis, 0f, 0f, x0, y0, 0.12f + scale * 0.08f, accentColor[0], accentColor[1], accentColor[2], 0.18f * pulse)
                    }
                }
                for (i in 0 until pointCount) {
                    val size = 0.45f + scale * 0.45f
                    drawSkyLocalQuad(buffer, matrix, basis, points[i * 2] * scale, points[i * 2 + 1] * scale, size, size, 1.0f, 1.0f, 1.0f, 0.48f * pulse)
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawHorizonMirages(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        val yaw = (seed and 255L).toFloat()
        repeat(5) { index ->
            val sway = kotlin.math.sin((effectTicks * 0.009f + index).toDouble()).toFloat() * 3.5f
            val color = when (index % 3) {
                0 -> 0x8DEBFF
                1 -> 0xD7A7FF
                else -> 0xFFF2A0
            }
            drawHorizonRainbowBand(
                matrices,
                buffer,
                tessellator,
                yaw + sway,
                -158f,
                158f,
                -18f + index * 2.0f,
                -6f + index * 2.2f,
                0.75f,
                color,
                0.18f,
                1.0f
            )
        }
    }

    private fun drawCrystalHalos(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            repeat(3) { index ->
                val basis = skyBasis(
                    20f + index * 118f + (seed % 53).toFloat(),
                    33f + index * 12f,
                    effectTicks * 0.018f + index * 27f,
                    98f
                )
                val pulse = 0.75f + 0.25f * kotlin.math.sin((effectTicks * 0.02f + index * 2.2f).toDouble()).toFloat()
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                drawSkyRing(buffer, matrix, basis, 10f, 11.3f, 0.62f, 0.92f, 1.0f, 0.25f * pulse, 6)
                drawSkyRing(buffer, matrix, basis, 15.5f, 17.1f, 0.92f, 0.76f, 1.0f, 0.18f * pulse, 6)
                repeat(6) { side ->
                    val a0 = side / 6f * kotlin.math.PI.toFloat() * 2f
                    val a1 = (side + 3) / 6f * kotlin.math.PI.toFloat() * 2f
                    drawSkyLocalLine(
                        buffer,
                        matrix,
                        basis,
                        kotlin.math.cos(a0) * 11.3f,
                        kotlin.math.sin(a0) * 11.3f,
                        kotlin.math.cos(a1) * 17.1f,
                        kotlin.math.sin(a1) * 17.1f,
                        0.18f,
                        0.72f,
                        1.0f,
                        0.94f,
                        0.16f * pulse
                    )
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawVoidFlecks(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x564F49444CL)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            repeat(34) { index ->
                val basis = skyBasis(
                    rand.nextFloat() * 360f,
                    16f + rand.nextFloat() * 66f,
                    rand.nextFloat() * 360f,
                    98f
                )
                val size = 1.1f + rand.nextFloat() * 4.8f
                val pulse = 0.72f + 0.28f * kotlin.math.sin((effectTicks * 0.021f + index).toDouble()).toFloat()
                drawSkyLocalQuad(buffer, matrix, basis, 0f, 0f, size * 1.34f, size * 1.34f, 0.42f, 0.16f, 0.88f, 0.16f * pulse)
                drawSkyLocalQuad(buffer, matrix, basis, 0f, 0f, size, size, 0.0f, 0.0f, 0.018f, 0.68f * pulse)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawSpiralGalaxies(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x47414C415859L)
            repeat(3) { galaxy ->
                val arms = 2 + rand.nextInt(4)
                val segments = 18 + rand.nextInt(12)
                val maxRadius = 14f + rand.nextFloat() * 24f
                val squash = 0.32f + rand.nextFloat() * 0.56f
                val twist = 1.8f + rand.nextFloat() * 2.4f
                val hue = (rand.nextFloat() + galaxy * 0.29f) % 1.0f
                val inner = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.34f, 1.0f) and 0xFFFFFF)
                val outer = colorComponents(java.awt.Color.HSBtoRGB((hue + 0.18f + rand.nextFloat() * 0.18f) % 1.0f, 0.72f, 1.0f) and 0xFFFFFF)
                val basis = skyBasis(
                    40f + galaxy * 112f + (seed % 41).toFloat() + rand.nextFloat() * 35f,
                    32f + rand.nextFloat() * 42f,
                    effectTicks * (0.006f + rand.nextFloat() * 0.01f) + galaxy * 38f,
                    98f
                )
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                repeat(arms) { arm ->
                    for (segment in 0 until segments) {
                        val t0 = segment / segments.toFloat()
                        val t1 = (segment + 1) / segments.toFloat()
                        val angle0 = t0 * twist * kotlin.math.PI.toFloat() + arm / arms.toFloat() * kotlin.math.PI.toFloat() * 2f
                        val angle1 = t1 * twist * kotlin.math.PI.toFloat() + arm / arms.toFloat() * kotlin.math.PI.toFloat() * 2f
                        val r0 = 1.8f + t0 * maxRadius
                        val r1 = 1.8f + t1 * maxRadius
                        val x0 = kotlin.math.cos(angle0) * r0
                        val y0 = kotlin.math.sin(angle0) * r0 * squash
                        val x1 = kotlin.math.cos(angle1) * r1
                        val y1 = kotlin.math.sin(angle1) * r1 * squash
                        val fade = (1f - t0) * (0.18f + rand.nextFloat() * 0.12f)
                        val color = if (segment % 2 == 0) inner else outer
                        drawSkyLocalLine(buffer, matrix, basis, x0, y0, x1, y1, 0.38f + (1f - t0) * 0.52f, color[0], color[1], color[2], fade)
                    }
                }
                drawSkyLocalQuad(buffer, matrix, basis, 0f, 0f, 1.8f + maxRadius * 0.04f, 1.8f + maxRadius * 0.04f, 1.0f, 0.95f, 0.82f, 0.52f)
                repeat(12) { star ->
                    val angle = rand.nextFloat() * kotlin.math.PI.toFloat() * 2f
                    val r = 4f + rand.nextFloat() * maxRadius
                    val size = 0.18f + rand.nextFloat() * 0.42f
                    val color = if (star % 3 == 0) outer else inner
                    drawSkyLocalQuad(buffer, matrix, basis, kotlin.math.cos(angle) * r, kotlin.math.sin(angle) * r * squash, size, size, color[0], color[1], color[2], 0.26f)
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawFallingSkyShards(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x534841524453L)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            repeat(11) { index ->
                val basis = skyBasis(
                    rand.nextFloat() * 360f,
                    22f + rand.nextFloat() * 55f,
                    -42f + rand.nextFloat() * 84f + effectTicks * 0.018f,
                    98f
                )
                val drift = kotlin.math.sin((effectTicks * 0.014f + index).toDouble()).toFloat() * 4f
                val width = 2.2f + rand.nextFloat() * 4.4f
                val height = 10f + rand.nextFloat() * 22f
                drawSkyLocalQuad(buffer, matrix, basis, drift, 0f, width * 1.25f, height * 1.08f, 0.42f, 0.80f, 1.0f, 0.14f)
                drawSkyLocalQuad(buffer, matrix, basis, drift, 0f, width, height, 0.72f, 0.96f, 1.0f, 0.23f)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawLightningVeins(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x4C494748544EL)
            repeat(4) { vein ->
                val basis = skyBasis(
                    vein * 86f + (seed % 29).toFloat(),
                    34f + rand.nextFloat() * 36f,
                    -22f + rand.nextFloat() * 44f,
                    98f
                )
                val pulse = 0.45f + 0.55f * kotlin.math.sin((effectTicks * 0.05f + vein * 2.7f).toDouble()).toFloat().coerceAtLeast(0f)
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                var lastX = -28f
                var lastY = (rand.nextFloat() * 2f - 1f) * 7f
                for (step in 1..9) {
                    val nextX = -28f + step * 7f
                    val nextY = lastY + (rand.nextFloat() * 2f - 1f) * 6f
                    drawSkyLocalLine(buffer, matrix, basis, lastX, lastY, nextX, nextY, 0.9f, 0.35f, 0.20f, 0.92f, 0.14f * pulse)
                    drawSkyLocalLine(buffer, matrix, basis, lastX, lastY, nextX, nextY, 0.34f, 0.88f, 0.80f, 1.0f, 0.38f * pulse)
                    lastX = nextX
                    lastY = nextY
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawLuminousColumns(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x434F4C554D4EL)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            repeat(9) { index ->
                val basis = skyBasis(index * 39f + (seed % 31).toFloat(), -4f + rand.nextFloat() * 12f, 0f, 99f)
                val pulse = 0.72f + 0.28f * kotlin.math.sin((effectTicks * 0.012f + index).toDouble()).toFloat()
                val width = 2.8f + rand.nextFloat() * 4.0f
                val height = 28f + rand.nextFloat() * 26f
                drawSkyLocalQuad(buffer, matrix, basis, 0f, height * 0.55f, width * 2.4f, height, 0.62f, 0.92f, 1.0f, 0.055f * pulse)
                drawSkyLocalQuad(buffer, matrix, basis, 0f, height * 0.55f, width, height, 0.92f, 1.0f, 0.86f, 0.12f * pulse)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawSkyMonoliths(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x4D4F4E4FL)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)

            repeat(9) { index ->
                val basis = skyBasis(
                    index * 42f + (seed % 67).toFloat() + rand.nextFloat() * 18f,
                    16f + rand.nextFloat() * 48f,
                    -12f + rand.nextFloat() * 24f,
                    97.5f
                )
                val width = 2.2f + rand.nextFloat() * 3.6f
                val height = 18f + rand.nextFloat() * 34f
                val depth = 3.0f + rand.nextFloat() * 5.8f
                val yOffset = -8f + rand.nextFloat() * 18f
                val pulse = 0.82f + 0.18f * kotlin.math.sin((effectTicks * 0.01f + index * 1.3f).toDouble()).toFloat()
                drawSkyLocalBox(buffer, matrix, basis, 0f, yOffset, width, height, depth, 0.18f, 0.23f, 0.34f, 0.36f)
                drawSkyLocalQuad(buffer, matrix, basis, -width * 0.18f, yOffset + height * 0.16f, width * 0.16f, height * 0.82f, 0.58f, 0.70f, 1.0f, 0.055f * pulse)
            }

            repeat(16) { index ->
                val basis = skyBasis(
                    rand.nextFloat() * 360f,
                    22f + rand.nextFloat() * 58f,
                    rand.nextFloat() * 360f + effectTicks * 0.012f,
                    98.0f
                )
                val size = 1.8f + rand.nextFloat() * 4.6f
                val depth = size * (0.72f + rand.nextFloat() * 0.56f)
                val alpha = 0.18f + rand.nextFloat() * 0.13f
                drawSkyLocalBox(buffer, matrix, basis, 0f, 0f, size, size, depth, 0.68f, 0.79f, 1.0f, alpha)
                drawSkyLocalQuad(buffer, matrix, basis, -size * 0.22f, size * 0.18f, size * 0.34f, size * 0.25f, 0.95f, 1.0f, 1.0f, alpha * 0.62f)
            }

            val lights = arrayOf(
                floatArrayOf(1.0f, 0.18f, 0.28f),
                floatArrayOf(0.24f, 1.0f, 0.46f),
                floatArrayOf(0.36f, 0.62f, 1.0f),
                floatArrayOf(0.72f, 0.42f, 1.0f)
            )
            repeat(12) { index ->
                val basis = skyBasis(
                    index * 31f + rand.nextFloat() * 22f + (seed % 29).toFloat(),
                    18f + rand.nextFloat() * 60f,
                    rand.nextFloat() * 360f,
                    98.5f
                )
                val color = lights[index % lights.size]
                val pulse = 0.52f + 0.48f * kotlin.math.sin((effectTicks * 0.045f + index * 1.9f).toDouble()).toFloat().coerceAtLeast(0f)
                val glow = 2.0f + rand.nextFloat() * 2.6f
                drawSkyLocalQuad(buffer, matrix, basis, 0f, 0f, glow, glow, color[0], color[1], color[2], 0.14f * pulse)
                drawSkyLocalQuad(buffer, matrix, basis, 0f, 0f, glow * 0.26f, glow * 0.26f, 1.0f, 0.96f, 0.90f, 0.62f * pulse)
            }

            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawPrismRings(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x505249534DL)
            repeat(4) { ringSet ->
                val basis = skyBasis(ringSet * 82f + rand.nextFloat() * 34f, 24f + rand.nextFloat() * 50f, effectTicks * 0.018f + ringSet * 19f, 98f)
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                repeat(5) { band ->
                    val color = colorComponents(java.awt.Color.HSBtoRGB((band / 5f + rand.nextFloat() * 0.08f) % 1.0f, 0.58f, 1.0f) and 0xFFFFFF)
                    val radius = 7f + band * 3.0f + rand.nextFloat() * 2f
                    drawSkyRing(buffer, matrix, basis, radius, radius + 1.1f, color[0], color[1], color[2], 0.12f + band * 0.012f, 48)
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawChromaWaves(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        val rand = Random(seed xor 0x4348524F4D41L)
        repeat(9) { index ->
            val hue = (index / 9f + rand.nextFloat() * 0.12f) % 1.0f
            val color = java.awt.Color.HSBtoRGB(hue, 0.68f, 1.0f) and 0xFFFFFF
            val sway = kotlin.math.sin((effectTicks * 0.008f + index * 0.7f).toDouble()).toFloat() * 8f
            drawHorizonRainbowBand(
                matrices,
                buffer,
                tessellator,
                (seed and 255L).toFloat() + sway,
                -170f,
                170f,
                -20f + index * 1.55f,
                4f + index * 2.25f + rand.nextFloat() * 10f,
                0.9f + rand.nextFloat() * 0.6f,
                color,
                0.13f + rand.nextFloat() * 0.08f,
                -2.0f + rand.nextFloat() * 4.0f
            )
        }
    }

    private fun drawOrbitalGrid(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x47524944L)
            repeat(3) { grid ->
                val basis = skyBasis(grid * 112f + (seed % 53).toFloat(), 38f + rand.nextFloat() * 32f, effectTicks * 0.01f + grid * 32f, 98f)
                val hue = (0.48f + rand.nextFloat() * 0.22f) % 1.0f
                val color = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.38f, 1.0f) and 0xFFFFFF)
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                drawSkyRing(buffer, matrix, basis, 13f, 13.5f, color[0], color[1], color[2], 0.18f, 64)
                drawSkyRing(buffer, matrix, basis, 21f, 21.45f, color[0], color[1], color[2], 0.12f, 64)
                for (line in -3..3) {
                    val offset = line * 6f
                    drawSkyLocalLine(buffer, matrix, basis, -24f, offset, 24f, offset, 0.12f, color[0], color[1], color[2], 0.10f)
                    drawSkyLocalLine(buffer, matrix, basis, offset, -16f, offset, 16f, 0.12f, color[0], color[1], color[2], 0.10f)
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawSkyLanterns(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x4C414E5445524EL)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            repeat(22) { index ->
                val basis = skyBasis(rand.nextFloat() * 360f, 8f + rand.nextFloat() * 62f, 45f + rand.nextFloat() * 90f, 98f)
                val hue = (0.08f + rand.nextFloat() * 0.16f + index * 0.037f) % 1.0f
                val color = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.46f, 1.0f) and 0xFFFFFF)
                val float = kotlin.math.sin((effectTicks * 0.014f + index).toDouble()).toFloat() * 2f
                val size = 0.8f + rand.nextFloat() * 2.2f
                drawSkyLocalQuad(buffer, matrix, basis, 0f, float, size * 2.8f, size * 2.8f, color[0], color[1], color[2], 0.08f)
                drawSkyLocalQuad(buffer, matrix, basis, 0f, float, size, size * 1.35f, 1.0f, 0.78f, 0.42f, 0.42f)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawFractureWeb(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x4652414354555245L)
            repeat(4) { web ->
                val basis = skyBasis(web * 87f + rand.nextFloat() * 42f, 28f + rand.nextFloat() * 44f, -30f + rand.nextFloat() * 60f, 98f)
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                var x = -24f + rand.nextFloat() * 12f
                var y = -6f + rand.nextFloat() * 12f
                repeat(9) { step ->
                    val nextX = x + 4f + rand.nextFloat() * 7f
                    val nextY = y + (rand.nextFloat() * 2f - 1f) * 7f
                    val hue = (0.55f + rand.nextFloat() * 0.22f) % 1.0f
                    val c = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.72f, 1.0f) and 0xFFFFFF)
                    val pulse = 0.55f + 0.45f * kotlin.math.sin((effectTicks * 0.025f + step + web).toDouble()).toFloat().coerceAtLeast(0f)
                    drawSkyLocalLine(buffer, matrix, basis, x, y, nextX, nextY, 0.22f, 0.10f, 0.03f, 0.22f, 0.20f * pulse)
                    drawSkyLocalLine(buffer, matrix, basis, x, y, nextX, nextY, 0.10f, c[0], c[1], c[2], 0.36f * pulse)
                    if (step % 2 == 0) {
                        drawSkyLocalLine(buffer, matrix, basis, nextX, nextY, nextX + rand.nextFloat() * 9f, nextY + (rand.nextFloat() * 2f - 1f) * 10f, 0.09f, c[0], c[1], c[2], 0.18f * pulse)
                    }
                    x = nextX
                    y = nextY
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawDreamVeils(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x5645494C53L)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            repeat(12) { veil ->
                val basis = skyBasis(veil * 31f + rand.nextFloat() * 35f, 12f + rand.nextFloat() * 54f, -18f + rand.nextFloat() * 36f, 99f)
                val hue = (rand.nextFloat() + veil * 0.083f + effectTicks * 0.0007f) % 1.0f
                val c = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.52f, 1.0f) and 0xFFFFFF)
                val width = 18f + rand.nextFloat() * 36f
                val height = 4f + rand.nextFloat() * 12f
                val y = kotlin.math.sin((effectTicks * 0.01f + veil).toDouble()).toFloat() * 7f
                drawSkyLocalQuad(buffer, matrix, basis, 0f, y, width, height, c[0], c[1], c[2], 0.055f + rand.nextFloat() * 0.05f)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawSkyBubbles(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x425542424C45L)
            repeat(18) { bubble ->
                val basis = skyBasis(rand.nextFloat() * 360f, 4f + rand.nextFloat() * 72f, effectTicks * 0.012f + rand.nextFloat() * 360f, 98f)
                val hue = (0.48f + rand.nextFloat() * 0.38f) % 1.0f
                val c = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.28f, 1.0f) and 0xFFFFFF)
                val radius = 2.6f + rand.nextFloat() * 7.0f
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                drawSkyRing(buffer, matrix, basis, radius, radius + 0.55f, c[0], c[1], c[2], 0.15f, 36)
                drawSkyLocalQuad(buffer, matrix, basis, -radius * 0.34f, radius * 0.32f, radius * 0.16f, radius * 0.10f, 1.0f, 1.0f, 1.0f, 0.16f)
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawStarfallBlooms(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x424C4F4F4DL)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            repeat(14) { bloom ->
                val basis = skyBasis(rand.nextFloat() * 360f, 18f + rand.nextFloat() * 62f, rand.nextFloat() * 360f, 98f)
                val hue = (rand.nextFloat() + bloom * 0.09f) % 1.0f
                val c = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.62f, 1.0f) and 0xFFFFFF)
                val pulse = 0.35f + 0.65f * kotlin.math.sin((effectTicks * 0.034f + bloom * 1.4f).toDouble()).toFloat().coerceAtLeast(0f)
                val radius = 3f + rand.nextFloat() * 7f
                repeat(8 + rand.nextInt(7)) { ray ->
                    val angle = ray / 14f * kotlin.math.PI.toFloat() * 2f + rand.nextFloat() * 0.2f
                    drawSkyLocalLine(buffer, matrix, basis, 0f, 0f, kotlin.math.cos(angle) * radius, kotlin.math.sin(angle) * radius, 0.16f, c[0], c[1], c[2], 0.28f * pulse)
                }
                drawSkyLocalQuad(buffer, matrix, basis, 0f, 0f, 0.7f, 0.7f, 1.0f, 0.95f, 0.84f, 0.46f * pulse)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawHorizonCrowns(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x43524F574EL)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            repeat(18) { spike ->
                val basis = skyBasis(spike * 20f + (seed % 19).toFloat(), -8f + rand.nextFloat() * 12f, 0f, 99f)
                val hue = (0.10f + spike * 0.018f + rand.nextFloat() * 0.08f) % 1.0f
                val c = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.56f, 1.0f) and 0xFFFFFF)
                val height = 10f + rand.nextFloat() * 24f
                val width = 1.2f + rand.nextFloat() * 2.4f
                val pulse = 0.74f + 0.26f * kotlin.math.sin((effectTicks * 0.012f + spike).toDouble()).toFloat()
                drawSkyLocalLine(buffer, matrix, basis, 0f, -2f, 0f, height, width, c[0], c[1], c[2], 0.11f * pulse)
                drawSkyLocalQuad(buffer, matrix, basis, 0f, height + 1f, width * 0.7f, width * 0.7f, 1.0f, 0.92f, 0.68f, 0.22f * pulse)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawCelestialScript(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x534352495054L)
            repeat(7) { line ->
                val basis = skyBasis(line * 49f + rand.nextFloat() * 28f, 20f + rand.nextFloat() * 54f, -16f + rand.nextFloat() * 32f, 98f)
                val hue = (0.52f + rand.nextFloat() * 0.36f) % 1.0f
                val c = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.44f, 1.0f) and 0xFFFFFF)
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                var x = -20f
                var y = (rand.nextFloat() * 2f - 1f) * 6f
                repeat(8) { stroke ->
                    val nx = x + 4f + rand.nextFloat() * 5f
                    val ny = y + (rand.nextFloat() * 2f - 1f) * 6f
                    val alpha = 0.18f + 0.18f * kotlin.math.sin((effectTicks * 0.02f + stroke + line).toDouble()).toFloat().coerceAtLeast(0f)
                    drawSkyLocalLine(buffer, matrix, basis, x, y, nx, ny, 0.20f, c[0], c[1], c[2], alpha)
                    if (stroke % 3 == 0) drawSkyLocalQuad(buffer, matrix, basis, nx, ny, 0.55f, 0.55f, 1.0f, 1.0f, 1.0f, alpha)
                    x = nx
                    y = ny
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawGlassConstellations(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x474C415353L)
            repeat(5) { pane ->
                val basis = skyBasis(pane * 72f + rand.nextFloat() * 44f, 24f + rand.nextFloat() * 48f, rand.nextFloat() * 360f, 98f)
                val points = List(5 + rand.nextInt(4)) {
                    (rand.nextFloat() * 2f - 1f) * (10f + rand.nextFloat() * 12f) to
                        (rand.nextFloat() * 2f - 1f) * (8f + rand.nextFloat() * 10f)
                }
                val hue = (0.48f + rand.nextFloat() * 0.24f) % 1.0f
                val c = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.35f, 1.0f) and 0xFFFFFF)
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                for (i in points.indices) {
                    val a = points[i]
                    val b = points[(i + 1) % points.size]
                    drawSkyLocalLine(buffer, matrix, basis, a.first, a.second, b.first, b.second, 0.18f, c[0], c[1], c[2], 0.22f)
                    drawSkyLocalQuad(buffer, matrix, basis, a.first, a.second, 0.56f, 0.56f, 1.0f, 1.0f, 1.0f, 0.42f)
                }
                repeat(points.size / 2) { idx ->
                    val a = points[idx]
                    val b = points[(idx + 2) % points.size]
                    drawSkyLocalLine(buffer, matrix, basis, a.first, a.second, b.first, b.second, 0.08f, c[0], c[1], c[2], 0.10f)
                }
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawRadiantWhirlpools(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val rand = Random(seed xor 0x574849524CL)
            repeat(4) { whirl ->
                val basis = skyBasis(whirl * 89f + rand.nextFloat() * 38f, 30f + rand.nextFloat() * 46f, effectTicks * 0.025f + rand.nextFloat() * 360f, 98f)
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                repeat(4) { arm ->
                    val hue = (arm / 4f + rand.nextFloat() * 0.16f) % 1.0f
                    val c = colorComponents(java.awt.Color.HSBtoRGB(hue, 0.72f, 1.0f) and 0xFFFFFF)
                    for (segment in 0 until 22) {
                        val t0 = segment / 22f
                        val t1 = (segment + 1) / 22f
                        val a0 = t0 * kotlin.math.PI.toFloat() * 5.2f + arm * kotlin.math.PI.toFloat() * 0.5f
                        val a1 = t1 * kotlin.math.PI.toFloat() * 5.2f + arm * kotlin.math.PI.toFloat() * 0.5f
                        val r0 = 1.4f + t0 * 22f
                        val r1 = 1.4f + t1 * 22f
                        drawSkyLocalLine(
                            buffer,
                            matrix,
                            basis,
                            kotlin.math.cos(a0) * r0,
                            kotlin.math.sin(a0) * r0,
                            kotlin.math.cos(a1) * r1,
                            kotlin.math.sin(a1) * r1,
                            0.28f + (1f - t0) * 0.42f,
                            c[0],
                            c[1],
                            c[2],
                            (1f - t0) * 0.18f
                        )
                    }
                }
                drawSkyLocalQuad(buffer, matrix, basis, 0f, 0f, 1.5f, 1.5f, 1.0f, 0.96f, 0.82f, 0.46f)
                tessellator.draw()
            }
        } finally {
            matrices.pop()
        }
    }

    private fun drawAurora(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        yaw: Float,
        color: Int,
        alpha: Float,
        index: Int
    ) {
        repeat(3) { layer ->
            val sway = kotlin.math.sin(Math.toRadians((yaw + layer * 57f).toDouble())).toFloat()
            drawTexturedSkySprite(
                matrices,
                buffer,
                tessellator,
                AURORA_TEXTURE,
                yaw + (layer - 1) * 8.0f,
                24f + sway * 5f,
                -7f + layer * 4.5f,
                64f + index * 2f,
                18f + layer * 2f,
                color,
                alpha * (0.62f - layer * 0.1f)
            )
        }
    }

    private fun drawFallenSkyRift(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        yaw: Float,
        altitude: Float,
        roll: Float,
        width: Float,
        height: Float,
        seed: Long,
        effectTicks: Float
    ) {
        matrices.push()
        try {
            val matrix = matrices.peek().positionMatrix
            val basis = skyBasis(yaw, altitude, roll, 98f)
            val pulse = 0.78f + 0.22f * kotlin.math.sin((effectTicks * 0.012f + seed % 97).toDouble()).toFloat()
            val outline = floatArrayOf(
                -0.95f, -0.28f,
                -0.72f, -0.92f,
                -0.14f, -0.77f,
                0.13f, -1.06f,
                0.82f, -0.72f,
                1.04f, -0.18f,
                0.66f, 0.15f,
                0.90f, 0.72f,
                0.18f, 0.98f,
                -0.10f, 0.62f,
                -0.70f, 0.78f,
                -1.04f, 0.24f
            )

            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR)
            drawSkyPolygon(buffer, matrix, basis, outline, width * 1.12f, height * 1.16f, 0.46f, 0.19f, 0.88f, 0.42f * pulse)
            drawSkyPolygon(buffer, matrix, basis, outline, width * 1.04f, height * 1.07f, 0.12f, 0.86f, 0.96f, 0.16f * pulse)
            drawSkyPolygon(buffer, matrix, basis, outline, width, height, 0.015f, 0.005f, 0.035f, 0.94f)
            tessellator.draw()

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            drawSkyLocalQuad(buffer, matrix, basis, -width * 0.38f, height * 0.18f, width * 0.42f, height * 0.08f, 0.11f, 0.03f, 0.28f, 0.32f)
            drawSkyLocalQuad(buffer, matrix, basis, width * 0.22f, -height * 0.12f, width * 0.34f, height * 0.055f, 0.02f, 0.38f, 0.48f, 0.20f)

            val rand = Random(seed)
            repeat(42) { star ->
                val x = (rand.nextFloat() * 1.72f - 0.86f) * width
                val y = (rand.nextFloat() * 1.62f - 0.81f) * height
                val size = 0.22f + rand.nextFloat() * 0.58f
                val tint = rand.nextFloat()
                val red = if (tint < 0.35f) 0.58f else 0.92f
                val green = if (tint < 0.35f) 0.84f else 0.94f
                val blue = if (tint < 0.35f) 1.0f else 0.74f + rand.nextFloat() * 0.26f
                val twinkle = 0.45f + 0.55f * kotlin.math.sin((effectTicks * 0.045f + star * 1.7f + seed % 31).toDouble()).toFloat().coerceAtLeast(0f)
                drawSkyLocalQuad(buffer, matrix, basis, x, y, size, size, red, green, blue, (0.32f + rand.nextFloat() * 0.46f) * twinkle)
            }

            repeat(4) { shard ->
                val shardBasis = skyBasis(
                    yaw + (shard - 1.5f) * 12f,
                    altitude + if (shard % 2 == 0) -8f else 9f,
                    roll + shard * 19f,
                    97f
                )
                val shardWidth = width * (0.12f + shard * 0.025f)
                val shardHeight = height * (0.16f + (3 - shard) * 0.018f)
                val x = if (shard % 2 == 0) -width * (1.02f + shard * 0.08f) else width * (0.90f + shard * 0.06f)
                val y = -height * 0.42f + shard * height * 0.28f
                drawSkyLocalQuad(buffer, matrix, shardBasis, x, y, shardWidth * 1.16f, shardHeight * 1.16f, 0.46f, 0.19f, 0.88f, 0.25f * pulse)
                drawSkyLocalQuad(buffer, matrix, shardBasis, x, y, shardWidth, shardHeight, 0.012f, 0.004f, 0.032f, 0.86f)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawHorizonRainbowBand(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        yaw: Float,
        startOffset: Float,
        endOffset: Float,
        edgeAltitude: Float,
        peakAltitude: Float,
        thickness: Float,
        color: Int,
        alpha: Float,
        tiltAltitude: Float = 0f
    ) {
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram)
            val matrix = matrices.peek().positionMatrix
            val red = ((color shr 16) and 0xFF) / 255.0f
            val green = ((color shr 8) and 0xFF) / 255.0f
            val blue = (color and 0xFF) / 255.0f
            val segments = 128

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
            for (segment in 0 until segments) {
                val t0 = segment / segments.toFloat()
                val t1 = (segment + 1) / segments.toFloat()
                val yaw0 = yaw + startOffset + (endOffset - startOffset) * t0
                val yaw1 = yaw + startOffset + (endOffset - startOffset) * t1
                val arch0 = edgeAltitude + (peakAltitude - edgeAltitude) * kotlin.math.sin(t0 * kotlin.math.PI).toFloat() + (t0 - 0.5f) * tiltAltitude
                val arch1 = edgeAltitude + (peakAltitude - edgeAltitude) * kotlin.math.sin(t1 * kotlin.math.PI).toFloat() + (t1 - 0.5f) * tiltAltitude
                val fade0 = (0.22f + 0.78f * kotlin.math.sin(t0 * kotlin.math.PI).toFloat().coerceAtLeast(0f)).coerceIn(0f, 1f)
                val fade1 = (0.22f + 0.78f * kotlin.math.sin(t1 * kotlin.math.PI).toFloat().coerceAtLeast(0f)).coerceIn(0f, 1f)
                val lower0 = skyBasis(yaw0, arch0 - thickness, 0f, 99f)
                val lower1 = skyBasis(yaw1, arch1 - thickness, 0f, 99f)
                val upper1 = skyBasis(yaw1, arch1 + thickness, 0f, 99f)
                val upper0 = skyBasis(yaw0, arch0 + thickness, 0f, 99f)

                colorVertex(buffer, matrix, lower0.centerX, lower0.centerY, lower0.centerZ, red, green, blue, alpha * fade0)
                colorVertex(buffer, matrix, lower1.centerX, lower1.centerY, lower1.centerZ, red, green, blue, alpha * fade1)
                colorVertex(buffer, matrix, upper1.centerX, upper1.centerY, upper1.centerZ, red, green, blue, alpha * fade1)
                colorVertex(buffer, matrix, upper0.centerX, upper0.centerY, upper0.centerZ, red, green, blue, alpha * fade0)
            }
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawMovingSkyStreak(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        yaw: Float,
        altitude: Float,
        roll: Float,
        progress: Float,
        travelDistance: Float,
        width: Float,
        height: Float,
        color: Int,
        alpha: Float
    ) {
        if (alpha <= 0.01f) return
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram)
            RenderSystem.setShaderTexture(0, STREAK_TEXTURE)
            val matrix = matrices.peek().positionMatrix
            val red = ((color shr 16) and 0xFF) / 255.0f
            val green = ((color shr 8) and 0xFF) / 255.0f
            val blue = (color and 0xFF) / 255.0f
            val basis = skyBasis(yaw, altitude, roll)
            val travel = (progress.coerceIn(0f, 1f) - 0.5f) * travelDistance
            val centerX = basis.centerX + basis.rightX * travel
            val centerY = basis.centerY + basis.rightY * travel
            val centerZ = basis.centerZ + basis.rightZ * travel

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR)
            texturedVertex(buffer, matrix, centerX - basis.rightX * width + basis.upX * height, centerY - basis.rightY * width + basis.upY * height, centerZ - basis.rightZ * width + basis.upZ * height, 0f, 0f, red, green, blue, alpha)
            texturedVertex(buffer, matrix, centerX + basis.rightX * width + basis.upX * height, centerY + basis.rightY * width + basis.upY * height, centerZ + basis.rightZ * width + basis.upZ * height, 1f, 0f, red, green, blue, alpha)
            texturedVertex(buffer, matrix, centerX + basis.rightX * width - basis.upX * height, centerY + basis.rightY * width - basis.upY * height, centerZ + basis.rightZ * width - basis.upZ * height, 1f, 1f, red, green, blue, alpha)
            texturedVertex(buffer, matrix, centerX - basis.rightX * width - basis.upX * height, centerY - basis.rightY * width - basis.upY * height, centerZ - basis.rightZ * width - basis.upZ * height, 0f, 1f, red, green, blue, alpha)
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun drawTexturedSkySprite(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        texture: Identifier,
        yaw: Float,
        altitude: Float,
        roll: Float,
        width: Float,
        height: Float,
        color: Int,
        alpha: Float
    ) {
        if (alpha <= 0.01f) return
        matrices.push()
        try {
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram)
            RenderSystem.setShaderTexture(0, texture)
            val matrix = matrices.peek().positionMatrix
            val red = ((color shr 16) and 0xFF) / 255.0f
            val green = ((color shr 8) and 0xFF) / 255.0f
            val blue = (color and 0xFF) / 255.0f
            val basis = skyBasis(yaw, altitude, roll)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR)
            texturedVertex(buffer, matrix, basis.centerX - basis.rightX * width + basis.upX * height, basis.centerY - basis.rightY * width + basis.upY * height, basis.centerZ - basis.rightZ * width + basis.upZ * height, 0f, 0f, red, green, blue, alpha)
            texturedVertex(buffer, matrix, basis.centerX + basis.rightX * width + basis.upX * height, basis.centerY + basis.rightY * width + basis.upY * height, basis.centerZ + basis.rightZ * width + basis.upZ * height, 1f, 0f, red, green, blue, alpha)
            texturedVertex(buffer, matrix, basis.centerX + basis.rightX * width - basis.upX * height, basis.centerY + basis.rightY * width - basis.upY * height, basis.centerZ + basis.rightZ * width - basis.upZ * height, 1f, 1f, red, green, blue, alpha)
            texturedVertex(buffer, matrix, basis.centerX - basis.rightX * width - basis.upX * height, basis.centerY - basis.rightY * width - basis.upY * height, basis.centerZ - basis.rightZ * width - basis.upZ * height, 0f, 1f, red, green, blue, alpha)
            tessellator.draw()
        } finally {
            matrices.pop()
        }
    }

    private fun skyBasis(yaw: Float, altitude: Float, roll: Float, radius: Float = 100f): SkyBasis {
        val yawRad = Math.toRadians(yaw.toDouble())
        val altitudeRad = Math.toRadians(altitude.coerceIn(-20f, 86f).toDouble())
        val rollRad = Math.toRadians(roll.toDouble())
        val sinYaw = kotlin.math.sin(yawRad).toFloat()
        val cosYaw = kotlin.math.cos(yawRad).toFloat()
        val sinAlt = kotlin.math.sin(altitudeRad).toFloat()
        val cosAlt = kotlin.math.cos(altitudeRad).toFloat()
        val centerX = sinYaw * cosAlt * radius
        val centerY = sinAlt * radius
        val centerZ = -cosYaw * cosAlt * radius
        val baseRightX = cosYaw
        val baseRightY = 0f
        val baseRightZ = sinYaw
        val baseUpX = -sinYaw * sinAlt
        val baseUpY = cosAlt
        val baseUpZ = cosYaw * sinAlt
        val cosRoll = kotlin.math.cos(rollRad).toFloat()
        val sinRoll = kotlin.math.sin(rollRad).toFloat()
        val rightX = baseRightX * cosRoll + baseUpX * sinRoll
        val rightY = baseRightY * cosRoll + baseUpY * sinRoll
        val rightZ = baseRightZ * cosRoll + baseUpZ * sinRoll
        val upX = baseUpX * cosRoll - baseRightX * sinRoll
        val upY = baseUpY * cosRoll - baseRightY * sinRoll
        val upZ = baseUpZ * cosRoll - baseRightZ * sinRoll

        return SkyBasis(centerX, centerY, centerZ, rightX, rightY, rightZ, upX, upY, upZ)
    }

    private fun colorComponents(color: Int): FloatArray = floatArrayOf(
        ((color shr 16) and 0xFF) / 255.0f,
        ((color shr 8) and 0xFF) / 255.0f,
        (color and 0xFF) / 255.0f
    )

    private fun drawSkyDisc(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        basis: SkyBasis,
        radius: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        segments: Int = 36
    ) {
        for (index in 0 until segments) {
            val angle0 = index / segments.toFloat() * kotlin.math.PI.toFloat() * 2f
            val angle1 = (index + 1) / segments.toFloat() * kotlin.math.PI.toFloat() * 2f
            colorVertexLocal(buffer, matrix, basis, 0f, 0f, red, green, blue, alpha)
            colorVertexLocal(buffer, matrix, basis, kotlin.math.cos(angle0) * radius, kotlin.math.sin(angle0) * radius, red, green, blue, alpha)
            colorVertexLocal(buffer, matrix, basis, kotlin.math.cos(angle1) * radius, kotlin.math.sin(angle1) * radius, red, green, blue, alpha)
        }
    }

    private fun drawSkyRing(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        basis: SkyBasis,
        innerRadius: Float,
        outerRadius: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        segments: Int = 48
    ) {
        for (index in 0 until segments) {
            val angle0 = index / segments.toFloat() * kotlin.math.PI.toFloat() * 2f
            val angle1 = (index + 1) / segments.toFloat() * kotlin.math.PI.toFloat() * 2f
            val cos0 = kotlin.math.cos(angle0)
            val sin0 = kotlin.math.sin(angle0)
            val cos1 = kotlin.math.cos(angle1)
            val sin1 = kotlin.math.sin(angle1)
            colorVertexLocal(buffer, matrix, basis, cos0 * innerRadius, sin0 * innerRadius, red, green, blue, alpha * 0.82f)
            colorVertexLocal(buffer, matrix, basis, cos1 * innerRadius, sin1 * innerRadius, red, green, blue, alpha * 0.82f)
            colorVertexLocal(buffer, matrix, basis, cos1 * outerRadius, sin1 * outerRadius, red, green, blue, alpha)
            colorVertexLocal(buffer, matrix, basis, cos0 * outerRadius, sin0 * outerRadius, red, green, blue, alpha)
        }
    }

    private fun drawSkyLocalLine(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        basis: SkyBasis,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        halfThickness: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val length = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(0.001f)
        val nx = -dy / length * halfThickness
        val ny = dx / length * halfThickness
        colorVertexLocal(buffer, matrix, basis, x0 - nx, y0 - ny, red, green, blue, alpha)
        colorVertexLocal(buffer, matrix, basis, x1 - nx, y1 - ny, red, green, blue, alpha)
        colorVertexLocal(buffer, matrix, basis, x1 + nx, y1 + ny, red, green, blue, alpha)
        colorVertexLocal(buffer, matrix, basis, x0 + nx, y0 + ny, red, green, blue, alpha)
    }

    private fun drawSkyPolygon(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        basis: SkyBasis,
        points: FloatArray,
        width: Float,
        height: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        val pointCount = points.size / 2
        for (index in 0 until pointCount) {
            val next = if (index == pointCount - 1) 0 else index + 1
            colorVertexLocal(buffer, matrix, basis, 0f, 0f, red, green, blue, alpha)
            colorVertexLocal(buffer, matrix, basis, points[index * 2] * width, points[index * 2 + 1] * height, red, green, blue, alpha)
            colorVertexLocal(buffer, matrix, basis, points[next * 2] * width, points[next * 2 + 1] * height, red, green, blue, alpha)
        }
    }

    private fun drawSkyLocalBox(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        basis: SkyBasis,
        centerX: Float,
        centerY: Float,
        halfWidth: Float,
        halfHeight: Float,
        depth: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        fun vertex(x: Float, y: Float, z: Float, shade: Float, faceAlpha: Float = 1.0f) {
            colorVertexLocalDepth(
                buffer,
                matrix,
                basis,
                centerX + x,
                centerY + y,
                z,
                (red * shade).coerceIn(0f, 1f),
                (green * shade).coerceIn(0f, 1f),
                (blue * shade).coerceIn(0f, 1f),
                (alpha * faceAlpha).coerceIn(0f, 1f)
            )
        }

        val near = -depth
        val far = depth
        vertex(-halfWidth, halfHeight, near, 1.22f, 1.0f)
        vertex(halfWidth, halfHeight, near, 1.22f, 1.0f)
        vertex(halfWidth, -halfHeight, near, 1.22f, 1.0f)
        vertex(-halfWidth, -halfHeight, near, 1.22f, 1.0f)

        vertex(halfWidth, halfHeight, near, 0.78f, 0.84f)
        vertex(halfWidth, halfHeight, far, 0.78f, 0.84f)
        vertex(halfWidth, -halfHeight, far, 0.78f, 0.84f)
        vertex(halfWidth, -halfHeight, near, 0.78f, 0.84f)

        vertex(-halfWidth, halfHeight, far, 0.58f, 0.72f)
        vertex(-halfWidth, halfHeight, near, 0.58f, 0.72f)
        vertex(-halfWidth, -halfHeight, near, 0.58f, 0.72f)
        vertex(-halfWidth, -halfHeight, far, 0.58f, 0.72f)

        vertex(-halfWidth, halfHeight, far, 1.05f, 0.86f)
        vertex(halfWidth, halfHeight, far, 1.05f, 0.86f)
        vertex(halfWidth, halfHeight, near, 1.05f, 0.86f)
        vertex(-halfWidth, halfHeight, near, 1.05f, 0.86f)

        vertex(-halfWidth, -halfHeight, near, 0.45f, 0.62f)
        vertex(halfWidth, -halfHeight, near, 0.45f, 0.62f)
        vertex(halfWidth, -halfHeight, far, 0.45f, 0.62f)
        vertex(-halfWidth, -halfHeight, far, 0.45f, 0.62f)
    }

    private fun drawSkyLocalQuad(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        basis: SkyBasis,
        centerX: Float,
        centerY: Float,
        halfWidth: Float,
        halfHeight: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        colorVertexLocal(buffer, matrix, basis, centerX - halfWidth, centerY + halfHeight, red, green, blue, alpha)
        colorVertexLocal(buffer, matrix, basis, centerX + halfWidth, centerY + halfHeight, red, green, blue, alpha)
        colorVertexLocal(buffer, matrix, basis, centerX + halfWidth, centerY - halfHeight, red, green, blue, alpha)
        colorVertexLocal(buffer, matrix, basis, centerX - halfWidth, centerY - halfHeight, red, green, blue, alpha)
    }

    private fun colorVertexLocal(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        basis: SkyBasis,
        x: Float,
        y: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        colorVertex(
            buffer,
            matrix,
            basis.centerX + basis.rightX * x + basis.upX * y,
            basis.centerY + basis.rightY * x + basis.upY * y,
            basis.centerZ + basis.rightZ * x + basis.upZ * y,
            red,
            green,
            blue,
            alpha
        )
    }

    private fun colorVertexLocalDepth(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        basis: SkyBasis,
        x: Float,
        y: Float,
        depth: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        val length = kotlin.math.sqrt(
            (basis.centerX * basis.centerX + basis.centerY * basis.centerY + basis.centerZ * basis.centerZ).toDouble()
        ).toFloat().coerceAtLeast(0.001f)
        val forwardX = basis.centerX / length
        val forwardY = basis.centerY / length
        val forwardZ = basis.centerZ / length
        colorVertex(
            buffer,
            matrix,
            basis.centerX + basis.rightX * x + basis.upX * y + forwardX * depth,
            basis.centerY + basis.rightY * x + basis.upY * y + forwardY * depth,
            basis.centerZ + basis.rightZ * x + basis.upZ * y + forwardZ * depth,
            red,
            green,
            blue,
            alpha
        )
    }

    private fun texturedVertex(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        x: Float,
        y: Float,
        z: Float,
        u: Float,
        v: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        buffer.vertex(matrix, x, y, z).texture(u, v).color(red, green, blue, alpha).next()
    }

    private fun colorVertex(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        x: Float,
        y: Float,
        z: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).next()
    }

    private fun tintQuad(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        x4: Float,
        y4: Float,
        z4: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, x3, y3, z3).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, x4, y4, z4).color(red, green, blue, alpha).next()
    }

    private fun drawCelestialBody(
        matrices: MatrixStack, buffer: BufferBuilder, tessellator: Tessellator, texture: Identifier,
        size: Float, timeAngle: Float, orbitOffsetPitch: Float, orbitOffsetYaw: Float,
        r: Float, g: Float, b: Float, a: Float, isMoon: Boolean = false
    ) {
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(orbitOffsetYaw))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(orbitOffsetPitch))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(timeAngle * 360.0f))

        RenderSystem.setShaderTexture(0, texture)
        val matrix = matrices.peek().positionMatrix
        val scale = 30.0f * size

        var u0 = 0.0f; var u1 = 1.0f; var v0 = 0.0f; var v1 = 1.0f
        if (isMoon) {
            val phase = (timeAngle * 8.0f).toInt() % 8
            val col = phase % 4; val row = phase / 4
            u0 = col / 4.0f; u1 = (col + 1) / 4.0f
            v0 = row / 2.0f; v1 = (row + 1) / 2.0f
        }

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR)
        // Back to fixed distance (100) to match vanilla sky
        buffer.vertex(matrix, -scale, 100.0f, -scale).texture(u0, v0).color(r, g, b, a).next()
        buffer.vertex(matrix, scale, 100.0f, -scale).texture(u1, v0).color(r, g, b, a).next()
        buffer.vertex(matrix, scale, 100.0f, scale).texture(u1, v1).color(r, g, b, a).next()
        buffer.vertex(matrix, -scale, 100.0f, scale).texture(u0, v1).color(r, g, b, a).next()
        tessellator.draw()
        matrices.pop()
    }
}
