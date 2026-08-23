package mystcraft.flood.generation

import mystcraft.flood.MystcraftReforged
import mystcraft.flood.generation.profile.AgeProfile
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.nbt.NbtIo
import net.minecraft.registry.Registries
import net.minecraft.structure.StructurePlacementData
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.gen.feature.DefaultFeatureConfig
import net.minecraft.world.gen.feature.util.FeatureContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isRegularFile
import kotlin.math.ceil
import kotlin.math.max

/**
 * Runtime and bundled structure-template overrides for procedural Age features.
 *
 * End-user templates in config/mystcraft-reforged/structures/<type> replace bundled templates.
 * When neither location contains an NBT file, the calling feature keeps its procedural fallback.
 */
object CustomStructureOverrides {
    enum class AnchorMode { SURFACE, SKY }

    data class StructureType(val id: String, val spacingChunks: Int, val anchorMode: AnchorMode = AnchorMode.SURFACE)

    val TYPES = listOf(
        StructureType("abandoned_archive", 14),
        StructureType("ancient_aqueducts", 12),
        StructureType("ancient_remains", 10),
        StructureType("collapsed_observatory", 14),
        StructureType("crystal_formations", 10),
        StructureType("floating_castle", 40, AnchorMode.SKY),
        StructureType("forgotten_ruins", 12),
        StructureType("gateway_ruins", 13),
        StructureType("giant_obelisks", 12),
        StructureType("giant_trees", 12),
        StructureType("memory_blooms", 10),
        StructureType("meteor_showers", 12),
        StructureType("page_storms", 12),
        StructureType("sky_spheres", 12, AnchorMode.SKY),
        StructureType("stable_sanctuaries", 12),
        StructureType("tendrils", 12, AnchorMode.SKY)
    ) + ExoticAgeThemes.ALL.map { theme -> StructureType(theme, 10) }

    private data class LoadedTemplate(val source: Path, val template: StructureTemplate)

    private val typeById = TYPES.associateBy(StructureType::id)
    private val templateCache = ConcurrentHashMap<String, List<LoadedTemplate>>()
    private val configRoot: Path
        get() = FabricLoader.getInstance().configDir.resolve(MystcraftReforged.MOD_ID).resolve("structures")

    fun initializeFolders() {
        TYPES.forEach { Files.createDirectories(configRoot.resolve(it.id)) }
        templateCache.clear()
        MystcraftReforged.LOGGER.info("Custom structure folders ready at {}", configRoot.toAbsolutePath())
    }

