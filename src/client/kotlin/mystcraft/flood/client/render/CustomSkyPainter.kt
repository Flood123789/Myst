package mystcraft.flood.client.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.generation.ChaosAgeThemes
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.VertexBuffer
import net.minecraft.client.render.*
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.random.Random as MCRandom
import org.joml.Matrix4f
import kotlin.random.Random

object CustomSkyPainter {
    private val SUN_TEXTURE = Identifier("textures/environment/sun.png")
    private val MOON_PHASES = Identifier("textures/environment/moon_phases.png")
    private var starBuffer: VertexBuffer? = null

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

        matrices.pop()

        drawChaosSky(matrices, buffer, tessellator, profile.modifiers, profile.seed, world.getSkyAngle(tickDelta), tickDelta)
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun drawChaosSky(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        modifiers: List<String>,
        seed: Long,
        skyAngle: Float,
        tickDelta: Float
    ) {
        if (modifiers.contains(ChaosAgeThemes.BRIGHT_SKY)) {
            drawSkyBand(matrices, buffer, tessellator, 0f, 95f, 72f, 0xFFF9B8, 0.15f)
        }
        if (modifiers.contains(ChaosAgeThemes.DARK_SKY)) {
            drawSkyBand(matrices, buffer, tessellator, 0f, 96f, 88f, 0x050713, 0.36f)
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_RAINBOWS)) {
            repeat(2) { index ->
                val offset = ((seed shr (index * 8)).toInt() and 255).toFloat()
                drawRainbowArc(matrices, buffer, tessellator, offset + index * 76f)
            }
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_AURORAS)) {
            repeat(4) { index ->
                val hue = ((seed shr (index * 10)).toInt() and 255) / 255.0f
                val color = java.awt.Color.HSBtoRGB(hue, 0.72f, 1.0f) and 0xFFFFFF
                drawSkyRibbon(matrices, buffer, tessellator, index * 54f + hue * 80f, 0xFF000000.toInt() or color, 0.24f)
            }
        }
        if (modifiers.contains(ChaosAgeThemes.SKY_RIFTS)) {
            repeat(2) { index ->
                drawSkySlash(matrices, buffer, tessellator, 45f + index * 130f + (seed % 37).toFloat(), 0xB287FF, 0.44f, 34f + index * 9f)
            }
        }
        if (modifiers.contains(ChaosAgeThemes.SHOOTING_STARS)) {
            repeat(7) { index ->
                val phase = ((skyAngle * 24000f + tickDelta * 20f + index * 137f) % 900f) / 900f
                drawSkySlash(matrices, buffer, tessellator, index * 48f + phase * 60f, 0xF9FFFF, 0.22f * (1.0f - phase), 8f + index % 3)
            }
        }
        if (modifiers.contains(ChaosAgeThemes.COMETS)) {
            repeat(2) { index ->
                val phase = ((skyAngle * 24000f + index * 311f) % 2400f) / 2400f
                drawComet(matrices, buffer, tessellator, 25f + index * 160f + phase * 70f, 0.62f - phase * 0.22f)
            }
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
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        val matrix = matrices.peek().positionMatrix
        val red = ((color shr 16) and 0xFF) / 255.0f
        val green = ((color shr 8) and 0xFF) / 255.0f
        val blue = (color and 0xFF) / 255.0f
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
        buffer.vertex(matrix, -width, height, -82f).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, width, height, -82f).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, width, height - 54f, -92f).color(red, green, blue, 0f).next()
        buffer.vertex(matrix, -width, height - 54f, -92f).color(red, green, blue, 0f).next()
        tessellator.draw()
        matrices.pop()
    }

    private fun drawRainbowArc(matrices: MatrixStack, buffer: BufferBuilder, tessellator: Tessellator, yaw: Float) {
        val colors = intArrayOf(0xFF5E5E, 0xFFB84A, 0xFFE96A, 0x62D66B, 0x56A8FF, 0x9A78FF)
        colors.forEachIndexed { index, color ->
            drawSkyBand(matrices, buffer, tessellator, yaw + index * 1.5f, 70f - index * 2.8f, 44f + index * 5f, color, 0.18f)
        }
    }

    private fun drawSkyRibbon(matrices: MatrixStack, buffer: BufferBuilder, tessellator: Tessellator, yaw: Float, color: Int, alpha: Float) {
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(8f))
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        val matrix = matrices.peek().positionMatrix
        val red = ((color shr 16) and 0xFF) / 255.0f
        val green = ((color shr 8) and 0xFF) / 255.0f
        val blue = (color and 0xFF) / 255.0f
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
        buffer.vertex(matrix, -30f, 96f, -92f).color(red, green, blue, 0f).next()
        buffer.vertex(matrix, 36f, 86f, -95f).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, 42f, 70f, -94f).color(red, green, blue, 0f).next()
        buffer.vertex(matrix, -36f, 78f, -92f).color(red, green, blue, alpha * 0.6f).next()
        tessellator.draw()
        matrices.pop()
    }

    private fun drawSkySlash(
        matrices: MatrixStack,
        buffer: BufferBuilder,
        tessellator: Tessellator,
        yaw: Float,
        color: Int,
        alpha: Float,
        length: Float
    ) {
        if (alpha <= 0.01f) return
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-24f))
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        val matrix = matrices.peek().positionMatrix
        val red = ((color shr 16) and 0xFF) / 255.0f
        val green = ((color shr 8) and 0xFF) / 255.0f
        val blue = (color and 0xFF) / 255.0f
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
        buffer.vertex(matrix, -length, 86f, -96f).color(red, green, blue, 0f).next()
        buffer.vertex(matrix, length * 0.35f, 88f, -96f).color(red, green, blue, alpha).next()
        buffer.vertex(matrix, length, 84f, -96f).color(red, green, blue, 0f).next()
        buffer.vertex(matrix, -length * 0.2f, 82f, -96f).color(red, green, blue, alpha * 0.5f).next()
        tessellator.draw()
        matrices.pop()
    }

    private fun drawComet(matrices: MatrixStack, buffer: BufferBuilder, tessellator: Tessellator, yaw: Float, alpha: Float) {
        drawSkySlash(matrices, buffer, tessellator, yaw, 0xBDEBFF, alpha * 0.45f, 42f)
        drawSkyBand(matrices, buffer, tessellator, yaw + 2f, 88f, 5f, 0xEFFFFF, alpha * 0.28f)
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
