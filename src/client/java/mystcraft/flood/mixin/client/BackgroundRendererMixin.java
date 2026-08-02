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
    private static void mystcraft$tintAgeFog(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (!world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(world.getRegistryKey().getValue());
        if (profile == null) {
            return;
        }

        CameraSubmersionType submersionType = camera.getSubmersionType();
        if (submersionType == CameraSubmersionType.WATER) {
            int color = profile.getColors().getWater();
            red = ((color >> 16) & 0xFF) / 255.0f;
            green = ((color >> 8) & 0xFF) / 255.0f;
            blue = (color & 0xFF) / 255.0f;
            return;
        }

        if (submersionType == CameraSubmersionType.LAVA) {
            int color = profile.getColors().getFireLava();
            red = ((color >> 16) & 0xFF) / 255.0f;
            green = ((color >> 8) & 0xFF) / 255.0f;
            blue = (color & 0xFF) / 255.0f;
            return;
        }

        int fog = profile.getColors().getFog();
        int ambient = profile.getColors().getAmbient();
        float fogRed = ((fog >> 16) & 0xFF) / 255.0f;
        float fogGreen = ((fog >> 8) & 0xFF) / 255.0f;
        float fogBlue = (fog & 0xFF) / 255.0f;
        float ambientRed = ((ambient >> 16) & 0xFF) / 255.0f;
        float ambientGreen = ((ambient >> 8) & 0xFF) / 255.0f;
        float ambientBlue = (ambient & 0xFF) / 255.0f;
        float blend = 0.18f;
        red = fogRed * (1.0f - blend) + ambientRed * blend;
        green = fogGreen * (1.0f - blend) + ambientGreen * blend;
        blue = fogBlue * (1.0f - blend) + ambientBlue * blend;
    }
}
