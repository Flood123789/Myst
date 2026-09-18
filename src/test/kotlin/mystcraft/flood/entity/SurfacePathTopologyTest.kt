package mystcraft.flood.entity

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfacePathTopologyTest {
    @Test
    fun `floor route can turn onto wall and wall route onto ceiling`() {
        val floorCell = BlockPos(0, 1, 0)
        val wallCell = BlockPos(1, 1, 0)
        val ceilingCell = BlockPos(0, 2, 0)
        val solids = setOf(
            BlockPos(0, 0, 0),
            wallCell,
            BlockPos(1, 2, 0),
            BlockPos(0, 3, 0)
        )
        fun valid(node: ReaperSurfaceNode): Boolean =
            node.cell !in solids && node.cell.offset(node.normal.opposite) in solids

        val floor = ReaperSurfaceNode(floorCell, Direction.UP)
        val wall = ReaperSurfaceNode(floorCell, Direction.WEST)
        val higherWall = ReaperSurfaceNode(ceilingCell, Direction.WEST)
        val ceiling = ReaperSurfaceNode(ceilingCell, Direction.DOWN)

        assertTrue(wall in SurfacePathTopology.neighbours(floor, ::valid, solids::contains))
        assertTrue(higherWall in SurfacePathTopology.neighbours(wall, ::valid, solids::contains))
        assertTrue(ceiling in SurfacePathTopology.neighbours(higherWall, ::valid, solids::contains))
    }
}
