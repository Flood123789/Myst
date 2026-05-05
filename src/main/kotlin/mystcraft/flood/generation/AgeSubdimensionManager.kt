package mystcraft.flood.generation

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.access.DimensionInjector
import mystcraft.flood.generation.profile.AgeDimensionRole
import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.world.World

object AgeSubdimensionManager {
    const val SUBDIMENSION_INSTABILITY_MAX = 25

    @JvmStatic
    fun roleOf(ageId: Identifier): AgeDimensionRole = when {
        ageId.namespace != MystcraftReforged.MOD_ID -> AgeDimensionRole.OVERWORLD
        ageId.path.endsWith(AgeDimensionRole.NETHER.suffix) -> AgeDimensionRole.NETHER
        ageId.path.endsWith(AgeDimensionRole.END.suffix) -> AgeDimensionRole.END
        else -> AgeDimensionRole.OVERWORLD
    }

    @JvmStatic
    fun rootIdOf(ageId: Identifier): Identifier {
        val role = roleOf(ageId)
        if (role == AgeDimensionRole.OVERWORLD) return ageId
        return Identifier(ageId.namespace, ageId.path.removeSuffix(role.suffix))
    }

    @JvmStatic
    fun isPrimaryAgeRealm(ageId: Identifier): Boolean =
        ageId.namespace == MystcraftReforged.MOD_ID && roleOf(ageId) == AgeDimensionRole.OVERWORLD

    @JvmStatic
    fun isDerivedSubdimension(ageId: Identifier): Boolean =
        ageId.namespace == MystcraftReforged.MOD_ID && roleOf(ageId) != AgeDimensionRole.OVERWORLD

    @JvmStatic
    fun derivedId(rootAgeId: Identifier, role: AgeDimensionRole): Identifier =
        if (role == AgeDimensionRole.OVERWORLD) {
            rootAgeId
        } else {
            Identifier(rootAgeId.namespace, rootAgeId.path + role.suffix)
        }

    @JvmStatic
    fun supportsSubdimensions(server: MinecraftServer, rootAgeId: Identifier): Boolean =
        supportsSubdimensions(AgeProfileManager.getOrGenerateProfile(server, rootIdOf(rootAgeId)))

    @JvmStatic
    fun supportsSubdimensions(profile: AgeProfile): Boolean =
        !profile.ageState.isSacrificed &&
            (
                !profile.stability.effectsEnabled ||
                    profile.stability.instabilityScore <= SUBDIMENSION_INSTABILITY_MAX
                )

    @JvmStatic
    fun ensureSubdimension(server: MinecraftServer, rootAgeId: Identifier, role: AgeDimensionRole): Identifier? {
        if (role == AgeDimensionRole.OVERWORLD) return rootAgeId
        val normalizedRootId = rootIdOf(rootAgeId)
        if (!supportsSubdimensions(server, normalizedRootId)) return null

        val derivedId = derivedId(normalizedRootId, role)
        (server as DimensionInjector).`mystcraft$injectDimension`(derivedId, emptyList())
        return derivedId
    }

    @JvmStatic
    fun routeNetherPortal(server: MinecraftServer, currentAgeId: Identifier): Identifier? {
        val role = roleOf(currentAgeId)
        val rootAgeId = rootIdOf(currentAgeId)
        return when (role) {
            AgeDimensionRole.OVERWORLD -> ensureSubdimension(server, rootAgeId, AgeDimensionRole.NETHER)
            AgeDimensionRole.NETHER -> rootAgeId
            AgeDimensionRole.END -> null
        }
    }

    @JvmStatic
    fun routeEndPortal(server: MinecraftServer, currentAgeId: Identifier): Identifier? {
        val role = roleOf(currentAgeId)
        return when (role) {
            AgeDimensionRole.OVERWORLD -> ensureSubdimension(server, currentAgeId, AgeDimensionRole.END)
            AgeDimensionRole.END -> rootIdOf(currentAgeId)
            AgeDimensionRole.NETHER -> null
        }
    }

    @JvmStatic
    fun portalCoordinateScale(sourceRole: AgeDimensionRole, targetRole: AgeDimensionRole): Double = when {
        sourceRole == AgeDimensionRole.OVERWORLD && targetRole == AgeDimensionRole.NETHER -> 1.0 / 8.0
        sourceRole == AgeDimensionRole.NETHER && targetRole == AgeDimensionRole.OVERWORLD -> 8.0
        else -> 1.0
    }

    @JvmStatic
    fun getWorld(server: MinecraftServer, ageId: Identifier): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, ageId))

    @JvmStatic
    fun refreshDerivedProfiles(server: MinecraftServer, rootAgeId: Identifier): List<Identifier> =
        listOf(
            derivedId(rootIdOf(rootAgeId), AgeDimensionRole.NETHER),
            derivedId(rootIdOf(rootAgeId), AgeDimensionRole.END)
        ).filter { derivedId ->
            val world = getWorld(server, derivedId)
            world != null || AgeProfileManager.profileExists(server, derivedId)
        }.onEach { derivedId ->
            AgeProfileManager.getOrGenerateProfile(server, derivedId)
            AgeProfileManager.save(server, derivedId)
        }

    @JvmStatic
    fun existingAgeFamily(server: MinecraftServer, ageId: Identifier): List<Identifier> {
        val rootId = rootIdOf(ageId)
        return buildList {
            add(rootId)
            addAll(refreshDerivedProfiles(server, rootId))
        }.distinct()
    }

    @JvmStatic
    fun isMystcraftRealm(worldKey: RegistryKey<World>): Boolean =
        worldKey.value.namespace == MystcraftReforged.MOD_ID
}
