package mystcraft.flood.server.command

import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.DimensionInjector
import mystcraft.flood.compat.MystcraftSeasons
import mystcraft.flood.compat.SereneSeasonsCompat
import mystcraft.flood.generation.AgeCurseManager
import mystcraft.flood.generation.AgeLifecycleManager
import mystcraft.flood.generation.AgeSubdimensionManager
import mystcraft.flood.generation.AgeWeatherController
import mystcraft.flood.generation.ImmersivePortalsCompat
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
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
                        .then(CommandManager.literal("resume").executes { resumeAgeTime(it) })
                        .then(CommandManager.literal("permanent")
                            .then(CommandManager.literal("set")
                                .then(CommandManager.literal("day").executes { setPermanentAgeTime(it, 1000L) })
                                .then(CommandManager.literal("noon").executes { setPermanentAgeTime(it, 6000L) })
                                .then(CommandManager.literal("night").executes { setPermanentAgeTime(it, 13000L) })
                                .then(CommandManager.literal("midnight").executes { setPermanentAgeTime(it, 18000L) })
                                .then(CommandManager.argument("ticks", IntegerArgumentType.integer(0))
                                    .executes { setPermanentAgeTime(it, IntegerArgumentType.getInteger(it, "ticks").toLong()) }
                                )
                            )
                        )
                    )
                    
                    // === 4. WEATHER COMMAND ===
                    .then(CommandManager.literal("weather")
                        .then(CommandManager.literal("clear").executes { clearAgeWeather(it) })
                        .then(CommandManager.literal("resume").executes { resumeAgeWeather(it) })
                        .then(CommandManager.literal("normal").executes { setAgeWeather(it, "normal") })
                        .then(CommandManager.literal("rain").executes { setAgeWeather(it, "rain") })
                        .then(CommandManager.literal("thunder").executes { setAgeWeather(it, "thunder") })
                        .then(CommandManager.literal("permanent")
                            .then(CommandManager.literal("clear").executes { setAgeWeather(it, "clear") })
                            .then(CommandManager.literal("normal").executes { setAgeWeather(it, "normal") })
                            .then(CommandManager.literal("rain").executes { setAgeWeather(it, "rain") })
                            .then(CommandManager.literal("thunder").executes { setAgeWeather(it, "thunder") })
                        )
                    )

                    // Optional Serene Seasons integration. Keep the node registered when the mod
                    // is absent so operators receive a useful compatibility message.
                    .then(CommandManager.literal("season")
                        .executes { getAgeSeason(it) }
                        .then(CommandManager.literal("set")
                            .then(CommandManager.argument("season", StringArgumentType.word())
                                .suggests { _, builder ->
                                    CommandSource.suggestMatching(
                                        MystcraftSeasons.subSeasonNames + listOf("spring", "summer", "autumn", "winter"),
                                        builder
                                    )
                                }
                                .executes {
                                    setAgeSeason(it, StringArgumentType.getString(it, "season"))
                                }
                            )
                        )
                        .then(CommandManager.literal("enable")
                            .executes { setAgeSeasonCycle(it, true) }
                        )
                        .then(CommandManager.literal("disable")
                            .executes { setAgeSeasonCycle(it, false) }
                        )
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
                    .then(CommandManager.literal("curse")
                        .then(CommandManager.literal("list")
                            .executes { listAgeCurses(it) }
                        )
                        .then(CommandManager.literal("cleanse")
                            .executes { cleanseAgeCurse(it) }
                        )
                    )
                    .then(CommandManager.literal("gravity")
                        .then(CommandManager.literal("get")
                            .executes { getAgeGravity(it) }
                        )
                        .then(CommandManager.literal("set")
                            .then(CommandManager.argument("scale", FloatArgumentType.floatArg(0.05f, 2.0f))
                                .executes { setAgeGravity(it, FloatArgumentType.getFloat(it, "scale")) }
                            )
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

    private fun getAgeSeason(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val world = requireMystcraftAge(source, "inspect its season") ?: return 0
        if (!requireSereneSeasons(source)) return 0

        val season = SereneSeasonsCompat.getSeason(world)
        val enabled = SereneSeasonsCompat.isSeasonCycleEnabled(world)
        if (season == null || enabled == null) {
            source.sendError(Text.literal("The installed Serene Seasons version could not be controlled."))
            return 0
        }

        val state = if (enabled) "enabled" else "disabled"
        source.sendFeedback(
            { Text.literal("Mystcraft season: $season; seasonal progression is $state for ${world.registryKey.value}.") },
            false
        )
        return 1
    }

    private fun setAgeSeason(context: CommandContext<ServerCommandSource>, seasonName: String): Int {
        val source = context.source
        val world = requireMystcraftAge(source, "change its season") ?: return 0
        if (!requireSereneSeasons(source)) return 0

        val index = MystcraftSeasons.indexOf(seasonName)
        if (index == null) {
            source.sendError(Text.literal("Unknown season '$seasonName'. Use spring, summer, autumn, winter, or an early/mid/late sub-season."))
            return 0
        }

        if (!SereneSeasonsCompat.setSeason(world, seasonName)) {
            source.sendError(Text.literal("The installed Serene Seasons version could not be controlled."))
            return 0
        }

        val normalized = MystcraftSeasons.nameOf(index)
        source.sendFeedback(
            { Text.literal("Mystcraft season set to $normalized for ${world.registryKey.value}.") },
            true
        )
        return 1
    }

    private fun setAgeSeasonCycle(context: CommandContext<ServerCommandSource>, enabled: Boolean): Int {
        val source = context.source
        val world = requireMystcraftAge(source, "change seasonal progression") ?: return 0
        if (!requireSereneSeasons(source)) return 0

        if (!SereneSeasonsCompat.setSeasonCycleEnabled(world, enabled)) {
            source.sendError(Text.literal("The installed Serene Seasons version could not be controlled."))
            return 0
        }

        val state = if (enabled) "enabled" else "disabled"
        source.sendFeedback(
            { Text.literal("Seasonal progression $state for ${world.registryKey.value}.") },
            true
        )
        return 1
    }

    private fun requireMystcraftAge(source: ServerCommandSource, action: String): ServerWorld? {
        val world = source.world
        if (world.registryKey.value.namespace == MystcraftReforged.MOD_ID) return world
        source.sendError(Text.literal("You must be in a Mystcraft Age to $action!"))
        return null
    }

    private fun requireSereneSeasons(source: ServerCommandSource): Boolean {
        if (SereneSeasonsCompat.isAvailable) return true
        source.sendError(Text.literal("Serene Seasons is not installed; Mystcraft season compatibility is inactive."))
        return false
    }

    private fun setAgeTime(context: CommandContext<ServerCommandSource>, time: Long): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value
        val timeOfDay = normalizeTimeOfDay(time)

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to change its time!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)

        if (profile.time.fixedTime != null) {
            profile.time.temporaryTimeOverride = timeOfDay
            source.sendFeedback({ Text.literal("Temporary visible time set to $timeOfDay for Age: $id") }, true)
        } else {
            val currentDayStart = profile.time.liveTimeOfDay - (profile.time.liveTimeOfDay % 24000L)
            profile.time.liveTimeOfDay = currentDayStart + timeOfDay
            profile.time.temporaryTimeOverride = null
            source.sendFeedback({ Text.literal("Live time set to $timeOfDay for Age: $id") }, true)
        }

        syncAgeTime(world, profile)
        return 1
    }

    private fun setPermanentAgeTime(context: CommandContext<ServerCommandSource>, time: Long): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value
        val timeOfDay = normalizeTimeOfDay(time)

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to permanently change its time!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        profile.time.temporaryTimeOverride = null

        if (profile.time.fixedTime != null) {
            profile.time.fixedTime = timeOfDay
            source.sendFeedback({ Text.literal("Fixed time permanently updated to $timeOfDay for Age: $id") }, true)
        } else {
            val currentDayStart = profile.time.liveTimeOfDay - (profile.time.liveTimeOfDay % 24000L)
            profile.time.liveTimeOfDay = currentDayStart + timeOfDay
            source.sendFeedback({ Text.literal("Live time permanently set to $timeOfDay for Age: $id") }, true)
        }

        AgeProfileManager.save(world.server, id)
        syncAgeTime(world, profile)
        return 1
    }

    private fun resumeAgeTime(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to resume its authored time!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        profile.time.temporaryTimeOverride = null
        syncAgeTime(world, profile)
        source.sendFeedback({ Text.literal("Temporary time override cleared for Age: $id") }, true)
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
        profile.weather.temporaryClearTicks = 0
        
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

        AgeProfileManager.save(world.server, id)
        source.sendFeedback({ Text.literal("Age weather permanently set to $weatherType for Age: $id") }, true)
        return 1
    }

    private fun clearAgeWeather(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to clear its weather!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        profile.weather.temporaryClearTicks = 12000
        profile.weather.currentRaining = false
        profile.weather.currentThundering = false
        profile.weather.clearTicks = profile.weather.clearTicks.coerceAtLeast(12000)
        profile.weather.rainTicks = 0
        profile.weather.thunderTicks = 0

        world.players.forEach { player ->
            ModMessages.sendDimensionSync(player, id, profile)
        }

        source.sendFeedback({ Text.literal("Weather temporarily cleared for Age: $id") }, true)
        return 1
    }

    private fun resumeAgeWeather(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to resume its weather!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        profile.weather.temporaryClearTicks = 0
        AgeWeatherController.tick(world, profile)
        world.players.forEach { player ->
            ModMessages.sendDimensionSync(player, id, profile)
        }

        source.sendFeedback({ Text.literal("Temporary weather override cleared for Age: $id") }, true)
        return 1
    }

    private fun normalizeTimeOfDay(time: Long): Long = ((time % 24000L) + 24000L) % 24000L

    private fun syncAgeTime(world: ServerWorld, profile: AgeProfile) {
        world.server.playerManager.playerList.forEach { player ->
            ModMessages.sendDimensionTimeSync(player, world.registryKey.value, profile)
        }
        world.players.forEach { player ->
            val packet = WorldTimeUpdateS2CPacket(
                world.time,
                profile.time.visibleTimeOfDay,
                !profile.time.visibleTimeFrozen
            )
            if (!ImmersivePortalsCompat.trySendWorldPacket(player, world, packet)) {
                player.networkHandler.sendPacket(packet)
            }
        }
    }

    private fun toggleAgeInstability(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val world = source.world
        val currentId = world.registryKey.value

        if (currentId.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to toggle instability effects!"))
            return 0
        }

        val rootAgeId = AgeSubdimensionManager.rootIdOf(currentId)
        val profile = AgeProfileManager.getOrGenerateProfile(world.server, rootAgeId)
        profile.stability.effectsEnabled = !profile.stability.effectsEnabled

        ModMessages.syncAgeFamily(world.server, rootAgeId)

        val stateText = if (profile.stability.effectsEnabled) "enabled" else "disabled"
        source.sendFeedback(
            { Text.literal("Instability effects are now $stateText for Age: $rootAgeId (score remains ${profile.stability.instabilityScore}).") },
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

    private fun listAgeCurses(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val id = source.world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to inspect curses!"))
            return 0
        }

        val rootAgeId = AgeSubdimensionManager.rootIdOf(id)
        val profile = AgeProfileManager.getOrGenerateProfile(source.server, rootAgeId)
        val curses = AgeCurseManager.refresh(profile)

        if (curses.isEmpty()) {
            source.sendFeedback({ Text.literal("No removable curses are legible in Age: $rootAgeId") }, false)
            return 1
        }

        source.sendFeedback({ Text.literal("Removable curses in Age $rootAgeId:") }, false)
        curses.forEach { curse ->
            source.sendFeedback({ Text.literal("- ${curse.label(profile)}") }, false)
        }
        return curses.size
    }

    private fun cleanseAgeCurse(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val id = source.world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to cleanse curses!"))
            return 0
        }

        val rootAgeId = AgeSubdimensionManager.rootIdOf(id)
        val profile = AgeProfileManager.getOrGenerateProfile(source.server, rootAgeId)
        val curse = AgeCurseManager.nextCurse(profile)
        if (curse == null) {
            source.sendFeedback({ Text.literal("No removable curses are legible in Age: $rootAgeId") }, false)
            return 0
        }

        val curseName = curse.label(profile)
        val result = AgeCurseManager.cleanseNext(profile)
        if (!result.changed) {
            source.sendError(Text.literal("The curse could not be cleansed."))
            return 0
        }

        AgeProfileManager.save(source.server, rootAgeId)
        ModMessages.syncAgeFamily(source.server, rootAgeId)
        source.sendFeedback(
            { Text.literal("Cleansed $curseName from $rootAgeId. Instability fell by ${result.instabilityReduced}.") },
            true
        )
        return 1
    }

    private fun getAgeGravity(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to inspect gravity!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        source.sendFeedback({ Text.literal("Gravity scale for Age $id is ${profile.physics.gravityScale}") }, false)
        return 1
    }

    private fun setAgeGravity(context: CommandContext<ServerCommandSource>, gravityScale: Float): Int {
        val source = context.source
        val world = source.world
        val id = world.registryKey.value

        if (id.namespace != MystcraftReforged.MOD_ID) {
            source.sendError(Text.literal("You must be in a Mystcraft Age to change gravity!"))
            return 0
        }

        val profile = AgeProfileManager.getOrGenerateProfile(world.server, id)
        profile.physics.gravityScale = gravityScale
        AgeProfileManager.save(world.server, id)
        world.players.forEach { player ->
            ModMessages.sendDimensionSync(player, id, profile)
        }

        source.sendFeedback({ Text.literal("Gravity scale set to $gravityScale for Age: $id") }, true)
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
