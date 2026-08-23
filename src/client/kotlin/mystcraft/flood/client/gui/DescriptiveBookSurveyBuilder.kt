package mystcraft.flood.client.gui

import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.generation.AgeCompiler
import mystcraft.flood.generation.AgeCurseManager
import mystcraft.flood.generation.ChaosAgeThemes
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.BiomeMode
import mystcraft.flood.generation.profile.TerrainTuningProfile
import mystcraft.flood.generation.profile.TerrainType
import mystcraft.flood.item.TerrainTuningBookData
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import kotlin.math.abs

/** One already-formatted page in the read-only Descriptive Book survey. */
data class DescriptiveBookSurveyPage(
    val title: String,
    val paragraphs: List<String>
)

/** View model consumed by [DescriptiveBookScreen]; it contains no mutable game state. */
data class DescriptiveBookSurvey(
    val title: String,
    val subtitle: String,
    val coverParagraphs: List<String>,
    val canActivate: Boolean,
    val panelTopColor: Int,
    val panelBottomColor: Int,
    val pages: List<DescriptiveBookSurveyPage>
)

/**
 * Translates book NBT plus an optional synced [AgeProfile] into human-readable survey pages.
 *
 * Unlinked books are previewed through [AgeCompiler]; linked books prefer the server-authored
 * client-cache snapshot. Keeping this translation outside the screen prevents rendering code
 * from duplicating grammar and profile interpretation.
 */
object DescriptiveBookSurveyBuilder {
    fun build(stack: ItemStack): DescriptiveBookSurvey {
        val ageId = stack.nbt?.getString("Age_ID")?.let(Identifier::tryParse)
        val symbols = extractSymbols(stack)

        if (ageId != null) {
            val profile = ClientAgeCache.getProperties(ageId)
            if (profile != null) {
                return linkedSurvey(extractDisplayName(stack, profile), ageId, profile, symbols)
            }

            return unresolvedLinkedSurvey(extractDisplayName(stack), ageId, symbols)
        }

        if (symbols.isNotEmpty()) {
            return draftSurvey(extractDisplayName(stack), symbols, stack)
        }

        return blankSurvey(extractDisplayName(stack), stack)
    }

