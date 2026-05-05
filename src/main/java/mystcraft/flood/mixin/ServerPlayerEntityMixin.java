package mystcraft.flood.mixin;

import com.mojang.datafixers.util.Either;
import mystcraft.flood.access.PlayerSpawnMemoryAccess;
import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import mystcraft.flood.player.PlayerSpawnMemory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements PlayerSpawnMemoryAccess {
    @Unique
    private final Map<String, NbtCompound> mystcraft$dimensionSpawns = new HashMap<>();

    @Override
    public Map<String, NbtCompound> mystcraft$getDimensionSpawns() {
        return this.mystcraft$dimensionSpawns;
    }

    @Inject(method = "trySleep", at = @At("HEAD"), cancellable = true)
    private void mystcraft$interceptFixedTimeSleep(BlockPos pos, CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ServerWorld world = player.getServerWorld();
        
        if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
            
            if (profile.getTime().getFixedTime() != null) {
                // The time is static! Manually set their spawn point here.
                player.setSpawnPoint(world.getRegistryKey(), pos, player.getYaw(), false, true);
                
                // Send the custom lore excuse to the action bar
                player.sendMessage(Text.literal("§5The static fabric of this reality leaves you restless... you cannot sleep, but your respawn point is set."), true);
                
                // Cancel the sleep attempt completely
                cir.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.OTHER_PROBLEM));
            }
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void mystcraft$writeSpawnMemory(NbtCompound nbt, CallbackInfo ci) {
        if (this.mystcraft$dimensionSpawns.isEmpty()) return;

        NbtCompound root = new NbtCompound();
        for (Map.Entry<String, NbtCompound> entry : this.mystcraft$dimensionSpawns.entrySet()) {
            root.put(entry.getKey(), entry.getValue().copy());
        }
        nbt.put("MystcraftDimensionSpawns", root);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void mystcraft$readSpawnMemory(NbtCompound nbt, CallbackInfo ci) {
        this.mystcraft$dimensionSpawns.clear();
        if (!nbt.contains("MystcraftDimensionSpawns", 10)) return;

        NbtCompound root = nbt.getCompound("MystcraftDimensionSpawns");
        for (String key : root.getKeys()) {
            this.mystcraft$dimensionSpawns.put(key, root.getCompound(key).copy());
        }
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void mystcraft$copySpawnMemory(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        PlayerSpawnMemory.INSTANCE.copyPersistentSpawns(oldPlayer, (ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "setSpawnPoint", at = @At("TAIL"))
    private void mystcraft$rememberSpawnPoint(
            RegistryKey<World> dimension,
            BlockPos pos,
            float angle,
            boolean forced,
            boolean sendMessage,
            CallbackInfo ci
    ) {
        PlayerSpawnMemory.INSTANCE.rememberFromVanillaSetSpawn(
                (ServerPlayerEntity) (Object) this,
                dimension,
                pos,
                angle,
                forced
        );
    }
}
