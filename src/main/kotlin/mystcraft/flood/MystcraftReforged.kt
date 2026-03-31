package mystcraft.flood

import mystcraft.flood.access.DimensionInjector
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.item.ModItemGroups
import mystcraft.flood.item.ModItems
import mystcraft.flood.network.ModMessages
import mystcraft.flood.registry.ModSymbols
import mystcraft.flood.server.command.AgeCommand
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import org.slf4j.LoggerFactory
import java.nio.file.Files

object MystcraftReforged : ModInitializer {
    const val MOD_ID = "mystcraft-reforged"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("Initializing Mystcraft Reforged...")

        ModSymbols.register()
        ModItems.registerModItems()
        ModItemGroups.registerItemGroups()
        AgeCommand.register()

        // 1. Re-mount all saved Ages when the server boots up
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dir = server.getSavePath(WorldSavePath.ROOT).resolve("mystcraft_profiles")
            if (Files.exists(dir)) {
                Files.list(dir).forEach { path ->
                    val fileName = path.fileName.toString()
                    if (fileName.endsWith(".json")) {
                        val ageName = fileName.removeSuffix(".json")
                        val ageId = Identifier(MOD_ID, ageName)
                        
                        // Call our Mixin injector to rebuild the DimensionType and World Properties!
                        (server as DimensionInjector).`mystcraft$injectDimension`(ageId)
                        LOGGER.info("Re-mounted Age on startup: $ageId")
                    }
                }
            }
        }

        // 2. Sync disk JSONs to the client when they join the world
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            server.worlds.forEach { world ->
                val id = world.registryKey.value
                if (id.namespace == MOD_ID) {
                    val profile = AgeProfileManager.getOrGenerateProfile(server, id)
                    ModMessages.sendDimensionSync(handler.player, id, profile)
                }
            }
        }
    }
}