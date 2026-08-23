package mystcraft.flood.client.render

import net.minecraft.client.util.math.MatrixStack
import org.joml.Matrix4f

/**
 * Remembers the camera basis the world was rendered with so the overlay pass can reuse it.
 *
 * The overlay runs after `WorldRenderer.render` returns, where the view rotation and projection are
 * no longer passed in. Copying them at the head of the world render keeps the overlay locked to the
 * exact same camera the sky pass would have used, including view bob and hurt tilt.
 */
object SkyFrameCapture {
    private val view = Matrix4f()
    private val projection = Matrix4f()

    @Volatile
    private var captured = false

    @JvmStatic
    fun capture(viewMatrix: Matrix4f, projectionMatrix: Matrix4f) {
        view.set(viewMatrix)
        projection.set(projectionMatrix)
        captured = true
    }

    /** A fresh matrix stack holding the captured view rotation, or null if no frame was captured. */
    fun viewStack(): MatrixStack? {
        if (!captured) return null
        val stack = MatrixStack()
        stack.peek().positionMatrix.set(view)
        return stack
    }

    fun projectionMatrix(): Matrix4f = Matrix4f(projection)
}
