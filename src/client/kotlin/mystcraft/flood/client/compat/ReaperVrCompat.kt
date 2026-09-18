package mystcraft.flood.client.compat

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import java.lang.reflect.Method

/**
 * Detects whether the local player is actually in VR, so the mount stops doing things that are
 * fine on a monitor and harmful in a headset.
 *
 * Reached by reflection through Vivecraft's published `VRAPI`, which keeps Vivecraft an optional
 * dependency and keeps this mod clear of its LGPL: nothing is linked at compile time and no code
 * is borrowed. Vivecraft's own class and method names are not remapped between development and
 * production, so a name lookup is stable in both; only the Minecraft parameter type is remapped,
 * and that happens consistently on both sides of the call.
 *
 * The result is cached per tick rather than per call. `isVRPlayer` is cheap, but the camera and
 * input paths would otherwise hit it several times a frame for an answer that cannot change
 * within one.
 */
object ReaperVrCompat {

    private const val VIVECRAFT_MOD_ID = "vivecraft"
    private const val VR_API_CLASS = "org.vivecraft.api.VRAPI"

    private var cachedTick = -1L
    private var cachedResult = false

    /**
     * True when the local player is being rendered through a headset.
     *
     * Two things are switched off by this. Artificial roll is the important one: rolling the
     * horizon of a headset that is not physically rolling is among the most reliable ways to make
     * someone ill, and it is a rule of VR comfort that the view's up axis belongs to the player's
     * actual neck rather than to anything in the world. Vivecraft also composes its own per-eye
     * view matrices, so the injection this mod uses on the flat pipeline is not the authority
     * there in any case.
     *
     * The second is the surface-relative look frame. On a monitor the aim is a pair of numbers
     * this mod may redefine; in VR it is where the player is physically looking, and rotating
     * that into the creature's frame would fight the headset.
     */
    @JvmStatic
    fun isInVr(): Boolean {
        val client = MinecraftClient.getInstance() ?: return false
        val player = client.player ?: return false

        val now = client.world?.time ?: 0L
        if (now == cachedTick) return cachedResult

        cachedTick = now
        cachedResult = queryVrPlayer(player)
        return cachedResult
    }

    /** True when Vivecraft is present at all, whether or not VR is currently active. */
    val isVivecraftLoaded: Boolean by lazy {
        FabricLoader.getInstance().isModLoaded(VIVECRAFT_MOD_ID)
    }

    private fun queryVrPlayer(player: PlayerEntity): Boolean {
        val instance = vrApiInstance ?: return false
        val method = isVrPlayerMethod ?: return false
        return runCatching { method.invoke(instance, player) as? Boolean ?: false }.getOrDefault(false)
    }

    private val vrApiClass: Class<*>? by lazy {
        if (!isVivecraftLoaded) return@lazy null
        runCatching { Class.forName(VR_API_CLASS) }.getOrNull()
    }

    private val vrApiInstance: Any? by lazy {
        val type = vrApiClass ?: return@lazy null
        runCatching { type.getMethod("instance").invoke(null) }.getOrNull()
    }

    private val isVrPlayerMethod: Method? by lazy {
        val type = vrApiClass ?: return@lazy null
        runCatching { type.getMethod("isVRPlayer", PlayerEntity::class.java) }.getOrNull()
    }
}
