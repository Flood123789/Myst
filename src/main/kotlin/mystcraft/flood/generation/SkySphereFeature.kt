package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.sqrt

class SkySphereFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {
    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val serverWorld = world.toServerWorld()
        val ageId = serverWorld.registryKey.value
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(ageId)) return false

        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, ageId)
        if (AgeLifecycleManager.isDeadAge(profile)) return false
        if (!profile.modifiers.contains(ChaosAgeThemes.SKY_SPHERES)) return false
        if (profile.terrainType == TerrainType.BIOSPHERES) return false

        val chunkPos = ChunkPos(context.origin)
        val regionSize = 8
        val regionX = Math.floorDiv(chunkPos.x, regionSize)
        val regionZ = Math.floorDiv(chunkPos.z, regionSize)
        var placed = false

        for (checkRegionX in regionX - 1..regionX + 1) {
            for (checkRegionZ in regionZ - 1..regionZ + 1) {
                val sphere = sphereFor(profile.seed, checkRegionX, checkRegionZ, regionSize) ?: continue
                if (!sphere.intersects(chunkPos)) continue
                placed = buildSphere(world, chunkPos, sphere) || placed
            }
        }

        return placed
    }

    private data class SkySphere(
        val center: BlockPos,
        val radius: Int,
        val palette: SpherePalette,
        val hollow: Boolean
    ) {
        fun intersects(chunkPos: ChunkPos): Boolean =
            center.x + radius >= chunkPos.startX &&
                center.x - radius <= chunkPos.endX &&
                center.z + radius >= chunkPos.startZ &&
                center.z - radius <= chunkPos.endZ
    }

    private data class SpherePalette(
        val core: Block,
        val mantle: Block,
        val shell: Block,
        val cap: Block,
        val light: Block
    )

    private fun sphereFor(seed: Long, regionX: Int, regionZ: Int, regionSize: Int): SkySphere? {
        val rand = java.util.Random(
            seed +
                regionX.toLong() * 12_876_421_721L +
                regionZ.toLong() * 8_761_243_391L +
                0x5F3A_EB00L
        )
        if (rand.nextFloat() > 0.72f) return null

        val chunkX = regionX * regionSize + 1 + rand.nextInt(regionSize - 2)
        val chunkZ = regionZ * regionSize + 1 + rand.nextInt(regionSize - 2)
        val radius = 8 + rand.nextInt(9)
        val center = BlockPos(
            chunkX * 16 + 8 + rand.nextInt(9) - 4,
            126 + rand.nextInt(78),
            chunkZ * 16 + 8 + rand.nextInt(9) - 4
        )
        val palette = palettes[rand.nextInt(palettes.size)]
        return SkySphere(center, radius, palette, rand.nextFloat() < 0.35f)
    }

    private fun buildSphere(world: StructureWorldAccess, chunkPos: ChunkPos, sphere: SkySphere): Boolean {
        var placed = false
        val radius = sphere.radius
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val pos = sphere.center.add(dx, dy, dz)
                    if ((pos.x shr 4) != chunkPos.x || (pos.z shr 4) != chunkPos.z) continue
                    val dist = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                    if (dist > radius + 0.35) continue

                    val block = when {
                        sphere.hollow && dist < radius - 3.0 -> {
                            if (dx == 0 && dy == 0 && dz == 0) sphere.palette.light else Blocks.AIR
                        }
                        dist >= radius - 1.15 -> {
                            if (dy > radius / 3) sphere.palette.cap else sphere.palette.shell
                        }
                        dist >= radius - 3.0 -> sphere.palette.mantle
                        else -> sphere.palette.core
                    }
                    FeatureBuildHelper.setBlock(world, chunkPos, pos, block, true)
                    placed = true
                }
            }
        }

        if (placed && !sphere.hollow) {
            buildGravityChain(world, chunkPos, sphere.center.down(radius), radius)
        }
        return placed
    }

    private fun buildGravityChain(world: StructureWorldAccess, chunkPos: ChunkPos, anchor: BlockPos, radius: Int) {
        if ((anchor.x shr 4) != chunkPos.x || (anchor.z shr 4) != chunkPos.z) return
        val length = (radius / 2).coerceAtLeast(4)
        for (step in 0..length) {
            FeatureBuildHelper.setBlock(world, chunkPos, anchor.down(step), if (step % 3 == 0) Blocks.CHAIN else Blocks.IRON_BARS, true)
        }
    }

    private val palettes = listOf(
        SpherePalette(Blocks.STONE, Blocks.DIRT, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.GLOWSTONE),
        SpherePalette(Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE, Blocks.SNOW_BLOCK, Blocks.SEA_LANTERN),
        SpherePalette(Blocks.END_STONE, Blocks.PURPUR_BLOCK, Blocks.CHORUS_FLOWER, Blocks.END_STONE_BRICKS, Blocks.SHROOMLIGHT),
        SpherePalette(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SAND, Blocks.RED_SAND, Blocks.GLOWSTONE),
        SpherePalette(Blocks.AMETHYST_BLOCK, Blocks.CALCITE, Blocks.TINTED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.SEA_LANTERN)
    )
}
