package mystcraft.flood.mixin.client;

import mystcraft.flood.client.render.PageIconItemRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Inject(
            method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V",
            at = @At("TAIL")
    )
    private void mystcraft$drawPageSymbolOverlay(
            LivingEntity entity,
            World world,
            ItemStack stack,
            int x,
            int y,
            int seed,
            int z,
            CallbackInfo ci
    ) {
        PageIconItemRenderer.renderGuiOverlay((DrawContext) (Object) this, stack, x, y);
    }
}
