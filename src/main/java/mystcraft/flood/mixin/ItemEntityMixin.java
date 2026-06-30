package mystcraft.flood.mixin;

import mystcraft.flood.entity.DescriptiveBookEntity;
import mystcraft.flood.item.AgeBookIntegrity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
    @Unique
    private boolean mystcraft$lostBookPenaltyApplied = false;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void mystcraft$anchorDroppedBook(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        if (entity.getWorld().isClient()) return;

        ItemStack stack = entity.getStack();
        if (!AgeBookIntegrity.shouldAnchorDroppedBook(entity.getWorld(), stack)) return;

        DescriptiveBookEntity anchor = new DescriptiveBookEntity(entity.getWorld(), entity.getPos(), stack, 20);
        if (entity.getWorld().spawnEntity(anchor)) {
            entity.discard();
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;discard()V"))
    private void mystcraft$penalizeDespawningBook(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        if (entity.getItemAge() < 5999) return;
        mystcraft$applyLostBookPenalty(entity.getStack());
    }

    @Inject(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;discard()V"))
    private void mystcraft$penalizeDestroyedBook(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity entity = (ItemEntity) (Object) this;
        mystcraft$applyLostBookPenalty(entity.getStack());
    }

    @Unique
    private void mystcraft$applyLostBookPenalty(ItemStack stack) {
        if (this.mystcraft$lostBookPenaltyApplied) return;
        ItemEntity entity = (ItemEntity) (Object) this;
        if (AgeBookIntegrity.handleLostLinkedBook(entity.getWorld(), stack, entity.getId())) {
            this.mystcraft$lostBookPenaltyApplied = true;
        }
    }
}
