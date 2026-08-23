package mystcraft.flood.generation

import mystcraft.flood.generation.profile.ColorCategory
import mystcraft.flood.registry.ModSymbols
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

sealed interface CompiledColor {
    fun resolve(rand: kotlin.random.Random): Int
    fun resolve(rand: java.util.Random): Int = resolve(kotlin.random.Random(rand.nextLong()))

    data class Exact(val rgb: Int) : CompiledColor {
        override fun resolve(rand: kotlin.random.Random): Int = rgb
        override fun resolve(rand: java.util.Random): Int = rgb
    }

    data class Preset(val category: ColorCategory) : CompiledColor {
        override fun resolve(rand: kotlin.random.Random): Int = category.sample(rand)
        override fun resolve(rand: java.util.Random): Int = category.sample(rand)
    }
}

/**
 * Intermediate result of parsing a book's ordered symbol pages.
 *
 * This deliberately uses nullable fields: null means the author omitted that category and
 * [AgeProfileManager] must choose a deterministic default. It is not the persisted Age schema;
 * [mystcraft.flood.generation.profile.AgeProfile] fills that role.
 */
data class CompiledAgeData(
    var terrainType: String? = null,
    val biomes: MutableList<String> = mutableListOf(),
    var biomeController: String? = null,
    var timeMode: String? = null,
    var fixedTimeOfDay: Long? = null,
    var timeScaleMultiplier: Float = 1.0f,
    var weatherMode: String? = null,
    
    var skyColorSpec: CompiledColor? = null,
    var fogColorSpec: CompiledColor? = null,
    var waterColorSpec: CompiledColor? = null,
    var grassColorSpec: CompiledColor? = null,
    var foliageColorSpec: CompiledColor? = null,
    var ambientColorSpec: CompiledColor? = null,
    var cloudColorSpec: CompiledColor? = null,
    var fireLavaColorSpec: CompiledColor? = null,

    var cloudHeight: Float? = null,
    var ageEffectId: String? = null,
    var lowGravity: Boolean = false,
    
    var conflictInstability: Int = 0
) {
    var skyColor: Int?
        get() = specToDefaultRgb(skyColorSpec)
        set(value) { skyColorSpec = value?.let { CompiledColor.Exact(it) } }

    var fogColor: Int?
        get() = specToDefaultRgb(fogColorSpec)
        set(value) { fogColorSpec = value?.let { CompiledColor.Exact(it) } }

    var waterColor: Int?
        get() = specToDefaultRgb(waterColorSpec)
        set(value) { waterColorSpec = value?.let { CompiledColor.Exact(it) } }

    var grassColor: Int?
        get() = specToDefaultRgb(grassColorSpec)
        set(value) { grassColorSpec = value?.let { CompiledColor.Exact(it) } }

    var foliageColor: Int?
        get() = specToDefaultRgb(foliageColorSpec)
        set(value) { foliageColorSpec = value?.let { CompiledColor.Exact(it) } }

    var ambientColor: Int?
        get() = specToDefaultRgb(ambientColorSpec)
        set(value) { ambientColorSpec = value?.let { CompiledColor.Exact(it) } }

    var cloudColor: Int?
        get() = specToDefaultRgb(cloudColorSpec)
        set(value) { cloudColorSpec = value?.let { CompiledColor.Exact(it) } }

    var fireLavaColor: Int?
        get() = specToDefaultRgb(fireLavaColorSpec)
        set(value) { fireLavaColorSpec = value?.let { CompiledColor.Exact(it) } }

    private fun specToDefaultRgb(spec: CompiledColor?): Int? = when (spec) {
        is CompiledColor.Exact -> spec.rgb
        is CompiledColor.Preset -> spec.category.defaultRgb
        null -> null
    }
}

/**
 * Implements the ordered symbol grammar used by Descriptive Books.
 *
 * Modifier pages are buffered until a target page consumes them. Exclusive categories collect
 * every candidate so the last page can win while earlier conflicts still add instability. The
 * compiler has no world or save-file side effects, which keeps previewing and unit testing cheap.
 */
