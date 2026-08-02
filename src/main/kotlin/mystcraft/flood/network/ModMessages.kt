package mystcraft.flood.network

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.entity.BookStandBlockEntity
import mystcraft.flood.generation.AgeCurseManager
import mystcraft.flood.generation.AgeSubdimensionManager
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.gui.BookBinderScreenHandler
import mystcraft.flood.gui.EditingTableScreenHandler
import mystcraft.flood.gui.NotebookScreenHandler
import mystcraft.flood.gui.PrintingTableScreenHandler
import mystcraft.flood.gui.WritingDeskScreenHandler
import mystcraft.flood.item.BookPreviewData
import mystcraft.flood.item.DescriptiveBookItem
import mystcraft.flood.item.DisplayedBookHelper
import mystcraft.flood.item.LinkingBookItem
import mystcraft.flood.item.ModItems
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Common network contract and server-side packet handlers.
 *
 * `C2S` handlers treat packet data as an untrusted request: the active screen, held item, slot,
 * permissions, and values must be re-validated on the server before state changes. The `S2C`
 * helpers serialize authoritative profile snapshots for client rendering and book previews.
 */
object ModMessages {
    val DIMENSION_SYNC = Identifier(MystcraftReforged.MOD_ID, "dimension_sync")
    val DIMENSION_TIME_SYNC = Identifier(MystcraftReforged.MOD_ID, "dimension_time_sync")
    val OPEN_DESCRIPTIVE_BOOK = Identifier(MystcraftReforged.MOD_ID, "open_descriptive_book")
    val OPEN_LINKING_BOOK = Identifier(MystcraftReforged.MOD_ID, "open_linking_book")
    val ACTIVATE_DESCRIPTIVE_BOOK = Identifier(MystcraftReforged.MOD_ID, "activate_descriptive_book")
    val ACTIVATE_LINKING_BOOK = Identifier(MystcraftReforged.MOD_ID, "activate_linking_book")
    val RENAME_DESCRIPTIVE_BOOK = Identifier(MystcraftReforged.MOD_ID, "rename_descriptive_book")
    val UPDATE_DESCRIPTIVE_BOOK_PREVIEW = Identifier(MystcraftReforged.MOD_ID, "update_descriptive_book_preview")
    val UPDATE_LINKING_BOOK_PREVIEW = Identifier(MystcraftReforged.MOD_ID, "update_linking_book_preview")
    val BOOK_BINDER_SET_AGE_NAME = Identifier(MystcraftReforged.MOD_ID, "book_binder_set_age_name")
    val WRITING_DESK_SET_AGE_NAME = Identifier(MystcraftReforged.MOD_ID, "writing_desk_set_age_name")
    val WRITING_DESK_TRANSCRIBE = Identifier(MystcraftReforged.MOD_ID, "writing_desk_transcribe")
    val EDITING_TABLE_APPLY = Identifier(MystcraftReforged.MOD_ID, "editing_table_apply")
    val PRINTING_TABLE_CYCLE_SELECTION = Identifier(MystcraftReforged.MOD_ID, "printing_table_cycle_selection")
    val NOTEBOOK_SET_NAME = Identifier(MystcraftReforged.MOD_ID, "notebook_set_name")
    val BOOK_STAND_SYNC = Identifier(MystcraftReforged.MOD_ID, "book_stand_sync")

    fun sendDimensionSync(player: ServerPlayerEntity, ageId: Identifier, profile: AgeProfile) {
        try {
            AgeCurseManager.refresh(profile)
            val buf = PacketByteBufs.create()
            buf.writeIdentifier(ageId)
            buf.writeString(profile.toJson(), 32767) // Max string length
            writeAgeTime(buf, profile)
            ServerPlayNetworking.send(player, DIMENSION_SYNC, buf)
        } catch (e: Exception) {
            MystcraftReforged.LOGGER.error("Packet failure: ${e.message}")
        }
    }

    fun sendDimensionTimeSync(player: ServerPlayerEntity, ageId: Identifier, profile: AgeProfile) {
        try {
            val buf = PacketByteBufs.create()
            buf.writeIdentifier(ageId)
            writeAgeTime(buf, profile)
            ServerPlayNetworking.send(player, DIMENSION_TIME_SYNC, buf)
        } catch (e: Exception) {
            MystcraftReforged.LOGGER.error("Age time packet failure for $ageId: ${e.message}")
        }
    }

    private fun writeAgeTime(buf: net.minecraft.network.PacketByteBuf, profile: AgeProfile) {
        buf.writeLong(profile.time.visibleTimeOfDay)
        buf.writeFloat(profile.time.timeScale)
        buf.writeBoolean(profile.time.visibleTimeFrozen)
    }

