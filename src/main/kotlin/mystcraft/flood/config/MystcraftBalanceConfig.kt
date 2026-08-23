package mystcraft.flood.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mystcraft.flood.MystcraftReforged
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.max

data class MystcraftBalanceConfig(
    var instability: InstabilityBalance = InstabilityBalance(),
    var worldGeneration: WorldGenerationBalance = WorldGenerationBalance(),
    var loot: LootBalance = LootBalance(),
    var pagePools: PagePoolBalance = PagePoolBalance()
) {
    fun normalized(): MystcraftBalanceConfig = copy(
        instability = instability.normalized(),
        worldGeneration = worldGeneration.normalized(),
        loot = loot.normalized(),
        pagePools = pagePools.normalized()
    )
}

data class InstabilityBalance(
    var overworldFunctionsMaxScore: Int = 25,
    var mildThreshold: Int = 45,
    var moderateThreshold: Int = 65,
    var severeThreshold: Int = 80,
    var worldEaterThreshold: Int = 100,
    var criticalThreshold: Int = 115,
    var mildChancePerSecond: Float = 0.05f,
    var moderateChancePerSecond: Float = 0.03f,
    var severeChancePerSecond: Float = 0.02f,
    var worldEaterBaseChancePerSecond: Float = 0.05f,
    var worldEaterChancePerPoint: Float = 0.01f,
    var criticalChancePerSecond: Float = 0.01f,
    var mildDurationTicks: Int = 200,
    var darknessDurationTicks: Int = 160,
    var poisonDurationTicks: Int = 100
) {
    fun normalized(): InstabilityBalance {
        val mild = mildThreshold.coerceAtLeast(1)
        val moderate = max(mild + 1, moderateThreshold)
        val severe = max(moderate + 1, severeThreshold)
        val eater = max(severe + 1, worldEaterThreshold)
        val critical = max(eater + 1, criticalThreshold)
        return copy(
            overworldFunctionsMaxScore = overworldFunctionsMaxScore.coerceIn(0, mild - 1),
            mildThreshold = mild,
            moderateThreshold = moderate,
            severeThreshold = severe,
            worldEaterThreshold = eater,
            criticalThreshold = critical,
            mildChancePerSecond = mildChancePerSecond.coerceIn(0f, 1f),
            moderateChancePerSecond = moderateChancePerSecond.coerceIn(0f, 1f),
            severeChancePerSecond = severeChancePerSecond.coerceIn(0f, 1f),
            worldEaterBaseChancePerSecond = worldEaterBaseChancePerSecond.coerceIn(0f, 1f),
            worldEaterChancePerPoint = worldEaterChancePerPoint.coerceIn(0f, 1f),
            criticalChancePerSecond = criticalChancePerSecond.coerceIn(0f, 1f),
            mildDurationTicks = mildDurationTicks.coerceIn(20, 12_000),
            darknessDurationTicks = darknessDurationTicks.coerceIn(20, 12_000),
            poisonDurationTicks = poisonDurationTicks.coerceIn(20, 12_000)
        )
    }
}

data class WorldGenerationBalance(
    var featureSpawnRateMultiplier: Float = 1.0f,
    var pageFeatureSpawnRateMultiplier: Float = 1.0f,
    var caveGlowLichenAttemptsPerChunk: Int = 24
) {
    fun normalized() = copy(
        featureSpawnRateMultiplier = featureSpawnRateMultiplier.coerceIn(0f, 10f),
        pageFeatureSpawnRateMultiplier = pageFeatureSpawnRateMultiplier.coerceIn(0f, 10f),
        caveGlowLichenAttemptsPerChunk = caveGlowLichenAttemptsPerChunk.coerceIn(0, 256)
    )
}

data class LootBalance(
    var vanillaChestPageChance: Float = 1.0f,
    var vanillaChestMinPages: Int = 2,
    var vanillaChestMaxPages: Int = 5,
    var ageFeatureChestPageChance: Float = 1.0f,
    var ageFeaturePageCountMultiplier: Float = 1.0f
) {
    fun normalized(): LootBalance {
        val minimum = vanillaChestMinPages.coerceIn(0, 64)
        return copy(
            vanillaChestPageChance = vanillaChestPageChance.coerceIn(0f, 1f),
            vanillaChestMinPages = minimum,
            vanillaChestMaxPages = vanillaChestMaxPages.coerceIn(minimum, 64),
            ageFeatureChestPageChance = ageFeatureChestPageChance.coerceIn(0f, 1f),
            ageFeaturePageCountMultiplier = ageFeaturePageCountMultiplier.coerceIn(0f, 10f)
        )
    }
}

data class PagePoolBalance(
    var blacklistedBiomeNamespaces: MutableList<String> = mutableListOf(),
    var blacklistedBiomePages: MutableList<String> = mutableListOf(),
    var blacklistedTerrainPages: MutableList<String> = mutableListOf(),
    var blacklistedSymbolPages: MutableList<String> = mutableListOf()
) {
    fun normalized() = copy(
        blacklistedBiomeNamespaces = normalizeEntries(blacklistedBiomeNamespaces),
        blacklistedBiomePages = normalizeEntries(blacklistedBiomePages),
        blacklistedTerrainPages = normalizeEntries(blacklistedTerrainPages),
        blacklistedSymbolPages = normalizeEntries(blacklistedSymbolPages)
    )

    fun allowsBiome(id: Identifier): Boolean =
        id.namespace !in blacklistedBiomeNamespaces && !matches(id, blacklistedBiomePages)

    fun allowsTerrainSymbol(id: Identifier): Boolean = !matches(id, blacklistedTerrainPages)

    fun allowsSymbol(id: Identifier, isBiome: Boolean): Boolean =
        !matches(id, blacklistedSymbolPages) &&
            (!isBiome || allowsBiome(id)) &&
            (!id.path.startsWith("terrain_") || allowsTerrainSymbol(id))

    private fun matches(id: Identifier, entries: List<String>): Boolean =
        id.toString() in entries || id.path in entries

    private fun normalizeEntries(values: List<String>): MutableList<String> = values
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::lowercase)
        .distinct()
        .sorted()
        .toMutableList()
}

object MystcraftConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path
        get() = FabricLoader.getInstance().configDir.resolve("mystcraft-reforged-balance.json")

    @Volatile
    var current: MystcraftBalanceConfig = MystcraftBalanceConfig()
        private set

    fun load() {
        current = try {
            if (Files.exists(configPath)) {
                val merged = gson.toJsonTree(MystcraftBalanceConfig()).asJsonObject
                mergeOntoDefaults(merged, JsonParser.parseString(Files.readString(configPath)).asJsonObject)
                gson.fromJson(merged, MystcraftBalanceConfig::class.java).normalized()
            } else {
                MystcraftBalanceConfig()
            }
        } catch (error: Exception) {
            MystcraftReforged.LOGGER.error("Could not read {}; using defaults", configPath, error)
            MystcraftBalanceConfig()
        }
        save()
    }

    private fun mergeOntoDefaults(defaults: JsonObject, loaded: JsonObject) {
        for ((key, value) in loaded.entrySet()) {
            val defaultValue = defaults.get(key)
            if (value.isJsonObject && defaultValue?.isJsonObject == true) {
                mergeOntoDefaults(defaultValue.asJsonObject, value.asJsonObject)
            } else {
                defaults.add(key, value.deepCopy())
            }
        }
    }

    @Synchronized
    fun replace(config: MystcraftBalanceConfig) {
        current = config.normalized()
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
}
