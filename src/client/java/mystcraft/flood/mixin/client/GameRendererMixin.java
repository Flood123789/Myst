package mystcraft.flood.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mystcraft.flood.client.config.SkyLayer;
import mystcraft.flood.client.config.SkyRenderConfig;
import mystcraft.flood.client.render.ClientRenderCompatibility;
import mystcraft.flood.client.render.CustomSkyPainter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints the Age sky elements that are routed to the overlay layer.
 *
 * Shader packs rebuild the frame from their own gbuffers during the composite passes that run at the
 * end of {@code WorldRenderer#render}, which discards anything drawn in the vanilla sky pass.
 * Injecting straight after that call puts the overlay on the finished image, so it survives every
 * shader pipeline, while still running before the hand is drawn.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(
        method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
            shift = At.Shift.AFTER
        )
    )
    private void mystcraft$paintSkyOverlay(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        if (ClientRenderCompatibility.isShaderPackActive()) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;
        if (!SkyRenderConfig.paintsAnythingOn(SkyLayer.OVERLAY)) return;

        CustomSkyPainter.paintSkyOverlay(world, tickDelta);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
