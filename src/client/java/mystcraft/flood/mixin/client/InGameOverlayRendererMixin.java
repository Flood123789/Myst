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
}
