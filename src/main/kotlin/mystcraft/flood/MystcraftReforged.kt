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

import mystcraft.flood.registry.ModLoot

import mystcraft.flood.generation.DeferredTreePlacer






object MystcraftReforged : ModInitializer {

    const val MOD_ID = "mystcraft-reforged"

    val LOGGER = LoggerFactory.getLogger(MOD_ID)



    override fun onInitialize() {

        LOGGER.info("Initializing Mystcraft Reforged...")



        // 1. Core Registries (Called exactly once)

        ModSymbols.register()

        ModItems.registerModItems()

        ModItemGroups.registerItemGroups()

        ModBlocks.registerModBlocks()

        ModBlockEntities.registerBlockEntities()

        ModScreens.register()

        ModLoot.register()

        AgeCommand.register()

        InstabilityManager.register()

        DecayManager.register()

       

        // 2. World Generation & Physics (Called exactly once)

        ModFeatures.register()

        DeferredTreePlacer.register()



        // 3. Inject Features into Biomes

        BiomeModifications.addFeature(

            BiomeSelectors.all(),

            GenerationStep.Feature.UNDERGROUND_ORES,

            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier(MOD_ID, "dense_ores"))

        )



        BiomeModifications.addFeature(

            BiomeSelectors.all(),

            GenerationStep.Feature.SURFACE_STRUCTURES,

            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier(MOD_ID, "abandoned_archive"))

        )



        BiomeModifications.addFeature(

            BiomeSelectors.all(),

            GenerationStep.Feature.VEGETAL_DECORATION,

            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier(MOD_ID, "giant_tree"))

        )

       

        BiomeModifications.addFeature(

            BiomeSelectors.all(),

            GenerationStep.Feature.SURFACE_STRUCTURES,

            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier(MOD_ID, "star_fissure"))

        )
        BiomeModifications.addFeature( // <-- Capital F here!
            BiomeSelectors.all(),
            GenerationStep.Feature.SURFACE_STRUCTURES,
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier(MOD_ID, "crystal_formations"))
        )



        // 4. Server Events (Time, Syncing, Unloading)

        ServerTickEvents.END_WORLD_TICK.register { world ->

            val id = world.registryKey.value

            if (id.namespace == MOD_ID) {

                val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)

               

                // If the time isn't locked to a specific hour, move the sun forward

                if (profile.time.fixedTime == null) {

                    profile.time.timeAccumulator += profile.time.timeScale

                   

                    if (profile.time.timeAccumulator >= 1.0f) {

                        val ticksToAdd = profile.time.timeAccumulator.toLong()

                        profile.time.liveTimeOfDay += ticksToAdd

                        profile.time.timeAccumulator -= ticksToAdd

                    }

                }

            }

        }



        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->

            server.worlds.forEach { world ->

                val id = world.registryKey.value

                if (id.namespace == MOD_ID) {

                    val profile = AgeProfileManager.getOrGenerateProfile(server, id)

                    ModMessages.sendDimensionSync(handler.player, id, profile)

                }

            }

        }



        ServerWorldEvents.UNLOAD.register { server, world ->

            val id = world.registryKey.value

            if (id.namespace == MOD_ID) {

                AgeProfileManager.saveAndUnload(server, id)

                LOGGER.info("Persisted Age: $id")

            }

        }

    }

}