    fun sendOpenDescriptiveBook(
        player: ServerPlayerEntity,
        stack: net.minecraft.item.ItemStack,
        hand: Hand? = null,
        standPos: BlockPos? = null
    ) {
        try {
            val buf = PacketByteBufs.create()
            buf.writeItemStack(stack)
            val openedFromStand = standPos != null
            buf.writeBoolean(openedFromStand)
            if (openedFromStand) {
                buf.writeBlockPos(standPos)
            } else {
                buf.writeEnumConstant(hand ?: Hand.MAIN_HAND)
            }

            val ageId = stack.nbt?.getString("Age_ID")?.let(Identifier::tryParse)
            if (ageId != null) {
                val profile = AgeProfileManager.getOrGenerateProfile(player.server, ageId)
                AgeCurseManager.refresh(profile)
                buf.writeBoolean(true)
                buf.writeString(profile.toJson(), 32767)
            } else {
                buf.writeBoolean(false)
            }

            ServerPlayNetworking.send(player, OPEN_DESCRIPTIVE_BOOK, buf)
        } catch (e: Exception) {
            MystcraftReforged.LOGGER.error("Open descriptive book packet failure: ${e.message}")
        }
    }

    fun sendOpenLinkingBook(player: ServerPlayerEntity, stack: net.minecraft.item.ItemStack, hand: Hand) {
        try {
            val buf = PacketByteBufs.create()
            buf.writeItemStack(stack)
            buf.writeEnumConstant(hand)
            ServerPlayNetworking.send(player, OPEN_LINKING_BOOK, buf)
        } catch (e: Exception) {
            MystcraftReforged.LOGGER.error("Open linking book packet failure: ${e.message}")
        }
    }

    fun sendBookStandSync(world: ServerWorld, pos: BlockPos, stack: ItemStack) {
        world.players.forEach { player ->
            val buf = PacketByteBufs.create()
            buf.writeBlockPos(pos)
            buf.writeItemStack(stack.copy())
            ServerPlayNetworking.send(player, BOOK_STAND_SYNC, buf)
        }
    }

