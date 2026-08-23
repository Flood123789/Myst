package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {

    @Inject(
            method = "renderQuad",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mystcraft$tintFireBlockQuads(
            BlockRenderView world,
            BlockState state,
            BlockPos pos,
            VertexConsumer vertexConsumer,
            MatrixStack.Entry matrixEntry,
            BakedQuad quad,
            float brightness0,
            float brightness1,
            float brightness2,
            float brightness3,
            int light0,
            int light1,
            int light2,
            int light3,
            int overlay,
            CallbackInfo ci
    ) {
        if (state.getBlock() instanceof AbstractFireBlock &&
                world instanceof ClientWorld clientWorld &&
                "mystcraft-reforged".equals(clientWorld.getRegistryKey().getValue().getNamespace())) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(clientWorld.getRegistryKey().getValue());
            if (profile != null) {
                int color = profile.getColors().getFireLava();
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;

                // Pass custom RGB tint for fire block quads
                float[] brightnesses = new float[]{brightness0, brightness1, brightness2, brightness3};
                int[] lights = new int[]{light0, light1, light2, light3};
                vertexConsumer.quad(matrixEntry, quad, brightnesses, r, g, b, lights, overlay, true);
                ci.cancel();
            }
        }
    }
}
