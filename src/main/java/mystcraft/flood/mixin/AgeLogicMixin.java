package mystcraft.flood.mixin;

import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import mystcraft.flood.generation.ImmersivePortalsCompat;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Random;

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
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                mystcraft.flood.network.ModMessages.INSTANCE.sendDimensionTimeSync(
                    player, world.getRegistryKey().getValue(), profile
                );
            }
            for (ServerPlayerEntity player : world.getPlayers()) {
                WorldTimeUpdateS2CPacket packet = new WorldTimeUpdateS2CPacket(
                    world.getTime(),
                    profile.getTime().getVisibleTimeOfDay(),
                    !profile.getTime().getVisibleTimeFrozen()
                );
                if (!ImmersivePortalsCompat.INSTANCE.trySendWorldPacket(player, world, packet)) {
                    player.networkHandler.sendPacket(packet);
                }
            }
            
            // Cancel the event so vanilla doesn't overwrite our frozen LevelProperties
            ci.cancel(); 
        }
    }

    // 3. Redirect vanilla's resetWeather() so that sleeping clears rain in Ages with the same
    //    20% probability vanilla uses for the Overworld. The vanilla path writes to
    //    ServerWorldProperties which the Age system never reads — this ensures the Age weather
    //    profile is the one that gets updated and synced to clients.
    @Inject(method = "resetWeather", at = @At("HEAD"), cancellable = true)
    private void mystcraft$resetAgeWeather(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (!world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) return;

        AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(
                world.getServer(), world.getRegistryKey().getValue());
        mystcraft.flood.generation.profile.WeatherSettings weather = profile.getWeather();

        // Only intervene for natural (normal) weather — endless rain/storm and noWeather
        // ages manage their own state and should never be cleared by sleeping.
        if (!weather.isNormalWeather()) {
            ci.cancel();
            return;
        }

        // Vanilla rolls a 20% chance to clear weather after sleep (see ServerWorld source).
        // We replicate the same probability here so Ages feel identical to the Overworld.
        Random random = new Random(world.getSeed() ^ world.getTime());
        if (random.nextFloat() < 0.2f) {
            // Clear weather: pick a fresh clear period (12000–180000 ticks, same as vanilla)
            int clearDuration = 12000 + random.nextInt(168001);
            weather.setClearTicks(clearDuration);
            weather.setRainTicks(0);
            weather.setThunderTicks(0);
            weather.setCurrentRaining(false);
            weather.setCurrentThundering(false);
        } else if (weather.getCurrentRaining()) {
            // Keep raining but roll a fresh rain duration so we don't get stuck with 0 ticks
            if (weather.getRainTicks() <= 0) {
                weather.setRainTicks(6000 + random.nextInt(12001));
            }
            if (weather.getThunderTicks() <= 0) {
                weather.setThunderTicks(3000 + random.nextInt(6001));
            }
        }

        // Persist profile and push the updated weather state to all clients.
        AgeProfileManager.INSTANCE.save(world.getServer(), world.getRegistryKey().getValue());
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            mystcraft.flood.network.ModMessages.INSTANCE.sendDimensionSync(
                    player, world.getRegistryKey().getValue(), profile);
        }

        // Cancel vanilla so it does not corrupt ServerWorldProperties with mismatched ticks.
        ci.cancel();
    }

    // 4. Forces the world to ALWAYS use the JSON seed
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void mystcraft$enforceAgeSeed(CallbackInfoReturnable<Long> cir) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
            cir.setReturnValue(profile.getSeed());
        }
    }
}
