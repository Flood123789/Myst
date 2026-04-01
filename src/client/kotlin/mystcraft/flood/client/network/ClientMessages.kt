package mystcraft.flood.client.network

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.client.cache.ClientAgeCache
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
                    
                    // FIX: We completely removed client.worldRenderer.reload() here!
                    
                } catch (e: Exception) {
                    MystcraftReforged.LOGGER.error("[CLIENT-NET] FATAL ERROR during injection: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}