package mystcraft.flood.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccessor {
    
    // @Mutable strips the 'final' modifier from the variable!
    @Mutable
    @Accessor("x")
    void setSlotX(int x);

    @Mutable
    @Accessor("y")
    void setSlotY(int y);
}