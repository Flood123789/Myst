package mystcraft.flood.access;

import net.minecraft.nbt.NbtCompound;

import java.util.Map;

public interface PlayerSpawnMemoryAccess {
    Map<String, NbtCompound> mystcraft$getDimensionSpawns();
}
