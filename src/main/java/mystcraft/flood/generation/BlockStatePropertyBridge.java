package mystcraft.flood.generation;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

import java.util.Optional;

final class BlockStatePropertyBridge {
    private BlockStatePropertyBridge() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static BlockState withParsed(BlockState state, Property property, String value) {
        Optional parsed = property.parse(value);
        if (parsed.isEmpty()) {
            return state;
        }
        return state.with(property, (Comparable) parsed.get());
    }
}
