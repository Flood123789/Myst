package mystcraft.flood.server

import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.PersistentState

class AgeTimeData(var ageTime: Long = 0L) : PersistentState() {

    // Serializes the data to the NBT file when the world saves
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        nbt.putLong("AgeTime", this.ageTime)
        return nbt
    }

    // Call this from your dimension's ServerTickEvent/Tick phase
    fun tick() {
        this.ageTime++
        this.markDirty() // Crucial: Tells the game this data needs to be saved to disk
    }

    companion object {
        // Deserializes the data from the NBT file when the world loads
        fun createFromNbt(nbt: NbtCompound): AgeTimeData {
            return AgeTimeData(nbt.getLong("AgeTime"))
        }

        // Helper method to grab this data specifically for the Age's ServerWorld
        fun get(world: ServerWorld): AgeTimeData {
            val persistentStateManager = world.persistentStateManager

            // In 1.20.1, getOrCreate takes (readFunction, supplier, id)
            return persistentStateManager.getOrCreate(
                { nbt: NbtCompound -> createFromNbt(nbt) }, 
                { AgeTimeData() },                          
                "myst_age_time"                             
            )
        }
    }
}