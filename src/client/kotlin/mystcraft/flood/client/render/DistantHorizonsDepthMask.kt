package mystcraft.flood.client.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.VertexSorter
import mystcraft.flood.MystcraftReforged
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gl.ShaderProgram
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Copies Distant Horizons' LOD depth into the depth buffer the sky overlay tests against.
 *
 * Distant Horizons renders its LOD chunks into its own framebuffer, so their depth never reaches
 * Minecraft's main depth attachment. Without this the overlay treats every LOD pixel as open sky and
 * paints straight over distant terrain.
 *
 * The pass only ever writes where DH recorded geometry, so it can add occlusion but never remove
 * any: if DH is absent, idle, or its texture is empty, every fragment is discarded and the depth
 * buffer is untouched. Minecraft clears depth again before the hand and the HUD, so stamping it here
 * affects nothing but the overlay.
 */
object DistantHorizonsDepthMask {
    private const val DH_DELAYED = "com.seibel.distanthorizons.api.DhApi\$Delayed"

    private val loaded: Boolean by lazy {
        val loader = FabricLoader.getInstance()
        loader.isModLoaded("distanthorizons") || loader.isModLoaded("distant_horizons")
    }
    private val warningLogged = AtomicBoolean(false)

    @Volatile
    private var program: ShaderProgram? = null

    @Volatile
    private var disabled = false

    val vertexFormat: VertexFormat = VertexFormats.POSITION

    fun setProgram(shader: ShaderProgram) {
        program = shader
    }

    /** Returns true when LOD depth was stamped, so the caller knows the mask covers DH terrain. */
    fun stampLodDepth(): Boolean {
        if (!loaded || disabled || ClientRenderCompatibility.isShaderPackActive()) return false
        val shader = program ?: return false
        val depthTexture = depthTextureId() ?: return false

        val previousProjection = RenderSystem.getProjectionMatrix()
        val previousSorter = RenderSystem.getVertexSorting()
        val modelView = RenderSystem.getModelViewStack()

        RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f), VertexSorter.BY_DISTANCE)
        modelView.push()
        modelView.loadIdentity()
        RenderSystem.applyModelViewMatrix()

        RenderSystem.colorMask(false, false, false, false)
        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.depthFunc(GL11.GL_ALWAYS)
        RenderSystem.depthMask(true)
        try {
            RenderSystem.setShader { shader }
            // DH deletes and recreates its textures, and a deleted name unbinds itself while the
            // state manager still believes it is bound. Clearing the unit makes the next bind real.
            GlStateManager._activeTexture(GL13.GL_TEXTURE0)
            GlStateManager._bindTexture(0)
            RenderSystem.setShaderTexture(0, depthTexture)

            val tessellator = Tessellator.getInstance()
            val buffer = tessellator.buffer
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION)
            buffer.vertex(0.0, 0.0, 0.0).next()
            buffer.vertex(1.0, 0.0, 0.0).next()
            buffer.vertex(1.0, 1.0, 0.0).next()
            buffer.vertex(0.0, 1.0, 0.0).next()
            tessellator.draw()
        } finally {
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.colorMask(true, true, true, true)
            RenderSystem.setShaderTexture(0, 0)
            modelView.pop()
            RenderSystem.applyModelViewMatrix()
            RenderSystem.setProjectionMatrix(previousProjection, previousSorter)
        }
        return true
    }

    /** Reflective because Distant Horizons is an optional dependency. */
    private fun depthTextureId(): Int? {
        return try {
            val renderProxy = Class.forName(DH_DELAYED).getField("renderProxy").get(null) ?: return null
            val getter = renderProxy.javaClass.methods.firstOrNull {
                it.name == "getDhDepthTextureId" && it.parameterCount == 0
            } ?: return null
            val result = getter.invoke(renderProxy) ?: return null
            val payload = payloadOf(result) ?: return null
            payload.takeIf { it > 0 }
        } catch (_: ClassNotFoundException) {
            disabled = true
            null
        } catch (throwable: Throwable) {
            if (warningLogged.compareAndSet(false, true)) {
                MystcraftReforged.LOGGER.warn(
                    "Could not read the Distant Horizons depth texture; sky overlays will not be occluded by LOD chunks.",
                    throwable
                )
            }
            disabled = true
            null
        }
    }

    /** DH wraps returns in a result object carrying a success flag and the value. */
    private fun payloadOf(result: Any): Int? {
        if (result is Int) return result
        val success = runCatching { result.javaClass.getField("success").getBoolean(result) }.getOrDefault(true)
        if (!success) return null
        return runCatching { result.javaClass.getField("payload").get(result) as? Int }.getOrNull()
    }
}
