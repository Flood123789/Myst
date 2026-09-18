package mystcraft.flood.client.render

/**
 * The roll angle a Reaper's rider is currently being carried at, in radians.
 *
 * Two separate injections are needed to roll a mounted view, and this is what carries the angle
 * between them. {@code Camera.update} is where the creature's drawn orientation is available and
 * where the camera's own basis vectors live, but rolling those does not rotate the picture: in
 * 1.20.1 the world's view matrix is rebuilt in {@code GameRenderer.renderWorld} from the camera's
 * scalar pitch and yaw, and the camera's quaternion only ever reaches particle and nameplate
 * billboarding. So the angle is measured in the camera pass and applied in the renderer pass.
 *
 * Ordering is not a concern: {@code renderWorld} calls {@code camera.update} itself, well before
 * it composes the view rotation, so the value read here is always from the current frame.
 */
object ReaperCameraRoll {

    @Volatile
    private var radians = 0.0f

    /** Set from the camera pass each frame; zero whenever the player is not riding a Reaper. */
    @JvmStatic
    fun publish(value: Float) {
        radians = if (value.isFinite()) value else 0.0f
    }

    @JvmStatic
    fun clear() {
        radians = 0.0f
    }

    @JvmStatic
    fun current(): Float = radians
}
