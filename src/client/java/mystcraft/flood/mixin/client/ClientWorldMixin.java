package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    
    // Intercepts the star fade-in and forces it to stay completely invisible!
    @Inject(method = "getStarBrightness", at = @At("HEAD"), cancellable = true)
    private void mystcraft$modifyStarBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
        ClientWorld world = (ClientWorld) (Object) this;
        
        if (world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
            if (profile != null && profile.getTime().getStarDensity() == 0) {
                cir.setReturnValue(0.0f);
            }
        }
    }
}