    fun registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_DESCRIPTIVE_BOOK) { server, player, _, buf, _ ->
            val openedFromStand = buf.readBoolean()
            val hand = if (openedFromStand) null else buf.readEnumConstant(Hand::class.java)
            val standPos = if (openedFromStand) buf.readBlockPos() else null

            server.execute {
                if (openedFromStand) {
                    activateDisplayedDescriptiveBook(server, player, standPos)
                } else if (hand != null) {
                    val stack = player.getStackInHand(hand)
                    val item = resolveDescriptiveBook(stack) ?: return@execute
                    item.activate(player.world, player, stack)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_LINKING_BOOK) { server, player, _, buf, _ ->
            val hand = buf.readEnumConstant(Hand::class.java)

            server.execute {
                val stack = player.getStackInHand(hand)
                val item = stack.item as? LinkingBookItem ?: return@execute
                item.activate(player.world, player, stack)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RENAME_DESCRIPTIVE_BOOK) { server, player, _, buf, _ ->
            val openedFromStand = buf.readBoolean()
            val hand = if (openedFromStand) null else buf.readEnumConstant(Hand::class.java)
            val standPos = if (openedFromStand) buf.readBlockPos() else null
            val requestedName = buf.readString(64)

            server.execute {
                if (openedFromStand) {
                    renameDisplayedDescriptiveBook(server, player, standPos, requestedName)
                } else if (hand != null) {
                    val stack = player.getStackInHand(hand)
                    if (resolveDescriptiveBook(stack) != null) {
                        renameDescriptiveBookStack(server, stack, requestedName)
                        syncPlayerInventory(player)
                    }
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(UPDATE_DESCRIPTIVE_BOOK_PREVIEW) { server, player, _, buf, _ ->
            val openedFromStand = buf.readBoolean()
            val hand = if (openedFromStand) null else buf.readEnumConstant(Hand::class.java)
            val standPos = if (openedFromStand) buf.readBlockPos() else null

            server.execute {
                if (openedFromStand) {
                    val blockEntity = standPos?.let { player.serverWorld.getBlockEntity(it) as? BookStandBlockEntity } ?: return@execute
                    val stack = blockEntity.getBook()
                    if (resolveDescriptiveBook(stack) != null && BookPreviewData.refreshForStack(server, stack)) {
                        blockEntity.markBookDirty()
                    }
                } else if (hand != null) {
                    val stack = player.getStackInHand(hand)
                    if (resolveDescriptiveBook(stack) != null && BookPreviewData.refreshForStack(server, stack)) {
                        syncPlayerInventory(player)
                    }
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(UPDATE_LINKING_BOOK_PREVIEW) { server, player, _, buf, _ ->
            val hand = buf.readEnumConstant(Hand::class.java)

            server.execute {
                val stack = player.getStackInHand(hand)
                if (stack.item is LinkingBookItem && BookPreviewData.refreshForStack(server, stack)) {
                    syncPlayerInventory(player)
                }
            }
        }

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

        ServerPlayNetworking.registerGlobalReceiver(WRITING_DESK_SET_AGE_NAME) { server, player, _, buf, _ ->
            val syncId = buf.readVarInt()
            val requestedName = buf.readString(64)

            server.execute {
                val handler = player.currentScreenHandler
                if (handler is WritingDeskScreenHandler && handler.syncId == syncId) {
                    handler.setDraftAgeName(requestedName)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(WRITING_DESK_TRANSCRIBE) { server, player, _, buf, _ ->
            val syncId = buf.readVarInt()

            server.execute {
                val handler = player.currentScreenHandler
                if (handler is WritingDeskScreenHandler && handler.syncId == syncId) {
                    handler.transcribeNotebook(player)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(EDITING_TABLE_APPLY) { server, player, _, buf, _ ->
            val syncId = buf.readVarInt()

            server.execute {
                val handler = player.currentScreenHandler
                if (handler is EditingTableScreenHandler && handler.syncId == syncId) {
                    handler.applyTuning(player)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PRINTING_TABLE_CYCLE_SELECTION) { server, player, _, buf, _ ->
            val syncId = buf.readVarInt()
            val delta = buf.readVarInt()

            server.execute {
                val handler = player.currentScreenHandler
                if (handler is PrintingTableScreenHandler && handler.syncId == syncId) {
                    handler.cycleSelection(delta)
                }
            }
        }
    }

    private fun activateDisplayedDescriptiveBook(server: MinecraftServer, player: ServerPlayerEntity, standPos: BlockPos?) {
        if (standPos == null) return
        val blockEntity = player.serverWorld.getBlockEntity(standPos) as? BookStandBlockEntity ?: return
        val stack = blockEntity.getBook()
        val item = resolveDescriptiveBook(stack) ?: return
        item.activate(player.serverWorld, player, stack)
        blockEntity.markBookDirty()
    }

    private fun resolveDescriptiveBook(stack: net.minecraft.item.ItemStack): DescriptiveBookItem? =
        when (val item = stack.item) {
            is DescriptiveBookItem -> item
            else -> if (stack.nbt?.contains("Age_ID") == true) ModItems.DESCRIPTIVE_BOOK else null
        }

    private fun renameDisplayedDescriptiveBook(server: MinecraftServer, player: ServerPlayerEntity, standPos: BlockPos?, requestedName: String) {
        if (standPos == null) return
        val blockEntity = player.serverWorld.getBlockEntity(standPos) as? BookStandBlockEntity ?: return
        val stack = blockEntity.getBook()
        if (resolveDescriptiveBook(stack) == null) return
        renameDescriptiveBookStack(server, stack, requestedName)
        blockEntity.markBookDirty()
    }

    private fun renameDescriptiveBookStack(server: MinecraftServer, stack: net.minecraft.item.ItemStack, requestedName: String) {
        val trimmedName = requestedName.trim().take(64)
        DisplayedBookHelper.applyAgeBookName(stack, trimmedName)

        val ageId = stack.nbt?.getString("Age_ID")?.takeIf { it.isNotBlank() }?.let(Identifier::tryParse) ?: return
        val rootAgeId = AgeSubdimensionManager.rootIdOf(ageId)
        val profile = AgeProfileManager.getOrGenerateProfile(server, rootAgeId)
        profile.ageState.displayName = trimmedName.ifBlank { null }
        syncAgeFamily(server, rootAgeId)
    }

    fun syncAgeFamily(server: MinecraftServer, ageId: Identifier): List<Identifier> {
        val syncedIds = AgeSubdimensionManager.existingAgeFamily(server, ageId)
        syncedIds.forEach { AgeProfileManager.save(server, it) }
        syncedIds.forEach { syncAge(server, it) }
        return syncedIds
    }

    private fun syncPlayerInventory(player: ServerPlayerEntity) {
        player.inventory.markDirty()
        player.playerScreenHandler.sendContentUpdates()
        player.currentScreenHandler.sendContentUpdates()
    }

    private fun syncAge(server: MinecraftServer, ageId: Identifier) {
        val profile = AgeProfileManager.getOrGenerateProfile(server, ageId)
        server.playerManager.playerList.forEach { player ->
            sendDimensionSync(player, ageId, profile)
        }
    }
}
