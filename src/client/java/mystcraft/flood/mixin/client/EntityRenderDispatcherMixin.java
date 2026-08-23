package mystcraft.flood.mixin.client;

import mystcraft.flood.client.cache.ClientAgeCache;
import mystcraft.flood.generation.profile.AgeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Unique
    private static final Identifier MYSTCRAFT_FIRE_0 = new Identifier("mystcraft-reforged", "block/fire_0");
    @Unique
    private static final Identifier MYSTCRAFT_FIRE_1 = new Identifier("mystcraft-reforged", "block/fire_1");

    @Redirect(
            method = "renderFire",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/model/ModelLoader;getSprite(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/texture/Sprite;"
            ),
            require = 0
    )
    private Sprite mystcraft$getEntityFireSprite(Identifier id) {
        var atlas = MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        Identifier mystSpriteId = id != null && id.getPath().contains("1") ? MYSTCRAFT_FIRE_1 : MYSTCRAFT_FIRE_0;
        Sprite customSprite = atlas.apply(mystSpriteId);
        if (customSprite != null) {
            return customSprite;
        }
        return MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(id);
    }

    @Redirect(
            method = "renderFire",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumer;color(IIII)Lnet/minecraft/client/render/VertexConsumer;"
            ),
            require = 0
    )
    private VertexConsumer mystcraft$tintEntityFireVertexInt(VertexConsumer consumer, int red, int green, int blue, int alpha) {
        var client = MinecraftClient.getInstance();
        if (client.world != null && "mystcraft-reforged".equals(client.world.getRegistryKey().getValue().getNamespace())) {
            AgeProfile profile = ClientAgeCache.INSTANCE.getProperties(client.world.getRegistryKey().getValue());
            if (profile != null) {
                int color = profile.getColors().getFireLava();
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                return consumer.color(r, g, b, alpha);
            }
        }
        return consumer.color(red, green, blue, alpha);
    }
}
