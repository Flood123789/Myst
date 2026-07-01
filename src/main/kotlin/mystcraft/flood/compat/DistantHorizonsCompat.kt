package mystcraft.flood.compat

import mystcraft.flood.MystcraftReforged
import net.fabricmc.loader.api.FabricLoader
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object DistantHorizonsCompat {
    private const val DH_API = "com.seibel.distanthorizons.api.DhApi"
    private const val DH_DELAYED = "com.seibel.distanthorizons.api.DhApi\$Delayed"
    private const val SCAN_INTERVAL_NANOS = 2_000_000_000L

    private val loaded: Boolean by lazy {
        val loader = FabricLoader.getInstance()
        loader.isModLoaded("distanthorizons") || loader.isModLoaded("distant_horizons")
    }
    private val registeredLevels = Collections.synchronizedSet(mutableSetOf<String>())
    private val warningLogged = AtomicBoolean(false)
    private val activeLogged = AtomicBoolean(false)

    @Volatile
    private var nextScanNanos = 0L

    @Volatile
    private var disabled = false

    fun tick() {
        if (!loaded || disabled) return
        val now = System.nanoTime()
        if (now < nextScanNanos) return
        nextScanNanos = now + SCAN_INTERVAL_NANOS
        scanLoadedLevels()
    }

    fun clear() {
        registeredLevels.clear()
        nextScanNanos = 0L
    }

    private fun scanLoadedLevels() {
        try {
            val worldProxy = Class.forName(DH_DELAYED).getField("worldProxy").get(null) ?: return
            val worldLoaded = worldProxy.javaClass.methods.firstOrNull {
                it.name == "worldLoaded" && it.parameterCount == 0
            }
            if (worldLoaded != null && worldLoaded.invoke(worldProxy) != true) return

            val levels = worldProxy.javaClass.methods.firstOrNull {
                it.name == "getAllLoadedLevelWrappers" && it.parameterCount == 0
            }?.invoke(worldProxy) as? Iterable<*> ?: return

            val overrideRegister = Class.forName(DH_API).getField("worldGenOverrides").get(null) ?: return
            val registerMethod = overrideRegister.javaClass.methods.firstOrNull {
                it.name == "registerWorldGeneratorOverride" && it.parameterCount == 2
            } ?: return
            val generatorInterface = registerMethod.parameterTypes.getOrNull(1) ?: return

            for (level in levels) {
                if (level == null) continue
                val identityValues = readLevelIdentity(level)
                if (!isMystcraftAge(identityValues)) continue

                val identity = identityValues.firstOrNull { it.isNotBlank() }
                    ?: "level-${System.identityHashCode(level)}"
                if (!registeredLevels.add(identity)) continue

                val generator = createNoOpWorldGenerator(generatorInterface)
                val result = registerMethod.invoke(overrideRegister, level, generator)
                if (!apiResultSuccess(result)) {
                    registeredLevels.remove(identity)
                    continue
                }

                if (activeLogged.compareAndSet(false, true)) {
                    MystcraftReforged.LOGGER.info("Distant Horizons compat active: disabling DH distant generation inside Mystcraft ages.")
                }
            }
        } catch (_: ClassNotFoundException) {
            disabled = true
        } catch (throwable: Throwable) {
            if (warningLogged.compareAndSet(false, true)) {
                MystcraftReforged.LOGGER.warn(
                    "Distant Horizons compat could not install its Mystcraft age generator override. Continuing without DH compat.",
                    throwable
                )
            }
        }
    }

    private fun createNoOpWorldGenerator(generatorInterface: Class<*>): Any {
        val returnType = generatorInterface.methods.firstOrNull {
            it.name == "getReturnType" && it.parameterCount == 0
        }?.returnType
        val apiChunksReturnType = returnType?.takeIf { it.isEnum }?.let { enumValueOrNull(it, "API_CHUNKS") }

        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "generateChunks", "generateApiChunks", "generateLod" -> completedFutureValue(method.returnType)
                "preGeneratorTaskStart", "close" -> null
                "getSmallestDataDetailLevel", "getLargestDataDetailLevel" -> detailLevelValue(method.returnType)
                "runApiValidation" -> defaultValue(method.returnType)
                "getReturnType" -> apiChunksReturnType ?: defaultValue(method.returnType)
                "getPriority" -> priorityValue(method.returnType)
                "getDelayedSetupComplete" -> true
                "finishDelayedSetup" -> defaultValue(method.returnType)
                "toString" -> "MystcraftDistantHorizonsNoOpWorldGenerator"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        }

        return Proxy.newProxyInstance(
            generatorInterface.classLoader,
            arrayOf(generatorInterface),
            handler
        )
    }

    private fun readLevelIdentity(level: Any): List<String> {
        val names = listOf("getDimensionName", "getDhIdentifier", "getDhSaveFolder")
        return names.mapNotNull { methodName ->
            val method = level.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
                ?: return@mapNotNull null
            runCatching { method.invoke(level)?.toString() }.getOrNull()
        }
    }

    private fun isMystcraftAge(identityValues: List<String>): Boolean {
        return identityValues.any { raw ->
            val value = raw.lowercase()
            value.contains("${MystcraftReforged.MOD_ID}:age_") ||
                value.contains("${MystcraftReforged.MOD_ID}\\age_") ||
                value.contains("${MystcraftReforged.MOD_ID}/age_") ||
                (value.contains(MystcraftReforged.MOD_ID) && value.contains("age_"))
        }
    }

    private fun apiResultSuccess(result: Any?): Boolean {
        if (result == null) return true
        return runCatching { result.javaClass.getField("success").getBoolean(result) }.getOrDefault(true)
    }

    private fun enumValueOrNull(enumClass: Class<*>, name: String): Any? {
        return runCatching {
            enumClass.getMethod("valueOf", String::class.java).invoke(null, name)
        }.getOrNull()
    }

    private fun completedFutureValue(type: Class<*>): Any? {
        return if (CompletableFuture::class.java.isAssignableFrom(type)) {
            CompletableFuture.completedFuture<Void?>(null)
        } else {
            defaultValue(type)
        }
    }

    private fun detailLevelValue(type: Class<*>): Any? {
        return when (type) {
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 0.toByte()
            Short::class.javaPrimitiveType, Short::class.javaObjectType -> 0.toShort()
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
            else -> if (type.isEnum) {
                enumValueOrNull(type, "BLOCK") ?: enumValueOrNull(type, "CHUNK") ?: defaultValue(type)
            } else {
                defaultValue(type)
            }
        }
    }

    private fun priorityValue(type: Class<*>): Any? {
        return when (type) {
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 100.toByte()
            Short::class.javaPrimitiveType, Short::class.javaObjectType -> 1_000.toShort()
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> 1_000
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> 1_000L
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> 1_000.0f
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> 1_000.0
            else -> defaultValue(type)
        }
    }

    private fun defaultValue(type: Class<*>): Any? {
        if (!type.isPrimitive) return null
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0.0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }
}
