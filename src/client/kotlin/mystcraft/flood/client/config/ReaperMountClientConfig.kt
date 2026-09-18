package mystcraft.flood.client.config

import com.google.gson.GsonBuilder
import mystcraft.flood.MystcraftReforged
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ReaperMountClientSettings(
    var bodyTrackingCamera: Boolean = true,
    var cameraRollStrength: Double = 1.0,
    var seatFollowsBody: Boolean = true
)

/**
 * Client-only accessibility switches for riding a bound Reaper.
 *
 * A mount that walks on ceilings turns the horizon upside down, and a camera that follows it
 * there is genuinely unpleasant for anyone prone to motion sickness. None of this affects the
 * creature's behaviour or anything another player sees, so it is kept per client rather than in
 * the shared balance config, and it is deliberately reachable from the mod list rather than only
 * from a file: someone who needs to turn it off usually needs to turn it off right now.
 */
object ReaperMountClientConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path get() = FabricLoader.getInstance().configDir.resolve("mystcraft-reforged-reaper-mount.json")

    @Volatile
    private var current = ReaperMountClientSettings()

    fun load() {
        current = try {
            if (Files.exists(path)) {
                gson.fromJson(Files.readString(path), ReaperMountClientSettings::class.java)
                    ?: ReaperMountClientSettings()
            } else {
                ReaperMountClientSettings()
            }
        } catch (error: Exception) {
            MystcraftReforged.LOGGER.error("Could not read {}; using defaults", path, error)
            ReaperMountClientSettings()
        }
        save()
    }

    /** A copy callers may edit freely; nothing takes effect until [apply]. */
    fun snapshot(): ReaperMountClientSettings = current.copy()

    fun apply(settings: ReaperMountClientSettings) {
        current = settings.copy(cameraRollStrength = settings.cameraRollStrength.coerceIn(0.0, 1.0))
        save()
    }

    @JvmStatic
    fun bodyTrackingCameraEnabled(): Boolean = current.bodyTrackingCamera

    /**
     * How much of the body's roll the view actually takes, 0 to 1.
     *
     * Zero keeps the view level with the world while the seat still tracks the creature, which is
     * the setting that makes a ceiling ride tolerable without giving up the mount entirely.
     */
    @JvmStatic
    fun cameraRollStrength(): Double = current.cameraRollStrength.coerceIn(0.0, 1.0)

    /** Whether the rider is placed on the drawn shell rather than on the raw hitbox. */
    @JvmStatic
    fun seatFollowsBody(): Boolean = current.seatFollowsBody

    @Synchronized
    private fun save() {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temporary, gson.toJson(current))
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
