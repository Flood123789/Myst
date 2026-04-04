package mystcraft.flood

import mystcraft.flood.registry.ModFeatures
import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.world.gen.GenerationStep
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.item.ModItemGroups
import mystcraft.flood.item.ModItems
import mystcraft.flood.network.ModMessages
import mystcraft.flood.registry.ModSymbols
import mystcraft.flood.server.command.AgeCommand
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.slf4j.LoggerFactory
import mystcraft.flood.block.ModBlocks
import mystcraft.flood.block.entity.ModBlockEntities
import mystcraft.flood.gui.ModScreens
import mystcraft.flood.generation.instability.InstabilityManager
import mystcraft.flood.block.DecayManager

object MystcraftReforged : ModInitializer {
    const val MOD_ID = "mystcraft-reforged"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("Initializing Mystcraft Reforged...")

        ModSymbols.register()
        ModItems.registerModItems()
        ModItemGroups.registerItemGroups()
        AgeCommand.register()
        ModBlocks.registerModBlocks()
        ModBlockEntities.registerBlockEntities()
        ModScreens.register() // From the previous GUI step!
        InstabilityManager.register() // From the upcoming Instability step!
        DecayManager.register() // From the upcoming Decay step!

        // 1. Manually move the Age's local clock
        ServerTickEvents.END_WORLD_TICK.register { world ->
            val id = world.registryKey.value
            if (id.namespace == MOD_ID) {
                val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
                
                // If the time isn't locked to a specific hour, move the sun forward
                if (profile.time.fixedTime == null) {
                    
                    // Add the timescale to our fractional bucket
                    profile.time.timeAccumulator += profile.time.timeScale
                    
                    // When the bucket overflows past 1.0, we add those whole numbers to the clock
                    if (profile.time.timeAccumulator >= 1.0f) {
                        val ticksToAdd = profile.time.timeAccumulator.toLong()
                        profile.time.liveTimeOfDay += ticksToAdd
                        profile.time.timeAccumulator -= ticksToAdd // Keep any leftover decimals
                    }
                }
            }
        }

        // 2. Sync profile to client on Join
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            server.worlds.forEach { world ->
                val id = world.registryKey.value
                if (id.namespace == MOD_ID) {
                    val profile = AgeProfileManager.getOrGenerateProfile(server, id)
                    ModMessages.sendDimensionSync(handler.player, id, profile)
                }
            }
        }

        // 3. Save Age state on Unload
        ServerWorldEvents.UNLOAD.register { server, world ->
            val id = world.registryKey.value
            if (id.namespace == MOD_ID) {
                AgeProfileManager.saveAndUnload(server, id)
                LOGGER.info("Persisted Age: $id")
            }
        }

        // Register the raw features first
        ModFeatures.register()

        // Inject Dense Ores into the UNDERGROUND generation step
        BiomeModifications.addFeature(
            BiomeSelectors.all(),
            GenerationStep.Feature.UNDERGROUND_ORES,
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier(MOD_ID, "dense_ores"))
        )

        // Inject the Star Fissure into the SURFACE generation step
        BiomeModifications.addFeature(
            BiomeSelectors.all(),
            GenerationStep.Feature.SURFACE_STRUCTURES,
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier(MOD_ID, "star_fissure"))
        )
            }
}