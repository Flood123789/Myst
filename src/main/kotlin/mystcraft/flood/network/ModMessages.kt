package mystcraft.flood.network

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfile
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

object ModMessages {
    val DIMENSION_SYNC = Identifier(MystcraftReforged.MOD_ID, "dimension_sync")

    fun sendDimensionSync(player: ServerPlayerEntity, ageId: Identifier, profile: AgeProfile) {
        try {
            val buf = PacketByteBufs.create()
            buf.writeIdentifier(ageId)
            buf.writeString(profile.toJson(), 32767) // Max string length
            ServerPlayNetworking.send(player, DIMENSION_SYNC, buf)
        } catch (e: Exception) {
            MystcraftReforged.LOGGER.error("Packet failure: ${e.message}")
        }
    }
}