package mystcraft.flood.mixin.client;

import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows Age time synchronization to invalidate third-party lightmap caches directly. */
@Mixin(LightmapTextureManager.class)
public interface LightmapTextureManagerAccessor {
    @Accessor("dirty")
    void mystcraft$setDirty(boolean dirty);
}
