package mystcraft.flood.client.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.client.render.ClientRenderCompatibility
import mystcraft.flood.generation.ChaosAgeThemes
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** The point in the frame an authored sky element is painted at. */
enum class SkyLayer {
    /** Inside the vanilla sky pass, before terrain. Shader packs usually discard this. */
    SKY_PASS,

    /** After the world (and any shader composite passes) finished, so the element always shows. */
    OVERLAY
}

/** Per-element placement. [AUTO] follows the global [SkyRenderMode]. */
enum class SkyLayerMode { AUTO, SKY_PASS, OVERLAY, HIDDEN }

/** Global placement. [AUTO] paints on top whenever a shader pack is active. */
enum class SkyRenderMode { AUTO, SKY_PASS, OVERLAY, OFF }

/**
 * Keys for every element [mystcraft.flood.client.render.CustomSkyPainter] can draw. Anomaly keys
 * match the [ChaosAgeThemes] modifier they react to so a profile modifier maps straight onto a
 * config entry.
 */
object SkyElements {
    const val SKY_TINT = "sky_tint"
    const val STARS = "stars"
    const val SUNS = "suns"
    const val MOONS = "moons"

    val CORE: List<String> = listOf(SKY_TINT, STARS, SUNS, MOONS)

    val ANOMALIES: List<String> = listOf(
        ChaosAgeThemes.BRIGHT_SKY,
        ChaosAgeThemes.DARK_SKY,
        ChaosAgeThemes.SKY_RAINBOWS,
        ChaosAgeThemes.SKY_AURORAS,
        ChaosAgeThemes.SKY_RIFTS,
        ChaosAgeThemes.SHOOTING_STARS,
        ChaosAgeThemes.COMETS,
        ChaosAgeThemes.SKY_NEBULAE,
        ChaosAgeThemes.ECLIPSE_HALOS,
        ChaosAgeThemes.STAR_GLYPHS,
        ChaosAgeThemes.HORIZON_MIRAGES,
        ChaosAgeThemes.CRYSTAL_HALOS,
        ChaosAgeThemes.VOID_FLECKS,
        ChaosAgeThemes.SPIRAL_GALAXIES,
        ChaosAgeThemes.FALLING_SKY_SHARDS,
        ChaosAgeThemes.LIGHTNING_VEINS,
        ChaosAgeThemes.LUMINOUS_COLUMNS,
        ChaosAgeThemes.SKY_MONOLITHS,
        ChaosAgeThemes.PRISM_RINGS,
        ChaosAgeThemes.CHROMA_WAVES,
        ChaosAgeThemes.ORBITAL_GRID,
        ChaosAgeThemes.SKY_LANTERNS,
        ChaosAgeThemes.FRACTURE_WEB,
        ChaosAgeThemes.DREAM_VEILS,
        ChaosAgeThemes.SKY_BUBBLES,
        ChaosAgeThemes.STARFALL_BLOOMS,
        ChaosAgeThemes.HORIZON_CROWNS,
        ChaosAgeThemes.CELESTIAL_SCRIPT,
        ChaosAgeThemes.GLASS_CONSTELLATIONS,
        ChaosAgeThemes.RADIANT_WHIRLPOOLS
    )

    val ALL: List<String> = CORE + ANOMALIES

    fun label(key: String): String = key.split('_').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }
}

data class SkyRenderSettings(
    var mode: SkyRenderMode = SkyRenderMode.AUTO,
    /** Restrict overlay elements to pixels where no world geometry was drawn. */
    var overlayMasksToSky: Boolean = true,
    /** Fold Distant Horizons' LOD depth into the mask so LOD terrain occludes the overlay too. */
    var overlayIncludesDistantHorizons: Boolean = true,
    /** Repaint the first sun, moon and star layer, which the overlay covers up. */
    var overlayDrawsBaseBodies: Boolean = true,
    var overlaySkyTintOpacity: Float = 0.6f,
    var skyPassTintOpacity: Float = 0.72f,
    var elements: MutableMap<String, SkyLayerMode> = defaultElements()
) {
    fun normalized(): SkyRenderSettings {
        val merged = defaultElements()
        for (key in SkyElements.ALL) {
            elements[key]?.let { merged[key] = it }
        }
        return copy(
            overlaySkyTintOpacity = overlaySkyTintOpacity.coerceIn(0.0f, 1.0f),
            skyPassTintOpacity = skyPassTintOpacity.coerceIn(0.0f, 1.0f),
            elements = merged
        )
    }

    companion object {
        fun defaultElements(): MutableMap<String, SkyLayerMode> =
            SkyElements.ALL.associateWithTo(LinkedHashMap()) { SkyLayerMode.AUTO }
    }
}

/**
 * Client-side placement rules for the authored sky.
 *
 * Shader packs replace the vanilla sky pass and rebuild the frame from their own gbuffers, which
 * drops anything the mod painted during [SkyLayer.SKY_PASS]. Routing elements to
 * [SkyLayer.OVERLAY] paints them after the shader composite instead, where they survive.
 */
object SkyRenderConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path
        get() = FabricLoader.getInstance().configDir.resolve("mystcraft-reforged-sky.json")

    @Volatile
    var current: SkyRenderSettings = SkyRenderSettings()
        private set

    fun load() {
        current = try {
            if (Files.exists(configPath)) {
                parse(JsonParser.parseString(Files.readString(configPath)).asJsonObject)
            } else {
                SkyRenderSettings()
            }
        } catch (error: Exception) {
            MystcraftReforged.LOGGER.error("Could not read {}; using defaults", configPath, error)
            SkyRenderSettings()
        }
        save()
    }

    private fun parse(json: JsonObject): SkyRenderSettings {
        val defaults = SkyRenderSettings()
        val elements = SkyRenderSettings.defaultElements()
        json.getAsJsonObject("elements")?.entrySet()?.forEach { (key, value) ->
            if (!elements.containsKey(key)) return@forEach
            enumOrNull<SkyLayerMode>(value.asString)?.let { elements[key] = it }
        }
        return SkyRenderSettings(
            mode = json.get("mode")?.asString?.let { enumOrNull<SkyRenderMode>(it) } ?: defaults.mode,
            overlayMasksToSky = json.get("overlayMasksToSky")?.asBoolean ?: defaults.overlayMasksToSky,
            overlayIncludesDistantHorizons = json.get("overlayIncludesDistantHorizons")?.asBoolean
                ?: defaults.overlayIncludesDistantHorizons,
            overlayDrawsBaseBodies = json.get("overlayDrawsBaseBodies")?.asBoolean ?: defaults.overlayDrawsBaseBodies,
            overlaySkyTintOpacity = json.get("overlaySkyTintOpacity")?.asFloat ?: defaults.overlaySkyTintOpacity,
            skyPassTintOpacity = json.get("skyPassTintOpacity")?.asFloat ?: defaults.skyPassTintOpacity,
            elements = elements
        ).normalized()
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    @Synchronized
    fun replace(settings: SkyRenderSettings) {
        current = settings.normalized()
        save()
    }

    @Synchronized
    fun save() {
        Files.createDirectories(configPath.parent)
        val temporary = configPath.resolveSibling(configPath.fileName.toString() + ".tmp")
        Files.writeString(temporary, gson.toJson(current))
        try {
            Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private var shaderProbeAtMs = 0L
    private var shaderPackActive = false

    /**
     * The shader check reaches Iris through reflection, and every element asks for it on every
     * frame. Re-probing a few times a second is far cheaper and still reacts to a shader toggle
     * before the player can see it.
     */
    private fun shaderPackActive(): Boolean {
        val now = System.currentTimeMillis()
        if (now - shaderProbeAtMs >= 250L) {
            shaderPackActive = ClientRenderCompatibility.isShaderPackActive()
            shaderProbeAtMs = now
        }
        return shaderPackActive
    }

    /** The layer [SkyLayerMode.AUTO] elements resolve to, or null when the sky is switched off. */
    fun resolvedLayer(): SkyLayer? = when (current.mode) {
        SkyRenderMode.OFF -> null
        SkyRenderMode.SKY_PASS -> SkyLayer.SKY_PASS
        SkyRenderMode.OVERLAY -> SkyLayer.OVERLAY
        SkyRenderMode.AUTO -> if (shaderPackActive()) SkyLayer.OVERLAY else SkyLayer.SKY_PASS
    }

    @JvmStatic
    fun drawsOn(element: String, layer: SkyLayer): Boolean {
        val auto = resolvedLayer() ?: return false
        return when (current.elements[element] ?: SkyLayerMode.AUTO) {
            SkyLayerMode.HIDDEN -> false
            SkyLayerMode.AUTO -> layer == auto
            SkyLayerMode.SKY_PASS -> layer == SkyLayer.SKY_PASS
            SkyLayerMode.OVERLAY -> layer == SkyLayer.OVERLAY
        }
    }

    /** Lets the render hooks skip all of their setup when the layer has nothing to paint. */
    @JvmStatic
    fun paintsAnythingOn(layer: SkyLayer): Boolean {
        val auto = resolvedLayer() ?: return false
        if (auto == layer) return current.elements.values.any { it != SkyLayerMode.HIDDEN }
        return current.elements.values.any {
            (it == SkyLayerMode.SKY_PASS && layer == SkyLayer.SKY_PASS) ||
                (it == SkyLayerMode.OVERLAY && layer == SkyLayer.OVERLAY)
        }
    }

    @JvmStatic
    fun overlayMasksToSky(): Boolean = current.overlayMasksToSky

    @JvmStatic
    fun overlayIncludesDistantHorizons(): Boolean = current.overlayIncludesDistantHorizons

    @JvmStatic
    fun overlayDrawsBaseBodies(): Boolean = current.overlayDrawsBaseBodies

    fun tintOpacity(layer: SkyLayer): Float = when (layer) {
        SkyLayer.SKY_PASS -> current.skyPassTintOpacity
        SkyLayer.OVERLAY -> current.overlaySkyTintOpacity
    }
}
