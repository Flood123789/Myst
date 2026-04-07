package mystcraft.flood.mixin;

import com.mojang.datafixers.util.Either;
import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

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
}