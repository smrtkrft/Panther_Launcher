package ch.smartkraft.pantherlauncher.ui.widgets

import android.annotation.SuppressLint
import android.util.SizeF
import android.os.Build
import androidx.core.graphics.ColorUtils
import android.util.TypedValue
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.graphics.Color
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import ch.smartkraft.components.views.FontBottomSheetDialogLocked
import ch.smartkraft.common.AppLogger
import ch.smartkraft.common.getLocalizedString
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.helper.getInstallSource
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class ResizableWidgetWrapper(
    context: Context,
    val hostView: AppWidgetHostView,
    val widgetInfo: AppWidgetProviderInfo,
    val appWidgetHost: AppWidgetHost,
    val onUpdate: () -> Unit,
    val onDelete: () -> Unit,
    private val gridColumns: Int,
    private val cellMargin: Int,
    val defaultCellsW: Int = 1,
    val defaultCellsH: Int = 1
) : FrameLayout(context) {

    companion object {
        private const val TAG = "ResizableWidgetWrapper"
    }

    // The widget's place on the grid in cells. Pixels are derived from these and never stored.
    var currentCol: Int = 0
    var currentRow: Int = 0
    var cellsW: Int = defaultCellsW
    var cellsH: Int = defaultCellsH

    /** Asked before a move or resize is accepted; false when the target cells are taken. */
    var canPlace: (GridRect) -> Boolean = { true }

    val gridRect: GridRect get() = GridRect(currentCol, currentRow, cellsW, cellsH)

    private var lastX = 0f
    private var lastY = 0f
    private val minSize = 100

    /** Called when this widget enters or leaves resize mode, so the page can dim the others. */
    var onResizeModeChanged: (Boolean) -> Unit = {}

    var isResizeMode = false
        set(value) {
            if (field == value) return
            field = value
            updateEditVisuals()
            onResizeModeChanged(value)
        }

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Int = (value * density).toInt()

    // Touch area of a handle; the visible pill inside it is much smaller
    private val handleSize = dp(28f)
    private val accentColor: Int = TypedValue().let {
        context.theme.resolveAttribute(R.attr.primaryColor, it, true)
        it.data
    }
    private val onAccentColor: Int = if (ColorUtils.calculateLuminance(accentColor) > 0.5) Color.BLACK else Color.WHITE
    private val blockedColor = "#E5484D".toColorInt()
    private val outlineRadius = dp(18f).toFloat()

    private val sizeLabel = TextView(context).apply {
        setTextColor(onAccentColor)
        textSize = 13f
        setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
        background = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            setColor(accentColor)
        }
        visibility = GONE
    }

    private val topHandle = createHandle()
    private val bottomHandle = createHandle()
    private val leftHandle = createHandle()
    private val rightHandle = createHandle()

    private val topLeftHandle = createHandle()
    private val topRightHandle = createHandle()
    private val bottomLeftHandle = createHandle()
    private val bottomRightHandle = createHandle()

    private var activeDialog: FontBottomSheetDialogLocked? = null
    private var ghostView: View? = null

    init {

        AppLogger.d(TAG, "🧩 Initializing wrapper for widget: ${widgetInfo.provider.packageName}")

        // Position and size come from the grid cells once the wrapper is attached
        post { applyCells() }

        addView(
            hostView, LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
        AppLogger.d(TAG, "✅ HostView added to wrapper")

        topHandle.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, handleSize).apply { gravity = Gravity.TOP }
        bottomHandle.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, handleSize).apply { gravity = Gravity.BOTTOM }
        leftHandle.layoutParams = LayoutParams(handleSize, LayoutParams.MATCH_PARENT).apply { gravity = Gravity.START }
        rightHandle.layoutParams = LayoutParams(handleSize, LayoutParams.MATCH_PARENT).apply { gravity = Gravity.END }

        topLeftHandle.layoutParams = LayoutParams(handleSize, handleSize).apply { gravity = Gravity.TOP or Gravity.START }
        topRightHandle.layoutParams = LayoutParams(handleSize, handleSize).apply { gravity = Gravity.TOP or Gravity.END }
        bottomLeftHandle.layoutParams = LayoutParams(handleSize, handleSize).apply { gravity = Gravity.BOTTOM or Gravity.START }
        bottomRightHandle.layoutParams = LayoutParams(handleSize, handleSize).apply { gravity = Gravity.BOTTOM or Gravity.END }


        listOf(topHandle, bottomHandle).forEach { it.background = handleDrawable(36f, 5f) }
        listOf(leftHandle, rightHandle).forEach { it.background = handleDrawable(5f, 36f) }
        listOf(topLeftHandle, topRightHandle, bottomLeftHandle, bottomRightHandle).forEach { it.background = handleDrawable(10f, 10f) }

        addView(sizeLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        addView(topHandle)
        addView(bottomHandle)
        addView(leftHandle)
        addView(rightHandle)

        addView(topLeftHandle)
        addView(topRightHandle)
        addView(bottomLeftHandle)
        addView(bottomRightHandle)

        topHandle.bringToFront()
        bottomHandle.bringToFront()
        leftHandle.bringToFront()
        rightHandle.bringToFront()

        topLeftHandle.bringToFront()
        topRightHandle.bringToFront()
        bottomLeftHandle.bringToFront()
        bottomRightHandle.bringToFront()


        setHandlesVisible(false)
        attachResizeAndDragHandlers()
    }

    private fun grid(): WidgetGrid? {
        val parentView = parent as? View ?: return null
        if (parentView.width <= 0) return null
        return WidgetGrid(parentView.width, gridColumns, cellMargin, parentView.height)
    }

    /** Places and sizes the wrapper from its grid cells. */
    fun applyCells() {
        val grid = grid() ?: return
        val widthPx = grid.sizeOf(cellsW)
        val heightPx = grid.sizeOf(cellsH)
        translationX = grid.offsetOf(currentCol).toFloat()
        translationY = grid.offsetOf(currentRow).toFloat()
        val lp = (layoutParams as? LayoutParams) ?: LayoutParams(widthPx, heightPx)
        lp.width = widthPx
        lp.height = heightPx
        lp.leftMargin = 0
        lp.topMargin = 0
        layoutParams = lp
        fillHostView(widthPx, heightPx)
    }

    /** Takes [target] if it is free and returns true; otherwise goes back to where it was. */
    private fun moveTo(target: GridRect): Boolean {
        val grid = grid() ?: return false
        val fitted = grid.fit(target)
        val accepted = fitted != gridRect && canPlace(fitted)
        if (accepted) {
            currentCol = fitted.col
            currentRow = fitted.row
            cellsW = fitted.cellsW
            cellsH = fitted.cellsH
        }
        applyCells()
        return accepted
    }

    /** The cells the wrapper covers at its current pixel position and size. */
    private fun rectUnderWrapper(): GridRect? {
        val grid = grid() ?: return null
        val lp = layoutParams as? LayoutParams ?: return null
        val col = grid.indexAt(translationX)
        val row = grid.indexAt(translationY)
        val endCol = grid.indexAt(translationX + lp.width + cellMargin)
        val endRow = grid.indexAt(translationY + lp.height + cellMargin)
        return grid.fit(GridRect(col, row, endCol - col, endRow - row))
    }

    fun exitResizeMode() {
        isResizeMode = false
        setHandlesVisible(false)
        applyCells()
    }

    private fun fillHostView(parentWidth: Int = width, parentHeight: Int = height) {
        AppLogger.d(TAG, "fillHostView($parentWidth x $parentHeight) called")
        // 1. Force hostView to fill THIS wrapper
        hostView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        hostView.requestLayout()

        // 2. Use parent’s width/height for widget sizing
        post {
            if (parentWidth <= 0 || parentHeight <= 0) {
                AppLogger.w(TAG, "⚠️ Skipping fillHostView — invalid size ($parentWidth x $parentHeight)")
                return@post
            }

            try {
                // The widget is told its real size in dp; it picks its layout from this
                val widthDp = (parentWidth / density).toInt()
                val heightDp = (parentHeight / density).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    hostView.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
                } else {
                    @Suppress("DEPRECATION")
                    hostView.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
                }

                AppLogger.i(TAG, "✅ fillHostView: using parent size width=$parentWidth, height=$parentHeight")
            } catch (e: Exception) {
                AppLogger.e(TAG, "❌ Failed to update widget options: ${e.message}")
            }
        }
    }

    /** A handle is a large invisible touch area with a small pill (edges) or dot (corners) drawn in it. */
    private fun createHandle(): View = View(context).apply { visibility = GONE }

    private fun handleDrawable(widthDp: Float, heightDp: Float): Drawable {
        val pill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8f).toFloat()
            setColor(accentColor)
            setStroke(dp(1.5f), onAccentColor)
        }
        // Centre the pill inside the touch area without stretching it
        return LayerDrawable(arrayOf(pill)).apply {
            setLayerGravity(0, Gravity.CENTER)
            setLayerSize(0, dp(widthDp), dp(heightDp))
        }
    }

    /** Outline, handles and size label for the current mode. */
    private fun updateEditVisuals() {
        foreground = if (isResizeMode) {
            GradientDrawable().apply {
                cornerRadius = outlineRadius
                setStroke(dp(1.5f), accentColor)
            }
        } else null
        setHandlesVisible(isResizeMode)
        sizeLabel.visibility = if (isResizeMode) VISIBLE else GONE
        if (isResizeMode) updateSizeLabel(gridRect)
    }

    private fun updateSizeLabel(rect: GridRect, free: Boolean = true) {
        sizeLabel.text = context.getString(R.string.widgets_size_label, rect.cellsW, rect.cellsH)
        (sizeLabel.background as? GradientDrawable)?.setColor(if (free) accentColor else blockedColor)
        sizeLabel.setTextColor(if (free) onAccentColor else Color.WHITE)
    }

    fun setHandlesVisible(visible: Boolean) {
        AppLogger.d(TAG, "setHandlesVisible($visible)")
        val state = if (visible) VISIBLE else GONE
        topHandle.visibility = state
        bottomHandle.visibility = state
        leftHandle.visibility = state
        rightHandle.visibility = state

        topLeftHandle.visibility = state
        topRightHandle.visibility = state
        bottomLeftHandle.visibility = state
        bottomRightHandle.visibility = state


        if (visible) {
            // 🔥 Bring handles on top again after adding overlay
            topHandle.bringToFront()
            bottomHandle.bringToFront()
            leftHandle.bringToFront()
            rightHandle.bringToFront()

            topLeftHandle.bringToFront()
            topRightHandle.bringToFront()
            bottomLeftHandle.bringToFront()
            bottomRightHandle.bringToFront()
        }
    }

    private var activeResizeHandle: String? = null

    private fun attachResizeAndDragHandlers() {
        val handles = listOf(
            topHandle, bottomHandle, leftHandle, rightHandle,
            topLeftHandle, topRightHandle, bottomLeftHandle, bottomRightHandle
        )
        val sides = listOf(
            "TOP", "BOTTOM", "LEFT", "RIGHT",
            "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
        )

        val parentView = parent as? View
        val parentWidth = parentView?.width ?: Int.MAX_VALUE
        val parentHeight = parentView?.height ?: Int.MAX_VALUE

        // --- Attach resize listeners to handles ---
        handles.zip(sides).forEach { (handle, side) ->
            handle.setOnTouchListener { _, event ->
                if (!isResizeMode) return@setOnTouchListener false

                val lp = layoutParams as? LayoutParams ?: return@setOnTouchListener false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activeResizeHandle = side
                        lastX = event.rawX
                        lastY = event.rawY
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        activeResizeHandle?.let { resizeSide ->
                            val dx = event.rawX - lastX
                            val dy = event.rawY - lastY

                            when (resizeSide) {
                                // --- Edge handles ---
                                "TOP" -> {
                                    val maxHeight = lp.height + translationY
                                    val newHeight = (lp.height - dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight.coerceAtMost(parentHeight.toFloat()).toInt())
                                    val actualDy = lp.height - newHeight
                                    translationY += actualDy
                                    lp.height = newHeight
                                }

                                "BOTTOM" -> {
                                    val maxHeight = parentHeight - top
                                    lp.height = (lp.height + dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight)
                                }

                                "LEFT" -> {
                                    val maxWidth = lp.width + translationX
                                    val newWidth = (lp.width - dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth.coerceAtMost(parentWidth.toFloat()).toInt())
                                    val actualDx = lp.width - newWidth
                                    translationX += actualDx
                                    lp.width = newWidth
                                }

                                "RIGHT" -> {
                                    val maxWidth = parentWidth - left
                                    lp.width = (lp.width + dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth)
                                }

                                // --- Corner handles ---
                                "TOP_LEFT" -> {
                                    val maxHeight = lp.height + translationY
                                    val maxWidth = lp.width + translationX
                                    val newHeight = (lp.height - dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight.coerceAtMost(parentHeight.toFloat()).toInt())
                                    val newWidth = (lp.width - dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth.coerceAtMost(parentWidth.toFloat()).toInt())
                                    val actualDy = lp.height - newHeight
                                    val actualDx = lp.width - newWidth
                                    translationY += actualDy
                                    translationX += actualDx
                                    lp.height = newHeight
                                    lp.width = newWidth
                                }

                                "TOP_RIGHT" -> {
                                    val maxHeight = lp.height + translationY
                                    val maxWidth = parentWidth - left
                                    val newHeight = (lp.height - dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight.coerceAtMost(parentHeight.toFloat()).toInt())
                                    val newWidth = (lp.width + dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth)
                                    translationY += lp.height - newHeight
                                    lp.height = newHeight
                                    lp.width = newWidth
                                }

                                "BOTTOM_LEFT" -> {
                                    val maxHeight = parentHeight - top
                                    val maxWidth = lp.width + translationX
                                    val newHeight = (lp.height + dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight)
                                    val newWidth = (lp.width - dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth.coerceAtMost(parentWidth.toFloat()).toInt())
                                    translationX += lp.width - newWidth
                                    lp.height = newHeight
                                    lp.width = newWidth
                                }

                                "BOTTOM_RIGHT" -> {
                                    val maxHeight = parentHeight - top
                                    val maxWidth = parentWidth - left
                                    lp.height = (lp.height + dy).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxHeight)
                                    lp.width = (lp.width + dx).toInt()
                                        .coerceAtLeast(minSize)
                                        .coerceAtMost(maxWidth)
                                }
                            }

                            layoutParams = lp
                            fillHostView(lp.width, lp.height)
                            rectUnderWrapper()?.let { updateSizeLabel(it, it == gridRect || canPlace(it)) }
                            lastX = event.rawX
                            lastY = event.rawY
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activeResizeHandle = null
                        val target = rectUnderWrapper()
                        if (target != null && moveTo(target)) onUpdate()
                        updateSizeLabel(gridRect)
                    }
                }
                true
            }
        }

        // --- Attach drag + long-press only to non-handle areas ---
        if (!isResizeMode) attachDragToWrapperAndChildren(this, skipViews = handles)
    }


    private fun attachDragToWrapperAndChildren(root: View, skipViews: List<View> = emptyList()) {

        fun attachDrag(view: View) {
            // Attach long-press menu and drag to this view
            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (!isResizeMode && WidgetFragment.isEditingWidgets) {
                        showWidgetMenu()
                    }
                }
            })

            var dialogDismissed = false

            view.setOnTouchListener { v, event ->
                // Skip handles
                if (v in skipViews) return@setOnTouchListener false
                // The wrapper follows the finger, so its own coordinates never change during a drag.
                // Screen coordinates let the detector see the movement and not report a long press.
                val onScreen = MotionEvent.obtain(event).apply { setLocation(event.rawX, event.rawY) }
                gestureDetector.onTouchEvent(onScreen)
                onScreen.recycle()
                if (isResizeMode) return@setOnTouchListener false

                // 🟡 If not in global edit mode, don't consume — allow normal widget touch behavior
                if (!WidgetFragment.isEditingWidgets) return@setOnTouchListener false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dialogDismissed = false

                        lastX = event.rawX
                        lastY = event.rawY

                        val parentFrame = parent as? FrameLayout
                        if (parentFrame != null) {
                            ghostView = View(context).apply {
                                background = ghostDrawable(true)
                                layoutParams = LayoutParams(width, height).apply {
                                    if (layoutParams is LayoutParams) {
                                        leftMargin = (layoutParams as LayoutParams).leftMargin
                                        topMargin = (layoutParams as LayoutParams).topMargin
                                    }
                                }
                            }
                            parentFrame.addView(ghostView)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY

                        translationX += dx
                        translationY += dy

                        lastX = event.rawX
                        lastY = event.rawY

                        updateGhostPosition()

                        // Dismiss only once if movement exceeds 10 pixels in any direction
                        if (!dialogDismissed && (abs(dx) > 5 || abs(dy) > 5)) {
                            activeDialog?.dismiss()
                            dialogDismissed = true
                        }
                    }


                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        dialogDismissed = false
                        (ghostView?.parent as? ViewGroup)?.removeView(ghostView)
                        ghostView = null
                        val grid = grid()
                        if (grid != null) {
                            val target = gridRect.copy(col = grid.indexAt(translationX), row = grid.indexAt(translationY))
                            if (moveTo(target)) onUpdate()
                        }
                    }
                }

                true
            }
        }

        attachDrag(root)
    }

    /** Landing preview: a soft rounded shape with a dashed outline, red when the cells are taken. */
    private fun ghostDrawable(free: Boolean): Drawable {
        val color = if (free) accentColor else blockedColor
        return GradientDrawable().apply {
            cornerRadius = outlineRadius
            setColor(ColorUtils.setAlphaComponent(color, 38))
            setStroke(dp(1.5f), color, dp(6f).toFloat(), dp(4f).toFloat())
        }
    }

    /** Shows where the widget would land. */
    private fun updateGhostPosition() {
        val grid = grid() ?: return
        val target = grid.fit(gridRect.copy(col = grid.indexAt(translationX), row = grid.indexAt(translationY)))
        val free = target == gridRect || canPlace(target)

        ghostView?.background = ghostDrawable(free)
        ghostView?.layoutParams = (ghostView?.layoutParams as LayoutParams).apply {
            leftMargin = grid.offsetOf(target.col)
            topMargin = grid.offsetOf(target.row)
            width = grid.sizeOf(target.cellsW)
            height = grid.sizeOf(target.cellsH)
        }
        ghostView?.requestLayout()
    }

    fun showWidgetMenu() {
        val dialog = FontBottomSheetDialogLocked(context)
        activeDialog = dialog
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        fun addMenuItem(title: String, onClick: () -> Unit) {
            val item = TextView(context).apply {
                text = title
                textSize = 16f
                setPadding(16, 32, 16, 32)
                setOnClickListener {
                    onClick()
                    dialog.dismiss()
                }
            }
            container.addView(item)
        }

        if (isResizeMode) {
            addMenuItem(getLocalizedString(R.string.widgets_exit_resize)) {
                exitResizeMode()
            }
        } else {
            addMenuItem(getLocalizedString(R.string.widgets_resize)) {
                isResizeMode = true
                setHandlesVisible(true)
            }
        }

        addMenuItem(getLocalizedString(R.string.widgets_remove)) {
            appWidgetHost.deleteAppWidgetId(hostView.appWidgetId)
            (parent as? ViewGroup)?.removeView(this)
            onDelete()
        }

        addMenuItem(getLocalizedString(R.string.widgets_open)) {
            context.packageManager.getLaunchIntentForPackage(widgetInfo.provider.packageName)?.let {
                context.startActivity(it)
            }
        }

        // Settings (only if widget has a config activity)
        widgetInfo.configure?.let { configureComponent ->
            addMenuItem(getLocalizedString(R.string.widgets_settings)) {

                // The host call also reaches configure screens that are not exported
                try {
                    (context as? Activity)?.let {
                        appWidgetHost.startAppWidgetConfigureActivityForResult(it, hostView.appWidgetId, 0, 0, null)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Could not open settings of ${configureComponent.flattenToShortString()}", e)
                }
            }
        }

        // View in Store (only if installed from Google Play)
        val packageManager = context.packageManager
        val installerPackage = getInstallSource(packageManager, widgetInfo.provider.packageName)
        when (installerPackage) {
            "Google Play Store" -> { // Google Play
                addMenuItem(getLocalizedString(R.string.widgets_view_in_store)) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "market://details?id=${widgetInfo.provider.packageName}".toUri()
                        )
                    )
                }
            }

            "Amazon Appstore" -> { // Amazon Appstore
                addMenuItem(getLocalizedString(R.string.widgets_view_in_store)) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "amzn://apps/android?p=${widgetInfo.provider.packageName}".toUri()
                        )
                    )
                }
            }

            else -> {
                // Debug / unknown installer, do not show "View in Store"
                AppLogger.d(TAG, "WidgetMenu: Skipping '${getLocalizedString(R.string.widgets_view_in_store)}': unrecognized installer package='$installerPackage'")
            }
        }

        dialog.setOnDismissListener { activeDialog = null }
        dialog.setContentView(container)
        dialog.show()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.onInterceptTouchEvent(ev)

        // ✅ Bypass all if global edit mode is enabled
        if (!isResizeMode && WidgetFragment.isEditingWidgets) {
            // Track initial touch for dragging if needed
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                lastX = ev.rawX
                lastY = ev.rawY
            }
            return true
        }

        // ✅ Normal resize logic
        if (!isResizeMode) return super.onInterceptTouchEvent(ev)

        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            activeResizeHandle = getHandleAt(ev.rawX, ev.rawY)
            lastX = ev.rawX
            lastY = ev.rawY
        }

        // Intercept only if touch is outside a resize handle
        return activeResizeHandle == null
    }

    private fun getHandleAt(x: Float, y: Float): String? {
        val handles = mapOf(
            topHandle to "TOP",
            bottomHandle to "BOTTOM",
            leftHandle to "LEFT",
            rightHandle to "RIGHT",
            topLeftHandle to "TOP_LEFT",
            topRightHandle to "TOP_RIGHT",
            bottomLeftHandle to "BOTTOM_LEFT",
            bottomRightHandle to "BOTTOM_RIGHT"
        )
        val location = IntArray(2)
        for ((view, name) in handles) {
            view.getLocationOnScreen(location)
            val left = location[0].toFloat()
            val top = location[1].toFloat()
            val right = left + view.width
            val bottom = top + view.height
            if (x in left..right && y in top..bottom) return name
        }
        return null
    }

}