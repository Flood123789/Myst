package mystcraft.flood.mixin;

import com.mojang.datafixers.DataFixer;
import mystcraft.flood.MystcraftReforged;
import mystcraft.flood.generation.LostCityChunkGenerator;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Restores real terrain noise for Ages whose chunk generator wraps a delegate.
 *
 * <p>The constructor derives the world's {@link NoiseConfig} only when the chunk generator is
 * itself a {@link NoiseChunkGenerator}, and otherwise falls back to
 * {@code ChunkGeneratorSettings.createMissingSettings()}, whose noise router is fifteen
 * {@code zero()} density functions.
 *
 * <p>{@link LostCityChunkGenerator} delegates terrain to a {@link NoiseChunkGenerator} but is not
 * one, so it took that fallback. Because {@code ChunkNoiseSampler} reads its density functions from
 * the world's NoiseConfig rather than from the settings it is handed, the final density was zero
 * everywhere and no solid block was ever placed, while the delegate's own settings still supplied
 * water at sea level and lava below y=-54 -- an ocean sitting on top of a lava sea.
 *
 * <p>Rebuilding the NoiseConfig from the delegate's settings gives those Ages the same terrain they
 * would have had without the wrapper. Generators that build their own blocks and never sample the
 * noise router, such as the biosphere generator, are unaffected and deliberately left alone.
 */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadedAnvilChunkStorageMixin {

    @Shadow
    @Final
    @Mutable
    private NoiseConfig noiseConfig;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mystcraft$restoreDelegateNoiseConfig(
        ServerWorld world,
        LevelStorage.Session session,
        DataFixer dataFixer,
        StructureTemplateManager structureTemplateManager,
        Executor executor,
        ThreadExecutor<Runnable> mainThreadExecutor,
        ChunkProvider chunkProvider,
        ChunkGenerator chunkGenerator,
        WorldGenerationProgressListener progressListener,
        ChunkStatusChangeListener chunkStatusChangeListener,
        Supplier<PersistentStateManager> persistentStateManagerFactory,
        int viewDistance,
        boolean dsync,
        CallbackInfo ci
    ) {
        if (!(chunkGenerator instanceof LostCityChunkGenerator wrapper)) {
            return;
        }
        if (!(wrapper.getDelegate() instanceof NoiseChunkGenerator delegate)) {
            return;
        }

        this.noiseConfig = NoiseConfig.create(
            delegate.getSettings().value(),
            world.getRegistryManager().getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS),
            world.getSeed()
        );
        MystcraftReforged.INSTANCE.getLOGGER().info(
            "Rebuilt noise config for {} from the wrapped terrain generator",
            world.getRegistryKey().getValue()
        );
    }
}
