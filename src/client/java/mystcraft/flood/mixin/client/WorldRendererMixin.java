package mystcraft.flood.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.client.config.SkyLayer;
import mystcraft.flood.client.config.SkyRenderConfig;
import mystcraft.flood.client.render.ClientRenderCompatibility;
import mystcraft.flood.client.render.CustomSkyPainter;
import mystcraft.flood.client.render.SkyFrameCapture;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Shadow private ClientWorld world;
    @Unique private ClientWorld mystcraft$lightmapWorld;
    @Unique private long mystcraft$lightmapTimeBucket = Long.MIN_VALUE;

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
        if (!SkyRenderConfig.paintsAnythingOn(SkyLayer.SKY_PASS)) return;

        if (ClientRenderCompatibility.isShaderPackActive()) {
            CustomSkyPainter.paintSkyWithDistantHorizonsOcclusion(this.world, matrices, projectionMatrix, tickDelta);
        } else {
            CustomSkyPainter.paintSky(this.world, matrices, projectionMatrix, tickDelta, SkyLayer.SKY_PASS);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD"))
    private void mystcraft$refreshDimensionLightmap(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, net.minecraft.client.render.GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        // The overlay pass runs after this method returns, where the camera basis is gone. Keep a
        // copy so it can paint with exactly the matrices the sky pass used.
        SkyFrameCapture.capture(matrices.peek().getPositionMatrix(), projectionMatrix);

        if (this.world == null) return;

        boolean ageWorld = "mystcraft-reforged".equals(this.world.getRegistryKey().getValue().getNamespace());
        long timeBucket = ageWorld ? Math.floorDiv(this.world.getTimeOfDay(), 20L) : Long.MIN_VALUE;
        if (this.world != this.mystcraft$lightmapWorld || (ageWorld && timeBucket != this.mystcraft$lightmapTimeBucket)) {
            ((LightmapTextureManagerAccessor) lightmapTextureManager).mystcraft$setDirty(true);
            this.mystcraft$lightmapWorld = this.world;
            this.mystcraft$lightmapTimeBucket = timeBucket;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
            at = @At("TAIL"))
    private void mystcraft$resetShaderColorAfterWorldRender(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, net.minecraft.client.render.GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
