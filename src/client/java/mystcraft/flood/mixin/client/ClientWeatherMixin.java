package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class ClientWeatherMixin {

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void mystcraft$isClientRaining(CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        if (world.isClient && world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
            if (profile != null) {
                cir.setReturnValue(profile.getWeather().isCurrentlyRaining());
            }
        }
    }

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void mystcraft$getClientRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        World world = (World) (Object) this;
        if (world.isClient && world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
            if (profile != null) {
                if (profile.getWeather().getNoWeather()) cir.setReturnValue(0.0f);
                else if (profile.getWeather().isCurrentlyRaining()) cir.setReturnValue(1.0f);
                else cir.setReturnValue(0.0f);
            }
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void mystcraft$getClientThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        World world = (World) (Object) this;
        if (world.isClient && world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
            if (profile != null) {
                if (profile.getWeather().isCurrentlyThundering()) cir.setReturnValue(1.0f);
                else cir.setReturnValue(0.0f);
            }
        }
    }
}