    private fun linkedSurvey(
        displayName: String,
        ageId: Identifier,
        profile: AgeProfile,
        symbols: List<String>
    ): DescriptiveBookSurvey {
        val sacrificed = profile.ageState.isSacrificed
        val tuning = profile.terrainTuning
        val activeCurses = AgeCurseManager.refresh(profile)
        val title = displayName.ifBlank { prettifyPath(ageId.path) }
        val subtitle = when {
            sacrificed -> "A spent and sacrificed Age"
            profile.stability.isStable -> "A linked Age of uncommon stillness"
            else -> "A linked Age under strain"
        }

        val coverParagraphs = buildList {
            add("This volume is bound to ${prettifyPath(ageId.path)}.")
            add("Its measured instability stands at ${profile.stability.instabilityScore}, and the present anchor lies ${formatAnchor(profile)}.")
            if (activeCurses.isNotEmpty()) {
                add("The survey names ${activeCurses.size} removable curse${if (activeCurses.size == 1) "" else "s"} still caught in the script.")
            }
            if (tuning.isEdited()) {
                add("Some part of the script was not left to chance. A scribe laid a firmer hand upon the land before first opening.")
            }
            if (sacrificed) {
                add("The leaves have been spent in sacrifice. The panel is dead, and no wise traveler should expect passage.")
            } else {
                add("Touch the panel to cross the threshold. Turn the leaves to read the fuller survey set down by the last careful wayfarer.")
            }
        }

        val pages = listOf(
            DescriptiveBookSurveyPage(
                "The Lay Of The Land",
                listOf(
                    "The country rises in the manner of ${terrainDescription(profile.terrainType)}.",
                    "Its regions are arranged as ${biomeModeDescription(profile.biomes.mode)}.",
                    biomeParagraph(profile),
                    "The cloud shelf hangs near ${profile.cloudHeight.toInt()} blocks above the stone, and the safest known arrival point is ${formatAnchor(profile)}."
                )
            ),
            DescriptiveBookSurveyPage(
                "Waters And Verdure",
                listOf(
                    "The waters take the hue ${formatColor(profile.colors.water)}, while the grasses answer in ${formatColor(profile.colors.grass)}.",
                    "Leaf and vine growth settle into ${formatColor(profile.colors.foliage)}, with the ambient wash leaning toward ${formatColor(profile.colors.ambient)}.",
                    "Fire and molten stone burn in ${formatColor(profile.colors.fireLava)}, a small thing to note until one must flee through it."
                )
            ),
            DescriptiveBookSurveyPage(
                "The Heavens",
                listOf(
                    celestialParagraph(profile),
                    timeParagraph(profile),
                    "The upper sky is stained ${formatColor(profile.colors.sky)}, while the higher banks of cloud are colored ${formatColor(profile.colors.cloud)}."
                )
            ),
            DescriptiveBookSurveyPage(
                "Sky Anomalies",
                skyAnomalyParagraphs(profile)
            ),
            DescriptiveBookSurveyPage(
                "Omens And Drift",
                buildList {
                    addAll(terrainPersonalityParagraphs(profile))
                    addAll(particlePersonalityParagraphs(profile))
                    if (isEmpty()) {
                        add("No unusual omens or drifting particles were recorded beyond the Age's ordinary weather.")
                    }
                }
            ),
            DescriptiveBookSurveyPage(
                "The Air And Season",
                listOf(
                    "The mists gather in ${formatColor(profile.colors.fog)}.",
                    weatherParagraph(profile),
                    "At the time of this reading, the air is ${currentWeatherParagraph(profile)}."
                )
            ),
            DescriptiveBookSurveyPage(
                "Life, Weight, And Enchantment",
                listOf(
                    spawnParagraph(profile),
                    gravityParagraph(profile),
                    effectParagraph(profile)
                )
            )
        ) + curseSurveyPages(profile) + listOf(
            DescriptiveBookSurveyPage(
                "The Scribe's Ledger",
                buildList {
                    add("Archivist path: ${ageId}.")
                    add("World-seed cipher: ${profile.seed}.")
                    add("Stability effects are ${if (profile.stability.effectsEnabled) "awake" else "bound and dormant"}." )
                    addAll(tuningParagraphs(tuning))
                    add(modifierParagraph(profile))
                    if (symbols.isNotEmpty()) {
                        add("Written leaves: ${summarizeList(symbols.map(::prettifySymbol), 8)}.")
                    }
                    if (profile.ageState.isSacrificed) {
                        add("Sacrifice record: ${sacrificeParagraph(profile)}.")
                    }
                }
            )
        )

        val panelTop = if (sacrificed) 0xFF2B2430.toInt() else withAlpha(profile.colors.sky)
        val panelBottom = if (sacrificed) 0xFF544B5A.toInt() else withAlpha(profile.colors.fog)
        return DescriptiveBookSurvey(title, subtitle, coverParagraphs, !sacrificed, panelTop, panelBottom, pages)
    }

    private fun unresolvedLinkedSurvey(displayName: String, ageId: Identifier, symbols: List<String>): DescriptiveBookSurvey {
        val title = displayName.ifBlank { prettifyPath(ageId.path) }
        return DescriptiveBookSurvey(
            title = title,
            subtitle = "A linked Age awaiting a fresh survey",
            coverParagraphs = buildList {
                add("This book is tied to ${prettifyPath(ageId.path)}, but its fuller survey has not yet reached the reader.")
                add("The panel should still answer. Turn the leaves for the written symbols that shaped it.")
            },
            canActivate = true,
            panelTopColor = 0xFF1A2744.toInt(),
            panelBottomColor = 0xFF224F6A.toInt(),
            pages = listOf(
                DescriptiveBookSurveyPage(
                    "The Known Leaves",
                    listOf(
                        if (symbols.isEmpty()) "No symbol list was preserved in this copy." else "Written leaves: ${summarizeList(symbols.map(::prettifySymbol), 12)}.",
                        "The rest must be learned by walking the place itself, or by opening the book again once the archivists have had time to take a proper measure."
                    )
                )
            )
        )
    }

