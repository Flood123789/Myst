package mystcraft.flood.entity

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import java.lang.reflect.Method

/** Optional target compatibility that does not make Whackdolls a required dependency. */
object ReaperTargetFilter {

    private const val WHACKDOLLS_MOD_ID = "whackdolls"
    private const val WHACKDOLLS_RAGDOLL_CLASS = "whackdolls.flood.entity.RagdollEntity"

    /** Whackdolls corpses are scenery, not living quarry or a source worth investigating. */
    fun isIgnoredEntity(entity: Entity): Boolean =
        isIgnoredEntityType(Registries.ENTITY_TYPE.getId(entity.type).toString())

    fun isIgnoredEntityType(entityTypeId: String): Boolean {
        val separator = entityTypeId.indexOf(':')
        if (separator <= 0 || separator == entityTypeId.lastIndex) return false
        val namespace = entityTypeId.substring(0, separator)
        val path = entityTypeId.substring(separator + 1)
        return namespace == WHACKDOLLS_MOD_ID && (path == "ragdoll" || path == "mob_ragdoll")
    }

    /**
     * A live Whackdolls body carries its hidden player along with the simulated chest. Without
     * this check a Reaper appears to target the corpse even though its actual target is that
     * invisible carrier player.
     */
    fun isTemporarilyRagdolled(player: PlayerEntity): Boolean {
        val serverPlayer = player as? ServerPlayerEntity ?: return false
        val method = whackdollsIsRagdolled ?: return false
        return runCatching { method.invoke(null, serverPlayer) as? Boolean ?: false }.getOrDefault(false)
    }

    private val whackdollsIsRagdolled: Method? by lazy {
        if (!FabricLoader.getInstance().isModLoaded(WHACKDOLLS_MOD_ID)) return@lazy null
        runCatching {
            Class.forName(WHACKDOLLS_RAGDOLL_CLASS)
                .getMethod("isRagdolled", ServerPlayerEntity::class.java)
        }.getOrNull()
    }
}
