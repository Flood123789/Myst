package mystcraft.flood.entity

import net.minecraft.entity.EntityDimensions
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.PriorityQueue

data class ReaperSurfaceNode(val cell: BlockPos, val normal: Direction)

/** Surface graph shared by floor, wall, and ceiling routes for the tiny owner-bound crawler. */
object ReaperSurfacePath {
    private data class OpenNode(val node: ReaperSurfaceNode, val score: Double)

    fun find(
        world: World,
        dimensions: EntityDimensions,
        startCell: BlockPos,
        startNormal: Direction,
        target: Vec3d,
        maxVisited: Int = 384
    ): List<ReaperSurfaceNode> {
        val validCache = HashMap<ReaperSurfaceNode, Boolean>()
        val solidCache = HashMap<BlockPos, Boolean>()
        val valid: (ReaperSurfaceNode) -> Boolean = { node ->
            validCache.getOrPut(node) {
                val support = node.cell.offset(node.normal.opposite)
                world.getBlockState(support).isSolidBlock(world, support) &&
                    world.isSpaceEmpty(fittingBox(node.cell, dimensions))
            }
        }
        val solid: (BlockPos) -> Boolean = { pos ->
            solidCache.getOrPut(pos) { world.getBlockState(pos).isSolidBlock(world, pos) }
        }
        val start = nearestValidStart(startCell, startNormal, valid) ?: return emptyList()

        val open = PriorityQueue(compareBy<OpenNode> { it.score })
        val cost = hashMapOf(start to 0.0)
        val previous = hashMapOf<ReaperSurfaceNode, ReaperSurfaceNode>()
        val closed = hashSetOf<ReaperSurfaceNode>()
        var closest = start
        var closestDistance = distanceSquared(start, target)
        open.add(OpenNode(start, kotlin.math.sqrt(closestDistance)))

        while (open.isNotEmpty() && closed.size < maxVisited) {
            val current = open.remove().node
            if (!closed.add(current)) continue
            val currentDistance = distanceSquared(current, target)
            if (currentDistance < closestDistance) {
                closest = current
                closestDistance = currentDistance
            }
            if (currentDistance <= TARGET_RADIUS_SQUARED) return reconstruct(current, previous)

            for (next in SurfacePathTopology.neighbours(current, valid, solid)) {
                if (next.cell.getManhattanDistance(start.cell) > MAX_ROUTE_RADIUS) continue
                val edge = if (next.cell == current.cell) TURN_COST else 1.0
                val candidateCost = cost.getValue(current) + edge
                if (candidateCost >= cost.getOrDefault(next, Double.POSITIVE_INFINITY)) continue
                cost[next] = candidateCost
                previous[next] = current
                val estimate = candidateCost + kotlin.math.sqrt(distanceSquared(next, target))
                open.add(OpenNode(next, estimate))
            }
        }

        return if (closest != start) reconstruct(closest, previous) else emptyList()
    }

    private fun nearestValidStart(
        cell: BlockPos,
        normal: Direction,
        valid: (ReaperSurfaceNode) -> Boolean
    ): ReaperSurfaceNode? {
        val exact = ReaperSurfaceNode(cell, normal)
        if (valid(exact)) return exact
        for (direction in Direction.values()) {
            val shifted = ReaperSurfaceNode(cell.offset(direction), normal)
            if (valid(shifted)) return shifted
        }
        return null
    }

    private fun reconstruct(
        destination: ReaperSurfaceNode,
        previous: Map<ReaperSurfaceNode, ReaperSurfaceNode>
    ): List<ReaperSurfaceNode> {
        val result = ArrayList<ReaperSurfaceNode>()
        var cursor: ReaperSurfaceNode? = destination
        while (cursor != null) {
            result.add(cursor)
            cursor = previous[cursor]
        }
        result.reverse()
        return result
    }

    private fun fittingBox(cell: BlockPos, dimensions: EntityDimensions): Box {
        val half = dimensions.width.toDouble() / 2.0
        return Box(
            cell.x + 0.5 - half, cell.y.toDouble(), cell.z + 0.5 - half,
            cell.x + 0.5 + half, cell.y + dimensions.height.toDouble(), cell.z + 0.5 + half
        )
    }

    private fun distanceSquared(node: ReaperSurfaceNode, target: Vec3d): Double =
        Vec3d.ofCenter(node.cell).squaredDistanceTo(target)

    private const val TARGET_RADIUS_SQUARED = 2.25 * 2.25
    private const val MAX_ROUTE_RADIUS = 20
    private const val TURN_COST = 0.35
}

/** Pure surface-transition rules, separated for regression tests. */
object SurfacePathTopology {
    fun neighbours(
        node: ReaperSurfaceNode,
        valid: (ReaperSurfaceNode) -> Boolean,
        solid: (BlockPos) -> Boolean
    ): List<ReaperSurfaceNode> {
        val result = LinkedHashSet<ReaperSurfaceNode>()
        for (heading in Direction.values()) {
            if (heading.axis == node.normal.axis) continue

            val samePlane = ReaperSurfaceNode(node.cell.offset(heading), node.normal)
            if (valid(samePlane)) result.add(samePlane)

            // Concave corner: remain in the air cell and roll onto the blocking face ahead.
            val concave = ReaperSurfaceNode(node.cell, heading.opposite)
            if (solid(node.cell.offset(heading)) && valid(concave)) result.add(concave)

            // Convex lip: move around the edge and down behind the previous supporting block.
            val convexCell = node.cell.offset(heading).offset(node.normal.opposite)
            val convex = ReaperSurfaceNode(convexCell, heading)
            if (valid(convex)) result.add(convex)
        }
        return result.toList()
    }
}
