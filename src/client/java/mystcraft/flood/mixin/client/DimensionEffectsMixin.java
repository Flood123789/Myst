package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.client.render.ClientRenderCompatibility;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionEffects.class)
public class DimensionEffectsMixin {
    @Inject(method = "getCloudsHeight", at = @At("HEAD"), cancellable = true)
    private void mystcraft$getAgeCloudHeight(CallbackInfoReturnable<Float> cir) {
        if (!ClientRenderCompatibility.canUseCustomCloudHeight()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        Identifier worldId = client.world.getRegistryKey().getValue();
        if (!"mystcraft-reforged".equals(worldId.getNamespace())) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(worldId);
        if (profile != null) {
            cir.setReturnValue(profile.getCloudHeight());
        }
    }
}
