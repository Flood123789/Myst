// src/main/java/mystcraft/flood/access/DimensionInjector.java
package mystcraft.flood.access;

import net.minecraft.util.Identifier;
import java.util.List;

public interface DimensionInjector {
    void mystcraft$injectDimension(Identifier ageId, List<String> symbols);
    void mystcraft$reloadDimension(Identifier ageId);
}
