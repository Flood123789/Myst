package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FluidRenderer.class)
public class FluidRendererMixin {

    @Shadow
    private Sprite[] lavaSprites;

    @Unique
    private static final Identifier MYSTCRAFT_LAVA_STILL = new Identifier("mystcraft-reforged", "block/fluid");
    @Unique
    private static final Identifier MYSTCRAFT_LAVA_FLOW = new Identifier("mystcraft-reforged", "block/fluid_flow");

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/color/world/BiomeColors;getWaterColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I"
            ),
            require = 0
    )
    private int mystcraft$getWaterColor(BlockRenderView world, BlockPos pos) {
        if (world instanceof ClientWorld clientWorld &&
                "mystcraft-reforged".equals(clientWorld.getRegistryKey().getValue().getNamespace())) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(clientWorld.getRegistryKey().getValue());
            if (profile != null) {
                return profile.getColors().getWater() & 0xFFFFFF;
            }
        }
        return BiomeColors.getWaterColor(world, pos);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/block/FluidRenderer;lavaSprites:[Lnet/minecraft/client/texture/Sprite;"
            ),
            require = 0
    )
    private Sprite[] mystcraft$getLavaSprites(FluidRenderer instance, BlockRenderView world, BlockPos pos, net.minecraft.client.render.VertexConsumer vertexConsumer, net.minecraft.block.BlockState blockState, FluidState fluidState) {
        if (world instanceof ClientWorld clientWorld &&
                "mystcraft-reforged".equals(clientWorld.getRegistryKey().getValue().getNamespace())) {
            var atlas = MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            Sprite still = atlas.apply(MYSTCRAFT_LAVA_STILL);
            Sprite flow = atlas.apply(MYSTCRAFT_LAVA_FLOW);
            if (still != null && flow != null) {
                return new Sprite[]{still, flow};
            }
        }
        return this.lavaSprites;
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"
            ),
            require = 0
    )
    private net.minecraft.client.render.VertexConsumer mystcraft$tintFluidVertex(net.minecraft.client.render.VertexConsumer consumer, float red, float green, float blue, float alpha, BlockRenderView world, BlockPos pos, net.minecraft.client.render.VertexConsumer vertexConsumer, net.minecraft.block.BlockState blockState, FluidState fluidState) {
        if (fluidState.isIn(FluidTags.LAVA) && world instanceof ClientWorld clientWorld &&
                "mystcraft-reforged".equals(clientWorld.getRegistryKey().getValue().getNamespace())) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(clientWorld.getRegistryKey().getValue());
            if (profile != null) {
                int color = profile.getColors().getFireLava();
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                return consumer.color(r, g, b, alpha);
            }
        }
        return consumer.color(red, green, blue, alpha);
    }
}



