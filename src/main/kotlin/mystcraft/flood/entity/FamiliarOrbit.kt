package mystcraft.flood.entity

import net.minecraft.util.math.Vec3d

/** The familiar's deliberately uneven active orbit around its owner. */
object FamiliarOrbit {

    fun anchor(ownerPosition: Vec3d, ageTicks: Int, phaseSeed: Long): Vec3d {
        val phase = ((phaseSeed ushr 12) and 0xffffL).toDouble() / 65535.0 * Math.PI * 2.0
        val clock = ageTicks.toDouble()
        val angle = phase + clock * ANGULAR_SPEED + Math.sin(clock * PRECESSION_SPEED + phase) * PRECESSION
        val radius = BASE_RADIUS + Math.sin(clock * RADIUS_SPEED + phase * 1.7) * RADIUS_WOBBLE
        val height = BASE_HEIGHT + Math.sin(clock * HEIGHT_SPEED + phase * 0.7) * HEIGHT_WOBBLE
        return ownerPosition.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius)
    }

    const val BASE_RADIUS = 3.6
    const val RADIUS_WOBBLE = 0.65
    const val BASE_HEIGHT = 1.25
    const val HEIGHT_WOBBLE = 0.38
    private const val ANGULAR_SPEED = 0.045
    private const val PRECESSION_SPEED = 0.017
    private const val RADIUS_SPEED = 0.026
    private const val HEIGHT_SPEED = 0.033
    private const val PRECESSION = 0.38
}