object AgeCompiler {
    fun compile(symbols: List<String>, rand: kotlin.random.Random = kotlin.random.Random.Default): CompiledAgeData {
        val data = CompiledAgeData()
        
        val pendingColors = mutableListOf<CompiledColor>()
        val ageEffectCandidates = mutableListOf<String>()
        var ageEffectTargetCount = 0
        
        val terrains = mutableListOf<String>()
        val times = mutableListOf<String>()
        val weathers = mutableListOf<String>()
        val biomeControllers = mutableListOf<String>()
        
        for (symbol in symbols) {
            var clean = symbol.lowercase().replace("mystcraft-reforged:", "")
            
            if (clean == "random") {
                val wildcards = listOf(
                    "terrain_floating_islands", "terrain_amplified", "terrain_alpha", "terrain_beta", "terrain_caves", "terrain_flat",
                    "terrain_biospheres", "terrain_cities", "terrain_nether", "terrain_end",
                    "time_fast", "time_fixed", 
                    "weather_storm", "weather_rain", "weather_normal",
                    "biome_checkerboard", "biome_vanilla", "low_gravity",
                    HistoricAgeThemes.COLLAPSED_OBSERVATORY, HistoricAgeThemes.ANCIENT_AQUEDUCTS, HistoricAgeThemes.GATEWAY_RUINS,
                    AmbientAgeThemes.PAGE_STORMS, AmbientAgeThemes.MEMORY_BLOOMS, AmbientAgeThemes.STABLE_SANCTUARIES
                ) + ChaosAgeThemes.SKY + listOf(
                    ChaosAgeThemes.METEOR_SHOWERS, ChaosAgeThemes.SKY_SPHERES,
                    ChaosAgeThemes.PARTICLE_MOTES, ChaosAgeThemes.PARTICLE_ASH, ChaosAgeThemes.PARTICLE_SPORES, ChaosAgeThemes.PARTICLE_VOID
                )
                clean = if (rand.nextFloat() < 0.01f) "terrain_void" else wildcards.random(rand)
                data.conflictInstability += 5
            }
            
            // 1. Parse Custom Hex Color from Anvil ("color_custom:#FF00AA")
            if (clean.startsWith("color_custom:#")) {
                val hex = clean.substringAfter("#")
                try {
                    pendingColors.add(CompiledColor.Exact(hex.toInt(16)))
                } catch (e: Exception) {
                    data.conflictInstability += 10
                }
                continue
            }
            
            when {
                // === COLORS (Modifiers) ===
                clean == "color_orange" -> pendingColors.add(CompiledColor.Preset(ColorCategory.ORANGE))
                clean == "color_cyan" -> pendingColors.add(CompiledColor.Preset(ColorCategory.CYAN))
                clean == "color_teal" -> pendingColors.add(CompiledColor.Preset(ColorCategory.TEAL))
                clean == "color_pink" -> pendingColors.add(CompiledColor.Preset(ColorCategory.PINK))
                clean == "color_magenta" -> pendingColors.add(CompiledColor.Preset(ColorCategory.MAGENTA))
                clean == "color_lime" -> pendingColors.add(CompiledColor.Preset(ColorCategory.LIME))
                clean == "color_brown" -> pendingColors.add(CompiledColor.Preset(ColorCategory.BROWN))
                clean == "color_gray" -> pendingColors.add(CompiledColor.Preset(ColorCategory.GRAY))
                clean == "color_light_blue" -> pendingColors.add(CompiledColor.Preset(ColorCategory.LIGHT_BLUE))
                clean.contains("red") -> pendingColors.add(CompiledColor.Preset(ColorCategory.RED))
                clean.contains("blue") -> pendingColors.add(CompiledColor.Preset(ColorCategory.BLUE))
                clean.contains("green") -> pendingColors.add(CompiledColor.Preset(ColorCategory.GREEN))
                clean.contains("black") -> pendingColors.add(CompiledColor.Preset(ColorCategory.BLACK))
                clean.contains("white") -> pendingColors.add(CompiledColor.Preset(ColorCategory.WHITE))
                clean.contains("yellow") -> pendingColors.add(CompiledColor.Preset(ColorCategory.YELLOW))
                clean.contains("purple") -> pendingColors.add(CompiledColor.Preset(ColorCategory.PURPLE))
                
                // === TARGETS (They "consume" the colors in memory) ===
                clean.contains("color_sky") -> { 
                    data.skyColorSpec = pendingColors.lastOrNull()
                    if (pendingColors.size > 1) data.conflictInstability += (pendingColors.size - 1) * 10
                    pendingColors.clear() 
                }
                clean.contains("color_fog") -> { 
                    data.fogColorSpec = pendingColors.lastOrNull()
                    if (pendingColors.size > 1) data.conflictInstability += (pendingColors.size - 1) * 10
                    pendingColors.clear() 
                }
                clean.contains("color_water") -> { data.waterColorSpec = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_grass") -> { data.grassColorSpec = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_foliage") -> { data.foliageColorSpec = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_ambient") -> { data.ambientColorSpec = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_cloud") -> { data.cloudColorSpec = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("color_fire_lava") -> { data.fireLavaColorSpec = pendingColors.lastOrNull(); pendingColors.clear() }
                clean.contains("cloud_height_low") -> data.cloudHeight = 96.0f
                clean.contains("cloud_height_normal") -> data.cloudHeight = 192.0f
                clean.contains("cloud_height_high") -> data.cloudHeight = 256.0f

                // === AGE EFFECTS ===
                clean == "age_effect" -> {
                    ageEffectTargetCount++
                }
                
                // === TERRAIN TYPES ===
                clean == "terrain_floating_islands" -> terrains.add("FLOATING_ISLANDS")
                clean == "terrain_amplified" -> terrains.add("AMPLIFIED")
                clean == "terrain_alpha" -> terrains.add("ALPHA")
                clean == "terrain_beta" -> terrains.add("BETA")
                clean == "terrain_cave" || clean == "terrain_caves" -> terrains.add("CAVES")
                clean == "terrain_flat" -> terrains.add("FLAT")
                clean == "terrain_void" -> terrains.add("VOID")
                clean == "terrain_biospheres" -> terrains.add("BIOSPHERES")
                clean == "terrain_cities" -> terrains.add("CITIES")
                clean == "terrain_nether" -> terrains.add("NETHER")
                clean == "terrain_end" -> terrains.add("END")
                
                // === BIOME CONTROLLERS ===
                clean == "biome_vanilla" || clean == "biome_vanilla_distribution" -> biomeControllers.add("VANILLA_DISTRIBUTION")
                clean == "biome_checkerboard" -> biomeControllers.add("CHECKERBOARD")
                clean == "biome_single" || clean == "biome_tiled" -> biomeControllers.add("SINGLE")
                
                // === WEATHER ===
                clean == "weather_rain" || clean == "weather_endless_rain" -> weathers.add("endless_rain")
                clean == "weather_storm" || clean == "weather_endless_storm" -> weathers.add("endless_storm")
                clean == "weather_normal" -> weathers.add("normal")
                clean == "weather_none" || clean == "weather_no_weather" -> weathers.add("no_weather")
                
                // === TIME ===
                clean.startsWith("time_fixed") -> times.add("fixed:${clean.substringAfter(":", "")}")
                clean == "time_fast" -> times.add("fast")
                clean == "time_slow" -> times.add("slow")
                clean == "time_normal" -> times.add("normal")

                // === OTHER ===
                clean == "low_gravity" -> data.lowGravity = true

                else -> {
                    if (isProfileOnlySymbol(clean)) {
                        // Parsed by AgeProfileManager; it is intentionally not part of the
                        // compiler's exclusive terrain/biome/color grammar.
                    } else if (isStatusEffectId(clean)) {
                        ageEffectCandidates.add(clean)
                    } else if (clean.startsWith("biome_") || ModSymbols.isBiomeSymbol(Identifier.tryParse(clean) ?: Identifier("minecraft", "plains"))) {
                        var biomeId = clean.replace("biome_", "")
                        if (!biomeId.contains(":")) biomeId = "minecraft:$biomeId"
                        data.biomes.add(biomeId)
                    }
                }
            }
        }
        
        if (pendingColors.isNotEmpty()) {
            data.conflictInstability += pendingColors.size * 25
        }

        if (ageEffectTargetCount > 0) {
            val selectedEffect = ageEffectCandidates.lastOrNull()
            if (selectedEffect == null) {
                data.conflictInstability += ageEffectTargetCount * 20
            } else {
                if (ageEffectCandidates.distinct().size > 1) {
                    data.conflictInstability += (ageEffectCandidates.distinct().size - 1) * 12
                }
                if (ageEffectTargetCount > 1) {
                    data.conflictInstability += (ageEffectTargetCount - 1) * 8
                }
                data.ageEffectId = selectedEffect
            }
        } else if (ageEffectCandidates.isNotEmpty()) {
            data.conflictInstability += ageEffectCandidates.size * 15
        }
        
        if (terrains.isNotEmpty()) {
            if (terrains.distinct().size > 1) {
                data.conflictInstability += (terrains.distinct().size - 1) * 30
            }
            data.terrainType = terrains.last()
        }

        if (biomeControllers.isNotEmpty()) {
            if (biomeControllers.distinct().size > 1) {
                data.conflictInstability += 20
            }
            data.biomeController = biomeControllers.last()
        }
        
        if (weathers.isNotEmpty()) {
            if (weathers.distinct().size > 1) {
                data.conflictInstability += (weathers.distinct().size - 1) * 15
            }
            data.weatherMode = weathers.last()
        }
        
        val timeModes = times.map { it.substringBefore(":") }
        val fastCount = timeModes.count { it == "fast" }
        val slowCount = timeModes.count { it == "slow" }
        
        if (times.isNotEmpty()) {
            val distinctTimes = timeModes.distinct()
            if (distinctTimes.size > 1) {
                data.conflictInstability += (distinctTimes.size - 1) * 20
            }
            if (fastCount > 0 && slowCount > 0) {
                data.conflictInstability += 50
            }

            data.timeMode = timeModes.last()
            data.fixedTimeOfDay = times.last()
                .substringAfter(":", "")
                .takeIf { it.isNotBlank() }
                ?.toLongOrNull()
            when (data.timeMode) {
                "fast" -> {
                    data.timeScaleMultiplier = fastCount.toFloat()
                    if (fastCount > 1) data.conflictInstability += fastCount * 15
                }
                "slow" -> {
                    data.timeScaleMultiplier = 1.0f / slowCount.toFloat()
                    if (slowCount > 1) data.conflictInstability += slowCount * 15
                }
            }
        }
        
        return data
    }

    private fun isProfileOnlySymbol(symbol: String): Boolean =
        symbol in PROFILE_ONLY_SYMBOLS ||
            symbol in HistoricAgeThemes.ALL ||
            symbol in AmbientAgeThemes.ALL ||
            symbol in ExoticAgeThemes.ALL ||
            symbol in ChaosAgeThemes.ALL

    private fun isStatusEffectId(symbol: String): Boolean {
        val id = Identifier.tryParse(symbol) ?: return false
        return Registries.STATUS_EFFECT.containsId(id)
    }

    private val PROFILE_ONLY_SYMBOLS = setOf(
        "dense_ores", "giant_trees", "crystal_formations", "tendrils", "obelisks",
        "spawning_no_mobs", "spawning_extra_hostile", "spawning_extra_passive",
        "sun_normal", "sun_red", "sun_blue", "moon_normal", "moon_extra",
        "stars_normal", "stars_dense", "no_stars"
    )
}
