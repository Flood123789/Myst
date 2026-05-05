package mystcraft.flood.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerEntity.class)
public interface PlayerEntitySpawnAccessor {
    @Accessor("spawnPointPosition")
    void mystcraft$setSpawnPointPosition(BlockPos spawnPointPosition);

    @Accessor("spawnPointDimension")
    void mystcraft$setSpawnPointDimension(RegistryKey<World> spawnPointDimension);

    @Accessor("spawnAngle")
    void mystcraft$setSpawnAngle(float spawnAngle);

    @Accessor("spawnForced")
    void mystcraft$setSpawnForced(boolean spawnForced);
}
