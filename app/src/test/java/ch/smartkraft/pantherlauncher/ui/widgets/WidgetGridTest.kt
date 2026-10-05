package ch.smartkraft.pantherlauncher.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGridTest {

    private val grid = WidgetGrid(parentWidth = 1080, columns = 14, margin = 16)

    @Test
    fun cellsSurviveARoundTripThroughPixels() {
        for (cells in 1..14) assertEquals(cells, grid.cellsFor(grid.sizeOf(cells)))
        for (index in 0..40) assertEquals(index, grid.indexAt(grid.offsetOf(index).toFloat()))
    }

    @Test
    fun positionsSnapToTheNearestCell() {
        val pitch = grid.cell + grid.margin
        assertEquals(3, grid.indexAt(3 * pitch + pitch * 0.4f))
        assertEquals(4, grid.indexAt(3 * pitch + pitch * 0.6f))
        assertEquals(5, grid.cellsFor(grid.sizeOf(5) + pitch / 3))
    }

    @Test
    fun overlapIsDetectedOnlyWhenCellsAreShared() {
        val a = GridRect(0, 0, 4, 2)
        assertTrue(a.overlaps(GridRect(3, 1, 4, 2)))
        assertFalse(a.overlaps(GridRect(4, 0, 4, 2)))   // side by side
        assertFalse(a.overlaps(GridRect(0, 2, 4, 2)))   // directly below
    }

    @Test
    fun fitKeepsAWidgetInsideTheGrid() {
        assertEquals(GridRect(10, 0, 4, 1), grid.fit(GridRect(13, -2, 4, 0)))
        assertEquals(GridRect(0, 3, 14, 2), grid.fit(GridRect(5, 3, 30, 2)))
        assertEquals(WidgetGrid.MIN_CELLS_W, grid.fit(GridRect(0, 0, 1, 1)).cellsW)
    }

    @Test
    fun newWidgetsGoToTheFirstPlaceThatIsReallyFree() {
        val occupied = listOf(GridRect(0, 0, 7, 8), GridRect(7, 0, 4, 2))
        assertEquals(GridRect(11, 0, 3, 2), grid.firstFree(occupied, 3, 2))
        assertEquals(GridRect(7, 2, 7, 4), grid.firstFree(occupied, 7, 4))
        assertEquals(GridRect(0, 8, 14, 4), grid.firstFree(occupied, 14, 4))
    }

    @Test
    fun nothingIsPlacedBelowThePage() {
        // 2000 px high page: 25 rows of 78 px
        val page = WidgetGrid(parentWidth = 1080, columns = 14, margin = 16, parentHeight = 2000)
        assertEquals(25, page.rows)
        assertEquals(GridRect(0, 21, 4, 4), page.fit(GridRect(0, 40, 4, 4)))      // pushed back up
        assertEquals(25, page.fit(GridRect(0, 0, 4, 60)).cellsH)                 // no taller than the page
        val full = listOf(GridRect(0, 0, 14, 20))
        assertEquals(GridRect(0, 20, 14, 5), page.firstFree(full, 14, 5))
        assertEquals(null, page.firstFree(full, 14, 6))                          // no room left
    }

    @Test
    fun defaultSizeFollowsWhatTheWidgetAsksFor() {
        assertEquals(7 to 8, grid.defaultSpan(2, 2, 289, 289))      // 2x2 widget: half the width
        assertEquals(14 to 16, grid.defaultSpan(4, 4, 656, 656))    // 4x4 widget: full width
        assertEquals(14 to 8, grid.defaultSpan(5, 2, 788, 315))     // wider than the screen: clamped
        assertEquals(9 to 2, grid.defaultSpan(0, 0, 683, 126))      // no cell size given: from pixels
    }
}
