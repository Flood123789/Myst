package mystcraft.flood.mixin;

import kotlin.Pair;
import mystcraft.flood.access.DimensionInjector;
import mystcraft.flood.generation.AgeBuilder;
import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.network.ModMessages;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorderListener;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements DimensionInjector {

    @Shadow public abstract DynamicRegistryManager.Immutable getRegistryManager();
    @Shadow public abstract ServerWorld getOverworld();
    @Shadow @Final protected LevelStorage.Session session;
    @Shadow @Final private Executor workerExecutor;
    @Shadow @Final private Map<RegistryKey<World>, ServerWorld> worlds;

    @Override
    public void mystcraft$injectDimension(Identifier ageId, List<String> symbols) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, ageId);

        if (this.worlds.containsKey(worldKey)) return;

        try {
            Registry<DimensionOptions> optionsRegistry = this.getRegistryManager().get(RegistryKeys.DIMENSION);
            Registry<DimensionType> typeRegistry = this.getRegistryManager().get(RegistryKeys.DIMENSION_TYPE);
            
            RegistryKey<DimensionType> baseAgeKey = RegistryKey.of(RegistryKeys.DIMENSION_TYPE, new Identifier("mystcraft-reforged", "base_age"));
            RegistryEntry<DimensionType> typeEntry = typeRegistry.getEntry(baseAgeKey).orElseThrow();

            // THE FIX: Pass the symbols straight into the AgeBuilder so the Compiler can read them!
            Pair<ChunkGenerator, AgeProfile> result = AgeBuilder.INSTANCE.buildGenerator(server, ageId, symbols);
            ChunkGenerator customGen = result.getFirst();
            AgeProfile profile = result.getSecond();

            SimpleRegistry<DimensionOptions> simpleOptionsRegistry = (SimpleRegistry<DimensionOptions>) optionsRegistry;
            SimpleRegistryAccessor optionsAccessor = (SimpleRegistryAccessor) simpleOptionsRegistry;
            
            RegistryKey<DimensionOptions> dimOptionsKey = RegistryKey.of(RegistryKeys.DIMENSION, ageId);

            if (!optionsRegistry.contains(dimOptionsKey)) {
                optionsAccessor.setFrozen(false);
                DimensionOptions newOptions = new DimensionOptions(typeEntry, customGen);
                Registry.register(simpleOptionsRegistry, dimOptionsKey.getValue(), newOptions);
                optionsAccessor.setFrozen(true);
            }

            ServerWorldProperties worldProperties = new UnmodifiableLevelProperties(
                    server.getSaveProperties(),
                    server.getSaveProperties().getMainWorldProperties()
            );

            WorldGenerationProgressListener listener = new WorldGenerationProgressListener() {
                @Override public void start(ChunkPos spawnPos) {}
                @Override public void setChunkStatus(ChunkPos pos, ChunkStatus status) {}
                @Override public void start() {}
                @Override public void stop() {}
            };

            ServerWorld newWorld = new ServerWorld(
                    server,
                    this.workerExecutor,
                    this.session,
                    worldProperties,
                    worldKey,
                    optionsRegistry.get(dimOptionsKey),
                    listener,
                    false,
                    profile.getSeed(), 
                    Collections.emptyList(),
                    false,
                    server.getOverworld().getRandomSequences()
            ) {
                // We force the new world to answer the NoiseConfig with the Age's seed.
                @Override
                public long getSeed() {
                    return profile.getSeed();
                }
            };

            server.getOverworld().getWorldBorder().addListener(new WorldBorderListener.WorldBorderSyncer(newWorld.getWorldBorder()));
            this.worlds.put(worldKey, newWorld);

            if (server.getPlayerManager() != null) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ModMessages.INSTANCE.sendDimensionSync(player, ageId, profile);
                }
            }

        } catch (Throwable t) {
            System.out.println("[MYSTCRAFT-DEBUG] FATAL CRASH DURING INJECTION:");
            t.printStackTrace();
        }
    }
}