package mystcraft.flood.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import mystcraft.flood.client.config.SkyElements
import mystcraft.flood.client.config.SkyLayerMode
import mystcraft.flood.client.config.SkyRenderConfig
import mystcraft.flood.client.config.SkyRenderMode
import mystcraft.flood.client.config.SkyRenderSettings
import mystcraft.flood.config.MystcraftConfig
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

class MystcraftModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        buildScreen(parent)
    }

    private fun buildScreen(parent: Screen): Screen {
        val working = MystcraftConfig.current.copy(
            instability = MystcraftConfig.current.instability.copy(),
            worldGeneration = MystcraftConfig.current.worldGeneration.copy(),
            loot = MystcraftConfig.current.loot.copy(),
            pagePools = MystcraftConfig.current.pagePools.copy(
                blacklistedBiomeNamespaces = MystcraftConfig.current.pagePools.blacklistedBiomeNamespaces.toMutableList(),
                blacklistedBiomePages = MystcraftConfig.current.pagePools.blacklistedBiomePages.toMutableList(),
                blacklistedTerrainPages = MystcraftConfig.current.pagePools.blacklistedTerrainPages.toMutableList(),
                blacklistedSymbolPages = MystcraftConfig.current.pagePools.blacklistedSymbolPages.toMutableList()
            )
        )
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.literal("Mystcraft Reforged"))
        val entries = builder.entryBuilder()

        val instability = builder.getOrCreateCategory(Text.literal("Instability"))
        fun instabilityInt(label: String, value: Int, min: Int = 0, max: Int = 10_000, save: (Int) -> Unit) {
            instability.addEntry(entries.startIntField(Text.literal(label), value).setMin(min).setMax(max).setSaveConsumer(save).build())
        }
        fun instabilityRate(label: String, value: Float, save: (Float) -> Unit) {
            instability.addEntry(entries.startFloatField(Text.literal(label), value).setMin(0f).setMax(1f).setSaveConsumer(save).build())
        }
        instabilityInt("Overworld functions max score", working.instability.overworldFunctionsMaxScore) { working.instability.overworldFunctionsMaxScore = it }
        instabilityInt("Mild effects threshold", working.instability.mildThreshold) { working.instability.mildThreshold = it }
        instabilityInt("Darkness + poison threshold", working.instability.moderateThreshold) { working.instability.moderateThreshold = it }
        instabilityInt("Lightning threshold", working.instability.severeThreshold) { working.instability.severeThreshold = it }
        instabilityInt("World eater threshold", working.instability.worldEaterThreshold) { working.instability.worldEaterThreshold = it }
        instabilityInt("Explosion threshold", working.instability.criticalThreshold) { working.instability.criticalThreshold = it }
        instabilityRate("Mild chance per second", working.instability.mildChancePerSecond) { working.instability.mildChancePerSecond = it }
        instabilityRate("Darkness + poison chance", working.instability.moderateChancePerSecond) { working.instability.moderateChancePerSecond = it }
        instabilityRate("Lightning chance", working.instability.severeChancePerSecond) { working.instability.severeChancePerSecond = it }
        instabilityRate("World eater base chance", working.instability.worldEaterBaseChancePerSecond) { working.instability.worldEaterBaseChancePerSecond = it }
        instabilityRate("World eater chance per point", working.instability.worldEaterChancePerPoint) { working.instability.worldEaterChancePerPoint = it }
        instabilityRate("Explosion chance", working.instability.criticalChancePerSecond) { working.instability.criticalChancePerSecond = it }
        instabilityInt("Mild duration (ticks)", working.instability.mildDurationTicks, 20, 12_000) { working.instability.mildDurationTicks = it }
        instabilityInt("Darkness duration (ticks)", working.instability.darknessDurationTicks, 20, 12_000) { working.instability.darknessDurationTicks = it }
        instabilityInt("Poison duration (ticks)", working.instability.poisonDurationTicks, 20, 12_000) { working.instability.poisonDurationTicks = it }

        val worldgen = builder.getOrCreateCategory(Text.literal("World Generation"))
        worldgen.addEntry(entries.startFloatField(Text.literal("All feature spawn multiplier"), working.worldGeneration.featureSpawnRateMultiplier).setMin(0f).setMax(10f).setSaveConsumer { working.worldGeneration.featureSpawnRateMultiplier = it }.build())
        worldgen.addEntry(entries.startFloatField(Text.literal("Page feature spawn multiplier"), working.worldGeneration.pageFeatureSpawnRateMultiplier).setMin(0f).setMax(10f).setSaveConsumer { working.worldGeneration.pageFeatureSpawnRateMultiplier = it }.build())
        worldgen.addEntry(entries.startIntField(Text.literal("Cave glow lichen attempts/chunk"), working.worldGeneration.caveGlowLichenAttemptsPerChunk).setMin(0).setMax(256).setSaveConsumer { working.worldGeneration.caveGlowLichenAttemptsPerChunk = it }.build())

        val loot = builder.getOrCreateCategory(Text.literal("Page Loot"))
        loot.addEntry(entries.startFloatField(Text.literal("Vanilla chest page chance"), working.loot.vanillaChestPageChance).setMin(0f).setMax(1f).setSaveConsumer { working.loot.vanillaChestPageChance = it }.build())
        loot.addEntry(entries.startIntField(Text.literal("Vanilla chest minimum pages"), working.loot.vanillaChestMinPages).setMin(0).setMax(64).setSaveConsumer { working.loot.vanillaChestMinPages = it }.build())
        loot.addEntry(entries.startIntField(Text.literal("Vanilla chest maximum pages"), working.loot.vanillaChestMaxPages).setMin(0).setMax(64).setSaveConsumer { working.loot.vanillaChestMaxPages = it }.build())
        loot.addEntry(entries.startFloatField(Text.literal("Age feature chest page chance"), working.loot.ageFeatureChestPageChance).setMin(0f).setMax(1f).setSaveConsumer { working.loot.ageFeatureChestPageChance = it }.build())
        loot.addEntry(entries.startFloatField(Text.literal("Age feature page count multiplier"), working.loot.ageFeaturePageCountMultiplier).setMin(0f).setMax(10f).setSaveConsumer { working.loot.ageFeaturePageCountMultiplier = it }.build())

        val pools = builder.getOrCreateCategory(Text.literal("Page Pool Blacklists"))
        pools.addEntry(entries.startStrList(Text.literal("Biome namespaces"), working.pagePools.blacklistedBiomeNamespaces).setSaveConsumer { working.pagePools.blacklistedBiomeNamespaces = it.toMutableList() }.build())
        pools.addEntry(entries.startStrList(Text.literal("Biome page IDs"), working.pagePools.blacklistedBiomePages).setSaveConsumer { working.pagePools.blacklistedBiomePages = it.toMutableList() }.build())
        pools.addEntry(entries.startStrList(Text.literal("Terrain/world page IDs"), working.pagePools.blacklistedTerrainPages).setSaveConsumer { working.pagePools.blacklistedTerrainPages = it.toMutableList() }.build())
        pools.addEntry(entries.startStrList(Text.literal("Any symbol page IDs"), working.pagePools.blacklistedSymbolPages).setSaveConsumer { working.pagePools.blacklistedSymbolPages = it.toMutableList() }.build())

        val sky = addSkyCategory(builder, entries)

        builder.setSavingRunnable {
            MystcraftConfig.replace(working)
            SkyRenderConfig.replace(sky)
        }
        return builder.build()
    }

    /**
     * Shader packs replace the vanilla sky pass, so the authored sky needs somewhere else to land.
     * This category chooses that placement globally and per element.
     */
    private fun addSkyCategory(builder: ConfigBuilder, entries: ConfigEntryBuilder): SkyRenderSettings {
        val sky = SkyRenderConfig.current.let { live ->
            live.copy(elements = LinkedHashMap(live.elements))
        }
        val category = builder.getOrCreateCategory(Text.literal("Sky Rendering"))

        category.addEntry(
            entries.startEnumSelector(Text.literal("Sky placement"), SkyRenderMode::class.java, sky.mode)
                .setEnumNameProvider { Text.literal(modeLabel(it as SkyRenderMode)) }
                .setTooltip(
                    Text.literal("Auto: draw on top of the frame while a shader pack is loaded, otherwise in the vanilla sky pass."),
                    Text.literal("Sky pass: vanilla behaviour. Most shaders discard it."),
                    Text.literal("Overlay: always draw on top, after shader composite passes."),
                    Text.literal("Off: hide every custom sky element.")
                )
                .setSaveConsumer { sky.mode = it }
                .build()
        )
        category.addEntry(
            entries.startBooleanToggle(Text.literal("Overlay only where sky is visible"), sky.overlayMasksToSky)
                .setTooltip(
                    Text.literal("Hide overlay elements behind terrain, buildings and Distant Horizons LOD chunks."),
                    Text.literal("Turn this off if a shader pack leaves the overlay invisible.")
                )
                .setSaveConsumer { sky.overlayMasksToSky = it }
                .build()
        )
        category.addEntry(
            entries.startBooleanToggle(Text.literal("Occlude behind Distant Horizons LODs"), sky.overlayIncludesDistantHorizons)
                .setTooltip(Text.literal("Distant Horizons keeps LOD depth in its own buffer; this folds it into the sky mask."))
                .setSaveConsumer { sky.overlayIncludesDistantHorizons = it }
                .build()
        )
        category.addEntry(
            entries.startBooleanToggle(Text.literal("Redraw sun, moon and stars in overlay"), sky.overlayDrawsBaseBodies)
                .setTooltip(
                    Text.literal("An Age only authors its extra bodies; the first sun, moon and star layer come from the vanilla sky pass that the overlay covers."),
                    Text.literal("Turn this off if you end up with a doubled sun or moon.")
                )
                .setSaveConsumer { sky.overlayDrawsBaseBodies = it }
                .build()
        )
        category.addEntry(
            entries.startFloatField(Text.literal("Overlay sky tint opacity"), sky.overlaySkyTintOpacity)
                .setMin(0f).setMax(1f)
                .setTooltip(Text.literal("How strongly the Age's sky colour is painted over the shader sky."))
                .setSaveConsumer { sky.overlaySkyTintOpacity = it }
                .build()
        )
        category.addEntry(
            entries.startFloatField(Text.literal("Sky pass tint opacity"), sky.skyPassTintOpacity)
                .setMin(0f).setMax(1f)
                .setSaveConsumer { sky.skyPassTintOpacity = it }
                .build()
        )

        category.addEntry(elementGroup(entries, "Suns, Moons & Stars", SkyElements.CORE, sky))
        category.addEntry(elementGroup(entries, "Sky Anomalies", SkyElements.ANOMALIES, sky))
        return sky
    }

    private fun elementGroup(
        entries: ConfigEntryBuilder,
        title: String,
        keys: List<String>,
        sky: SkyRenderSettings
    ): AbstractConfigListEntry<*> {
        val elementEntries = ArrayList<AbstractConfigListEntry<*>>()
        for (key in keys) {
            elementEntries.add(
                entries.startEnumSelector(
                    Text.literal(SkyElements.label(key)),
                    SkyLayerMode::class.java,
                    sky.elements[key] ?: SkyLayerMode.AUTO
                )
                    .setEnumNameProvider { Text.literal(elementModeLabel(it as SkyLayerMode)) }
                    .setSaveConsumer { sky.elements[key] = it }
                    .build()
            )
        }
        return entries.startSubCategory(Text.literal(title), elementEntries).build()
    }

    private fun modeLabel(mode: SkyRenderMode): String = when (mode) {
        SkyRenderMode.AUTO -> "Auto (on top with shaders)"
        SkyRenderMode.SKY_PASS -> "Vanilla sky pass"
        SkyRenderMode.OVERLAY -> "Draw on top"
        SkyRenderMode.OFF -> "Off"
    }

    private fun elementModeLabel(mode: SkyLayerMode): String = when (mode) {
        SkyLayerMode.AUTO -> "Follow sky placement"
        SkyLayerMode.SKY_PASS -> "Vanilla sky pass"
        SkyLayerMode.OVERLAY -> "Draw on top"
        SkyLayerMode.HIDDEN -> "Hidden"
    }
}
