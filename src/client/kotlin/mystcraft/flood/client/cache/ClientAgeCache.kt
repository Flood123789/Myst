package mystcraft.flood.client.cache // Must match your directory structure!

import mystcraft.flood.generation.profile.AgeProfile
import net.minecraft.util.Identifier
import java.util.concurrent.ConcurrentHashMap

object ClientAgeCache {
    private val cache = ConcurrentHashMap<Identifier, AgeProfile>()

    fun update(id: Identifier, profile: AgeProfile) {
        cache[id] = profile
    }

    fun getProperties(id: Identifier): AgeProfile? = cache[id]

    fun clear() {
        cache.clear()
    }
}