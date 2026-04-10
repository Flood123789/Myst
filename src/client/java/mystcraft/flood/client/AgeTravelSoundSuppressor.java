package mystcraft.flood.client;

import mystcraft.flood.MystcraftReforged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.Set;

public final class AgeTravelSoundSuppressor {
    public static final AgeTravelSoundSuppressor INSTANCE = new AgeTravelSoundSuppressor();

    private Identifier lastWorldId = null;
    private int suppressTicks = 0;

    private final Set<Identifier> mutedSounds = Set.of(
        new Identifier("minecraft", "block.portal.trigger"),
        new Identifier("minecraft", "block.portal.travel"),
        new Identifier("minecraft", "block.portal.ambient"),
        new Identifier("minecraft", "item.chorus_fruit.teleport"),
        new Identifier("minecraft", "entity.enderman.teleport")
    );

    private AgeTravelSoundSuppressor() {
    }

    public void arm() {
        suppressTicks = 30;
    }

    public void tick(MinecraftClient client) {
        Identifier currentWorldId = client.world != null ? client.world.getRegistryKey().getValue() : null;
        if (currentWorldId != lastWorldId && (currentWorldId == null || !currentWorldId.equals(lastWorldId))) {
            boolean oldMyst = lastWorldId != null && MystcraftReforged.MOD_ID.equals(lastWorldId.getNamespace());
            boolean newMyst = currentWorldId != null && MystcraftReforged.MOD_ID.equals(currentWorldId.getNamespace());
            if (oldMyst || newMyst) {
                arm();
            }
            lastWorldId = currentWorldId;
        }

        if (suppressTicks > 0) {
            suppressTicks--;
        }
    }

    public boolean shouldSuppress(Identifier soundId) {
        return suppressTicks > 0 && soundId != null && mutedSounds.contains(soundId);
    }
}