    private fun draftSurvey(displayName: String, symbols: List<String>, stack: ItemStack): DescriptiveBookSurvey {
        val compiled = AgeCompiler.compile(symbols)
        val tuning = TerrainTuningBookData.get(stack)
        val draftSkySymbols = symbols
            .map { it.lowercase().substringAfter(':') }
            .filter { it in ChaosAgeThemes.SKY }
            .distinct()
        val title = displayName.ifBlank { "Unfixed Descriptive Book" }
        val unresolved = mutableListOf<String>()
        if (compiled.terrainType == null) unresolved += "terrain"
        if (compiled.biomes.isEmpty() && compiled.biomeController == null) unresolved += "regional pattern"
        if (compiled.timeMode == null) unresolved += "the passage of time"
        if (compiled.weatherMode == null) unresolved += "weather"
        if (
            compiled.skyColor == null &&
            compiled.fogColor == null &&
            compiled.waterColor == null &&
            compiled.grassColor == null &&
            compiled.foliageColor == null &&
            compiled.cloudColor == null
        ) {
            unresolved += "its pigments"
        }

        val coverParagraphs = buildList {
            add("This script is not yet fixed to a world, but the written leaves already whisper their intent.")
            if (tuning.isEdited()) {
                add("A deliberate hand has already pressed weights and measures into the pages, so the first opening will not be wholly left to chance.")
            }
            add("Touch the panel to make the first crossing. Turn the leaves to read what can be known before the Age is fully born.")
        }

        val draftPages = mutableListOf(
            DescriptiveBookSurveyPage(
                "What The Script Already Says",
                buildList {
                    add(terrainDraftParagraph(compiled.terrainType))
                    add(biomeDraftParagraph(compiled.biomeController, compiled.biomes))
                    add(timeDraftParagraph(compiled.timeMode, compiled.timeScaleMultiplier))
                    add(weatherDraftParagraph(compiled.weatherMode))
                }
            ),
            DescriptiveBookSurveyPage(
                "Pigments And Signs",
                buildList {
                    add(colorDraftParagraph("Sky", compiled.skyColor))
                    add(colorDraftParagraph("Fog", compiled.fogColor))
                    add(colorDraftParagraph("Water", compiled.waterColor))
                    add(colorDraftParagraph("Grass", compiled.grassColor))
                    add(colorDraftParagraph("Foliage", compiled.foliageColor))
                    if (compiled.cloudColor != null || compiled.cloudHeight != null) {
                        val colorNote = compiled.cloudColor?.let(::formatColor) ?: "the common shade"
                        val heightNote = compiled.cloudHeight?.toInt()?.let { "at roughly $it blocks" } ?: "at an ordinary height"
                        add("The clouds are marked to gather in $colorNote and to ride $heightNote.")
                    }
                    if (compiled.lowGravity) {
                        add("The leaves plainly call for a lightened world, one where a traveler's step would carry farther than it ought.")
                    }
                    val draftEffectId = compiled.ageEffectId
                    if (draftEffectId != null) {
                        add("An enchantment clings to the script: ${formatEffectName(draftEffectId)}.")
                    }
                }.filterNot { it.contains("not yet spoken for") && abs(it.length) > 200 }
            ),
            DescriptiveBookSurveyPage(
                "Leaves In Order",
                listOf(
                    "Written leaves: ${summarizeList(symbols.map(::prettifySymbol), 16)}.",
                    if (compiled.conflictInstability > 0) {
                        "The grammar pulls against itself in places. The draft already carries ${compiled.conflictInstability} points of discord before the Age is even first opened."
                    } else {
                        "The grammar reads cleanly enough so far, with no obvious strain between the written leaves."
                    }
                )
            )
        )

        if (unresolved.isNotEmpty()) {
            draftPages += DescriptiveBookSurveyPage(
                "What Is Yet Left To Chance",
                listOf(
                    "These matters remain unwritten and would be left to chance on first opening: ${summarizeList(unresolved, 6)}.",
                    "A first crossing would settle those silences into fact, after which the archivist's ledger could be read in full."
                )
            )
        }

        if (draftSkySymbols.isNotEmpty()) {
            draftPages += DescriptiveBookSurveyPage(
                "Sky Anomalies",
                listOf(
                    "The draft gives the sky its own strange vocabulary: ${summarizeList(draftSkySymbols.map(::prettifySymbol), 10)}.",
                    "Such pages are descriptive detail rather than empty noise. When written deliberately, they help the Age understand what sort of impossible sky it is meant to wear."
                )
            )
        }

        if (tuning.isEdited()) {
            draftPages += DescriptiveBookSurveyPage(
                "The Scribe's Hand",
                tuningParagraphs(tuning)
            )
        }

        val panelTop = withAlpha(compiled.skyColor ?: compiled.fogColor ?: 0x1A2744)
        val panelBottom = withAlpha(compiled.fogColor ?: compiled.skyColor ?: 0x224F6A)
        return DescriptiveBookSurvey(
            title = title,
            subtitle = "An unfinished draft, still warm with possibility",
            coverParagraphs = coverParagraphs,
            canActivate = symbols.isNotEmpty(),
            panelTopColor = panelTop,
            panelBottomColor = panelBottom,
            pages = draftPages
        )
    }

