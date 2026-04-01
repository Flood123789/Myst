package mystcraft.flood.mixin;

import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public class AgeLogicMixin {

    // 1. Stops the Overworld from dictating our time
    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void mystcraft$cancelVanillaTickTime(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            ci.cancel(); 
        }
    }

    // 2. THE NEW FIX: Forces the world to ALWAYS use the JSON seed
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void mystcraft$enforceAgeSeed(CallbackInfoReturnable<Long> cir) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            // Grab the profile from the manager and return its specific seed
            AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
            cir.setReturnValue(profile.getSeed());
        }
    }
}