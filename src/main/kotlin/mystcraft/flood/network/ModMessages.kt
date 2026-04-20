package mystcraft.flood.network

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.gui.BookBinderScreenHandler
import mystcraft.flood.gui.NotebookScreenHandler
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

object ModMessages {
    val DIMENSION_SYNC = Identifier(MystcraftReforged.MOD_ID, "dimension_sync")
    val BOOK_BINDER_SET_AGE_NAME = Identifier(MystcraftReforged.MOD_ID, "book_binder_set_age_name")
    val NOTEBOOK_SET_NAME = Identifier(MystcraftReforged.MOD_ID, "notebook_set_name")

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

    fun registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(BOOK_BINDER_SET_AGE_NAME) { server, player, _, buf, _ ->
            val syncId = buf.readVarInt()
            val requestedName = buf.readString(64)

            server.execute {
                val handler = player.currentScreenHandler
                if (handler is BookBinderScreenHandler && handler.syncId == syncId) {
                    handler.setDraftAgeName(requestedName)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(NOTEBOOK_SET_NAME) { server, player, _, buf, _ ->
            val syncId = buf.readVarInt()
            val requestedName = buf.readString(64)

            server.execute {
                val handler = player.currentScreenHandler
                if (handler is NotebookScreenHandler && handler.syncId == syncId) {
                    handler.setNotebookName(requestedName)
                }
            }
        }
    }
}
