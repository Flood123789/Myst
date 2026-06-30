package mystcraft.flood.client.network

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.entity.BookStandBlockEntity
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.client.gui.DescriptiveBookScreen
import mystcraft.flood.client.gui.LinkingBookScreen
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.mixin.client.ClientPlayNetworkHandlerAccessor
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.util.Hand
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys

object ClientMessages {
    fun registerS2CPackets() {
        MystcraftReforged.LOGGER.info("[CLIENT-NET] Registering dimension sync receiver.")
        
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.DIMENSION_SYNC) { client, handler, buf, _ ->
            val ageId = buf.readIdentifier()
            val json = buf.readString(32767)
            val profile = AgeProfile.fromJson(json)
            
            val worldKey = RegistryKey.of(RegistryKeys.WORLD, ageId)
            
            client.execute {
                try {
                    // 1. Store the JSON profile in our client-side cache
                    ClientAgeCache.update(ageId, profile)
                    
                    // 2. Inject into the client's locked world list so the renderer doesn't panic
                    val accessor = handler as ClientPlayNetworkHandlerAccessor
                    val currentKeys = accessor.`mystcraft$getWorldKeys`().toMutableSet()
                    currentKeys.add(worldKey)
                    accessor.`mystcraft$setWorldKeys`(currentKeys)

                    if (client.world?.registryKey == worldKey) {
                        client.worldRenderer.reload()
                    }
                    
                } catch (e: Exception) {
                    MystcraftReforged.LOGGER.error("[CLIENT-NET] FATAL ERROR during injection: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.OPEN_DESCRIPTIVE_BOOK) { client, _, buf, _ ->
            val stack = buf.readItemStack()
            val openedFromStand = buf.readBoolean()
            val standPos = if (openedFromStand) buf.readBlockPos() else null
            val hand = if (openedFromStand) null else buf.readEnumConstant(Hand::class.java)
            val hasProfile = buf.readBoolean()
            val profileJson = if (hasProfile) buf.readString(32767) else null

            client.execute {
                val ageId = stack.nbt?.getString("Age_ID")?.let(net.minecraft.util.Identifier::tryParse)
                if (ageId != null && profileJson != null) {
                    ClientAgeCache.update(ageId, AgeProfile.fromJson(profileJson))
                }
                client.setScreen(DescriptiveBookScreen(stack, hand, standPos))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.OPEN_LINKING_BOOK) { client, _, buf, _ ->
            val stack = buf.readItemStack()
            val hand = buf.readEnumConstant(Hand::class.java)

            client.execute {
                client.setScreen(LinkingBookScreen(stack, hand))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.BOOK_STAND_SYNC) { client, _, buf, _ ->
            val pos = buf.readBlockPos()
            val stack = buf.readItemStack()

            client.execute {
                val blockEntity = client.world?.getBlockEntity(pos) as? BookStandBlockEntity ?: return@execute
                blockEntity.applySyncedBook(stack)
            }
        }
    }
}
