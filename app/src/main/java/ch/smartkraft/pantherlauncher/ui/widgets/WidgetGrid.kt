package ch.smartkraft.pantherlauncher.ui.widgets

import kotlin.math.ceil
import kotlin.math.roundToInt

/** A widget's place on the grid, in cells. This is the only thing that is saved for a widget. */
data class GridRect(val col: Int, val row: Int, val cellsW: Int, val cellsH: Int) {
    fun overlaps(other: GridRect): Boolean =
        col < other.col + other.cellsW && other.col < col + cellsW &&
                row < other.row + other.cellsH && other.row < row + cellsH
}

/**
 * Converts between grid cells and pixels for the widget page. Pixels are always derived from cells
 * and never stored, so a save/restore cycle cannot move or resize a widget.
 */
class WidgetGrid(parentWidth: Int, val columns: Int, val margin: Int, parentHeight: Int = 0) {

    companion object {
        const val MIN_CELLS_W = 2
        const val MIN_CELLS_H = 1

        // A widget declares its preferred size for a home screen that is about four cells wide
        private const val LAUNCHER_COLUMNS = 4
        private const val CELLS_PER_LAUNCHER_ROW = 4
    }

    val cell: Int = ((parentWidth - margin * (columns - 1)) / columns).coerceAtLeast(1)
    private val pitch: Int = cell + margin

    /** Rows that fit on the page. The page does not scroll, so nothing may be placed below them. */
    val rows: Int = if (parentHeight > 0) ((parentHeight + margin) / pitch).coerceAtLeast(MIN_CELLS_H) else Int.MAX_VALUE

    /** Pixel position of the left or top edge of a column or row. */
    fun offsetOf(index: Int): Int = index * pitch

    /** Pixel size of a span of [cells] cells, including the gaps between them. */
    fun sizeOf(cells: Int): Int = cells * pitch - margin

    /** The column or row whose edge is nearest to [px]. */
    fun indexAt(px: Float): Int = (px / pitch).roundToInt()

    /** How many cells a pixel size covers, rounded to the nearest cell. */
    fun cellsFor(px: Int): Int = ((px + margin).toFloat() / pitch).roundToInt()

    /** Keeps [rect] at least the minimum size and inside the grid. */
    fun fit(rect: GridRect): GridRect {
        val cellsW = rect.cellsW.coerceIn(MIN_CELLS_W, columns)
        val cellsH = rect.cellsH.coerceIn(MIN_CELLS_H, rows)
        return GridRect(rect.col.coerceIn(0, columns - cellsW), rect.row.coerceIn(0, rows - cellsH), cellsW, cellsH)
    }

    /**
     * First place, scanning row by row, where a widget of this size overlaps none of [occupied];
     * null when the page has no room left for it.
     */
    fun firstFree(occupied: List<GridRect>, cellsW: Int, cellsH: Int): GridRect? {
        val size = fit(GridRect(0, 0, cellsW, cellsH))
        val lastRow = if (rows == Int.MAX_VALUE) (occupied.maxOfOrNull { it.row + it.cellsH } ?: 0) else rows - size.cellsH
        for (row in 0..lastRow) {
            for (col in 0..columns - size.cellsW) {
                val candidate = size.copy(col = col, row = row)
                if (occupied.none { it.overlaps(candidate) }) return candidate
            }
        }
        return null
    }

    /**
     * Size a widget starts with. Uses the size the widget asks for in launcher cells when it gives one
     * (Android 12+), otherwise its declared default size in pixels.
     */
    fun defaultSpan(targetCellsW: Int, targetCellsH: Int, minWidthPx: Int, minHeightPx: Int): Pair<Int, Int> {
        val cellsW = if (targetCellsW > 0) {
            (targetCellsW * columns.toFloat() / LAUNCHER_COLUMNS).roundToInt()
        } else {
            ceil((minWidthPx + margin).toDouble() / pitch).toInt()
        }
        val cellsH = if (targetCellsH > 0) {
            targetCellsH * CELLS_PER_LAUNCHER_ROW
        } else {
            ceil((minHeightPx + margin).toDouble() / pitch).toInt()
        }
        val fitted = fit(GridRect(0, 0, cellsW, cellsH))
        return fitted.cellsW to fitted.cellsH
    }
}
