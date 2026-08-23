package mystcraft.flood.client.render

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.color.world.BiomeColors
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockRenderView

object AgePlantTintHelper {
    private const val DEFAULT_FOLIAGE = 0x48B518
    private const val DEFAULT_BIRCH = 0x80A755
    private const val DEFAULT_SPRUCE = 0x619961
    private const val DEFAULT_GRASS = 0x91BD59
    private const val DEFAULT_WATER = 0x3F76E4

    fun getTintFor(block: Block, world: BlockRenderView?, pos: BlockPos?): Int {
        val profile = AgeColorContext.getProfile(world) ?: return vanillaTint(block, world, pos)
        val foliage = profile.colors.foliage and 0xFFFFFF
        val grass = profile.colors.grass and 0xFFFFFF
        val water = profile.colors.water and 0xFFFFFF

        return when (block) {
            Blocks.OAK_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.MANGROVE_LEAVES,
            Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.VINE,
            Blocks.LILY_PAD -> foliage

            Blocks.CHERRY_LEAVES -> -1

            Blocks.GRASS_BLOCK,
            Blocks.GRASS,
            Blocks.TALL_GRASS,
            Blocks.FERN,
            Blocks.LARGE_FERN,
            Blocks.POTTED_FERN,
            Blocks.SEAGRASS,
            Blocks.TALL_SEAGRASS,
            Blocks.SUGAR_CANE,
            Blocks.SMALL_DRIPLEAF,
            Blocks.BIG_DRIPLEAF,
            Blocks.BIG_DRIPLEAF_STEM,
            Blocks.MOSS_BLOCK,
            Blocks.MOSS_CARPET,
            Blocks.PINK_PETALS -> grass

            Blocks.WATER -> water
            else -> vanillaTint(block, world, pos)
        }
    }

    private fun vanillaTint(block: Block, world: BlockRenderView?, pos: BlockPos?): Int {
        return when (block) {
            Blocks.OAK_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES -> if (world != null && pos != null) BiomeColors.getFoliageColor(world, pos) else DEFAULT_FOLIAGE
            Blocks.BIRCH_LEAVES -> DEFAULT_BIRCH
            Blocks.SPRUCE_LEAVES -> DEFAULT_SPRUCE
            Blocks.MANGROVE_LEAVES,
            Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.VINE,
            Blocks.LILY_PAD,
            Blocks.SUGAR_CANE,
            Blocks.SMALL_DRIPLEAF,
            Blocks.BIG_DRIPLEAF,
            Blocks.BIG_DRIPLEAF_STEM -> if (world != null && pos != null) BiomeColors.getFoliageColor(world, pos) else DEFAULT_FOLIAGE

            Blocks.MELON_STEM,
            Blocks.ATTACHED_MELON_STEM,
            Blocks.PUMPKIN_STEM,
            Blocks.ATTACHED_PUMPKIN_STEM,
            Blocks.GRASS_BLOCK,
            Blocks.GRASS,
            Blocks.TALL_GRASS,
            Blocks.FERN,
            Blocks.LARGE_FERN,
            Blocks.POTTED_FERN,
            Blocks.SEAGRASS,
            Blocks.TALL_SEAGRASS,
            Blocks.MOSS_BLOCK,
            Blocks.MOSS_CARPET,
            Blocks.PINK_PETALS -> if (world != null && pos != null) BiomeColors.getGrassColor(world, pos) else DEFAULT_GRASS
            Blocks.WATER -> if (world != null && pos != null) BiomeColors.getWaterColor(world, pos) else DEFAULT_WATER

            else -> -1
        }
    }

    private fun shift(
        rgb: Int,
        hueShift: Float,
        saturationScale: Float,
        valueScale: Float,
        minSaturation: Float,
        maxSaturation: Float,
        minValue: Float,
        maxValue: Float
    ): Int {
        val hsv = rgbToHsv(rgb)
        val hue = rotateHue(hsv[0], hueShift)
        val saturation = MathHelper.clamp(hsv[1] * saturationScale, minSaturation, maxSaturation)
        val value = MathHelper.clamp(hsv[2] * valueScale, minValue, maxValue)
        return hsvToRgb(hue, saturation, value)
    }

    private fun rotateHue(hue: Float, delta: Float): Float {
        var value = hue + delta
        while (value < 0f) value += 1f
        while (value > 1f) value -= 1f
        return value
    }

    private fun rgbToHsv(rgb: Int): FloatArray {
        val r = ((rgb shr 16) and 0xFF) / 255.0f
        val g = ((rgb shr 8) and 0xFF) / 255.0f
        val b = (rgb and 0xFF) / 255.0f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = when {
            delta == 0f -> 0f
            max == r -> (((g - b) / delta) % 6f) / 6f
            max == g -> (((b - r) / delta) + 2f) / 6f
            else -> (((r - g) / delta) + 4f) / 6f
        }

        val normalizedHue = if (hue < 0f) hue + 1f else hue
        val saturation = if (max == 0f) 0f else delta / max
        val value = max
        return floatArrayOf(normalizedHue, saturation, value)
    }

    private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue * 6f) % 6f + 6f) % 6f
        val c = value * saturation
        val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
        val m = value - c

        val (r1, g1, b1) = when {
            h < 1f -> Triple(c, x, 0f)
            h < 2f -> Triple(x, c, 0f)
            h < 3f -> Triple(0f, c, x)
            h < 4f -> Triple(0f, x, c)
            h < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }
}
