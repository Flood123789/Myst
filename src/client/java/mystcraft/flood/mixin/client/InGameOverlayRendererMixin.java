package mystcraft.flood.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    @Redirect(
        method = "renderUnderwaterOverlay",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V")
    )
    private static void mystcraft$tintUnderwaterOverlay(float red, float green, float blue, float alpha, MinecraftClient client, MatrixStack matrices) {
        if (client.world == null || !client.world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            RenderSystem.setShaderColor(red, green, blue, alpha);
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(client.world.getRegistryKey().getValue());
        if (profile == null) {
            RenderSystem.setShaderColor(red, green, blue, alpha);
            return;
        }

        int color = profile.getColors().getWater();
        float targetRed = ((color >> 16) & 0xFF) / 255.0f;
        float targetGreen = ((color >> 8) & 0xFF) / 255.0f;
        float targetBlue = (color & 0xFF) / 255.0f;
        float blend = 0.65f;

        RenderSystem.setShaderColor(
            red * (1.0f - blend) + targetRed * blend,
            green * (1.0f - blend) + targetGreen * blend,
            blue * (1.0f - blend) + targetBlue * blend,
            alpha
        );
    }

    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void mystcraft$beforeFireOverlay(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (client.world == null || !client.world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            return;
        }

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(client.world.getRegistryKey().getValue());
        if (profile == null) {
            return;
        }

        int color = profile.getColors().getFireLava();
        float targetRed = ((color >> 16) & 0xFF) / 255.0f;
        float targetGreen = ((color >> 8) & 0xFF) / 255.0f;
        float targetBlue = (color & 0xFF) / 255.0f;
        float blend = 0.72f;
        float base = 1.0f - blend;

        RenderSystem.setShaderColor(
            base + targetRed * blend,
            base + targetGreen * blend,
            base + targetBlue * blend,
            1.0f
        );
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void mystcraft$afterFireOverlay(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Inject(method = "renderFireOverlay", at = @At("TAIL"))
    private static void mystcraft$afterFireOverlayTail(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