    private fun blankSurvey(displayName: String, stack: ItemStack = ItemStack.EMPTY): DescriptiveBookSurvey {
        val tuning = if (stack.isEmpty) TerrainTuningProfile() else TerrainTuningBookData.get(stack)
        val pages = mutableListOf(
            DescriptiveBookSurveyPage(
                "The Sealed Leaves",
                listOf(
                    "No field notes can yet be taken from this volume. Its land, sky, season, and hazards remain hidden behind the first unread crossing.",
                    "What lies in this book remains to be read."
                )
            )
        )
        if (tuning.isEdited()) {
            pages += DescriptiveBookSurveyPage(
                "The Scribe's Hand",
                tuningParagraphs(tuning)
            )
        }

        return DescriptiveBookSurvey(
            title = displayName.ifBlank { "Blank Descriptive Book" },
            subtitle = "A sealed volume whose country has not yet been read",
            coverParagraphs = buildList {
                add("The leaves give up nothing to the eye. Whatever country sleeps inside this binding has not yet been coaxed into the light.")
                if (tuning.isEdited()) {
                    add("Even so, a careful hand has already set weights, depths, and omissions into the script before the first reading.")
                }
                add("Touch the panel to make the first crossing. Only then will the book yield a proper survey of the realm it contains.")
            },
            canActivate = true,
            panelTopColor = 0xFF111111.toInt(),
            panelBottomColor = 0xFF111111.toInt(),
            pages = pages
        )
    }

    private fun biomeParagraph(profile: AgeProfile): String {
        if (profile.biomes.biomes.isEmpty()) {
            return "No single region was named in the script; the world follows a more natural spread of climates."
        }
        return "The named regions most clearly called out are ${summarizeList(profile.biomes.biomes.map { prettifyPath(it.biomeId) }, 6)}."
    }

    private fun celestialParagraph(profile: AgeProfile): String {
        val suns = buildList {
            if (profile.time.sunNormalCount > 0) add("${profile.time.sunNormalCount} common")
            if (profile.time.sunRedCount > 0) add("${profile.time.sunRedCount} red")
            if (profile.time.sunBlueCount > 0) add("${profile.time.sunBlueCount} blue")
        }.ifEmpty { listOf("no visible") }
        return "The sky carries ${summarizeList(suns, 3)} sun${if (suns.size == 1 && suns.first() == "no visible") "s" else ""}, ${profile.time.moonCount} moon${if (profile.time.moonCount == 1) "" else "s"}, and ${starDensityDescription(profile.time.starDensity)} stars."
    }

    private fun timeParagraph(profile: AgeProfile): String {
        val fixedTime = profile.time.fixedTime
        return if (fixedTime != null) {
            "The clock is nailed to tick $fixedTime, ${describeFixedTime(fixedTime)}."
        } else {
            "Time moves ${timeScaleDescription(profile.time.timeScale)}, and the heavenly bodies appear somewhat ${sizeDescription(profile.time.sunSize, profile.time.moonSize)} than common."
        }
    }

    private fun weatherParagraph(profile: AgeProfile): String = when {
        profile.weather.noWeather -> "No rain or storm is permitted here; the sky keeps its temper to itself."
        profile.weather.isEndlessStorm -> "Storm is the natural law of the place. Thunder is not a guest but a resident."
        profile.weather.isEndlessRain -> "Rain falls as though the heavens had forgotten how to stop."
        else -> "Weather follows a natural cycle, neither fixed clear nor chained to storm."
    }

