package mystcraft.flood.item

import mystcraft.flood.generation.profile.AgeProfile
import mystcraft.flood.generation.profile.AgeProfileManager
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import kotlin.math.max
import kotlin.math.min

data class BookPreviewSnapshot(
    val width: Int,
    val height: Int,
    val skyTopColor: Int,
    val skyBottomColor: Int,
    val pixels: IntArray
)

object BookPreviewData {
    private const val KEY_WIDTH = "PreviewWidth"
    private const val KEY_HEIGHT = "PreviewHeight"
    private const val KEY_SKY_TOP = "PreviewSkyTop"
    private const val KEY_SKY_BOTTOM = "PreviewSkyBottom"
    private const val KEY_PIXELS = "PreviewPixels"

    fun read(stack: ItemStack): BookPreviewSnapshot? {
        val nbt = stack.nbt ?: return null
        if (!nbt.contains(KEY_WIDTH) || !nbt.contains(KEY_HEIGHT) || !nbt.contains(KEY_PIXELS)) return null
        val width = nbt.getInt(KEY_WIDTH)
        val height = nbt.getInt(KEY_HEIGHT)
        val pixels = nbt.getIntArray(KEY_PIXELS)
        if (width <= 0 || height <= 0 || pixels.size != width * height) return null
        return BookPreviewSnapshot(
            width = width,
            height = height,
            skyTopColor = nbt.getInt(KEY_SKY_TOP),
            skyBottomColor = nbt.getInt(KEY_SKY_BOTTOM),
            pixels = pixels
        )
    }

    fun write(stack: ItemStack, snapshot: BookPreviewSnapshot) {
        val nbt = stack.orCreateNbt
        nbt.putInt(KEY_WIDTH, snapshot.width)
        nbt.putInt(KEY_HEIGHT, snapshot.height)
        nbt.putInt(KEY_SKY_TOP, snapshot.skyTopColor)
        nbt.putInt(KEY_SKY_BOTTOM, snapshot.skyBottomColor)
        nbt.putIntArray(KEY_PIXELS, snapshot.pixels)
    }

    fun refreshForStack(server: MinecraftServer, stack: ItemStack): Boolean {
        val nbt = stack.nbt ?: return false

        val ageId = nbt.getString("Age_ID").takeIf { it.isNotBlank() }?.let(Identifier::tryParse)
        if (ageId != null) {
            val worldKey = RegistryKey.of(RegistryKeys.WORLD, ageId)
            val world = server.getWorld(worldKey) ?: return false
            val profile = AgeProfileManager.getOrGenerateProfile(server, ageId)
            val anchorX = profile.ageState.surfaceSpawnX ?: 0
            val anchorY = (profile.ageState.surfaceSpawnY ?: world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, anchorX, profile.ageState.surfaceSpawnZ ?: 0))
                .coerceAtLeast(world.bottomY + 1)
            val anchorZ = profile.ageState.surfaceSpawnZ ?: 0
            val anchor = BlockPos(
                anchorX,
                anchorY,
                anchorZ
            )
            write(stack, capture(world, anchor, profile.colors.sky, profile.colors.fog, profile))
            return true
        }

