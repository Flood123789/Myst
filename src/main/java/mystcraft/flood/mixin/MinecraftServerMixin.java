package mystcraft.flood.mixin;

import kotlin.Pair;
import mystcraft.flood.access.DimensionInjector;
import mystcraft.flood.generation.AgeBuilder;
import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.network.ModMessages;
import net.minecraft.registry.DynamicRegistryManager;
import mystcraft.flood.generation.AgeWorldProperties;
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
    public void mystcraft$injectDimension(Identifier ageId) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        
        // SAFETY FIRST: If vanilla already loaded the world from level.dat, skip injection!
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, ageId);
        if (this.worlds.containsKey(worldKey)) {
            System.out.println("[MYSTCRAFT] Age " + ageId + " is already loaded natively. Skipping injection.");
            return;
        }

        try {
            Registry<DimensionOptions> optionsRegistry = this.getRegistryManager().get(RegistryKeys.DIMENSION);
            Registry<DimensionType> typeRegistry = this.getRegistryManager().get(RegistryKeys.DIMENSION_TYPE);
            
            // CLEAN: Grab the permanent static JSON physics instead of hacking RAM
            RegistryKey<DimensionType> baseAgeKey = RegistryKey.of(RegistryKeys.DIMENSION_TYPE, new Identifier("mystcraft-reforged", "base_age"));
            RegistryEntry<DimensionType> typeEntry = typeRegistry.getEntry(baseAgeKey).orElseThrow();

            Pair<ChunkGenerator, AgeProfile> result = AgeBuilder.INSTANCE.buildGenerator(server, ageId);
            ChunkGenerator customGen = result.getFirst();
            AgeProfile profile = result.getSecond();

            SimpleRegistry<DimensionOptions> simpleOptionsRegistry = (SimpleRegistry<DimensionOptions>) optionsRegistry;
            SimpleRegistryAccessor optionsAccessor = (SimpleRegistryAccessor) simpleOptionsRegistry;
            
            RegistryKey<DimensionOptions> dimOptionsKey = RegistryKey.of(RegistryKeys.DIMENSION, ageId);
            DimensionOptions newOptions = new DimensionOptions(typeEntry, customGen);
            
            optionsAccessor.setFrozen(false);
            Registry.register(simpleOptionsRegistry, dimOptionsKey.getValue(), newOptions);
            optionsAccessor.setFrozen(true);

            // CLEAN: Use our isolated runtime properties so time and weather are independent!
            ServerWorld overworld = this.getOverworld();
            ServerWorldProperties worldProperties = new AgeWorldProperties(
                    server.getSaveProperties().getMainWorldProperties(),
                    profile
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
                    newOptions,
                    listener,
                    false,
                    profile.getSeed(), 
                    Collections.emptyList(),
                    false,
                    overworld.getRandomSequences()
            );

            overworld.getWorldBorder().addListener(new WorldBorderListener.WorldBorderSyncer(newWorld.getWorldBorder()));
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