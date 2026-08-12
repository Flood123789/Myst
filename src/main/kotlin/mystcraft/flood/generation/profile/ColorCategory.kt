package mystcraft.flood.generation.profile

import kotlin.random.Random

enum class ColorCategory(
    val minHue: Float,
    val maxHue: Float,
    val minSat: Float,
    val maxSat: Float,
    val minVal: Float,
    val maxVal: Float,
    val defaultRgb: Int
) {
    RED(348f, 12f, 0.55f, 0.98f, 0.35f, 0.95f, 0xFF0000),
    ORANGE(20f, 40f, 0.60f, 0.98f, 0.50f, 0.95f, 0xFF8800),
    YELLOW(48f, 62f, 0.65f, 0.98f, 0.60f, 0.98f, 0xFFFF00),
    LIME(75f, 105f, 0.55f, 0.95f, 0.50f, 0.95f, 0x7FFF00),
    GREEN(115f, 145f, 0.50f, 0.95f, 0.35f, 0.90f, 0x00FF00),
    TEAL(155f, 172f, 0.45f, 0.90f, 0.40f, 0.90f, 0x008080),
    CYAN(173f, 192f, 0.50f, 0.95f, 0.50f, 0.98f, 0x00FFFF),
    LIGHT_BLUE(193f, 215f, 0.35f, 0.75f, 0.65f, 0.98f, 0x66CCFF),
    BLUE(216f, 248f, 0.55f, 0.98f, 0.35f, 0.95f, 0x0000FF),
    PURPLE(255f, 285f, 0.50f, 0.95f, 0.35f, 0.90f, 0x800080),
    MAGENTA(286f, 315f, 0.55f, 0.95f, 0.45f, 0.95f, 0xFF00FF),
    PINK(320f, 345f, 0.35f, 0.75f, 0.70f, 0.98f, 0xFF69B4),
    BROWN(15f, 32f, 0.45f, 0.85f, 0.25f, 0.55f, 0x8B4513),
    GRAY(0f, 360f, 0.00f, 0.12f, 0.30f, 0.75f, 0x808080),
    BLACK(0f, 360f, 0.00f, 0.25f, 0.05f, 0.22f, 0x000000),
    WHITE(0f, 360f, 0.00f, 0.10f, 0.88f, 1.00f, 0xFFFFFF);

    fun sample(rand: Random): Int {
        val h = if (minHue > maxHue) {
            val range = (360f - minHue) + maxHue
            val valIn = rand.nextFloat() * range
            var result = minHue + valIn
            if (result >= 360f) result -= 360f
            result
        } else {
            minHue + rand.nextFloat() * (maxHue - minHue)
        }
        val s = minSat + rand.nextFloat() * (maxSat - minSat)
        val v = minVal + rand.nextFloat() * (maxVal - minVal)
        return hsvToRgb(h / 360f, s, v)
    }

    fun sample(rand: java.util.Random): Int = sample(Random(rand.nextLong()))

    companion object {
        fun sampleVibrantRandom(rand: Random): Int {
            val vibrantCategories = entries.filter { it != BLACK && it != GRAY && it != WHITE }
            return vibrantCategories[rand.nextInt(vibrantCategories.size)].sample(rand)
        }

        fun sampleVibrantRandom(rand: java.util.Random): Int = sampleVibrantRandom(Random(rand.nextLong()))

        fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
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
}
