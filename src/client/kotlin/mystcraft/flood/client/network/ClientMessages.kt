package mystcraft.flood.client.network

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.cache.ClientAgeCache
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.mixin.client.ClientPlayNetworkHandlerAccessor
import mystcraft.flood.network.ModMessages
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys

object ClientMessages {
    fun registerS2CPackets() {
        MystcraftReforged.LOGGER.info("[CLIENT-NET] Registering dimension sync receiver.")
        
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.DIMENSION_SYNC) { client, handler, buf, _ ->
            val ageId = buf.readIdentifier()
            
            // Read the JSON string we sent from the server instead of NBT
            val json = buf.readString(32767)
            val profile = AgeProfile.fromJson(json)
            
            MystcraftReforged.LOGGER.info("[CLIENT-NET] PACKET RECEIVED! Age ID: $ageId")
            
            val worldKey = RegistryKey.of(RegistryKeys.WORLD, ageId)
            
            client.execute {
                try {
                    // 1. Store the JSON profile in our client-side cache
                    ClientAgeCache.addProperties(ageId, profile)
                    
                    // 2. Inject into the client's locked world list
                    val accessor = handler as ClientPlayNetworkHandlerAccessor
                    val currentKeys = accessor.`mystcraft$getWorldKeys`().toMutableSet()
                    currentKeys.add(worldKey)
                    accessor.`mystcraft$setWorldKeys`(currentKeys)
                    
                    // 3. Force the sky/fog to update immediately
                    client.worldRenderer.reload()
                    
                    MystcraftReforged.LOGGER.info("[CLIENT-NET] SUCCESS: Synced atmosphere and worldKeys for $ageId")
                } catch (e: Exception) {
                    MystcraftReforged.LOGGER.error("[CLIENT-NET] FATAL ERROR during injection: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}