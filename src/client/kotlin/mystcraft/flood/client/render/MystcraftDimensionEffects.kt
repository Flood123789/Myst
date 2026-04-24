package mystcraft.flood.client.render

import mystcraft.flood.client.cache.ClientAgeCache
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.DimensionEffects
import net.minecraft.util.math.Vec3d

class MystcraftDimensionEffects : DimensionEffects(
    192.0f, // Cloud height
    true,   // Has ground
    SkyType.NORMAL,
    false,  // Force bright
    false   // Alternate sky color
) {
    /**
     * Required implementation for 1.20.1. 
     * Keep this neutral. Driving ambient tint through DimensionEffects can make
     * distant renderers and vanilla chunk-edge fog disagree, which shows up as a
     * dark wall at the edge of the render distance in some Age palettes.
     */
    override fun adjustFogColor(color: Vec3d, sunHeight: Float): Vec3d {
        return color
    }

    /**
     * Required implementation for 1.20.1.
     * Returning null tells Minecraft to use the standard fog color logic.
     */
    override fun getFogColorOverride(skyAngle: Float, tickDelta: Float): FloatArray? {
        return null
    }

    override fun useThickFog(camX: Int, camY: Int): Boolean {
        return false
    }

    override fun getCloudsHeight(): Float {
        if (!ClientRenderCompatibility.canUseCustomCloudHeight()) return super.getCloudsHeight()

        val world = MinecraftClient.getInstance().world ?: return super.getCloudsHeight()
        if (world.registryKey.value.namespace != "mystcraft-reforged") return super.getCloudsHeight()
        return ClientAgeCache.getProperties(world.registryKey.value)?.cloudHeight ?: super.getCloudsHeight()
    }
}
