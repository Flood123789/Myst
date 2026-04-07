package mystcraft.flood.client.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import mystcraft.flood.client.cache.ClientAgeCache
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
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
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