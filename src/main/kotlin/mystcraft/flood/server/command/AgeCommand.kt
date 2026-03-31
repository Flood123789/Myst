package mystcraft.flood.server.command

import com.mojang.brigadier.context.CommandContext
import mystcraft.flood.access.DimensionInjector
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import net.minecraft.world.TeleportTarget

object AgeCommand {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("mystcraft")
                    .requires { it.hasPermissionLevel(2) }
                    .then(CommandManager.literal("create_age")
                        .executes { context ->
                            val ageId = Identifier("mystcraft-reforged", "age_${System.currentTimeMillis()}")
                            (context.source.server as DimensionInjector).`mystcraft$injectDimension`(ageId)
                            context.source.sendFeedback({ Text.literal("§aAge Created: $ageId") }, true)
                            1
                        }
                    )
                    .then(CommandManager.literal("tp")
                        .then(CommandManager.argument("age", DimensionArgumentType.dimension())
                            .executes { context: CommandContext<ServerCommandSource> ->
                                val source = context.source
                                val player = source.player ?: return@executes 0
                                val targetWorld = DimensionArgumentType.getDimensionArgument(context, "age")
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
            )
        }
    }
}