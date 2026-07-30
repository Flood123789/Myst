package mystcraft.flood.mixin;

import kotlin.Pair;
import mystcraft.flood.MystcraftReforged;
import mystcraft.flood.access.DimensionInjector;
import mystcraft.flood.generation.AgeBuilder;
import mystcraft.flood.generation.AgeSubdimensionManager;
import mystcraft.flood.generation.ImmersivePortalsCompat;
import mystcraft.flood.generation.profile.AgeDimensionRole;
import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.network.ModMessages;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
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
        mystcraft$createOrReplaceDimension(ageId, symbols, false);
    }

    @Override
    public void mystcraft$reloadDimension(Identifier ageId) {
        mystcraft$createOrReplaceDimension(ageId, Collections.emptyList(), true);
    }

    private void mystcraft$createOrReplaceDimension(Identifier ageId, List<String> symbols, boolean replaceExisting) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, ageId);

        if (this.worlds.containsKey(worldKey) && !replaceExisting) return;

        try {
            Registry<DimensionOptions> optionsRegistry = this.getRegistryManager().get(RegistryKeys.DIMENSION);
            Registry<DimensionType> typeRegistry = this.getRegistryManager().get(RegistryKeys.DIMENSION_TYPE);

            AgeDimensionRole role = AgeSubdimensionManager.INSTANCE.roleOf(ageId);
            RegistryKey<DimensionType> dimensionTypeKey = RegistryKey.of(
                    RegistryKeys.DIMENSION_TYPE,
                    new Identifier("mystcraft-reforged", role.getDimensionTypePath())
            );
            RegistryEntry<DimensionType> typeEntry = typeRegistry.getEntry(dimensionTypeKey).orElseThrow();

            Pair<ChunkGenerator, AgeProfile> result = AgeBuilder.INSTANCE.buildGenerator(server, ageId, symbols);
            ChunkGenerator customGen = result.getFirst();
            AgeProfile profile = result.getSecond();
            DimensionOptions liveOptions = new DimensionOptions(typeEntry, customGen);

            SimpleRegistry<DimensionOptions> simpleOptionsRegistry = (SimpleRegistry<DimensionOptions>) optionsRegistry;
            SimpleRegistryAccessor optionsAccessor = (SimpleRegistryAccessor) simpleOptionsRegistry;
            RegistryKey<DimensionOptions> dimOptionsKey = RegistryKey.of(RegistryKeys.DIMENSION, ageId);

            // IP's dynamic-dimension API must receive an unregistered dimension.
            // It creates the ServerWorld, inserts the options in the registry, updates
            // IP's integer dimension-ID record, and synchronizes that record to clients.
            // Pre-registering liveOptions here makes Fabric reject IP's registration as
            // a duplicate object. Falling back to a manually-created world after that
            // leaves IP unaware of the Age and crashes its teleport confirmation tick.
            if (!replaceExisting && ImmersivePortalsCompat.INSTANCE.isAvailable()) {
                if (optionsRegistry.contains(dimOptionsKey)) {
                    MystcraftReforged.INSTANCE.getLOGGER().error(
                            "Cannot dynamically create Age {} through Immersive Portals: dimension options are already registered",
                            ageId
                    );
                    return;
                }

                if (!ImmersivePortalsCompat.INSTANCE.tryAddDimensionDynamically(server, ageId, liveOptions)) {
                    MystcraftReforged.INSTANCE.getLOGGER().error(
                            "Immersive Portals failed to register Age {}; refusing to create an unsynchronized world",
                            ageId
                    );
                    return;
                }

                ServerWorld createdWorld = server.getWorld(worldKey);
                if (createdWorld == null) {
                    MystcraftReforged.INSTANCE.getLOGGER().error(
                            "Immersive Portals reported Age {} as registered but did not create its ServerWorld",
                            ageId
                    );
                    return;
                }

                if (role == AgeDimensionRole.END) {
                    createdWorld.setEnderDragonFight(new EnderDragonFight(createdWorld, profile.getSeed(), EnderDragonFight.Data.DEFAULT));
                }
                try {
                    ServerWorldEvents.LOAD.invoker().onWorldLoad(server, createdWorld);
                } catch (Throwable t) {
                    System.out.println("[MYSTCRAFT-DEBUG] Error dispatching ServerWorldEvents.LOAD: " + t.getMessage());
                }
                if (server.getPlayerManager() != null) {
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        ModMessages.INSTANCE.sendDimensionSync(player, ageId, profile);
                    }
                }
                return;
            }

            if (!optionsRegistry.contains(dimOptionsKey)) {
                optionsAccessor.setFrozen(false);
                Registry.register(simpleOptionsRegistry, dimOptionsKey.getValue(), liveOptions);
                optionsAccessor.setFrozen(true);
            }

            if (replaceExisting) {
                ServerWorld oldWorld = this.worlds.remove(worldKey);
                if (oldWorld != null) {
                    try {
                        ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, oldWorld);
                        oldWorld.close();
                    } catch (Throwable ignored) {
                    }
                }
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
                    liveOptions,
                    listener,
                    false,
                    profile.getSeed(), 
                    Collections.emptyList(),
                    false,
                    server.getOverworld().getRandomSequences()
            ) {
                @Override
                public long getSeed() {
                    return profile.getSeed();
                }
            };

            if (role == AgeDimensionRole.END) {
                newWorld.setEnderDragonFight(new EnderDragonFight(newWorld, profile.getSeed(), EnderDragonFight.Data.DEFAULT));
            }

            server.getOverworld().getWorldBorder().addListener(new WorldBorderListener.WorldBorderSyncer(newWorld.getWorldBorder()));
            this.worlds.put(worldKey, newWorld);
            ServerWorldEvents.LOAD.invoker().onWorldLoad(server, newWorld);

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
