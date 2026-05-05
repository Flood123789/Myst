package mystcraft.flood.mixin;

import mystcraft.flood.generation.AgePortalRouting;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFireBlock.class)
public class AbstractFireBlockMixin {
    @Inject(method = "onBlockAdded", at = @At("HEAD"), cancellable = true)
    private void mystcraft$lightAgeNetherPortal(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        if (oldState.isOf(state.getBlock())) {
            return;
        }

        if (AgePortalRouting.tryCreateNetherPortal(world, pos)) {
            ci.cancel();
        }
    }
}