    private fun currentWeatherParagraph(profile: AgeProfile): String = when {
        profile.weather.isCurrentlyThundering() -> "open thunder and hard weather"
        profile.weather.isCurrentlyRaining() -> "steady rain"
        profile.weather.noWeather -> "still and utterly dry"
        else -> "clear for the moment"
    }

    private fun spawnParagraph(profile: AgeProfile): String = when {
        profile.spawning.noMobs -> "No natural creatures are meant to take root here."
        profile.spawning.hostileMultiplier != 1.0f || profile.spawning.passiveMultiplier != 1.0f ->
            "Hostile life is set to ${formatMultiplier(profile.spawning.hostileMultiplier)}, while gentle life is set to ${formatMultiplier(profile.spawning.passiveMultiplier)}."
        else -> "Creature life follows ordinary measure, neither thinned nor made overbold."
    }

    private fun gravityParagraph(profile: AgeProfile): String =
        if (profile.physics.gravityScale < 1.0f) {
            "Weight is lightened here to roughly ${"%.2f".format(profile.physics.gravityScale)} of the common pull."
        } else {
            "The pull of the world feels ordinary underfoot."
        }

    private fun curseSurveyPages(profile: AgeProfile): List<DescriptiveBookSurveyPage> {
        val active = AgeCurseManager.refresh(profile)
        val cleansed = profile.curses.cleansedCurses.distinct()
        if (active.isEmpty() && cleansed.isEmpty()) return emptyList()

        return listOf(
            DescriptiveBookSurveyPage(
                "Curses And Cleanings",
                buildList {
                    if (active.isEmpty()) {
                        add("No removable curse is presently legible in the linked script.")
                    } else {
                        add("The Editing Table can lift one curse at a time when fed a Curse Cleansing page, ink, lapis, and amethyst.")
                        active.forEach { curse -> add(curse.description(profile)) }
                    }
                    if (cleansed.isNotEmpty()) {
                        add("Already lifted: ${summarizeList(cleansed.map { AgeCurseManager.labelFor(it, profile) }, 6)}.")
                    }
                }
            )
        )
    }

    private fun effectParagraph(profile: AgeProfile): String {
        val effectId = profile.ageEffect.effectId ?: return "No lingering enchantment was recorded in the air."
        val effectName = formatEffectName(effectId)
        return if (profile.ageEffect.enabled) {
            "A persistent working hangs over the place: $effectName."
        } else {
            "The script names $effectName, though its stronger effects are presently held in check."
        }
    }

    private fun modifierParagraph(profile: AgeProfile): String =
        if (profile.modifiers.isEmpty()) {
            "No great oddities were marked beyond the core script"
        } else {
            "Marked anomalies: ${summarizeList(profile.modifiers.map(::prettifySymbol), 10)}"
        }

