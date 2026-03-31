package mystcraft.flood.cache

import mystcraft.flood.generation.profile.AgeProfile
import net.minecraft.util.Identifier

object ClientAgeCache {
    private val dimensionProfiles = mutableMapOf<Identifier, AgeProfile>()

    fun addProperties(id: Identifier, profile: AgeProfile) {
        dimensionProfiles[id] = profile
    }

    fun getProperties(id: Identifier): AgeProfile? = dimensionProfiles[id]

    // NEW: The Memory Wiper
    fun clear() {
        dimensionProfiles.clear()
    }
}