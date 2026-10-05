package ch.smartkraft.pantherlauncher.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import ch.smartkraft.pantherlauncher.R

/** Draws a faint dot at every cell corner, so the grid is visible while widgets are arranged. */
class GridDotsDrawable(private val grid: WidgetGrid, context: Context) : Drawable() {

    private val radius = 1.5f * context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val value = TypedValue()
        context.theme.resolveAttribute(R.attr.primaryColor, value, true)
        color = ColorUtils.setAlphaComponent(value.data, 70)
    }

    override fun draw(canvas: Canvas) {
        val half = grid.margin / 2f
        var row = 0
        while (grid.offsetOf(row) - half <= bounds.height()) {
            for (col in 0..grid.columns) {
                canvas.drawCircle(grid.offsetOf(col) - half, grid.offsetOf(row) - half, radius, paint)
            }
            row++
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
