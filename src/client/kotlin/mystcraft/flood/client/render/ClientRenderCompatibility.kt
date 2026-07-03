package mystcraft.flood.client.render

import net.fabricmc.loader.api.FabricLoader

object ClientRenderCompatibility {
    private data class IrisApiProbe(
        val api: Any,
        val isShaderPackInUse: java.lang.reflect.Method,
        val isRenderingShadowPass: java.lang.reflect.Method?
    )

    private val shaderRendererAvailable: Boolean by lazy {
        val loader = FabricLoader.getInstance()
        loader.isModLoaded("iris") ||
            loader.isModLoaded("oculus") ||
            loader.isModLoaded("canvas")
    }

    private val irisShaderPackProbe: IrisApiProbe? by lazy {
        runCatching {
            val apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            val api = apiClass.getMethod("getInstance").invoke(null)
            val isShaderPackInUse = apiClass.getMethod("isShaderPackInUse")
            val isRenderingShadowPass = runCatching { apiClass.getMethod("isRenderingShadowPass") }.getOrNull()
            IrisApiProbe(api, isShaderPackInUse, isRenderingShadowPass)
        }.getOrNull()
    }

    private val distantHorizonsLoaded: Boolean by lazy {
        val loader = FabricLoader.getInstance()
        loader.isModLoaded("distanthorizons") ||
            loader.isModLoaded("distant_horizons")
    }

    @JvmStatic
    fun isShaderPackActive(): Boolean {
        irisShaderPackProbe?.let { probe ->
            return runCatching {
                probe.isShaderPackInUse.invoke(probe.api) as? Boolean ?: false
            }.getOrDefault(false)
        }

        return false
    }

    @JvmStatic
    fun isIrisShadowPass(): Boolean {
        val probe = irisShaderPackProbe ?: return false
        val isRenderingShadowPass = probe.isRenderingShadowPass ?: return false
        return runCatching {
            isRenderingShadowPass.invoke(probe.api) as? Boolean ?: false
        }.getOrDefault(false)
    }

    @JvmStatic
    fun useShaderSafeWorldRendering(): Boolean = isShaderPackActive()

    @JvmStatic
    fun canUseRenderSystemWeatherTint(): Boolean = !isShaderPackActive()

    @JvmStatic
    fun canUseRenderSystemCloudTint(): Boolean = !isIrisShadowPass()

    @JvmStatic
    fun canUseCustomSkyOverlay(): Boolean = !isIrisShadowPass() && (!isShaderPackActive() || distantHorizonsLoaded)

    @JvmStatic
    fun canUseShaderFallbackSkyOverlay(): Boolean = isShaderPackActive() && !isIrisShadowPass() && !distantHorizonsLoaded

    @JvmStatic
    fun canUseCustomCloudHeight(): Boolean = !isShaderPackActive() && !distantHorizonsLoaded

    @JvmStatic
    fun canUseLateAmbientFogTint(): Boolean = !isShaderPackActive() && !distantHorizonsLoaded

    @JvmStatic
    fun describe(): String {
        val modes = mutableListOf<String>()
        if (shaderRendererAvailable) modes.add("shader-api")
        if (isShaderPackActive()) modes.add("shader-pack")
        if (distantHorizonsLoaded) modes.add("distant-horizons")
        return if (modes.isEmpty()) "vanilla" else modes.joinToString("+")
    }
}
