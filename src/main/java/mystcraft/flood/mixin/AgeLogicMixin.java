package mystcraft.flood.mixin;

import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public class AgeLogicMixin {

    // 1. Cancel vanilla tickTime so it doesn't mess with our custom Kotlin tick!
    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void mystcraft$cancelVanillaTickTime(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            ci.cancel(); 
        }
    }

    // 2. NEW FIX: Intercept Vanilla's SleepManager (or /time commands) trying to skip the night!
    @Inject(method = "setTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void mystcraft$interceptSetTime(long timeOfDay, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
            long requestedTimeOfDay = timeOfDay % 24000L;
            
            if (profile.getTime().getFixedTime() == null) {
                // Extract what time of day vanilla WANTS it to be
                long currentAgeTime = profile.getTime().getLiveTimeOfDay();
                long currentDayStart = currentAgeTime - (currentAgeTime % 24000L);
                
                long newTime = currentDayStart + requestedTimeOfDay;
                
                // If the requested time is "behind" us in the current day, bump it to tomorrow!
                // (This ensures sleeping always skips forward to the next morning, never backward)
                if (newTime <= currentAgeTime) {
                    newTime += 24000L;
                }

                // Update our custom profile
                profile.getTime().setLiveTimeOfDay(newTime);
                profile.getTime().setTemporaryTimeOverride(null);
            } else {
                profile.getTime().setTemporaryTimeOverride(requestedTimeOfDay);
            }
                
            // Force a network sync so the client sky instantly jumps.
            for (ServerPlayerEntity player : world.getPlayers()) {
                player.networkHandler.sendPacket(new WorldTimeUpdateS2CPacket(
                    world.getTime(),
                    profile.getTime().getVisibleTimeOfDay(),
                    !profile.getTime().getVisibleTimeFrozen()
                ));
            }
            
            // Cancel the event so vanilla doesn't overwrite our frozen LevelProperties
            ci.cancel(); 
        }
    }

    // 3. Forces the world to ALWAYS use the JSON seed
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void mystcraft$enforceAgeSeed(CallbackInfoReturnable<Long> cir) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
            cir.setReturnValue(profile.getSeed());
        }
    }
}
