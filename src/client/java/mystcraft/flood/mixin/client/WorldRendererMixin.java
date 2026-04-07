package mystcraft.flood.mixin.client;

import mystcraft.flood.client.render.CustomSkyPainter;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", 
            at = @At("TAIL"))
    private void mystcraft$renderExtraCelestialBodies(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        
        // We still receive 'camera' from Minecraft, but we no longer need to pass it to our Painter
        CustomSkyPainter.INSTANCE.paintExtraSky(matrices, projectionMatrix, tickDelta);
        
    }
}