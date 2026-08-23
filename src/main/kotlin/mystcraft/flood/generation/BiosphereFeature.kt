package mystcraft.flood.generation

import com.mojang.serialization.Codec
import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfileManager
import mystcraft.flood.generation.profile.TerrainType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.enums.BedPart
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.StructureWorldAccess
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.util.FeatureContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun positiveHash(x: Int, y: Int, z: Int): Int {
    var hash = x * 73428767
    hash = hash xor (y * 912931)
    hash = hash xor (z * 438289)
    return hash and Int.MAX_VALUE
}

/**
 * Constructs the glass shell, interior terrain, and safe-entry area of a biosphere cell.
 * Sphere centers come from [BiosphereLayout], allowing the chunk generator, feature placement,
 * and travel safety code to agree on the same geometry without sharing mutable state.
 */
class BiosphereFeature(codec: Codec<DefaultFeatureConfig>) : Feature<DefaultFeatureConfig>(codec) {

    companion object {
        const val SAFE_ENTRY_Y = 108

        fun buildOriginBiosphere(world: ServerWorld) {
            val layout = buildLayout(world, -260, 260, -260, 260)
            layout.spheres.forEach { sphere ->
                emitSphere(sphere, sphere.centerX - sphere.radius - 2, sphere.centerX + sphere.radius + 2, sphere.centerZ - sphere.radius - 2, sphere.centerZ + sphere.radius + 2) { pos, state ->
                    setIfInBuildHeight(world, pos, state)
                }
            }
            layout.bridges.forEach { bridge ->
                emitBridge(bridge.from, bridge.to) { pos, state -> setIfInBuildHeight(world, pos, state) }
            }
            layout.cottages.forEach { cottage ->
                emitCottage(cottage) { pos, state -> setIfInBuildHeight(world, pos, state) }
            }
            buildOriginPlatform { pos, state -> setIfInBuildHeight(world, pos, state) }
        }

        private fun buildLayout(serverWorld: ServerWorld, minX: Int, maxX: Int, minZ: Int, maxZ: Int): SphereLayout {
            val biomeRegistry = serverWorld.registryManager.get(RegistryKeys.BIOME)
            val surfaceIds = BiosphereLayout.collectBiomeIds(biomeRegistry, caveOnly = false)
            val caveIds = BiosphereLayout.collectBiomeIds(biomeRegistry, caveOnly = true)
            val raw = BiosphereLayout.generate(serverWorld.seed, surfaceIds, caveIds, minX, maxX, minZ, maxZ)
            val spheres = raw.spheres.map {
                SphereInfo(it.key, it.centerX, it.centerY, it.centerZ, it.radius, it.cave, it.biomeId, it.styleSeed, paletteFor(it.biomeId, it.cave))
            }
            val sphereMap = spheres.associateBy { it.key }
            val bridges = raw.bridges.mapNotNull { bridge ->
                val from = sphereMap[bridge.fromKey]
                val to = sphereMap[bridge.toKey]
                if (from != null && to != null) BridgeInfo(from, to) else null
            }
            val cottages = spheres.flatMap(::createCottagesForSphere)
            return SphereLayout(spheres, bridges, cottages)
        }

        private fun emitSphere(sphere: SphereInfo, minX: Int, maxX: Int, minZ: Int, maxZ: Int, setter: (BlockPos, BlockState) -> Unit) {
            val radiusSq = sphere.radius * sphere.radius
            for (gX in minX..maxX) {
                for (gZ in minZ..maxZ) {
                    val dx = gX - sphere.centerX
                    val dz = gZ - sphere.centerZ
                    val horizSq = dx * dx + dz * dz
                    if (horizSq > radiusSq) continue
                    val vertical = sqrt((radiusSq - horizSq).toDouble()).roundToInt()
                    val shellTopY = sphere.centerY + vertical
                    val shellBottomY = sphere.centerY - vertical
                    val horiz = sqrt(horizSq.toDouble())
                    val topY = terrainSurfaceY(sphere, horiz, gX, gZ)
                    val bottomY = topY - 4
                    val pondRadius = sphere.radius / sphere.palette.pondDivisor
                    val pondSurfaceY = topY - 2
                    val pondBottomY = pondSurfaceY - sphere.palette.pondDepth
                    val hasPond = sphere.palette.liquid != null && !sphere.cave

                    for (y in shellBottomY..shellTopY) {
                        val dy = y - sphere.centerY
                        val dist = sqrt((horizSq + dy * dy).toDouble())
                        val pos = BlockPos(gX, y, gZ)
                        when {
                            dist in (sphere.radius - 1.15)..(sphere.radius + 0.2) -> setter(pos, Blocks.GLASS.defaultState)
                            y in bottomY..topY -> setter(pos, surfaceBlockFor(sphere, y, topY, pondSurfaceY, pondBottomY, horiz, pondRadius, hasPond) ?: continue)
                            dist < sphere.radius - 1.15 && y < bottomY -> setter(pos, undergroundBlockFor(sphere, gX, y, gZ))
                            dist < sphere.radius - 1.15 && y > topY -> setter(pos, Blocks.AIR.defaultState)
                        }
                    }
                }
            }
        }

        private fun emitBridge(from: SphereInfo, to: SphereInfo, setter: (BlockPos, BlockState) -> Unit) {
            val dx = to.centerX - from.centerX
            val dz = to.centerZ - from.centerZ
            val useX = abs(dx) >= abs(dz)
            val startAxis = if (useX) from.centerX + if (dx >= 0) from.radius - 1 else -from.radius + 1 else from.centerZ + if (dz >= 0) from.radius - 1 else -from.radius + 1
            val endAxis = if (useX) to.centerX - if (dx >= 0) to.radius - 1 else -to.radius + 1 else to.centerZ - if (dz >= 0) to.radius - 1 else -to.radius + 1
            val step = if (endAxis >= startAxis) 1 else -1
            val length = abs(endAxis - startAxis).coerceAtLeast(1)
            val startY = from.centerY - from.radius / 3 + 1
            val endY = to.centerY - to.radius / 3 + 1
            var axis = startAxis
            while (true) {
                val progress = abs(axis - startAxis).toDouble() / length.toDouble()
                val deckY = (startY + (endY - startY) * progress).roundToInt()
                val centerX = if (useX) axis else from.centerX
                val centerZ = if (useX) from.centerZ else axis
                for (cross in -1..1) {
                    val gX = if (useX) centerX else centerX + cross
                    val gZ = if (useX) centerZ + cross else centerZ
                    setter(BlockPos(gX, deckY - 1, gZ), Blocks.STONE_BRICKS.defaultState)
                    setter(BlockPos(gX, deckY, gZ), Blocks.SMOOTH_STONE.defaultState)
                    setter(BlockPos(gX, deckY + 3, gZ), Blocks.AIR.defaultState)
                }
                if (useX) {
                    setter(BlockPos(centerX, deckY + 1, centerZ - 2), Blocks.GLASS_PANE.defaultState)
                    setter(BlockPos(centerX, deckY + 1, centerZ + 2), Blocks.GLASS_PANE.defaultState)
                } else {
                    setter(BlockPos(centerX - 2, deckY + 1, centerZ), Blocks.GLASS_PANE.defaultState)
                    setter(BlockPos(centerX + 2, deckY + 1, centerZ), Blocks.GLASS_PANE.defaultState)
                }
                if (axis == endAxis) break
                axis += step
            }
        }

        private fun buildOriginPlatform(setter: (BlockPos, BlockState) -> Unit) {
            val deckY = SAFE_ENTRY_Y - 1
            for (x in -5..5) for (z in -5..5) {
                val ring = abs(x) == 5 || abs(z) == 5
                setter(BlockPos(x, deckY - 1, z), Blocks.STONE_BRICKS.defaultState)
                setter(BlockPos(x, deckY, z), if (ring) Blocks.SMOOTH_STONE_SLAB.defaultState else Blocks.POLISHED_ANDESITE.defaultState)
                for (y in (deckY + 1)..(deckY + 4)) setter(BlockPos(x, y, z), Blocks.AIR.defaultState)
            }
        }

        private fun createCottagesForSphere(sphere: SphereInfo): List<CottageInfo> {
            if (sphere.cave || !sphere.palette.allowCottages) return emptyList()
            val hash = positiveHash(sphere.centerX, sphere.centerY, sphere.centerZ)
            if (hash % 5 != 0) return emptyList()
            val offsets = listOf(Pair(-sphere.radius / 2, sphere.radius / 3), Pair(sphere.radius / 2 - 2, -sphere.radius / 3), Pair(-sphere.radius / 3, -sphere.radius / 2 + 2))
            val count = 1 + (hash % 3)
            return (0 until count).map { index ->
                val (offsetX, offsetZ) = offsets[index]
                val horiz = sqrt((offsetX * offsetX + offsetZ * offsetZ).toDouble())
                val floorY = terrainSurfaceY(sphere, horiz, sphere.centerX + offsetX, sphere.centerZ + offsetZ) + 1
                val facing = when {
                    abs(offsetX) >= abs(offsetZ) && offsetX > 0 -> Direction.WEST
                    abs(offsetX) >= abs(offsetZ) && offsetX < 0 -> Direction.EAST
                    offsetZ > 0 -> Direction.NORTH
                    else -> Direction.SOUTH
                }
                CottageInfo(sphere.centerX + offsetX, floorY, sphere.centerZ + offsetZ, facing, 5 + ((hash shr (index + 2)) and 1), 5 + ((hash shr (index + 4)) and 1), hash % 4)
            }
        }

        private fun emitCottage(cottage: CottageInfo, setter: (BlockPos, BlockState) -> Unit) {
            val halfX = cottage.width / 2
            val halfZ = cottage.depth / 2
            val floorY = cottage.floorY
            val wallState = when (cottage.variant) {
                1 -> Blocks.SPRUCE_PLANKS.defaultState
                2 -> Blocks.BIRCH_PLANKS.defaultState
                3 -> Blocks.MUD_BRICKS.defaultState
                else -> Blocks.OAK_PLANKS.defaultState
            }
            val roofState = when (cottage.variant) {
                1 -> Blocks.SPRUCE_STAIRS.defaultState
                2 -> Blocks.BIRCH_STAIRS.defaultState
                3 -> Blocks.MUD_BRICK_STAIRS.defaultState
                else -> Blocks.OAK_STAIRS.defaultState
            }
            for (x in -halfX..halfX) for (z in -halfZ..halfZ) {
                setter(BlockPos(cottage.centerX + x, floorY - 1, cottage.centerZ + z), Blocks.COBBLESTONE.defaultState)
                setter(BlockPos(cottage.centerX + x, floorY, cottage.centerZ + z), Blocks.OAK_PLANKS.defaultState)
                for (y in (floorY + 1)..(floorY + 4)) setter(BlockPos(cottage.centerX + x, y, cottage.centerZ + z), Blocks.AIR.defaultState)
            }
            for (x in -halfX..halfX) for (z in -halfZ..halfZ) {
                val gX = cottage.centerX + x
                val gZ = cottage.centerZ + z
                val wall = abs(x) == halfX || abs(z) == halfZ
                if (wall) for (y in (floorY + 1)..(floorY + 3)) setter(BlockPos(gX, y, gZ), wallState)
                val ridge = if (cottage.depth >= cottage.width) abs(z) else abs(x)
                setter(BlockPos(gX, floorY + 4 + ridge / 2, gZ), roofState)
            }
            val doorDepth = if (cottage.facing.axis == Direction.Axis.Z) halfZ else halfX
            val (doorX, doorZ) = facingOffset(cottage.facing, doorDepth)
            setter(BlockPos(cottage.centerX + doorX, floorY + 1, cottage.centerZ + doorZ), Blocks.OAK_DOOR.defaultState.with(Properties.HORIZONTAL_FACING, cottage.facing).with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER))
            setter(BlockPos(cottage.centerX + doorX, floorY + 2, cottage.centerZ + doorZ), Blocks.OAK_DOOR.defaultState.with(Properties.HORIZONTAL_FACING, cottage.facing).with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER))
            val (backX, backZ) = facingOffset(cottage.facing.opposite, doorDepth - 1)
            setter(BlockPos(cottage.centerX + backX, floorY + 1, cottage.centerZ + backZ), Blocks.RED_BED.defaultState.with(Properties.HORIZONTAL_FACING, cottage.facing).with(Properties.BED_PART, BedPart.FOOT))
            setter(BlockPos(cottage.centerX + backX + cottage.facing.offsetX, floorY + 1, cottage.centerZ + backZ + cottage.facing.offsetZ), Blocks.RED_BED.defaultState.with(Properties.HORIZONTAL_FACING, cottage.facing).with(Properties.BED_PART, BedPart.HEAD))
            val sideDir = if (cottage.facing.axis == Direction.Axis.Z) Direction.EAST else Direction.SOUTH
            setter(BlockPos(cottage.centerX + sideDir.offsetX * max(1, halfX - 1), floorY + 1, cottage.centerZ + sideDir.offsetZ * max(1, halfZ - 1)), Blocks.CRAFTING_TABLE.defaultState)
            setter(BlockPos(cottage.centerX + sideDir.offsetX * max(1, halfX - 1) - cottage.facing.offsetX, floorY + 1, cottage.centerZ + sideDir.offsetZ * max(1, halfZ - 1) - cottage.facing.offsetZ), Blocks.FURNACE.defaultState)
            setter(BlockPos(cottage.centerX, floorY + 2, cottage.centerZ), Blocks.LANTERN.defaultState)
        }

        private fun terrainSurfaceY(sphere: SphereInfo, horiz: Double, gX: Int, gZ: Int): Int {
            val base = sphere.centerY - sphere.radius / 3
            val crown = ((sphere.radius - horiz) / sphere.palette.crownDivisor).coerceAtLeast(0.0)
            val mainNoise = sampleNoise(sphere.styleSeed, gX * sphere.palette.mainFreq, gZ * sphere.palette.mainFreq)
            val detailNoise = sampleNoise(sphere.styleSeed xor 0x5bd1e995.toInt(), gX * sphere.palette.detailFreq, gZ * sphere.palette.detailFreq)
            val delta = when (sphere.palette.terrainKind) {
                TerrainKind.PLAINS -> mainNoise * 2.5 + detailNoise
                TerrainKind.FOREST -> mainNoise * 4.5 + detailNoise * 1.8
                TerrainKind.TAIGA -> mainNoise * 5.5 + detailNoise * 2.0
                TerrainKind.SNOWY -> mainNoise * 3.5 + detailNoise
                TerrainKind.SWAMP -> mainNoise * 1.2 + detailNoise * 0.5
                TerrainKind.DESERT -> abs(mainNoise) * 4.5 + detailNoise
                TerrainKind.BADLANDS -> max(0.0, mainNoise * 8.0) + detailNoise * 1.2
                TerrainKind.MUSHROOM -> mainNoise * 2.0 + detailNoise
                TerrainKind.BEACH -> mainNoise * 0.8 + detailNoise * 0.4
                TerrainKind.CRIMSON, TerrainKind.WARPED, TerrainKind.SOUL, TerrainKind.BASALT -> mainNoise * 6.0 + abs(detailNoise) * 2.5
                TerrainKind.LUSH_CAVE -> mainNoise * 3.5 + detailNoise * 1.5
                TerrainKind.DRIPSTONE_CAVE -> abs(mainNoise) * 5.0 + detailNoise
                TerrainKind.DEEP_DARK -> mainNoise * 1.5 + detailNoise * 0.5
                TerrainKind.END -> mainNoise * 2.0 + detailNoise * 0.6
            }
            return (base + crown + delta).roundToInt().coerceIn(base - 3, sphere.centerY + sphere.radius / 2 - 5)
        }

        private fun surfaceBlockFor(sphere: SphereInfo, y: Int, topY: Int, pondSurfaceY: Int, pondBottomY: Int, horiz: Double, pondRadius: Double, hasPond: Boolean): BlockState? {
            val inPond = hasPond && horiz < pondRadius
            return when {
                inPond && y > pondSurfaceY -> Blocks.AIR.defaultState
                inPond && y in pondBottomY..pondSurfaceY -> sphere.palette.liquid
                y == topY -> sphere.palette.top
                y >= topY - 2 -> sphere.palette.filler
                else -> sphere.palette.stone
            }
        }

        private fun undergroundBlockFor(sphere: SphereInfo, gX: Int, y: Int, gZ: Int): BlockState {
            val hash = positiveHash(gX, y, gZ)
            if (sphere.palette.stone.isOf(Blocks.NETHERRACK)) {
                return when {
                    hash % 97 == 0 -> Blocks.ANCIENT_DEBRIS.defaultState
                    hash % 23 == 0 -> Blocks.NETHER_GOLD_ORE.defaultState
                    hash % 11 == 0 -> Blocks.NETHER_QUARTZ_ORE.defaultState
                    else -> sphere.palette.stone
                }
            }
            val deep = y < sphere.centerY - sphere.radius / 2
            val baseStone = if (deep && !sphere.palette.stone.isOf(Blocks.DEEPSLATE)) Blocks.DEEPSLATE.defaultState else sphere.palette.stone
            return when {
                baseStone.isOf(Blocks.DEEPSLATE) && hash % 131 == 0 -> Blocks.DEEPSLATE_DIAMOND_ORE.defaultState
                baseStone.isOf(Blocks.DEEPSLATE) && hash % 83 == 0 -> Blocks.DEEPSLATE_LAPIS_ORE.defaultState
                baseStone.isOf(Blocks.DEEPSLATE) && hash % 61 == 0 -> Blocks.DEEPSLATE_REDSTONE_ORE.defaultState
                baseStone.isOf(Blocks.DEEPSLATE) && hash % 37 == 0 -> Blocks.DEEPSLATE_GOLD_ORE.defaultState
                baseStone.isOf(Blocks.DEEPSLATE) && hash % 29 == 0 -> Blocks.DEEPSLATE_IRON_ORE.defaultState
                baseStone.isOf(Blocks.DEEPSLATE) && hash % 17 == 0 -> Blocks.DEEPSLATE_COAL_ORE.defaultState
                hash % 131 == 0 -> Blocks.DIAMOND_ORE.defaultState
                hash % 83 == 0 -> Blocks.LAPIS_ORE.defaultState
                hash % 61 == 0 -> Blocks.REDSTONE_ORE.defaultState
                hash % 37 == 0 -> Blocks.GOLD_ORE.defaultState
                hash % 29 == 0 -> Blocks.IRON_ORE.defaultState
                hash % 17 == 0 -> Blocks.COAL_ORE.defaultState
                else -> baseStone
            }
        }

        private fun paletteFor(biomeId: Identifier, cave: Boolean): BiomePalette {
            val path = biomeId.path
            return when {
                "crimson" in path -> BiomePalette(Blocks.CRIMSON_NYLIUM.defaultState, Blocks.NETHERRACK.defaultState, Blocks.NETHERRACK.defaultState, Blocks.LAVA.defaultState, false, TerrainKind.CRIMSON, 6.2, 0.052, 0.12, 3.8, 4)
                "warped" in path -> BiomePalette(Blocks.WARPED_NYLIUM.defaultState, Blocks.NETHERRACK.defaultState, Blocks.NETHERRACK.defaultState, Blocks.LAVA.defaultState, false, TerrainKind.WARPED, 6.0, 0.05, 0.12, 4.0, 4)
                "soul" in path || "valley" in path -> BiomePalette(Blocks.SOUL_SAND.defaultState, Blocks.SOUL_SOIL.defaultState, Blocks.NETHERRACK.defaultState, Blocks.LAVA.defaultState, false, TerrainKind.SOUL, 6.4, 0.05, 0.12, 4.6, 4)
                "basalt" in path || "delta" in path || "volcan" in path || "ash" in path -> BiomePalette(Blocks.BASALT.defaultState, Blocks.BLACKSTONE.defaultState, Blocks.NETHERRACK.defaultState, Blocks.LAVA.defaultState, false, TerrainKind.BASALT, 5.5, 0.048, 0.12, 4.8, 4)
                "nether" in path -> BiomePalette(Blocks.NETHERRACK.defaultState, Blocks.NETHERRACK.defaultState, Blocks.NETHERRACK.defaultState, Blocks.LAVA.defaultState, false, TerrainKind.CRIMSON, 6.0, 0.05, 0.12, 4.2, 4)
                "deep_dark" in path || "skulk" in path -> BiomePalette(Blocks.SCULK.defaultState, Blocks.DEEPSLATE.defaultState, Blocks.DEEPSLATE.defaultState, null, false, TerrainKind.DEEP_DARK, 8.5, 0.035, 0.08, 4.6, 0)
                "dripstone" in path -> BiomePalette(Blocks.DRIPSTONE_BLOCK.defaultState, Blocks.STONE.defaultState, Blocks.STONE.defaultState, Blocks.WATER.defaultState, false, TerrainKind.DRIPSTONE_CAVE, 7.5, 0.04, 0.09, 4.3, 3)
                "lush" in path -> BiomePalette(Blocks.MOSS_BLOCK.defaultState, Blocks.ROOTED_DIRT.defaultState, Blocks.STONE.defaultState, Blocks.WATER.defaultState, false, TerrainKind.LUSH_CAVE, 7.0, 0.042, 0.1, 4.1, 3)
                "end" in path || "chorus" in path -> BiomePalette(Blocks.END_STONE.defaultState, Blocks.END_STONE.defaultState, Blocks.END_STONE.defaultState, null, false, TerrainKind.END, 8.0, 0.03, 0.08, 5.0, 0)
                "mushroom" in path -> BiomePalette(Blocks.MYCELIUM.defaultState, Blocks.DIRT.defaultState, Blocks.STONE.defaultState, Blocks.WATER.defaultState, true, TerrainKind.MUSHROOM, 7.2, 0.04, 0.1, 4.4, 4)
                "desert" in path || "dune" in path -> BiomePalette(Blocks.SAND.defaultState, Blocks.SANDSTONE.defaultState, Blocks.SANDSTONE.defaultState, Blocks.WATER.defaultState, true, TerrainKind.DESERT, 9.5, 0.038, 0.09, 5.2, 3)
                "badlands" in path || "mesa" in path -> BiomePalette(Blocks.RED_SAND.defaultState, Blocks.TERRACOTTA.defaultState, Blocks.RED_SANDSTONE.defaultState, Blocks.WATER.defaultState, false, TerrainKind.BADLANDS, 8.4, 0.034, 0.08, 5.2, 3)
                "snow" in path || "frozen" in path || "ice" in path -> BiomePalette(Blocks.SNOW_BLOCK.defaultState, Blocks.DIRT.defaultState, Blocks.STONE.defaultState, Blocks.WATER.defaultState, true, TerrainKind.SNOWY, 7.2, 0.042, 0.1, 4.5, 4)
                "taiga" in path || "spruce" in path || "redwood" in path -> BiomePalette(Blocks.PODZOL.defaultState, Blocks.DIRT.defaultState, Blocks.STONE.defaultState, Blocks.WATER.defaultState, true, TerrainKind.TAIGA, 6.6, 0.045, 0.11, 4.3, 4)
                "swamp" in path || "mangrove" in path || "marsh" in path -> BiomePalette(Blocks.MUD.defaultState, Blocks.DIRT.defaultState, Blocks.STONE.defaultState, Blocks.WATER.defaultState, true, TerrainKind.SWAMP, 10.0, 0.03, 0.08, 5.0, 3)
                "beach" in path || "ocean" in path || "river" in path -> BiomePalette(Blocks.SAND.defaultState, Blocks.SANDSTONE.defaultState, Blocks.STONE.defaultState, Blocks.WATER.defaultState, true, TerrainKind.BEACH, 10.0, 0.028, 0.08, 5.4, 3)
                cave -> BiomePalette(Blocks.STONE.defaultState, Blocks.DEEPSLATE.defaultState, Blocks.DEEPSLATE.defaultState, null, false, TerrainKind.DRIPSTONE_CAVE, 7.8, 0.038, 0.09, 4.2, 0)
                else -> BiomePalette(Blocks.GRASS_BLOCK.defaultState, Blocks.DIRT.defaultState, Blocks.STONE.defaultState, Blocks.WATER.defaultState, true, TerrainKind.FOREST, 7.0, 0.042, 0.1, 4.2, 4)
            }
        }

        private fun facingOffset(facing: Direction, amount: Int): Pair<Int, Int> = Pair(facing.offsetX * amount, facing.offsetZ * amount)
        private fun sampleNoise(seed: Int, x: Double, z: Double): Double {
            val x0 = floor(x).toInt()
            val z0 = floor(z).toInt()
            val tx = x - x0
            val tz = z - z0
            val n00 = hashedValue(seed, x0, z0)
            val n10 = hashedValue(seed, x0 + 1, z0)
            val n01 = hashedValue(seed, x0, z0 + 1)
            val n11 = hashedValue(seed, x0 + 1, z0 + 1)
            val sx = tx * tx * (3.0 - 2.0 * tx)
            val sz = tz * tz * (3.0 - 2.0 * tz)
            return lerp(lerp(n00, n10, sx), lerp(n01, n11, sx), sz)
        }
        private fun hashedValue(seed: Int, x: Int, z: Int): Double = (positiveHash(seed, x, z) % 10_000) / 5_000.0 - 1.0
        private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
        private fun setIfInBuildHeight(world: ServerWorld, pos: BlockPos, state: BlockState) { if (pos.y in world.bottomY until (world.bottomY + world.height)) world.setBlockState(pos, state, 2) }
    }

    private enum class TerrainKind { PLAINS, FOREST, TAIGA, SNOWY, SWAMP, DESERT, BADLANDS, MUSHROOM, BEACH, CRIMSON, WARPED, SOUL, BASALT, LUSH_CAVE, DRIPSTONE_CAVE, DEEP_DARK, END }
    private data class BiomePalette(val top: BlockState, val filler: BlockState, val stone: BlockState, val liquid: BlockState?, val allowCottages: Boolean, val terrainKind: TerrainKind, val crownDivisor: Double, val mainFreq: Double, val detailFreq: Double, val pondDivisor: Double, val pondDepth: Int)
    private data class SphereInfo(val key: String, val centerX: Int, val centerY: Int, val centerZ: Int, val radius: Int, val cave: Boolean, val biomeId: Identifier, val styleSeed: Int, val palette: BiomePalette)
    private data class BridgeInfo(val from: SphereInfo, val to: SphereInfo)
    private data class CottageInfo(val centerX: Int, val floorY: Int, val centerZ: Int, val facing: Direction, val width: Int, val depth: Int, val variant: Int)
    private data class SphereLayout(val spheres: List<SphereInfo>, val bridges: List<BridgeInfo>, val cottages: List<CottageInfo>)

    override fun generate(context: FeatureContext<DefaultFeatureConfig>): Boolean {
        val world = context.world
        val origin = context.origin
        val serverWorld = world.toServerWorld()
        if (!AgeSubdimensionManager.isPrimaryAgeRealm(serverWorld.registryKey.value)) return false
        val profile = AgeProfileManager.getOrGenerateProfile(serverWorld.server, serverWorld.registryKey.value)
        if (profile.terrainType != TerrainType.BIOSPHERES) return false
        val layout = buildLayout(serverWorld, origin.x - 4, origin.x + 19, origin.z - 4, origin.z + 19)
        layout.spheres.forEach { sphere ->
            val minX = max(origin.x, sphere.centerX - sphere.radius - 2)
            val maxX = min(origin.x + 15, sphere.centerX + sphere.radius + 2)
            val minZ = max(origin.z, sphere.centerZ - sphere.radius - 2)
            val maxZ = min(origin.z + 15, sphere.centerZ + sphere.radius + 2)
            if (minX <= maxX && minZ <= maxZ) emitSphere(sphere, minX, maxX, minZ, maxZ) { pos, state -> setIfInBuildHeight(world, pos, state) }
        }
        layout.bridges.forEach { bridge -> emitBridge(bridge.from, bridge.to) { pos, state -> if (pos.x in origin.x..(origin.x + 15) && pos.z in origin.z..(origin.z + 15)) setIfInBuildHeight(world, pos, state) } }
        layout.cottages.forEach { cottage -> emitCottage(cottage) { pos, state -> if (pos.x in origin.x..(origin.x + 15) && pos.z in origin.z..(origin.z + 15)) setIfInBuildHeight(world, pos, state) } }
        buildOriginPlatform { pos, state -> if (pos.x in origin.x..(origin.x + 15) && pos.z in origin.z..(origin.z + 15)) setIfInBuildHeight(world, pos, state) }
        return true
    }

    private fun setIfInBuildHeight(world: StructureWorldAccess, pos: BlockPos, state: BlockState) {
        if (pos.y in world.bottomY until (world.bottomY + world.height)) world.setBlockState(pos, state, 2)
    }
}
