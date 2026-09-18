package mystcraft.flood.entity

import net.minecraft.registry.RegistryKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.world.World
import java.util.UUID

/** A short, per-player quiet period after entering or respawning in a Mystcraft Age. */
object ReaperArrivalGrace {
    private data class Grace(val world: RegistryKey<World>, val expiresAtServerTick: Int)

    private val protectedPlayers = mutableMapOf<UUID, Grace>()

    fun grant(player: ServerPlayerEntity, seconds: Int) {
        if (seconds <= 0) {
            revoke(player)
            return
        }
        protectedPlayers[player.uuid] = Grace(
            player.serverWorld.registryKey,
            player.server.ticks + seconds * 20
        )
        player.sendMessage(Text.translatable("message.mystcraft-reforged.reaper_grace", seconds), true)
    }

    fun isProtected(player: ServerPlayerEntity): Boolean {
        val grace = protectedPlayers[player.uuid] ?: return false
        if (grace.world != player.serverWorld.registryKey || player.server.ticks >= grace.expiresAtServerTick) {
            protectedPlayers.remove(player.uuid)
            return false
        }
        return true
    }

    fun revoke(player: ServerPlayerEntity) {
        protectedPlayers.remove(player.uuid)
    }

    fun clear() {
        protectedPlayers.clear()
    }
}
