package mystcraft.flood.server.command

import mystcraft.flood.entity.ModEntities
import mystcraft.flood.entity.ParadoxReaperEntity
import mystcraft.flood.entity.ReaperDebugLure
import mystcraft.flood.config.MystcraftConfig
import mystcraft.flood.generation.instability.ParadoxReaperSpawner
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.item.ModItems
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.entity.SpawnReason
import net.minecraft.item.ItemStack
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * Test harness for the Paradox Reaper.
 *
 * Waiting for a natural hunt to happen to cross the exact geometry you wanted to watch is a poor
 * way to test surface traversal, so this exists to drive it directly: spawn a drone, then walk it
 * up walls and across ceilings with the lure tool.
 */
object ReaperCommand {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("reaper")
                    .requires { it.hasPermissionLevel(2) }

                    .then(CommandManager.literal("lure").executes { context ->
                        giveLure(context.source)
                    })

                    .then(CommandManager.literal("drone").executes { context ->
                        spawn(context.source, ReaperSpawnForm.DRONE)
                    })

                    .then(CommandManager.literal("spawn")
                        .then(CommandManager.literal("greater").executes { context ->
                            spawn(context.source, ReaperSpawnForm.GREATER)
                        })
                        .then(CommandManager.literal("lesser").executes { context ->
                            spawn(context.source, ReaperSpawnForm.LESSER)
                        })
                        .then(CommandManager.literal("crawler").executes { context ->
                            spawn(context.source, ReaperSpawnForm.CRAWLER)
                        })
                        .then(CommandManager.literal("petty").executes { context ->
                            spawn(context.source, ReaperSpawnForm.CRAWLER)
                        })
                        .then(CommandManager.literal("bound_lesser").executes { context ->
                            spawn(context.source, ReaperSpawnForm.BOUND_LESSER)
                        })
                        .then(CommandManager.literal("bound_greater").executes { context ->
                            spawn(context.source, ReaperSpawnForm.BOUND_GREATER)
                        })
                        .then(CommandManager.literal("drone").executes { context ->
                            spawn(context.source, ReaperSpawnForm.DRONE)
                        })
                    )

                    .then(CommandManager.literal("creative")
                        .executes { context ->
                            reportCreativeRule(context.source)
                        }
                        .then(CommandManager.literal("include").executes { context ->
                            setCreativeRule(context.source, include = true)
                        })
                        .then(CommandManager.literal("exclude").executes { context ->
                            setCreativeRule(context.source, include = false)
                        })
                    )

                    .then(CommandManager.literal("status").executes { context ->
                        status(context.source)
                    })

                    .then(CommandManager.literal("clear").executes { context ->
                        clear(context.source)
                    })
            )
        }
    }

    private fun giveLure(source: ServerCommandSource): Int {
        val player = source.player ?: run {
            source.sendError(Text.literal("Needs to be run by a player."))
            return 0
        }
        player.giveItemStack(ItemStack(ModItems.REAPER_LURE))
        source.sendFeedback({ Text.literal("Right-click to send drones wherever your crosshair points, up to 160 blocks. Keep it held to keep them coming.") }, false)
        return 1
    }

    /**
     * Places a Reaper a few blocks ahead of where the player is looking, so it lands in open air
     * rather than inside them.
     */
    private fun spawn(source: ServerCommandSource, form: ReaperSpawnForm): Int {
        val world = source.world
        val player = source.player
        if (form.requiresOwner && player == null) {
            source.sendError(Text.literal("That Reaper form needs a player owner."))
            return 0
        }
        val origin: Vec3d = if (player != null) {
            player.pos.add(player.rotationVector.multiply(SPAWN_AHEAD)).add(0.0, 1.0, 0.0)
        } else {
            source.position
        }

        val reaper = ModEntities.PARADOX_REAPER.create(world) ?: return 0
        reaper.applyVariant(form.lesser)
        reaper.refreshPositionAndAngles(origin.x, origin.y, origin.z, world.random.nextFloat() * 360f, 0f)
        reaper.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(origin)), SpawnReason.COMMAND, null, null)
        when (form) {
            ReaperSpawnForm.DRONE -> reaper.makeDrone()
            ReaperSpawnForm.CRAWLER -> reaper.applyPetty(player!!.uuid)
            ReaperSpawnForm.BOUND_LESSER,
            ReaperSpawnForm.BOUND_GREATER -> reaper.bindToOwnerForCommand(player!!.uuid)
            else -> Unit
        }
        world.spawnEntity(reaper)

        source.sendFeedback({ Text.literal("Spawned a ${form.displayName}.") }, false)
        return 1
    }

    private fun clear(source: ServerCommandSource): Int {
        val world: ServerWorld = source.world
        var removed = 0
        world.iterateEntities().forEach { entity ->
            if (entity is ParadoxReaperEntity) {
                entity.discard()
                removed++
            }
        }
        ReaperDebugLure.clear(world)
        source.sendFeedback({ Text.literal("Removed $removed Reaper(s) and cleared the lure.") }, false)
        return removed
    }

    private fun reportCreativeRule(source: ServerCommandSource): Int {
        val included = MystcraftConfig.current.paradoxReaper.targetCreativePlayers
        source.sendFeedback({
            Text.literal("Creative-mode players are ${if (included) "included in" else "excluded from"} Paradox Reaper targeting.")
        }, false)
        return if (included) 1 else 0
    }

    private fun status(source: ServerCommandSource): Int {
        val player = source.player ?: run {
            source.sendError(Text.literal("Needs to be run by a player."))
            return 0
        }
        val world = source.world
        val ageId = world.registryKey.value
        if (ageId.namespace != "mystcraft-reforged") {
            source.sendError(Text.literal("Natural Paradox Reapers only spawn inside a Mystcraft Age."))
            return 0
        }
        val profile = AgeProfileManager.getOrGenerateProfile(world.server, ageId)
        source.sendFeedback({ Text.literal(ParadoxReaperSpawner.describeStatus(world, profile, player)) }, false)
        return 1
    }

    private fun setCreativeRule(source: ServerCommandSource, include: Boolean): Int {
        val current = MystcraftConfig.current
        MystcraftConfig.replace(
            current.copy(paradoxReaper = current.paradoxReaper.copy(targetCreativePlayers = include))
        )
        source.sendFeedback({
            Text.literal("Paradox Reapers will ${if (include) "now target" else "ignore"} creative-mode players. Saved to the balance config.")
        }, true)
        return 1
    }

    private const val SPAWN_AHEAD = 4.0

    private enum class ReaperSpawnForm(
        val displayName: String,
        val lesser: Boolean,
        val requiresOwner: Boolean = false
    ) {
        GREATER("greater Reaper", false),
        LESSER("lesser Reaper", true),
        CRAWLER("Little Anomaly crawler", true, true),
        BOUND_LESSER("bound lesser Reaper", true, true),
        BOUND_GREATER("bound greater Reaper", false, true),
        DRONE("greater Reaper drone", false)
    }
}