    private fun skyAnomalyParagraphs(profile: AgeProfile): List<String> = buildList {
        if (profile.modifiers.contains(ChaosAgeThemes.BRIGHT_SKY)) {
            add("The whole vault of heaven is written bright, lending the Age a pale luminous cast.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.DARK_SKY)) {
            add("A darkening sign lies over the sky, making even ordinary colors feel deep and late.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SKY_RAINBOWS)) {
            add("Rainbow arcs are expected to hang in the upper air when the light catches correctly.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SKY_AURORAS)) {
            add("Auroral curtains move through the nightward sky.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SHOOTING_STARS)) {
            add("The stars are restless here, leaving brief silver scratches across the dark.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.COMETS)) {
            add("Slow comets cross the vault, bright-headed and trailing cold fire.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SKY_RIFTS)) {
            add("Long luminous wounds appear in the sky, as though the Age were showing its binding through the clouds.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SKY_NEBULAE)) {
            add("Soft colored nebulae gather in the high air like bruised light behind glass.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.ECLIPSE_HALOS)) {
            add("Cold eclipse rings are marked above, dim centers circled by pale blue fire.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.STAR_GLYPHS)) {
            add("Constellations here can lock into deliberate glyphs before loosening back into scattered stars.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.HORIZON_MIRAGES)) {
            add("Mirage bands shimmer low on the horizon, bending the edge of the world.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.CRYSTAL_HALOS)) {
            add("Angular crystal halos turn overhead with a faceted, impossible symmetry.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.VOID_FLECKS)) {
            add("Black flecks drift across the sky where small pieces of light seem to be missing.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SPIRAL_GALAXIES)) {
            add("Spiral galaxies burn close enough to feel painted onto the vault.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.FALLING_SKY_SHARDS)) {
            add("Glass-bright shards hang in descent, as if the sky is slowly shedding splinters.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.LIGHTNING_VEINS)) {
            add("Thin lightning veins crawl through the upper dark without needing a storm.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.LUMINOUS_COLUMNS)) {
            add("Faint luminous columns stand around the horizon like distant searchlights.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SKY_MONOLITHS)) {
            add("Deep blue monoliths, glass cubes, and tiny colored lights drift in a geometric night above the Age.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.PRISM_RINGS)) {
            add("Prismatic rings overlap in the distance, each turning with a slightly different color and pitch.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.CHROMA_WAVES)) {
            add("Bands of chromatic wave-light pass through the horizon like colored sound made visible.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.ORBITAL_GRID)) {
            add("A faint orbital lattice crosses the sky, as though another machine were measuring the heavens.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SKY_LANTERNS)) {
            add("Soft lantern lights drift high overhead, gathering and parting with no visible wind.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.FRACTURE_WEB)) {
            add("A web of fine fractures threads the sky, each crack catching color at its edge.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.DREAM_VEILS)) {
            add("Dream-veils wash over the vault in thin translucent sheets of changing color.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SKY_BUBBLES)) {
            add("Transparent bubbles rise through the high air, briefly lensing stars and clouds behind them.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.STARFALL_BLOOMS)) {
            add("Starfall blooms open in the dark: small bursts that blossom, fade, and leave no ash.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.HORIZON_CROWNS)) {
            add("Crown-like rays stand along the horizon, giving the far world a ceremonial edge.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.CELESTIAL_SCRIPT)) {
            add("Loose script strokes write themselves across the constellations before dissolving.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.GLASS_CONSTELLATIONS)) {
            add("Glass constellations join stars into panes and facets, bright lines over a darker vault.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.RADIANT_WHIRLPOOLS)) {
            add("Radiant whirlpools turn in the upper sky, drawing colored starlight into slow spirals.")
        }
        if (isEmpty()) {
            add("No dedicated sky anomalies were recorded beyond ordinary light, weather, and celestial motion.")
        }
    }

    private fun terrainPersonalityParagraphs(profile: AgeProfile): List<String> = buildList {
        if (profile.modifiers.contains(ChaosAgeThemes.METEOR_SHOWERS)) {
            add("Meteor scars may be found across the land: blackened bowls, hot cores, and fragments of stranger stone.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.SKY_SPHERES)) {
            add("Spheres of earth and glass drift high above the surface, some solid and some hollow around a hidden light.")
        }
    }

    private fun particlePersonalityParagraphs(profile: AgeProfile): List<String> = buildList {
        if (profile.modifiers.contains(ChaosAgeThemes.PARTICLE_MOTES)) {
            add("Soft motes wander through the air near travelers.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.PARTICLE_ASH)) {
            add("Fine ash drifts down in quiet sheets, even when no fire is visible.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.PARTICLE_SPORES)) {
            add("Loose spores float on the wind and give the Age a drowsy, twilight hush.")
        }
        if (profile.modifiers.contains(ChaosAgeThemes.PARTICLE_VOID)) {
            add("Dark sparks wink in and out at the edge of sight.")
        }
    }

    private fun sacrificeParagraph(profile: AgeProfile): String {
        val by = profile.ageState.sacrificedBy?.takeIf { it.isNotBlank() } ?: "an unknown hand"
        val at = profile.ageState.sacrificedAt?.toString() ?: "an unknown hour"
        return "given over by $by at $at"
    }

    private fun terrainDraftParagraph(terrain: String?): String = when (terrain) {
        "FLOATING_ISLANDS" -> "The draft plainly calls for a world of floating islands."
        "ALPHA" -> "The draft leans toward an old, jagged Alpha-style land."
        "BETA" -> "The draft leans toward the rolling roughness of Beta land."
        "AMPLIFIED" -> "The draft asks for amplified heights and brutal rises."
        "CAVE" -> "The draft speaks of a cave-ridden world."
        "BIOSPHERES" -> "The draft names biospheres, great domed pockets of land and sky."
        "CITIES" -> "The draft points toward the ordered ruin of cities."
        "STANDARD" -> "The draft asks for a more ordinary land."
        "FLAT" -> "The draft reduces the land almost to a tabletop."
        "VOID" -> "The draft courts the void itself."
        else -> "The landform has not yet been fully spoken for."
    }

    private fun biomeDraftParagraph(controller: String?, biomes: List<String>): String = when {
        controller == "CHECKERBOARD" -> "The regions are meant to break against one another in a checkerboard arrangement."
        controller == "VANILLA" -> "The script allows the land to sort its climates in a more natural spread."
        biomes.isNotEmpty() -> "The named regions in the leaves are ${summarizeList(biomes.map(::prettifyPath), 8)}."
        else -> "No particular region has yet been named."
    }

    private fun timeDraftParagraph(timeMode: String?, multiplier: Float): String = when (timeMode) {
        "fast" -> "The draft urges the heavens forward at ${"%.2f".format(multiplier)} times the usual pace."
        "slow" -> "The draft drags the heavens to ${"%.2f".format(multiplier)} of their usual pace."
        "fixed" -> "The draft asks that the heavens be fixed in one station."
        else -> "The passage of time is not yet nailed down."
    }

    private fun weatherDraftParagraph(weatherMode: String?): String = when (weatherMode) {
        "endless_rain" -> "The leaves call for unending rain."
        "endless_storm" -> "The leaves call for an endless storm."
        "no_weather" -> "The leaves bar weather entirely."
        "normal" -> "The leaves allow ordinary weather."
        else -> "The weather has not yet been fully spoken for."
    }

    private fun colorDraftParagraph(label: String, color: Int?): String =
        color?.let { "$label is marked as ${formatColor(it)}." } ?: "$label has not yet been spoken for."

    private fun terrainDescription(terrain: TerrainType): String = when (terrain) {
        TerrainType.STANDARD -> "a familiar and largely natural world"
        TerrainType.BETA -> "the rough, nostalgic swell of Beta country"
        TerrainType.ALPHA -> "an older and more violent cut of land"
        TerrainType.AMPLIFIED -> "towering heights and dangerous drops"
        TerrainType.CAVES -> "a realm gnawed hollow with caves"
        TerrainType.FLOATING_ISLANDS -> "broken shelves of land adrift in open air"
        TerrainType.FLAT -> "broad flat country with little relief"
        TerrainType.BIOSPHERES -> "domed worlds held apart in glass-like pockets"
        TerrainType.CITIES -> "ordered ruins and urban bones"
        TerrainType.NETHER -> "a Nether-shaped realm built from burning stone"
        TerrainType.END -> "an End-shaped realm of islands over the void"
        TerrainType.VOID -> "a near-empty gulf with little but sky and hazard"
    }

    private fun biomeModeDescription(mode: BiomeMode): String = when (mode) {
        BiomeMode.SINGLE -> "a single dominant region"
        BiomeMode.WEIGHTED -> "a weighted mingle of chosen regions"
        BiomeMode.CHECKERBOARD -> "a checkerboard of abrupt borders"
        BiomeMode.VANILLA_DISTRIBUTION -> "a native spread much like the common Overworld"
    }

    private fun formatAnchor(profile: AgeProfile): String =
        if (
            profile.ageState.surfaceSpawnX != null &&
            profile.ageState.surfaceSpawnY != null &&
            profile.ageState.surfaceSpawnZ != null
        ) {
            "at X ${profile.ageState.surfaceSpawnX}, Y ${profile.ageState.surfaceSpawnY}, Z ${profile.ageState.surfaceSpawnZ}"
        } else {
            "without a fixed anchor yet recorded"
        }

    private fun describeFixedTime(time: Long): String {
        val tick = ((time % 24000) + 24000) % 24000
        return when (tick) {
            in 0..999 -> "just past dawn"
            in 1000..5999 -> "in the bright climb toward noon"
            in 6000..6999 -> "at midday"
            in 7000..11999 -> "in the long afternoon"
            in 12000..13999 -> "at dusk"
            in 14000..17999 -> "in the early night"
            in 18000..21999 -> "under the deep midnight"
            else -> "in the last dark before dawn"
        }
    }

    private fun timeScaleDescription(scale: Float): String = when {
        scale > 1.05f -> "${"%.2f".format(scale)} times faster than common"
        scale < 0.95f -> "${"%.2f".format(scale)} of the common pace"
        else -> "at the common pace"
    }

    private fun sizeDescription(sunSize: Float, moonSize: Float): String = when {
        sunSize > 1.2f || moonSize > 1.2f -> "larger"
        sunSize < 0.85f && moonSize < 0.85f -> "smaller"
        else -> "closer to common size"
    }

    private fun starDensityDescription(density: Int): String = when {
        density <= 0 -> "no"
        density == 1 -> "a normal scatter of"
        density == 2 -> "a dense field of"
        else -> "an overcrowded blaze of"
    }

    private fun formatMultiplier(value: Float): String = "${"%.2f".format(value)}x"

    private fun tuningParagraphs(tuning: TerrainTuningProfile): List<String> {
        if (!tuning.isEdited()) return emptyList()

        val lines = mutableListOf<String>()
        tuning.terrainTurbulence?.let {
            lines += "The shaping of the land has been pressed toward a turbulence reading of $it in 16."
        }
        tuning.seaLevel?.let {
            lines += "The waters are called to a deliberate level of $it in 16."
        }
        tuning.caveDensity?.let {
            lines += "The underworld is instructed toward a cave density of $it in 16."
        }
        tuning.biomeSize?.let {
            lines += "Regional breadth is weighted to $it in 16 rather than left wholly to whim."
        }
        tuning.verticalRange?.let {
            lines += "The script presses the world's vertical reach to $it in 16."
        }
        if (tuning.superFlat) {
            lines += "A flattening injunction has been written into the script."
        }
        if (tuning.noMobs) {
            lines += "The scribe has forbidden natural creatures from taking root."
        }
        if (tuning.caveWorld) {
            lines += "The script has been bent toward a cave-bound world."
        }
        if (tuning.noAquifers) {
            lines += "Underground waters have been intentionally denied."
        }
        return lines
    }

    private fun formatEffectName(rawId: String): String {
        val id = Identifier.tryParse(rawId) ?: return prettifyPath(rawId)
        val effect = Registries.STATUS_EFFECT.get(id)
        return effect?.name?.string?.takeIf { it.isNotBlank() } ?: prettifyPath(id.path)
    }

    private fun formatColor(rgb: Int): String = "${hex(rgb)}"

    private fun hex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

    private fun summarizeList(items: List<String>, maxItems: Int): String {
        if (items.isEmpty()) return "none"
        val visible = items.take(maxItems)
        return if (items.size > maxItems) {
            visible.joinToString(", ") + ", and ${items.size - maxItems} more"
        } else {
            visible.joinToString(", ")
        }
    }

    private fun extractDisplayName(stack: ItemStack, profile: AgeProfile? = null): String {
        val nbt = stack.nbt
        if (nbt != null) {
            val draftName = nbt.getString("Age_Name")
            if (draftName.isNotBlank()) return draftName
        }
        val profileName = profile?.ageState?.displayName?.takeIf { it.isNotBlank() }
        if (profileName != null) {
            return profileName
        }
        if (stack.hasCustomName()) {
            return stack.name.string
        }
        val ageId = nbt?.getString("Age_ID")?.takeIf { it.isNotBlank() }
        if (ageId != null) {
            return prettifyPath(ageId.substringAfter(':'))
        }
        return stack.name.string
    }

    private fun extractSymbols(stack: ItemStack): List<String> {
        val pages = stack.nbt?.getList("Pages", 8) ?: return emptyList()
        return List(pages.size) { index -> pages.getString(index) }
    }

    private fun prettifyPath(raw: String): String =
        raw.substringAfter(':')
            .replace('/', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { raw }

    private fun prettifySymbol(raw: String): String = prettifyPath(raw)

    private fun withAlpha(rgb: Int): Int = 0xFF000000.toInt() or (rgb and 0xFFFFFF)
}
