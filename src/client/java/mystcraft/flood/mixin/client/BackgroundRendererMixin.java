package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Shadow
    private static float red;

    @Shadow
    private static float green;

    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void mystcraft$tintUnderwaterFog(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (camera.getSubmersionType() != CameraSubmersionType.WATER) {
            return;
        }

        if (!world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
        if (profile == null) {
            return;
        }

        int color = profile.getColors().getWater();
        red = ((color >> 16) & 0xFF) / 255.0f;
        green = ((color >> 8) & 0xFF) / 255.0f;
        blue = (color & 0xFF) / 255.0f;
    }
}
