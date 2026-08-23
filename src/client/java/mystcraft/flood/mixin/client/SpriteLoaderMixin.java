package mystcraft.flood.mixin.client;

import mystcraft.flood.client.render.DynamicGrayscaleTextureGenerator;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SpriteLoader.class)
public class SpriteLoaderMixin {

    @Inject(
            method = "load",
            at = @At("HEAD")
    )
    private void mystcraft$autoGenerateGrayscaleSpritesFromActivePack(
            ResourceManager resourceManager,
            Identifier atlasId,
            int mipmapLevels,
            Executor executor,
            CallbackInfoReturnable<CompletableFuture<?>> cir
    ) {
        if (!atlasId.equals(new Identifier("textures/atlas/blocks.png"))) {
            return;
        }

        // Dynamically desaturate the active resource pack's fire and lava textures in memory
        try {
            Identifier[] targets = new Identifier[]{
                    new Identifier("mystcraft-reforged", "block/fluid"),
                    new Identifier("mystcraft-reforged", "block/fluid_flow"),
                    new Identifier("mystcraft-reforged", "block/fire_0"),
                    new Identifier("mystcraft-reforged", "block/fire_1")
            };

            Identifier[] sources = new Identifier[]{
                    new Identifier("minecraft", "textures/block/lava_still.png"),
                    new Identifier("minecraft", "textures/block/lava_flow.png"),
                    new Identifier("minecraft", "textures/block/fire_0.png"),
                    new Identifier("minecraft", "textures/block/fire_1.png")
            };

            for (int i = 0; i < targets.length; i++) {
                DynamicGrayscaleTextureGenerator.createGrayscaleSprite(targets[i], sources[i], resourceManager);
            }
        } catch (Exception e) {
            System.err.println("[Mystcraft Reforged] Error auto-generating grayscale sprites during atlas load: " + e.getMessage());
        }
    }
}
