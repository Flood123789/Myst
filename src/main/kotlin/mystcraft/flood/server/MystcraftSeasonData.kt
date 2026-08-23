package mystcraft.flood.server

import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.PersistentState

/** Per-dimension ownership state for the optional Serene Seasons clock. */
class MystcraftSeasonData(
    var initialized: Boolean = false,
    var cycleEnabled: Boolean = true,
    var frozenSeasonTicks: Int = 0
) : PersistentState() {
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        nbt.putBoolean("Initialized", initialized)
        nbt.putBoolean("CycleEnabled", cycleEnabled)
        nbt.putInt("FrozenSeasonTicks", frozenSeasonTicks)
        return nbt
    }

    companion object {
        private const val DATA_ID = "mystcraft_serene_seasons"

        fun get(world: ServerWorld): MystcraftSeasonData = world.persistentStateManager.getOrCreate(
            { nbt ->
                MystcraftSeasonData(
                    initialized = nbt.getBoolean("Initialized"),
                    cycleEnabled = !nbt.contains("CycleEnabled") || nbt.getBoolean("CycleEnabled"),
                    frozenSeasonTicks = nbt.getInt("FrozenSeasonTicks")
                )
            },
            ::MystcraftSeasonData,
            DATA_ID
        )
    }
}
