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
    var pagePools: PagePoolBalance = PagePoolBalance(),
    var paradoxReaper: ParadoxReaperBalance = ParadoxReaperBalance()
) {
    fun normalized(): MystcraftBalanceConfig = copy(
        instability = instability.normalized(),
        worldGeneration = worldGeneration.normalized(),
        loot = loot.normalized(),
        pagePools = pagePools.normalized(),
        paradoxReaper = paradoxReaper.normalized()
    )
}

/**
 * Population controls for the Paradox Reaper, the hunter unstable Ages send after players.
 *
 * Reapers are gated on instability rather than on light level or biome: they are a symptom of a
 * badly written Age, so a clean Age should never produce one no matter how dark it gets.
 */
data class ParadoxReaperBalance(
    var enabled: Boolean = true,
    /** Whether Reapers may see, hear, pursue, and naturally spawn around creative players. */
    var targetCreativePlayers: Boolean = false,
    /** No Reapers below this instability score. Defaults to the existing mild-decay threshold. */
    var minimumInstabilityScore: Int = 45,
    /** Scales the sprint-matching base speed. 1.0 keeps pace with a sprinting player exactly. */
    var speedMultiplier: Float = 1.0f,
    var maxAlivePerPlayer: Int = 1,
    var spawnChancePerSecond: Float = 0.01f,
    var spawnChancePerInstabilityPoint: Float = 0.0003f,
    /** Minimum time after a successful natural spawn before that player can roll another. */
    var spawnCooldownSeconds: Int = 90,
    /** Time after entering or respawning in an Age before Reapers may notice the player. */
    var arrivalGraceSeconds: Int = 45,
    var minSpawnDistance: Int = 24,
    var maxSpawnDistance: Int = 42,
    /** Reapers further than this from every player are culled to keep the hunt local. */
    var despawnDistance: Int = 72,
    /**
     * Whether Reapers shatter thin glass to get at a target. Membership is the block tag
     * `mystcraft-reforged:reaper_breakable`, which ships with the vanilla glass panes only:
     * full glass blocks are deliberately excluded as too thick to worry a Reaper.
     */
    var canBreakGlassPanes: Boolean = true,
    var paneBreakTicks: Int = 24,
    /**
     * Greater Reapers screech for lesser backup on two occasions: when a hunt begins, and when
     * they drop below 40% health. Each occasion is one roll at [summonChance]. Lesser forms
     * never summon, and a greater form cannot screech again while its backup is still alive.
     */
    var canSummonBackup: Boolean = true,
    var summonChance: Float = 0.2f,
    var summonMinimum: Int = 1,
    var summonMaximum: Int = 2,
    /**
     * Multiplies the spawn rate at spots walled by white or black decay, and makes those spots
     * the preferred pick. Decay is where Reapers belong, so a rotting Age produces them out of
     * the rot rather than at random.
     */
    var decayChanceMultiplier: Float = 2.0f,

    // --- senses -------------------------------------------------------------------------
    /** How far a Reaper hears. Listens on the Warden's own game-event tag. */
    var hearingRange: Int = 24,
    /** Ticks between sight checks while dormant. Long, so a careful player can slip past. */
    var dormantScanIntervalTicks: Int = 30,
    /** Sight range while dormant, in blocks. Deliberately short. */
    var dormantSightRange: Double = 10.0,
    /** Sight range once alerted. */
    var alertSightRange: Double = 26.0,
    /** Multiplies sight range against a crouching player. */
    var sneakSightMultiplier: Double = 0.4,
    /** How far an alert propagates to other Reapers. */
    var alertShareRadius: Int = 28,
    /**
     * Radius, in blocks, inside which any living thing making noise is worth getting up for.
     *
     * Separate from [hearingRange] on purpose. A Reaper hears a long way, but its business is the
     * player; one that abandoned its roost for every distant animal would never be where it
     * settled. Close in, nothing gets a pass for not being the player.
     */
    var neighbourHearingRadius: Double = 10.0,

    // --- pursuit speed ------------------------------------------------------------------
    /**
     * Track the hunted player's own sprint speed instead of a fixed vanilla figure. Matters in
     * packs where origins, levels, or gear move the movement-speed attribute: without it a fast
     * build simply outruns the one mob that is not supposed to be outrunnable.
     */
    var matchTargetSpeed: Boolean = true,
    /**
     * Fraction of the target's sprint to chase at. Just under 1 on purpose, so investing in speed
     * still buys a real escape on open ground without the chase feeling like rubber-banding.
     */
    var targetSpeedFraction: Double = 0.93,
    /** Ceiling on the above, as a multiple of base speed, so an outlier build cannot make a blur. */
    var maxTargetSpeedMultiplier: Double = 1.6,
    /**
     * Read the target's speed once, when the hunt begins, instead of every tick.
     *
     * This is what keeps speed potions useful in a pack full of permanent movement buffs. Track
     * the live value and a player who already runs at Speed II is simply chased at Speed II, so
     * drinking Speed II gains them nothing and the whole category of consumable is dead weight
     * for exactly the builds carrying a permanent buff. Against a snapshot, permanent speed is
     * priced in once and anything applied after the hunt starts opens a real gap.
     */
    var lockSpeedAtAcquisition: Boolean = true,

    // --- traversal ----------------------------------------------------------------------
    /** Whether Reapers may jump gaps they cannot crawl around. */
    var canJumpGaps: Boolean = true,
    /** Furthest gap a Reaper will attempt, in blocks. */
    var maxJumpBlocks: Int = 5,
    /** Launch angle off the surface plane, in degrees. Measured against the clinging normal. */
    var jumpAngleDegrees: Double = 38.0,
    var jumpCooldownTicks: Int = 50
) {
    fun normalized(): ParadoxReaperBalance {
        val minimum = minSpawnDistance.coerceIn(2, 128)
        val summonFloor = summonMinimum.coerceIn(0, 16)
        return copy(
            paneBreakTicks = paneBreakTicks.coerceIn(1, 600),
            summonChance = summonChance.coerceIn(0f, 1f),
            summonMinimum = summonFloor,
            summonMaximum = summonMaximum.coerceIn(summonFloor, 16),
            decayChanceMultiplier = decayChanceMultiplier.coerceIn(1f, 50f),
            hearingRange = hearingRange.coerceIn(1, 128),
            dormantScanIntervalTicks = dormantScanIntervalTicks.coerceIn(1, 200),
            dormantSightRange = dormantSightRange.coerceIn(0.0, 128.0),
            alertSightRange = alertSightRange.coerceIn(0.0, 128.0),
            sneakSightMultiplier = sneakSightMultiplier.coerceIn(0.0, 1.0),
            alertShareRadius = alertShareRadius.coerceIn(0, 128),
            neighbourHearingRadius = neighbourHearingRadius.coerceIn(0.0, 64.0),
            targetSpeedFraction = targetSpeedFraction.coerceIn(0.1, 2.0),
            maxJumpBlocks = maxJumpBlocks.coerceIn(2, 24),
            jumpAngleDegrees = jumpAngleDegrees.coerceIn(5.0, 85.0),
            jumpCooldownTicks = jumpCooldownTicks.coerceIn(0, 600),
            maxTargetSpeedMultiplier = maxTargetSpeedMultiplier.coerceAtLeast(1.0),
            minimumInstabilityScore = minimumInstabilityScore.coerceIn(0, 1000),
            speedMultiplier = speedMultiplier.coerceIn(0.1f, 4f),
            maxAlivePerPlayer = maxAlivePerPlayer.coerceIn(0, 32),
            spawnChancePerSecond = spawnChancePerSecond.coerceIn(0f, 1f),
            spawnChancePerInstabilityPoint = spawnChancePerInstabilityPoint.coerceIn(0f, 1f),
            spawnCooldownSeconds = spawnCooldownSeconds.coerceIn(0, 3600),
            arrivalGraceSeconds = arrivalGraceSeconds.coerceIn(0, 600),
            minSpawnDistance = minimum,
            maxSpawnDistance = maxSpawnDistance.coerceIn(minimum + 1, 256),
            despawnDistance = despawnDistance.coerceIn(16, 512)
        )
    }
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
