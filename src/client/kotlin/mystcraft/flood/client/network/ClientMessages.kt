package mystcraft.flood.client.network

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.block.entity.BookStandBlockEntity
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.client.cache.ClientAgeTimeCache
import mystcraft.flood.client.gui.DescriptiveBookScreen
import mystcraft.flood.client.gui.LinkingBookScreen
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.compat.DistantHorizonsCompat
import mystcraft.flood.mixin.client.ClientPlayNetworkHandlerAccessor
import mystcraft.flood.mixin.client.LightmapTextureManagerAccessor
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.util.Hand
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys

/**
 * Installs receivers for server-authored Age state and book UI messages.
 *
 * Network callbacks may run off the render thread, so every cache, screen, world-key, or renderer
 * mutation is scheduled through `client.execute`. These caches are presentation state only.
 */
object ClientMessages {
    fun registerS2CPackets() {
        MystcraftReforged.LOGGER.info("[CLIENT-NET] Registering dimension sync receiver.")
        
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.DIMENSION_SYNC) { client, handler, buf, _ ->
            val ageId = buf.readIdentifier()
            val json = buf.readString(32767)
            val profile = AgeProfile.fromJson(json)
            val visibleTime = buf.readLong()
            val timeScale = buf.readFloat()
            val timeFrozen = buf.readBoolean()
            
            val worldKey = RegistryKey.of(RegistryKeys.WORLD, ageId)
            
            client.execute {
                try {
                    // Cache the snapshot before exposing its world key; render hooks can run as
                    // soon as the key becomes visible and expect profile/time data to exist.
                    ClientAgeCache.update(ageId, profile)
                    ClientAgeTimeCache.update(ageId, visibleTime, timeScale, timeFrozen)
                    (client.gameRenderer.lightmapTextureManager as LightmapTextureManagerAccessor)
                        .`mystcraft$setDirty`(true)
                    DistantHorizonsCompat.requestColorRefresh(ageId)
                    MystcraftReforged.LOGGER.info(
                        "[CLIENT-NET] Cached Age {} at time {} with sky #{}, fog #{}, water #{}",
                        ageId,
                        visibleTime,
                        profile.colors.sky.toString(16).padStart(6, '0'),
                        profile.colors.fog.toString(16).padStart(6, '0'),
                        profile.colors.water.toString(16).padStart(6, '0')
                    )
                    
                    // Dynamic worlds are absent from the immutable login packet. Extend the
                    // client's known-key set so vanilla accepts the later dimension transfer.
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

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.DIMENSION_TIME_SYNC) { client, _, buf, _ ->
            val ageId = buf.readIdentifier()
            val visibleTime = buf.readLong()
            val timeScale = buf.readFloat()
            val timeFrozen = buf.readBoolean()

            client.execute {
                ClientAgeTimeCache.update(ageId, visibleTime, timeScale, timeFrozen)
                (client.gameRenderer.lightmapTextureManager as LightmapTextureManagerAccessor)
                    .`mystcraft$setDirty`(true)
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
