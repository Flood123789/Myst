package mystcraft.flood.mixin;

import mystcraft.flood.generation.StrongholdCompatibleBiomeSource;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Redirect(
        method = "trySetStructureStart",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/gen/structure/Structure;createStructureStart(Lnet/minecraft/registry/DynamicRegistryManager;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/world/biome/source/BiomeSource;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/structure/StructureTemplateManager;JLnet/minecraft/util/math/ChunkPos;ILnet/minecraft/world/HeightLimitView;Ljava/util/function/Predicate;)Lnet/minecraft/structure/StructureStart;"
        )
    )
    private StructureStart mystcraft$allowStrongholdsInAgeBiomes(
        Structure structure,
        DynamicRegistryManager registryManager,
        ChunkGenerator generator,
        BiomeSource biomeSource,
        NoiseConfig noiseConfig,
        StructureTemplateManager templateManager,
        long seed,
        ChunkPos chunkPos,
        int references,
        HeightLimitView world,
        Predicate<RegistryEntry<Biome>> validBiomes
    ) {
        Identifier structureId = registryManager.get(RegistryKeys.STRUCTURE).getId(structure);
        Predicate<RegistryEntry<Biome>> effectivePredicate =
            biomeSource instanceof StrongholdCompatibleBiomeSource && new Identifier("minecraft", "stronghold").equals(structureId)
                ? biome -> true
                : validBiomes;
        return structure.createStructureStart(
            registryManager, generator, biomeSource, noiseConfig, templateManager, seed, chunkPos, references, world, effectivePredicate
        );
    }
}
