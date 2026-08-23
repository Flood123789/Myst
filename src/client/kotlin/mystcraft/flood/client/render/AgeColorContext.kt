package mystcraft.flood.client.render

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.client.cache.ClientAgeCache
import mystcraft.flood.generation.profile.AgeProfile
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import net.minecraft.world.BlockRenderView
import net.minecraft.world.World
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Resolves an Age palette from the level that owns a render view. */
object AgeColorContext {
    private const val DH_PACKAGE = "com.seibel.distanthorizons."
    private const val SODIUM_PACKAGE = "me.jellysquid.mods.sodium."
    private const val MODERN_SODIUM_PACKAGE = "net.caffeinemc.mods.sodium."

    private sealed interface ViewAccessor {
        fun world(view: BlockRenderView): World?
    }

    private data class DistantHorizonsViewAccessor(
        val levelWrapperField: Field,
        val unwrapMethod: Method
    ) : ViewAccessor {
        override fun world(view: BlockRenderView): World? {
            val wrapper = runCatching { levelWrapperField.get(view) }.getOrNull() ?: return null
            return runCatching { unwrapMethod.invoke(wrapper) as? World }.getOrNull()
        }
    }

    private data class WorldFieldAccessor(val worldField: Field) : ViewAccessor {
        override fun world(view: BlockRenderView): World? =
            runCatching { worldField.get(view) as? World }.getOrNull()
    }

    private data class WorldMethodAccessor(val method: Method) : ViewAccessor {
        override fun world(view: BlockRenderView): World? =
            runCatching { method.invoke(view) as? World }.getOrNull()
    }

    private object UnsupportedViewAccessor : ViewAccessor {
        override fun world(view: BlockRenderView): World? = null
    }

    private val viewAccessors = object : ClassValue<ViewAccessor>() {
        override fun computeValue(type: Class<*>): ViewAccessor {
            val worldMethod = type.methods.firstOrNull {
                World::class.java.isAssignableFrom(it.returnType) && it.parameterCount == 0
            }
            if (worldMethod != null && worldMethod.trySetAccessible()) {
                return WorldMethodAccessor(worldMethod)
            }

            val worldField = generateSequence(type) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { World::class.java.isAssignableFrom(it.type) }
            if (worldField != null && worldField.trySetAccessible()) {
                return WorldFieldAccessor(worldField)
            }

            if (type.name.startsWith(SODIUM_PACKAGE) || type.name.startsWith(MODERN_SODIUM_PACKAGE)) {
                return UnsupportedViewAccessor
            }

            if (!type.name.startsWith(DH_PACKAGE)) return UnsupportedViewAccessor

            val field = generateSequence(type) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { it.name == "clientLevelWrapper" }
                ?: return UnsupportedViewAccessor
            if (!field.trySetAccessible()) return UnsupportedViewAccessor

            val unwrap = field.type.methods.firstOrNull {
                it.name == "getWrappedMcObject" && it.parameterCount == 0
            } ?: return UnsupportedViewAccessor

            return DistantHorizonsViewAccessor(field, unwrap)
        }
    }

    @JvmStatic
    fun getProfile(view: BlockRenderView?): AgeProfile? {
        val ageId = getAgeId(view) ?: return null
        return ClientAgeCache.getProperties(ageId)
    }

    @JvmStatic
    fun getAgeId(view: BlockRenderView?): Identifier? {
        val world: World? = when (view) {
            is World -> view
            null -> null
            else -> viewAccessors.get(view.javaClass).world(view)
        } ?: runCatching {
            MinecraftClient.getInstance().world
        }.getOrNull()

        val id = world?.registryKey?.value ?: return null
        return id.takeIf { it.namespace == MystcraftReforged.MOD_ID }
    }
}