    /** Null means there is no override and the feature should run its procedural generator. */
    fun generateIfPresent(
        context: FeatureContext<DefaultFeatureConfig>,
        typeId: String,
        profile: AgeProfile,
        activationChance: Float = 1.0f
    ): Boolean? {
        val type = typeById[typeId] ?: return null
        val templates = templateCache.computeIfAbsent(typeId, ::loadTemplates)
        if (templates.isEmpty()) return null

        val world = context.world
        val currentChunk = ChunkPos(context.origin)
        val maxDiameterBlocks = templates.maxOf { loaded ->
            max(loaded.template.size.x, loaded.template.size.z)
        }.coerceAtLeast(1)
        val neighborRadius = ceil(maxDiameterBlocks / (type.spacingChunks * 16.0)).toInt().coerceAtLeast(1)
        val currentCellX = Math.floorDiv(currentChunk.x, type.spacingChunks)
        val currentCellZ = Math.floorDiv(currentChunk.z, type.spacingChunks)
        var placed = false

        for (cellX in currentCellX - neighborRadius..currentCellX + neighborRadius) {
            for (cellZ in currentCellZ - neighborRadius..currentCellZ + neighborRadius) {
                val seed = placementSeed(profile.seed, typeId, cellX, cellZ)
                val random = java.util.Random(seed)
                if (random.nextFloat() > activationChance.coerceIn(0f, 1f)) continue

                val loaded = templates[random.nextInt(templates.size)]
                val rotation = BlockRotation.entries[random.nextInt(BlockRotation.entries.size)]
                val mirror = if (random.nextBoolean()) BlockMirror.NONE else BlockMirror.FRONT_BACK
                val placement = StructurePlacementData()
                    .setRotation(rotation)
                    .setMirror(mirror)
                    .setIgnoreEntities(false)

                val margin = (type.spacingChunks / 5).coerceAtLeast(1)
                val span = (type.spacingChunks - margin * 2).coerceAtLeast(1)
                val anchorChunkX = cellX * type.spacingChunks + margin + random.nextInt(span)
                val anchorChunkZ = cellZ * type.spacingChunks + margin + random.nextInt(span)
                val anchorX = anchorChunkX * 16 + 8
                val anchorZ = anchorChunkZ * 16 + 8

                val rawBounds = loaded.template.calculateBoundingBox(placement, BlockPos.ORIGIN)
                val originX = anchorX - Math.floorDiv(rawBounds.minX + rawBounds.maxX, 2)
                val originZ = anchorZ - Math.floorDiv(rawBounds.minZ + rawBounds.maxZ, 2)
                // Do not sample terrain in a distant owner cell while this chunk is generating.
                // First reject templates whose horizontal footprint cannot touch this chunk.
                val horizontalBounds = loaded.template.calculateBoundingBox(placement, BlockPos(originX, 0, originZ))
                if (!intersectsChunk(horizontalBounds, currentChunk)) continue

                val anchorY = when (type.anchorMode) {
                    AnchorMode.SURFACE -> FeatureBuildHelper.findGround(world, anchorX, anchorZ)?.y?.plus(1) ?: continue
                    AnchorMode.SKY -> 144 + random.nextInt(48)
                }
                val origin = BlockPos(originX, anchorY - rawBounds.minY, originZ)
                val bounds = loaded.template.calculateBoundingBox(placement, origin)
                if (!intersectsChunk(bounds, currentChunk)) continue

                val chunkBounds = BlockBox(
                    currentChunk.startX,
                    world.bottomY,
                    currentChunk.startZ,
                    currentChunk.endX,
                    world.topY - 1,
                    currentChunk.endZ
                )
                val clipped = placement.copy().setBoundingBox(chunkBounds)
                val placementRandom = net.minecraft.util.math.random.Random.create(seed xor currentChunk.toLong())
                placed = loaded.template.place(world, origin, origin, clipped, placementRandom, 2 or 16) || placed
            }
        }
        return placed
    }

    private fun loadTemplates(typeId: String): List<LoadedTemplate> {
        val external = loadDirectory(configRoot.resolve(typeId))
        val selected = if (external.isNotEmpty()) external else bundledDirectories(typeId).flatMap(::loadDirectory)
        if (selected.isNotEmpty()) {
            MystcraftReforged.LOGGER.info(
                "Loaded {} {} structure override(s) from {}",
                selected.size,
                typeId,
                if (external.isNotEmpty()) "config" else "bundled assets"
            )
        }
        return selected.sortedBy { it.source.toString() }
    }

    private fun bundledDirectories(typeId: String): List<Path> {
        val relative = "data/${MystcraftReforged.MOD_ID}/structures/customizable/$typeId"
        return FabricLoader.getInstance()
            .getModContainer(MystcraftReforged.MOD_ID)
            .map { container -> container.rootPaths.map { root -> root.resolve(relative) } }
            .orElse(emptyList())
    }

    private fun loadDirectory(directory: Path): List<LoadedTemplate> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.walk(directory).use { paths ->
            paths.iterator().asSequence()
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".nbt", ignoreCase = true) }
                .mapNotNull(::loadTemplate)
                .toList()
        }
    }

    private fun loadTemplate(path: Path): LoadedTemplate? = try {
        val nbt = Files.newInputStream(path).use { input -> NbtIo.readCompressed(input) }
        val template = StructureTemplate()
        template.readNbt(Registries.BLOCK.readOnlyWrapper, nbt)
        LoadedTemplate(path, template)
    } catch (error: Exception) {
        MystcraftReforged.LOGGER.error("Could not load custom structure template {}", path, error)
        null
    }

    private fun placementSeed(seed: Long, typeId: String, cellX: Int, cellZ: Int): Long =
        seed xor
            (typeId.hashCode().toLong() * 6_364_136_223_846_793_005L) xor
            (cellX.toLong() * 1_442_695_040_888_963_407L) xor
            (cellZ.toLong() * 2_853_942_125_093_471_117L)

    private fun intersectsChunk(bounds: BlockBox, chunk: ChunkPos): Boolean =
        bounds.maxX >= chunk.startX && bounds.minX <= chunk.endX &&
            bounds.maxZ >= chunk.startZ && bounds.minZ <= chunk.endZ
}
