package mystcraft.flood.mixin;

import mystcraft.flood.generation.profile.AgeProfile;
import mystcraft.flood.generation.profile.AgeProfileManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnHelper.class)
public class SpawnHelperMixin {
    
    // Safety lock to prevent infinite recursive loops when we multiply spawns
    private static boolean mystcraft$isMultiplying = false;

    @Inject(method = "spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Info;ZZZ)V", 
            at = @At("HEAD"), cancellable = true)
    private static void mystcraft$modifySpawns(ServerWorld world, WorldChunk chunk, SpawnHelper.Info info, boolean spawnAnimals, boolean spawnMonsters, boolean rareSpawn, CallbackInfo ci) {
        
        if (mystcraft$isMultiplying) return;

        if (!world.isClient() && world.getRegistryKey().getValue().getNamespace().equals("mystcraft-reforged")) {
            AgeProfile profile = AgeProfileManager.INSTANCE.getOrGenerateProfile(world.getServer(), world.getRegistryKey().getValue());
            
            // 1. Peace and Quiet! (Complete Spawn Block)
            if (profile.getSpawning().getNoMobs()) {
                ci.cancel();
                return;
            }

            // 2. The Horde! (Spawn Multipliers)
            int extraHostiles = (int) profile.getSpawning().getHostileMultiplier() - 1;
            int extraPassives = (int) profile.getSpawning().getPassiveMultiplier() - 1;

            if (extraHostiles > 0 || extraPassives > 0) {
                mystcraft$isMultiplying = true; // Lock the recursive loop
                
                int maxPasses = Math.max(extraHostiles, extraPassives);
                
                // Vanilla is about to do 1 pass. We force it to do extra passes!
                for (int i = 0; i < maxPasses; i++) {
                    boolean doHostile = i < extraHostiles && spawnMonsters;
                    boolean doPassive = i < extraPassives && spawnAnimals;
                    
                    if (doHostile || doPassive) {
                        SpawnHelper.spawn(world, chunk, info, doPassive, doHostile, rareSpawn);
                    }
                }
                
                mystcraft$isMultiplying = false; // Unlock for the next tick
            }
        }
    }
}