package mystcraft.flood.client.cache // Must match your directory structure!

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfile
import net.minecraft.util.Identifier
import java.util.concurrent.ConcurrentHashMap

object ClientAgeCache {
    private val cache = ConcurrentHashMap<Identifier, AgeProfile>()

    fun update(id: Identifier, profile: AgeProfile) {
        cache[id] = profile
        // The celestial counts decide what the sky painter has to draw, and they are the first
        // thing worth checking when an Age's sky looks emptier than expected.
        MystcraftReforged.LOGGER.info(
            "Age {} sky: {} red suns, {} blue suns, {} moons, star density {}, {} sky modifiers",
            id,
            profile.time.sunRedCount,
            profile.time.sunBlueCount,
            profile.time.moonCount,
            profile.time.starDensity,
            profile.modifiers.size
        )
    }

    fun getProperties(id: Identifier): AgeProfile? = cache[id]

    fun clear() {
        cache.clear()
    }
}