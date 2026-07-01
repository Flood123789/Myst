package mystcraft.flood.mixin;

import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldDayNightMixin {

    // 1. Completely bypasses vanilla math to tell beds exactly when it's night!
    @Inject(method = "isDay", at = @At("HEAD"), cancellable = true)
    private void mystcraft$enforceAgeDay(CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            if (serverWorld.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
                AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(serverWorld.getServer(), serverWorld.getRegistryKey().getValue());
                
                long time = profile.getTime().getVisibleTimeOfDay();
                
                long timeOfDay = time % 24000L;
                boolean isDaytime = timeOfDay < 13000L || timeOfDay >= 23000L;
                cir.setReturnValue(isDaytime);
            }
        }
    }

    // 2. Tells daylight sensors, mob spawners, and other mechanics what time it is
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void mystcraft$enforceAgeTime(CallbackInfoReturnable<Long> cir) {
        World world = (World) (Object) this;
        
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            if (serverWorld.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
                AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(serverWorld.getServer(), serverWorld.getRegistryKey().getValue());
                cir.setReturnValue(profile.getTime().getVisibleTimeOfDay());
            }
        }
    }

    // 3. THE SUNSCREEN REMOVER: Tells the Server's Light Engine to blast the surface with UV rays!
    @Inject(method = "getAmbientDarkness", at = @At("HEAD"), cancellable = true)
    private void mystcraft$enforceAgeDarkness(CallbackInfoReturnable<Integer> cir) {
        World world = (World) (Object) this;
        
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            if (serverWorld.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
                AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(serverWorld.getServer(), serverWorld.getRegistryKey().getValue());
                
                long time = profile.getTime().getVisibleTimeOfDay();
                
                long timeOfDay = time % 24000L;
                boolean isDaytime = timeOfDay < 13000L || timeOfDay >= 23000L;
                
                // 0 = Bright Day (Zombies Burn), 11 = Pitch Black Night (Zombies Safe)
                cir.setReturnValue(isDaytime ? 0 : 11);
            }
        }
    }
}
