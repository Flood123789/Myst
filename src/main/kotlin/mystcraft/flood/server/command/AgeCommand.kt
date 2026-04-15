package mystcraft.flood.server.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.DimensionInjector
import mystcraft.flood.generation.AgeLifecycleManager
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import net.minecraft.world.TeleportTarget

object AgeCommand {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("mystcraft")
                    .requires { it.hasPermissionLevel(2) }

                    // === 1. CREATE AGE COMMAND ===
                    .then(CommandManager.literal("create_age")
                        .executes { context ->
                            val ageId = Identifier("mystcraft-reforged", "age_${System.currentTimeMillis()}")
                            // THE FIX: Passed emptyList() to satisfy the new symbols requirement
                            (context.source.server as DimensionInjector).`mystcraft$injectDimension`(ageId, emptyList())
                            context.source.sendFeedback({ Text.literal("§aAge Created: $ageId") }, true)
                            1
                        }
                    )

                    // === 2. TP COMMAND ===
                    .then(CommandManager.literal("tp")
                        .then(CommandManager.argument("age", DimensionArgumentType.dimension())
                            .executes { context: CommandContext<ServerCommandSource> ->
                                val source = context.source
                                val player = source.player ?: return@executes 0
                                val targetWorld = DimensionArgumentType.getDimensionArgument(context, "age")
                                if (!AgeLifecycleManager.mayEnterAge(player, targetWorld.registryKey.value)) {
                                    return@executes 0
                                }
                                targetWorld.getChunk(0, 0, net.minecraft.world.chunk.ChunkStatus.FULL, true)
                                val surfaceY = targetWorld.getTopY(Heightmap.Type.WORLD_SURFACE, 0, 0).toDouble()

                                val targetPos = TeleportTarget(
                                    Vec3d(0.0, surfaceY + 1.0, 0.0),
                                    Vec3d.ZERO,
                                    player.yaw,
                                    player.pitch
                                )
                                
                                FabricDimensions.teleport(player, targetWorld, targetPos)
                                source.sendFeedback({ Text.literal("§bWarping to surface...") }, false)
                                1
                            }
                        )
                    )

                    // === 3. TIME COMMAND ===
                    .then(CommandManager.literal("time")
                        .then(CommandManager.literal("set")
                            .then(CommandManager.literal("day").executes { setAgeTime(it, 1000L) })
                            .then(CommandManager.literal("noon").executes { setAgeTime(it, 6000L) })
                            .then(CommandManager.literal("night").executes { setAgeTime(it, 13000L) })
                            .then(CommandManager.literal("midnight").executes { setAgeTime(it, 18000L) })
                            .then(CommandManager.argument("ticks", IntegerArgumentType.integer(0))
                                .executes { setAgeTime(it, IntegerArgumentType.getInteger(it, "ticks").toLong()) }
                            )
                        )
                    )
                    
                    // === 4. WEATHER COMMAND ===
                    .then(CommandManager.literal("weather")
                        .then(CommandManager.literal("clear").executes { setAgeWeather(it, "clear") })
                        .then(CommandManager.literal("normal").executes { setAgeWeather(it, "normal") })
                        .then(CommandManager.literal("rain").executes { setAgeWeather(it, "rain") })
                        .then(CommandManager.literal("thunder").executes { setAgeWeather(it, "thunder") })
                    )

