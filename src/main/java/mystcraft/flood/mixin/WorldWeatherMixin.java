package mystcraft.flood.mixin;

import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldWeatherMixin {

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void mystcraft$isServerRaining(CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        if (!world.isClient && world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
            cir.setReturnValue(profile.getWeather().isCurrentlyRaining());
        }
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void mystcraft$isServerThundering(CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        if (!world.isClient && world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
            cir.setReturnValue(profile.getWeather().isCurrentlyThundering());
        }
    }
}
