package mystcraft.flood.item

import mystcraft.flood.generation.AgeLifecycleManager
import mystcraft.flood.entity.DescriptiveBookEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.world.World
import java.util.UUID

object AgeBookIntegrity {
    const val LOST_BOOK_INSTABILITY = 200
    private const val ANCHOR_RELEASE_UNTIL = "MystcraftAnchorReleaseUntil"
    private const val BOOK_LINEAGE_ID = "MystcraftBookLineageId"
    private const val AGE_ID_KEY = "Age_ID"

    @JvmStatic
    fun isLinkedDescriptiveBook(stack: ItemStack): Boolean =
        stack.item === ModItems.DESCRIPTIVE_BOOK && stack.nbt?.contains(AGE_ID_KEY) == true

    @JvmStatic
    fun isDescriptiveBook(stack: ItemStack): Boolean =
        stack.item === ModItems.DESCRIPTIVE_BOOK

    @JvmStatic
    fun isLinkingBook(stack: ItemStack): Boolean =
        stack.item === ModItems.LINKING_BOOK

    @JvmStatic
    fun isAnchorableBook(stack: ItemStack): Boolean =
        isDescriptiveBook(stack) || isLinkingBook(stack)

    @JvmStatic
    fun ensureTrackedLinkedBook(stack: ItemStack): String? {
        val identity = linkedIdentity(stack) ?: return null
        val existing = identity.lineageId
        if (existing != null) return existing

        val lineage = UUID.randomUUID().toString()
        stack.orCreateNbt.putString(BOOK_LINEAGE_ID, lineage)
        return lineage
    }

    @JvmStatic
    fun getLinkedBookLineage(stack: ItemStack): String? =
        stack.nbt?.getString(BOOK_LINEAGE_ID)?.takeIf { it.isNotBlank() }

    @JvmStatic
    fun shouldAnchorDroppedBook(world: World, stack: ItemStack): Boolean {
        if (!isAnchorableBook(stack)) return false
        val releaseUntil = stack.nbt?.getLong(ANCHOR_RELEASE_UNTIL) ?: return true
        return world.time >= releaseUntil
    }

    @JvmStatic
    fun markReleasedFromAnchor(stack: ItemStack, world: World, graceTicks: Long = 40L): ItemStack {
        val marked = stack.copy()
        val nbt = marked.orCreateNbt
        nbt.putLong(ANCHOR_RELEASE_UNTIL, world.time + graceTicks)
        return marked
    }

    @JvmStatic
    fun clearAnchorReleaseMarker(stack: ItemStack) {
        val nbt = stack.nbt ?: return
        nbt.remove(ANCHOR_RELEASE_UNTIL)
        if (nbt.isEmpty) {
            stack.nbt = null
        }
    }

    @JvmStatic
    fun handleLostLinkedBook(world: World, stack: ItemStack, excludedEntityId: Int = -1): Boolean {
        val server = (world as? ServerWorld)?.server ?: return false
        val identity = linkedIdentity(stack) ?: return false
        ensureTrackedLinkedBook(stack)
        if (hasSurvivingCopy(server, identity, excludedEntityId)) {
            return false
        }
        return AgeLifecycleManager.addInstability(server, identity.ageId, LOST_BOOK_INSTABILITY)
    }

    private fun hasSurvivingCopy(
        server: net.minecraft.server.MinecraftServer,
        identity: LinkedBookIdentity,
        excludedEntityId: Int
    ): Boolean {
        server.playerManager.playerList.forEach { player ->
            if (inventoryContains(player.inventory, identity)) return true
            if (inventoryContains(player.enderChestInventory, identity)) return true
        }

        server.worlds.forEach { world ->
            val foundEntity = world.iterateEntities().any { entity ->
                when (entity) {
                    is ItemEntity -> entity.id != excludedEntityId && matchesIdentity(entity.stack, identity)
                    is DescriptiveBookEntity -> entity.id != excludedEntityId && matchesIdentity(entity.getStoredBook(), identity)
                    else -> false
                }
            }
            if (foundEntity) return true
        }

        return false
    }

    private fun inventoryContains(inventory: Inventory, identity: LinkedBookIdentity): Boolean {
        for (slot in 0 until inventory.size()) {
            if (matchesIdentity(inventory.getStack(slot), identity)) {
                return true
            }
        }
        return false
    }

    private fun matchesIdentity(stack: ItemStack, identity: LinkedBookIdentity): Boolean {
        val other = linkedIdentity(stack) ?: return false
        return when {
            identity.lineageId != null && other.lineageId != null -> identity.lineageId == other.lineageId
            else -> identity.ageId == other.ageId
        }
    }

    private fun linkedIdentity(stack: ItemStack): LinkedBookIdentity? {
        if (!isLinkedDescriptiveBook(stack)) return null
        val ageId = stack.nbt?.getString(AGE_ID_KEY)?.takeIf { it.isNotBlank() }?.let(Identifier::tryParse) ?: return null
        val lineageId = stack.nbt?.getString(BOOK_LINEAGE_ID)?.takeIf { it.isNotBlank() }
        return LinkedBookIdentity(ageId, lineageId)
    }

    private data class LinkedBookIdentity(
        val ageId: Identifier,
        val lineageId: String?
    )
}