                    // === 5. INSTABILITY COMMAND ===
                    .then(CommandManager.literal("instability")
                        .then(CommandManager.literal("toggle")
                            .executes { toggleAgeInstability(it) }
                        )
                    )
                    .then(CommandManager.literal("age_effect")
                        .then(CommandManager.literal("toggle")
                            .executes { toggleAgeEffect(it) }
                        )
                    )
                    .then(CommandManager.literal("sacrifice")
                        .then(CommandManager.argument("age", DimensionArgumentType.dimension())
                            .executes { sacrificeAge(it) }
                        )
                    )
            )
        }
    }

    private fun setAgeTime(context: CommandContext<ServerCommandSource>, time: Long): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to change its time!"))
            return 0
        }

        // Pass emptyList() here too if getOrGenerateProfile requires it as a fallback, 
        // though our AgeProfileManager implementation made it default to emptyList() so it shouldn't strictly need it.
        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        
        // If the age is frozen, update the frozen time. Otherwise, update the live clock.
        if (profile.time.fixedTime != null) {
            profile.time.fixedTime = time
            source.sendFeedback({ Text.literal("Fixed time updated to $time for Age: $id") }, true)
        } else {
            profile.time.liveTimeOfDay = time
            source.sendFeedback({ Text.literal("Live time set to $time for Age: $id") }, true)
        }
        
        return 1
    }

    private fun setAgeWeather(context: CommandContext<ServerCommandSource>, weatherType: String): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to change its weather!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        
        when (weatherType) {
            "clear" -> {
                profile.weather.noWeather = true
                profile.weather.isEndlessRain = false
                profile.weather.isEndlessStorm = false
                profile.weather.currentRaining = false
                profile.weather.currentThundering = false
                profile.weather.clearTicks = 12000
                profile.weather.rainTicks = 0
                profile.weather.thunderTicks = 0
            }
            "normal" -> {
                profile.weather.noWeather = false
                profile.weather.isEndlessRain = false
                profile.weather.isEndlessStorm = false
                profile.weather.currentRaining = false
                profile.weather.currentThundering = false
                profile.weather.clearTicks = 12000
                profile.weather.rainTicks = 0
                profile.weather.thunderTicks = 0
            }
            "rain" -> {
                profile.weather.noWeather = false
                profile.weather.isEndlessRain = true
                profile.weather.isEndlessStorm = false
                profile.weather.currentRaining = true
                profile.weather.currentThundering = false
                profile.weather.clearTicks = 0
                profile.weather.rainTicks = Int.MAX_VALUE
                profile.weather.thunderTicks = 0
            }
            "thunder" -> {
                profile.weather.noWeather = false
                profile.weather.isEndlessRain = true
                profile.weather.isEndlessStorm = true
                profile.weather.currentRaining = true
                profile.weather.currentThundering = true
                profile.weather.clearTicks = 0
                profile.weather.rainTicks = Int.MAX_VALUE
                profile.weather.thunderTicks = Int.MAX_VALUE
            }
        }

        // Send a sync packet to all players in this Age so the sky instantly updates
        world.players.forEach { player ->
            ModMessages.sendDimensionSync(player, id, profile)
        }

        source.sendFeedback({ Text.literal("Age weather permanently set to $weatherType for Age: $id") }, true)
        return 1
    }

    private fun toggleAgeInstability(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to toggle instability effects!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        profile.stability.effectsEnabled = !profile.stability.effectsEnabled

        world.players.forEach { player ->
            ModMessages.sendDimensionSync(player, id, profile)
        }

        val stateText = if (profile.stability.effectsEnabled) "enabled" else "disabled"
        source.sendFeedback(
            { Text.literal("Instability effects are now $stateText for Age: $id (score remains ${profile.stability.instabilityScore}).") },
            true
        )
        return 1
    }

    private fun toggleAgeEffect(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to toggle age effects!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        val effectId = profile.ageEffect.effectId
        if (effectId == null) {
            source.sendFeedback({ Text.literal("This Age does not have an age effect selected.") }, false)
            return 0
        }

        profile.ageEffect.enabled = !profile.ageEffect.enabled
        world.players.forEach { player ->
            ModMessages.sendDimensionSync(player, id, profile)
        }

        val stateText = if (profile.ageEffect.enabled) "enabled" else "disabled"
        source.sendFeedback({ Text.literal("Age effect '$effectId' is now $stateText for Age: $id") }, true)
        return 1
    }

    private fun sacrificeAge(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val targetWorld = DimensionArgumentType.getDimensionArgument(context, "age")
        val ageId = targetWorld.registryKey.value
        if (ageId.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("Only Mystcraft Ages can be sacrificed."))
            return 0
        }

        val success = AgeLifecycleManager.sacrificeAge(source.server, ageId, source.name)
        if (!success) {
            source.sendError(Text.literal("That Age is already sacrificed or could not be processed."))
            return 0
        }

        source.sendFeedback({ Text.literal("Sacrificed Age: $ageId") }, true)
        return 1
    }
}
