package mystcraft.flood.client.render

import net.fabricmc.loader.api.FabricLoader

object ClientRenderCompatibility {
    private val shaderRendererLoaded: Boolean by lazy {
        val loader = FabricLoader.getInstance()
        loader.isModLoaded("iris") ||
            loader.isModLoaded("oculus") ||
            loader.isModLoaded("canvas")
    }

    private val distantHorizonsLoaded: Boolean by lazy {
        val loader = FabricLoader.getInstance()
        loader.isModLoaded("distanthorizons") ||
            loader.isModLoaded("distant_horizons")
    }

    @JvmStatic
    fun useShaderSafeWorldRendering(): Boolean = shaderRendererLoaded

    @JvmStatic
    fun canUseRenderSystemWeatherTint(): Boolean = !shaderRendererLoaded

    @JvmStatic
    fun canUseCustomSkyOverlay(): Boolean = true

    @JvmStatic
    fun canUseCustomCloudHeight(): Boolean = !shaderRendererLoaded && !distantHorizonsLoaded

    @JvmStatic
    fun canUseLateAmbientFogTint(): Boolean = !shaderRendererLoaded && !distantHorizonsLoaded

    @JvmStatic
    fun describe(): String {
        val modes = mutableListOf<String>()
        if (shaderRendererLoaded) modes.add("shader-safe")
        if (distantHorizonsLoaded) modes.add("distant-horizons")
        return if (modes.isEmpty()) "vanilla" else modes.joinToString("+")
    }
}
