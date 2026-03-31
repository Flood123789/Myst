package mystcraft.flood.symbol

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

data class AgeSymbol(
    val id: Identifier,
    val instability: Int,
    val category: Category
) {
    enum class Category { TERRAIN, BIOME, FEATURE, MODIFIER }

    companion object {
        val CODEC: Codec<AgeSymbol> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(AgeSymbol::id),
                Codec.INT.fieldOf("instability").forGetter(AgeSymbol::instability),
                Codec.STRING.xmap({ Category.valueOf(it) }, { it.name }).fieldOf("category").forGetter(AgeSymbol::category)
            ).apply(instance, ::AgeSymbol)
        }
    }
}