        val dimId = nbt.getString("Dimension").takeIf { it.isNotBlank() }?.let(Identifier::tryParse) ?: return false
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, dimId)
        val world = server.getWorld(worldKey) ?: return false
        val anchor = BlockPos.ofFloored(nbt.getDouble("PosX"), nbt.getDouble("PosY"), nbt.getDouble("PosZ"))
        val (skyTop, skyBottom) = defaultSkyColors(world)
        val profile = if (dimId.namespace == "mystcraft-reforged") AgeProfileManager.getOrGenerateProfile(server, dimId) else null
        write(stack, capture(world, anchor, skyTop, skyBottom, profile))
        return true
    }

    private fun capture(world: ServerWorld, anchor: BlockPos, skyTopColor: Int, skyBottomColor: Int, profile: AgeProfile? = null): BookPreviewSnapshot {
        val width = 24
        val height = 18
        val horizonRows = 5
        val pixels = IntArray(width * height)
        val baseAnchorY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, anchor.x, anchor.z)
        val sampleRadius = 36

        for (py in 0 until height) {
            for (px in 0 until width) {
                val index = py * width + px
                if (py < horizonRows) {
                    val mix = py.toDouble() / max(1, horizonRows - 1).toDouble()
                    pixels[index] = blend(skyTopColor, skyBottomColor, mix)
                    continue
                }

                val worldX = anchor.x + (((px / (width - 1.0)) - 0.5) * sampleRadius * 2.0).toInt()
                val worldZ = anchor.z + ((((py - horizonRows) / (height - horizonRows - 1.0)) - 0.5) * sampleRadius * 2.0).toInt()
                val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ)
                val surfacePos = BlockPos(worldX, topY - 1, worldZ)
                val surfaceState = world.getBlockState(surfacePos)
                val baseColor = approximateBlockColor(surfaceState, profile)
                val heightDelta = (topY - baseAnchorY).coerceIn(-24, 24)
                val brightness = 0.82 + (heightDelta / 80.0)
                pixels[index] = shade(baseColor, brightness)
            }
        }

        return BookPreviewSnapshot(
            width = width,
            height = height,
            skyTopColor = skyTopColor,
            skyBottomColor = skyBottomColor,
            pixels = pixels
        )
    }

    private fun approximateBlockColor(state: BlockState, profile: AgeProfile? = null): Int {
        val path = net.minecraft.registry.Registries.BLOCK.getId(state.block).path
        return when {
            "water" in path -> profile?.colors?.water ?: 0x3F74C9
            "lava" in path -> profile?.colors?.fireLava ?: 0xFF6A00
            "prismarine" in path -> 0x62B0A6
            "deepslate" in path -> 0x43474D
            "netherrack" in path || "nether" in path -> 0x7A3E35
            "soul" in path -> 0x5A5672
            "end_stone" in path || "end" in path -> 0xD8D6A4
            "sand" in path || "sandstone" in path -> 0xD7C27D
            "terracotta" in path || "brick" in path || "mud" in path -> 0xA67355
            "grass" in path || "moss" in path -> profile?.colors?.grass ?: 0x5C9D49
            "leaves" in path || "vine" in path || "azalea" in path -> profile?.colors?.foliage ?: 0x4A8A3D
            "snow" in path || "ice" in path -> 0xDCEFFD
            "stone" in path || "cobble" in path || "andesite" in path || "diorite" in path || "granite" in path -> 0x8E929A
            "log" in path || "wood" in path || "planks" in path -> 0x8B6C46
            "mycel" in path -> 0x7E6277
            else -> 0x73815A
        }
    }

    private fun defaultSkyColors(world: ServerWorld): Pair<Int, Int> = when (world.registryKey.value.path) {
        "the_nether" -> 0x7B2C1D to 0x2A0B08
        "the_end" -> 0x55627D to 0x181B2D
        else -> 0x78A7FF to 0xC6E3FF
    }

    private fun shade(rgb: Int, brightness: Double): Int {
        val clamped = brightness.coerceIn(0.45, 1.35)
        val r = (((rgb shr 16) and 0xFF) * clamped).toInt().coerceIn(0, 255)
        val g = (((rgb shr 8) and 0xFF) * clamped).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * clamped).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun blend(a: Int, b: Int, t: Double): Int {
        val mix = t.coerceIn(0.0, 1.0)
        val r = (((a shr 16) and 0xFF) + ((((b shr 16) and 0xFF) - ((a shr 16) and 0xFF)) * mix)).toInt()
        val g = (((a shr 8) and 0xFF) + ((((b shr 8) and 0xFF) - ((a shr 8) and 0xFF)) * mix)).toInt()
        val blue = ((a and 0xFF) + (((b and 0xFF) - (a and 0xFF)) * mix)).toInt()
        return (r shl 16) or (g shl 8) or blue
    }
}
