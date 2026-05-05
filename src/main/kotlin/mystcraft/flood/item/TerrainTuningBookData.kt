package mystcraft.flood.item

import mystcraft.flood.generation.profile.TerrainTuningProfile
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement

object TerrainTuningBookData {
    private const val KEY = "TerrainTuning"

    fun get(stack: ItemStack): TerrainTuningProfile {
        val root = stack.nbt ?: return TerrainTuningProfile()
        if (!root.contains(KEY)) return TerrainTuningProfile()
        val tuning = root.getCompound(KEY)
        return TerrainTuningProfile(
            terrainTurbulence = tuning.readNullableInt("TerrainTurbulence"),
            seaLevel = tuning.readNullableInt("SeaLevel"),
            caveDensity = tuning.readNullableInt("CaveDensity"),
            biomeSize = tuning.readNullableInt("BiomeSize"),
            verticalRange = tuning.readNullableInt("VerticalRange"),
            superFlat = tuning.getBoolean("SuperFlat"),
            noMobs = tuning.getBoolean("NoMobs"),
            caveWorld = tuning.getBoolean("CaveWorld"),
            noAquifers = tuning.getBoolean("NoAquifers")
        ).normalized()
    }

    fun set(stack: ItemStack, tuning: TerrainTuningProfile) {
        val normalized = tuning.normalized()
        if (!normalized.isEdited()) {
            clear(stack)
            return
        }

        val root = stack.orCreateNbt
        val tuningNbt = NbtCompound()
        tuningNbt.writeNullableInt("TerrainTurbulence", normalized.terrainTurbulence)
        tuningNbt.writeNullableInt("SeaLevel", normalized.seaLevel)
        tuningNbt.writeNullableInt("CaveDensity", normalized.caveDensity)
        tuningNbt.writeNullableInt("BiomeSize", normalized.biomeSize)
        tuningNbt.writeNullableInt("VerticalRange", normalized.verticalRange)

        if (normalized.superFlat) tuningNbt.putBoolean("SuperFlat", true)
        if (normalized.noMobs) tuningNbt.putBoolean("NoMobs", true)
        if (normalized.caveWorld) tuningNbt.putBoolean("CaveWorld", true)
        if (normalized.noAquifers) tuningNbt.putBoolean("NoAquifers", true)

        root.put(KEY, tuningNbt)
    }

    fun clear(stack: ItemStack) {
        stack.nbt?.remove(KEY)
        if (stack.nbt?.isEmpty == true) {
            stack.nbt = null
        }
    }

    fun summarize(tuning: TerrainTuningProfile): List<String> {
        if (!tuning.isEdited()) return emptyList()

        return buildList {
            tuning.terrainTurbulence?.let { add("terrain turbulence ${it}/16") }
            tuning.seaLevel?.let { add("sea level ${it}/16") }
            tuning.caveDensity?.let { add("cave density ${it}/16") }
            tuning.biomeSize?.let { add("biome breadth ${it}/16") }
            tuning.verticalRange?.let { add("vertical reach ${it}/16") }
            if (tuning.superFlat) add("superflat script")
            if (tuning.noMobs) add("quiet spawning")
            if (tuning.caveWorld) add("cave world shaping")
            if (tuning.noAquifers) add("dry caverns")
        }
    }

    private fun NbtCompound.readNullableInt(key: String): Int? =
        if (contains(key, NbtElement.INT_TYPE.toInt())) getInt(key) else null

    private fun NbtCompound.writeNullableInt(key: String, value: Int?) {
        if (value != null) putInt(key, value)
    }
}
