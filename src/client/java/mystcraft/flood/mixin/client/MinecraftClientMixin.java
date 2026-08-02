package mystcraft.flood.mixin.client;

import mystcraft.flood.client.AgeTravelSoundSuppressor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "setWorld", at = @At("HEAD"))
    private void mystcraft$armTravelSuppression(ClientWorld world, CallbackInfo ci) {
        AgeTravelSoundSuppressor.INSTANCE.arm();
    }

    @Inject(method = "setWorld", at = @At("TAIL"))
    private void mystcraft$invalidateLightmapAfterWorldChange(ClientWorld world, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        LightmapTextureManager lightmap = client.gameRenderer.getLightmapTextureManager();
        ((LightmapTextureManagerAccessor) lightmap).mystcraft$setDirty(true);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mystcraft$tickAgeTravelSuppression(CallbackInfo ci) {
        AgeTravelSoundSuppressor.INSTANCE.tick((MinecraftClient) (Object) this);
    }
}
