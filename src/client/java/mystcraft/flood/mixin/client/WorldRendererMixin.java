package mystcraft.flood.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.client.render.ClientRenderCompatibility;
import mystcraft.flood.client.render.CustomSkyPainter;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Shadow private ClientWorld world;

    @Inject(method = "renderWeather", at = @At("HEAD"))
    private void mystcraft$tintAgeRain(LightmapTextureManager manager, float tickDelta, double x, double y, double z, CallbackInfo ci) {
        if (!ClientRenderCompatibility.canUseRenderSystemWeatherTint()) return;
        if (this.world == null) return;
        if (!this.world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) return;

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(this.world.getRegistryKey().getValue());
        if (profile == null || !profile.getWeather().isCurrentlyRaining()) return;

        int color = profile.getColors().getWater();
        float red = ((color >> 16) & 255) / 255.0f;
        float green = ((color >> 8) & 255) / 255.0f;
        float blue = (color & 255) / 255.0f;
        RenderSystem.setShaderColor(red, green, blue, 1.0f);
    }

    @Inject(method = "renderWeather", at = @At("RETURN"))
    private void mystcraft$resetAgeRainTint(LightmapTextureManager manager, float tickDelta, double x, double y, double z, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Inject(method = "renderWeather", at = @At("TAIL"))
    private void mystcraft$resetAgeRainTintTail(LightmapTextureManager manager, float tickDelta, double x, double y, double z, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void mystcraft$tintAgeClouds(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, double x, double y, double z, CallbackInfo ci) {
        if (!ClientRenderCompatibility.canUseRenderSystemCloudTint()) return;
        if (this.world == null) return;
        if (!this.world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) return;

        AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(this.world.getRegistryKey().getValue());
        if (profile == null) return;

        int color = profile.getColors().getCloud();
        float red = ((color >> 16) & 255) / 255.0f;
        float green = ((color >> 8) & 255) / 255.0f;
        float blue = (color & 255) / 255.0f;
        RenderSystem.setShaderColor(red, green, blue, 1.0f);
    }

    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void mystcraft$resetAgeCloudTint(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, double x, double y, double z, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Inject(method = "renderClouds", at = @At("TAIL"))
    private void mystcraft$resetAgeCloudTintTail(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, double x, double y, double z, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", 
            at = @At("TAIL"))
    private void mystcraft$renderExtraCelestialBodies(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if (ClientRenderCompatibility.isIrisShadowPass()) return;
        if (this.world == null) return;

        if (ClientRenderCompatibility.canUseCustomSkyOverlay()) {
            CustomSkyPainter.INSTANCE.paintSkyTint(this.world, matrices, projectionMatrix);
            CustomSkyPainter.INSTANCE.paintExtraSky(this.world, matrices, projectionMatrix, tickDelta);
        } else if (ClientRenderCompatibility.canUseShaderFallbackSkyOverlay()) {
            CustomSkyPainter.INSTANCE.paintShaderFallbackSky(this.world, matrices, projectionMatrix, tickDelta);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
            at = @At("TAIL"))
    private void mystcraft$resetShaderColorAfterWorldRender(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, net.minecraft.client.render.GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
