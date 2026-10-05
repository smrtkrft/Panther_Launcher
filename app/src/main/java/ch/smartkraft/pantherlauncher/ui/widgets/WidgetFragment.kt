package ch.smartkraft.pantherlauncher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.pm.ApplicationInfo
import android.appwidget.AppWidgetHostView
import android.os.Build
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ch.smartkraft.components.views.FontBottomSheetDialogLocked
import ch.smartkraft.common.AppLogger
import ch.smartkraft.common.appWidgetManager
import ch.smartkraft.common.getLocalizedString
import ch.smartkraft.common.showLongToast
import ch.smartkraft.common.isGestureNavigationEnabled
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.data.Prefs
import ch.smartkraft.pantherlauncher.data.SavedWidgetEntity
import ch.smartkraft.pantherlauncher.data.database.WidgetDao
import ch.smartkraft.pantherlauncher.data.database.WidgetDatabase
import ch.smartkraft.pantherlauncher.databinding.FragmentWidgetBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.ceil

data class AppWidgetGroup(
    val appName: String,
    val appIcon: Drawable?,
    val widgets: MutableList<AppWidgetProviderInfo>
)

class WidgetFragment : Fragment() {

    private lateinit var prefs: Prefs

    private var _binding: FragmentWidgetBinding? = null
    private val binding get() = _binding!!

    private lateinit var widgetDao: WidgetDao
    lateinit var appWidgetManager: AppWidgetManager
    lateinit var appWidgetHost: AppWidgetHost
    private val widgetWrappers = mutableListOf<ResizableWidgetWrapper>()

    companion object {
        private const val TAG = "WidgetFragment"
        private const val HOST_PREFS = "widget_host"
        private const val HOST_ID_KEY = "host_id"

        /** Widgets being bound or configured; they are not saved yet and must survive the orphan cleanup. */
        private val pendingWidgetIds: MutableSet<Int> = java.util.Collections.synchronizedSet(mutableSetOf())

        /**
         * The widget host id is chosen once and then kept. It used to be derived from the app name on
         * every start, so renaming the app would have orphaned every widget.
         */
        fun widgetHostId(context: Context): Int {
            val store = context.getSharedPreferences(HOST_PREFS, Context.MODE_PRIVATE)
            if (!store.contains(HOST_ID_KEY)) {
                // Existing installs keep the id their widgets were created with
                store.edit().putInt(HOST_ID_KEY, getLocalizedString(R.string.app_name).hashCode().absoluteValue).apply()
            }
            return store.getInt(HOST_ID_KEY, 0)
        }
        private const val GRID_COLUMNS = 14
        private const val CELL_MARGIN = 16

        // Minimum cell count per widget
        private const val MIN_CELL_W = 2
        private const val MIN_CELL_H = 1

        var isEditingWidgets: Boolean = false
    }

    private var activeGridDialog: FontBottomSheetDialogLocked? = null
    private var lastWidgetInfo: AppWidgetProviderInfo? = null
    private var placeholderVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWidgetBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back press handling for exiting resize mode
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val resizeWidget = widgetWrappers.firstOrNull { it.isResizeMode }
            if (resizeWidget != null) {
                AppLogger.i(TAG, "🔄 Exiting resize mode for widgetId=${resizeWidget.hostView.appWidgetId}")
                resizeWidget.exitResizeMode()
            } else {
                // Disable this callback so the system default back behavior can run
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.apply {
            AppLogger.i(TAG, "🟢 Widget grid initialized")

            val isGestureNav = isGestureNavigationEnabled(requireContext())

            val params = widgetGrid.layoutParams as ViewGroup.MarginLayoutParams
            if (isGestureNav) {
                params.bottomMargin = resources.getDimensionPixelSize(R.dimen.bottom_margin_gesture_nav) // or just in px
            } else {
                params.bottomMargin = resources.getDimensionPixelSize(R.dimen.bottom_margin_3_button_nav) // or just in px
            }
            params.topMargin = resources.getDimensionPixelSize(R.dimen.top_margin) // or just in px
            widgetGrid.layoutParams = params

            // Setup AppWidgetManager and Host
            appWidgetManager = requireContext().appWidgetManager
            appWidgetHost = AppWidgetHost(requireContext(), widgetHostId(requireContext()))
            appWidgetHost.startListening()
            AppLogger.i(TAG, "🟢 AppWidgetHost started listening")
            cleanupOrphanedWidgets()

            widgetGrid.apply {
                setOnLongClickListener {
                    // Only show grid menu if no widget is currently being resized
                    val resizing = (0 until widgetGrid.childCount)
                        .mapNotNull { widgetGrid.getChildAt(it) as? ResizableWidgetWrapper }
                        .any { it.isResizeMode }  // or activeResizeHandle != null for more precision

                    if (!resizing) {
                        showGridMenu()
                        true
                    } else {
                        // Ignore long press while resizing
                        false
                    }
                }

                // Post widget loading after layout to prevent jumps
                post {
                    (activity as? WidgetActivity)?.flushPendingWidgets()
                    AppLogger.i(TAG, "🟢 Pending widgets flushed and grid visible")
                }

                AppLogger.i(TAG, "🟢 WidgetFragment onViewCreated setup complete")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isEditingWidgets = false
    }

    override fun onResume() {
        super.onResume()
        restoreWidgets()
        AppLogger.i(TAG, "🔄 WidgetFragment onResume, widgets restored")
        updateEmptyPlaceholder(widgetWrappers)
    }

    fun cleanupOrphanedWidgets() {
        CoroutineScope(Dispatchers.IO).launch {
            // Get the list of all saved widget IDs from your database
            val savedIds = widgetDao.getAll().map { it.appWidgetId }.toSet()

            val allocatedIds = appWidgetHost.appWidgetIds
            for (id in allocatedIds) {
                if (id !in savedIds && id !in pendingWidgetIds) {
                    appWidgetHost.deleteAppWidgetId(id)
                    AppLogger.i(TAG, "🗑️ Deleted orphaned widgetId=$id")
                }
            }
        }
    }


    private val pendingWidgetsList = mutableListOf<Pair<AppWidgetProviderInfo, Int>>()

    fun postPendingWidgets(widgets: List<Pair<AppWidgetProviderInfo, Int>>) {
        // Add new widgets to the pending list
        pendingWidgetsList.addAll(widgets)
        AppLogger.d(TAG, "postPendingWidgets: ${widgets.size} widgets added to pending list. Total pending=${pendingWidgetsList.size}")

        // Only post if the fragment is attached and view is created
        if (!isAdded || !isViewCreated()) {
            AppLogger.w(TAG, "postPendingWidgets: Fragment not ready, pending widgets will remain queued")
            return
        }

        // Post to widgetGrid after view is laid out
        binding.widgetGrid.post {
            if (!isAdded || !isViewCreated()) {
                AppLogger.w(TAG, "postPendingWidgets: Fragment detached, aborting widget posting")
                return@post
            }

            AppLogger.i(TAG, "🟢 Posting ${pendingWidgetsList.size} pending widgets to widgetGrid")

            // Create widgets safely
            pendingWidgetsList.forEach { (info, id) ->
                AppLogger.d(TAG, "Creating widget: ${info.loadLabel(requireContext().packageManager)} (id=$id)")
                createWidgetWrapperSafe(info, id)
            }

            // Clear the pending list after posting
            pendingWidgetsList.clear()
            AppLogger.d(TAG, "Pending widgets list cleared after posting")

            AppLogger.d(TAG, "Saved widgets restored")

            // Update empty placeholder visibility
            updateEmptyPlaceholder(widgetWrappers)
            AppLogger.d(TAG, "Empty placeholder updated")
        }
    }


    /** Grid menu for adding/resetting widgets */
    private fun showGridMenu() {
        activeGridDialog?.dismiss()
        val bottomSheetDialog = FontBottomSheetDialogLocked(requireContext())
        activeGridDialog = bottomSheetDialog
        AppLogger.d(TAG, "🎛️ Showing widget grid menu")

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        fun addOption(title: String, action: () -> Unit) {
            val option = TextView(requireContext()).apply {
                text = title
                textSize = 16f
                setPadding(16, 16, 16, 16)
                setOnClickListener { action(); bottomSheetDialog.dismiss() }
            }
            container.addView(option)
        }

        addOption(getLocalizedString(R.string.widgets_add_widget)) { showCustomWidgetPicker() }

        if (isEditingWidgets) {
            addOption(getLocalizedString(R.string.widgets_reset_widget)) { resetAllWidgets() }
            addOption(getLocalizedString(R.string.widgets_remove_widget)) { removeAllWidgets() }
        }

        // Toggleable edit mode option
        val editTitle = if (isEditingWidgets) getLocalizedString(R.string.widgets_stop_editing_widget) else getLocalizedString(R.string.widgets_edit_widget)
        addOption(editTitle) {
            // Toggle edit mode
            isEditingWidgets = !isEditingWidgets

            // Edit mode shows the grid as faint dots instead of a frame
            binding.widgetGrid.background = if (isEditingWidgets) GridDotsDrawable(grid(), requireContext()) else null
        }

        bottomSheetDialog.setContentView(container)
        bottomSheetDialog.show()
    }

    private fun removeAllWidgets() {
        AppLogger.w(TAG, "🧹 Removing all widgets")
        // deleteWidget removes from the list, so walk a copy
        widgetWrappers.toList().forEach { wrapper ->
            deleteWidget(wrapper.hostView.appWidgetId)
        }
        widgetWrappers.clear()
        binding.widgetGrid.apply {
            for (i in childCount - 1 downTo 0) {
                val child = getChildAt(i)
                if (child.id != binding.emptyPlaceholder.id) {
                    removeViewAt(i)
                }
            }
        }
        saveWidgets()
        updateEmptyPlaceholder(widgetWrappers)
        AppLogger.i(TAG, "🧹 All widgets cleared and placeholder shown")
    }

    fun deleteWidget(widgetId: Int) {
        // 1️⃣ Delete from AppWidgetHost
        appWidgetHost.deleteAppWidgetId(widgetId)
        pendingWidgetIds.remove(widgetId)
        AppLogger.w(TAG, "🗑️ Deleting widgetId=$widgetId")

        // 2️⃣ Remove from UI + in-memory list safely
        val iterator = widgetWrappers.iterator()
        while (iterator.hasNext()) {
            val wrapper = iterator.next()
            if (wrapper.hostView.appWidgetId == widgetId) {
                binding.widgetGrid.removeView(wrapper)
                iterator.remove()
                AppLogger.i(TAG, "🗑️ Removed wrapper for widgetId=$widgetId from grid")
                break
            }
        }

        // 3️⃣ Delete from database + log how many remain
        CoroutineScope(Dispatchers.IO).launch {
            try {
                widgetDao.deleteById(widgetId)
                AppLogger.d(TAG, "🗑️ Deleted widgetId=$widgetId from DB")

                val remainingCount = widgetDao.getAll().size
                AppLogger.i(TAG, "📊 Widgets remaining in DB: $remainingCount")
            } catch (e: Exception) {
                AppLogger.e(TAG, "⚠️ Failed to delete or count widgets", e)
            }
        }
    }


    /** Gives every widget its default size again and packs them from the top without overlap. */
    private fun resetAllWidgets() {
        AppLogger.w(TAG, "🧹 Resetting all widgets positions")
        val grid = grid()
        val placed = mutableListOf<GridRect>()

        widgetWrappers.forEach { wrapper ->
            val (cellsW, cellsH) = defaultCells(wrapper.widgetInfo)
            // When the defaults no longer all fit, a widget keeps its minimum size instead
            val place = grid.firstFree(placed, cellsW, cellsH)
                ?: grid.firstFree(placed, WidgetGrid.MIN_CELLS_W, WidgetGrid.MIN_CELLS_H)
                ?: grid.fit(GridRect(0, 0, cellsW, cellsH))
            placed.add(place)
            wrapper.currentCol = place.col
            wrapper.currentRow = place.row
            wrapper.cellsW = place.cellsW
            wrapper.cellsH = place.cellsH
            wrapper.applyCells()
        }

        saveWidgets()
        updateEmptyPlaceholder(widgetWrappers)
    }

    private fun showCustomWidgetPicker() {
        // Every widget is offered; a configure screen that is not exported is opened through the host
        val widgets = appWidgetManager.installedProviders

        val pm = requireContext().packageManager

        // Group widgets by package
        val grouped = widgets.groupBy { it.provider.packageName }.map { (pkg, widgetList) ->
            val appInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (_: Exception) {
                null
            }
            val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
            val appIcon = appInfo?.let { pm.getApplicationIcon(it) }
            AppWidgetGroup(appName, appIcon, widgetList.toMutableList())
        }.sortedBy { it.appName.lowercase() }

        AppLogger.d(TAG, "🧩 Showing custom widget picker with ${grouped.size} apps")

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        val scrollView = ScrollView(requireContext()).apply { addView(container) }

        activeGridDialog?.dismiss()
        val bottomSheetDialog = FontBottomSheetDialogLocked(requireContext())
        activeGridDialog = bottomSheetDialog
        bottomSheetDialog.setContentView(scrollView)
        bottomSheetDialog.setTitle(getLocalizedString(R.string.widgets_select_widget))
        bottomSheetDialog.show()

        grouped.forEach { group ->
            val appRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 16, 8, 16)
                gravity = Gravity.CENTER_VERTICAL
            }
            val iconView = ImageView(requireContext()).apply {
                group.appIcon?.let { setImageDrawable(it) }
                layoutParams = LinearLayout.LayoutParams(64, 64)
            }
            val labelView = TextView(requireContext()).apply {
                text = group.appName
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(16, 0, 0, 0)
            }
            val expandIcon = TextView(requireContext()).apply { text = "▼"; textSize = 16f }
            appRow.addView(iconView)
            appRow.addView(labelView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            appRow.addView(expandIcon)

            val widgetContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }

            group.widgets.forEach { widgetInfo ->
                val widgetLabel = widgetInfo.loadLabel(requireContext().packageManager)

                val (defaultCellsW, defaultCellsH) = defaultCells(widgetInfo)
                val widgetSize = "${defaultCellsW}x${defaultCellsH}"

                val widgetRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(32, 12, 12, 12)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val labelView = TextView(requireContext()).apply {
                    text = getLocalizedString(R.string.pass_a_string, widgetLabel)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val sizeView = TextView(requireContext()).apply {
                    text = widgetSize
                    textSize = 14f
                    gravity = Gravity.END
                    setTypeface(null, Typeface.ITALIC)
                }

                widgetRow.addView(labelView)
                widgetRow.addView(sizeView)

                widgetRow.setOnClickListener {
                    AppLogger.i(TAG, "➕ Selected widget $widgetLabel to add")
                    addWidget(widgetInfo)
                    bottomSheetDialog.dismiss()
                }

                widgetContainer.addView(widgetRow)
            }


            appRow.setOnClickListener {
                if (widgetContainer.isVisible) {
                    widgetContainer.visibility = View.GONE
                    expandIcon.text = "▼"
                } else {
                    widgetContainer.visibility = View.VISIBLE
                    expandIcon.text = "▲"
                }
            }

            container.addView(appRow)
            container.addView(widgetContainer)
        }
    }

    /** Public entry point: add a widget */
    private fun addWidget(widgetInfo: AppWidgetProviderInfo) {
        lastWidgetInfo = widgetInfo
        val widgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetIds.add(widgetId)
        AppLogger.d(TAG, "🆕 Allocated appWidgetId=$widgetId for provider=${widgetInfo.provider.packageName}")

        val manager = requireContext().appWidgetManager

        // Check if binding is allowed
        val bound = manager.bindAppWidgetIdIfAllowed(widgetId, widgetInfo.provider)
        if (bound) {
            AppLogger.i(TAG, "✅ Bound widget immediately: widgetId=$widgetId")
            maybeConfigureOrCreate(widgetInfo, widgetId)
        } else {
            AppLogger.w(TAG, "🔒 Widget bind not allowed, requesting permission")
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widgetInfo.provider)
            }

            // Use the id allocated above; the id in the returned intent comes from another app
            (requireActivity() as WidgetActivity).launchWidgetPermission(intent) { resultCode, _, _ ->
                handleWidgetResult(resultCode, widgetId)
            }
        }
    }

    /** Handle result from binding or configuration */
    private fun handleWidgetResult(resultCode: Int, appWidgetId: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                AppLogger.i(TAG, "✅ Widget bind/config OK for appWidgetId=$appWidgetId")
                lastWidgetInfo?.let { maybeConfigureOrCreate(it, appWidgetId) }
                lastWidgetInfo = null
            }

            Activity.RESULT_CANCELED -> {
                AppLogger.w(TAG, "❌ Widget bind/config canceled for appWidgetId=$appWidgetId")
                safeRemoveWidget(appWidgetId)
            }
        }
    }

    /** Opens the widget's configure screen when it needs one, then creates the wrapper. */
    private fun maybeConfigureOrCreate(widgetInfo: AppWidgetProviderInfo, widgetId: Int) {
        val widgetActivity = activity as? WidgetActivity
        if (widgetInfo.configure == null || isConfigurationOptional(widgetInfo) || widgetActivity == null) {
            AppLogger.i(TAG, "📦 No configuration needed, creating wrapper immediately")
            createWidgetWrapperSafe(widgetInfo, widgetId)
            return
        }

        AppLogger.i(TAG, "⚙️ Widget has configuration, launching config activity")
        widgetActivity.launchWidgetConfigure(appWidgetHost, widgetId) { resultCode ->
            if (resultCode == Activity.RESULT_OK) {
                AppLogger.i(TAG, "✅ Widget configured, creating wrapper: $widgetId")
                widgetActivity.safeCreateWidget(widgetInfo, widgetId)
            } else {
                AppLogger.w(TAG, "❌ Widget config canceled, removing: $widgetId")
                safeRemoveWidget(widgetId)
            }
        }
    }

    /**
     * An app that has never been opened (or was force-stopped) is in Android's "stopped" state: it
     * gets no broadcasts and runs nothing in the background, so its widget stays at the placeholder
     * the app ships ("open the app first"). Only the user can change that, so say so and offer it.
     */
    private fun offerToOpenStoppedApp(widgetInfo: AppWidgetProviderInfo) {
        val packageName = widgetInfo.provider.packageName
        val pm = requireContext().packageManager
        val stopped = try {
            pm.getApplicationInfo(packageName, 0).flags and ApplicationInfo.FLAG_STOPPED != 0
        } catch (_: Exception) {
            false
        }
        if (!stopped) return
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(appName)
            .setMessage(getLocalizedString(R.string.widgets_app_not_opened_yet, appName))
            .setPositiveButton(getLocalizedString(R.string.widgets_open)) { _, _ ->
                pm.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
            }
            .setNegativeButton(getLocalizedString(R.string.cancel), null)
            .show()
    }

    /** Widgets may declare that they work without being configured first (Android 12+). */
    private fun isConfigurationOptional(widgetInfo: AppWidgetProviderInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val optional = AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL or AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
        return widgetInfo.widgetFeatures and optional == optional
    }

    /**
     * Context for widget host views. The application context is used because the activity's AppCompat
     * inflater would replace the views of another app's layout and break it. A package context of the
     * provider with CONTEXT_INCLUDE_CODE must not be used: it loads that app's code into this process.
     */
    private fun widgetHostContext(): Context = requireContext().applicationContext

    private fun grid() = WidgetGrid(binding.widgetGrid.width.coerceAtLeast(1), GRID_COLUMNS, CELL_MARGIN, binding.widgetGrid.height)

    private fun occupiedBy(others: List<ResizableWidgetWrapper>) = others.map { it.gridRect }

    /** The size a widget starts with: what it asks for, not the smallest it accepts. */
    private fun defaultCells(widgetInfo: AppWidgetProviderInfo): Pair<Int, Int> {
        val targetW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) widgetInfo.targetCellWidth else 0
        val targetH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) widgetInfo.targetCellHeight else 0
        return grid().defaultSpan(targetW, targetH, widgetInfo.minWidth, widgetInfo.minHeight)
    }

    private fun newWrapper(hostView: AppWidgetHostView, info: AppWidgetProviderInfo, widgetId: Int, cellsW: Int, cellsH: Int): ResizableWidgetWrapper {
        val wrapper = ResizableWidgetWrapper(
            requireContext(), hostView, info, appWidgetHost,
            { saveWidgets() }, { deleteWidget(widgetId) },
            GRID_COLUMNS, CELL_MARGIN, cellsW, cellsH
        )
        wrapper.canPlace = { target -> widgetWrappers.none { it !== wrapper && it.gridRect.overlaps(target) } }
        // While one widget is being resized the others step back and the grid shows
        wrapper.onResizeModeChanged = { resizing ->
            widgetWrappers.forEach { if (it !== wrapper) it.animate().alpha(if (resizing) 0.35f else 1f).setDuration(150).start() }
            binding.widgetGrid.background = if (resizing || isEditingWidgets) GridDotsDrawable(grid(), requireContext()) else null
        }
        return wrapper
    }

    fun createWidgetWrapperSafe(widgetInfo: AppWidgetProviderInfo, appWidgetId: Int) {
        if (!isAdded) {
            AppLogger.w(TAG, "⚠️ Skipping widget creation, fragment not attached")
            return
        }
        binding.widgetGrid.post {
            createWidgetWrapper(widgetInfo, appWidgetId)
        }
    }

    fun createWidgetWrapper(widgetInfo: AppWidgetProviderInfo, appWidgetId: Int) {
        val hostView = try {
            // Use the existing widget ID if it's valid, otherwise allocate a new one
            val appWidgetManager = AppWidgetManager.getInstance(requireContext())
            val widgetIdToUse = if (isWidgetIdValid(appWidgetId, appWidgetManager)) {
                appWidgetId
            } else {
                val newWidgetId = appWidgetHost.allocateAppWidgetId()

                // Bind the new ID to the provider
                if (!appWidgetManager.bindAppWidgetIdIfAllowed(newWidgetId, widgetInfo.provider)) {
                    AppLogger.e(TAG, "⚠️ Failed to bind new widgetId=$newWidgetId")
                    safeRemoveWidget(newWidgetId)
                    return
                }
                newWidgetId
            }

            // Now create the host view
            appWidgetHost.createView(widgetHostContext(), widgetIdToUse, widgetInfo)

        } catch (e: Exception) {
            AppLogger.e(TAG, "⚠️ Failed to create widgetId=$appWidgetId, removing", e)
            safeRemoveWidget(appWidgetId)
            return
        }

        AppLogger.d(TAG, "🖼️ Creating wrapper for widgetId=$appWidgetId, provider=${widgetInfo.provider.packageName}")

        val (defaultCellsW, defaultCellsH) = defaultCells(widgetInfo)
        AppLogger.v(TAG, "📐 Default size for widgetId=$appWidgetId: $defaultCellsW x $defaultCellsH cells")

        val wrapper = newWrapper(hostView, widgetInfo, appWidgetId, defaultCellsW, defaultCellsH)

        if (!addWrapperToGrid(wrapper)) return
        AppLogger.i(TAG, "✅ Wrapper created for widgetId=$appWidgetId")
        offerToOpenStoppedApp(widgetInfo)
        updateEmptyPlaceholder(widgetWrappers)
        saveWidgets()
        logGridSnapshot()
    }

    fun isWidgetIdValid(widgetId: Int, appWidgetManager: AppWidgetManager): Boolean {
        val info = try {
            appWidgetManager.getAppWidgetInfo(widgetId)
        } catch (_: Exception) {
            null
        }
        return info != null
    }


    private fun safeRemoveWidget(widgetId: Int) {
        try {
            AppLogger.w(TAG, "🗑️ Removing widgetId=$widgetId due to error")
            deleteWidget(widgetId)
            saveWidgets()
            updateEmptyPlaceholder(widgetWrappers)
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ Failed to remove widgetId=$widgetId", e)
        }
    }

    /** Puts a new widget on the first free place; returns false when the page is full. */
    private fun addWrapperToGrid(wrapper: ResizableWidgetWrapper): Boolean {
        val id = wrapper.hostView.appWidgetId
        val place = grid().firstFree(occupiedBy(widgetWrappers), wrapper.cellsW, wrapper.cellsH)
        if (place == null) {
            AppLogger.w(TAG, "No room left for widgetId=$id")
            showLongToast(getLocalizedString(R.string.widgets_no_space))
            appWidgetHost.deleteAppWidgetId(id)
            return false
        }
        wrapper.currentCol = place.col
        wrapper.currentRow = place.row
        wrapper.cellsW = place.cellsW
        wrapper.cellsH = place.cellsH

        addWrapperSafely(wrapper)
        AppLogger.i(TAG, "✅ Placed widgetId=$id at row=${place.row} col=${place.col} | cells=${place.cellsW}x${place.cellsH}")
        return true
    }

    private fun addWrapperSafely(wrapper: ResizableWidgetWrapper) {
        val id = wrapper.hostView.appWidgetId

        val existing = widgetWrappers.find { it.hostView.appWidgetId == id }
        if (existing != null) {
            AppLogger.w(TAG, "♻️ Replacing existing wrapper for appWidgetId=$id")
            binding.widgetGrid.removeView(existing)
            widgetWrappers.remove(existing)
        }

        binding.widgetGrid.addView(wrapper)
        widgetWrappers.add(wrapper)

        AppLogger.i(
            TAG,
            "🟩 Added #${widgetWrappers.size} → id=${wrapper.hostView.appWidgetId} | Pinned -> col=${wrapper.currentCol}, row=${wrapper.currentRow} | Size -> width=${wrapper.width}, height=${wrapper.height} | Cells -> width=${wrapper.defaultCellsW}, height=${wrapper.defaultCellsH}"
        )

        updateEmptyPlaceholder(widgetWrappers)
    }

    /** Saves every widget's grid cells. The pixel columns are kept for older versions and not read back. */
    private fun saveWidgets() {
        val grid = grid()
        val savedList = widgetWrappers.map { wrapper ->
            val rect = wrapper.gridRect
            SavedWidgetEntity(
                wrapper.hostView.appWidgetId, rect.col, rect.row,
                grid.sizeOf(rect.cellsW), grid.sizeOf(rect.cellsH), rect.cellsW, rect.cellsH
            )
        }

        lifecycleScope.launch {
            widgetDao.insertAll(savedList)
            pendingWidgetIds.removeAll(savedList.map { it.appWidgetId }.toSet())
            AppLogger.i(TAG, "💾 Widgets saved to Room: ${savedList.size}")
        }
    }

    /** Restore widgets from JSON */
    private fun restoreWidgets() {
        lifecycleScope.launch {
            val savedWidgets = widgetDao.getAll()
            if (savedWidgets.isEmpty()) {
                AppLogger.w(TAG, "⚠️ No saved widgets found in Room")
                return@launch
            }

            AppLogger.i(TAG, "📥 Restoring ${savedWidgets.size} widgets from Room")

            binding.apply {
                widgetGrid.post {
                    // Saved cells are taken as they are, without the page height: a shorter page (landscape)
                    // must not shrink widgets and write that back. Only real overlaps are repaired.
                    val grid = WidgetGrid(widgetGrid.width.coerceAtLeast(1), GRID_COLUMNS, CELL_MARGIN)
                    var repaired = false

                    savedWidgets.forEach { saved ->
                        val info = appWidgetManager.getAppWidgetInfo(saved.appWidgetId)
                        if (info == null) {
                            AppLogger.e(TAG, "❌ No AppWidgetInfo for id=${saved.appWidgetId}, removing")
                            safeRemoveWidget(saved.appWidgetId)
                            return@forEach
                        }

                        val hostView = try {
                            appWidgetHost.createView(widgetHostContext(), saved.appWidgetId, info)
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "⚠️ Failed to restore widgetId=${saved.appWidgetId}, removing", e)
                            safeRemoveWidget(saved.appWidgetId)
                            return@forEach
                        }

                        // Older versions could save overlapping or out-of-grid widgets; move those to free cells
                        val wanted = grid.fit(GridRect(saved.col, saved.row, saved.cellsW, saved.cellsH))
                        // A restore can run again while the page is open, so leave out this widget's own old wrapper
                        val taken = occupiedBy(widgetWrappers.filter { it.hostView.appWidgetId != saved.appWidgetId })
                        val place = if (taken.none { it.overlaps(wanted) }) wanted else grid.firstFree(taken, wanted.cellsW, wanted.cellsH) ?: wanted
                        if (place != GridRect(saved.col, saved.row, saved.cellsW, saved.cellsH)) repaired = true

                        val wrapper = newWrapper(hostView, info, saved.appWidgetId, place.cellsW, place.cellsH)
                        wrapper.currentCol = place.col
                        wrapper.currentRow = place.row

                        addWrapperSafely(wrapper)

                        logWidgetRestored(saved)
                    }
                    if (repaired) saveWidgets()
                }
                logGridSnapshot()
            }
        }
    }

    private fun logWidgetRestored(saved: SavedWidgetEntity) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val info = appWidgetManager.getAppWidgetInfo(saved.appWidgetId)

        val packageManager = requireContext().packageManager
        val widgetName = info?.loadLabel(packageManager) ?: "Unknown Widget"

        val appName = info?.provider?.packageName?.let { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName // fallback if app name can't be resolved
            }
        } ?: "Unknown"

        AppLogger.i(
            TAG,
            "🔄 RESTORED → id=${saved.appWidgetId} | App=$appName | Widget=$widgetName | Pinned -> col=${saved.col}, row=${saved.row} | Size -> width=${saved.width}, height=${saved.height} | Cells -> width=${saved.cellsW}, height=${saved.cellsH}"
        )
    }

    private fun logGridSnapshot() {
        lifecycleScope.launch {
            val savedWidgets = widgetDao.getAll()
            if (savedWidgets.isEmpty()) {
                AppLogger.i(TAG, "⚠️ No widgets in database, grid empty")
                return@launch
            }

            val maxRow = (savedWidgets.maxOfOrNull { it.row + it.cellsH } ?: 0)
            val grid = Array(maxRow) { Array(GRID_COLUMNS) { "□" } }

            savedWidgets.forEach { w ->
                for (r in w.row until w.row + w.cellsH) {
                    for (c in w.col until w.col + w.cellsW) {
                        if (r in grid.indices && c in 0 until GRID_COLUMNS) {
                            grid[r][c] = "■"
                        }
                    }
                }
            }

            val snapshot = grid.joinToString("\n") { it.joinToString(" ") }
            AppLogger.i(TAG, "📐 Grid Snapshot:\n$snapshot")
        }
    }

    private fun updateEmptyPlaceholder(wrappers: List<ResizableWidgetWrapper>) {
        val shouldBeVisible = wrappers.isEmpty()

        // Only update if visibility changed
        if (placeholderVisible == shouldBeVisible) {
            AppLogger.v(TAG, "updateEmptyPlaceholder: no change (visible=$placeholderVisible)")
            return
        }

        placeholderVisible = shouldBeVisible

        binding.emptyPlaceholder.isVisible = shouldBeVisible

        AppLogger.i(TAG, if (shouldBeVisible) "🟨 Showing empty placeholder" else "🟩 Hiding empty placeholder")
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        AppLogger.i(TAG, "🔗 WidgetFragment onAttach called, context=$context")
        widgetDao = WidgetDatabase.getDatabase(requireContext()).widgetDao()
        if (!isViewCreated()) {
            appWidgetHost = AppWidgetHost(context, widgetHostId(context))
            appWidgetHost.startListening()
            AppLogger.i(TAG, "🟢 Initialized AppWidgetHost")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appWidgetHost.stopListening()
        AppLogger.i(TAG, "🛑 AppWidgetHost stopped listening")
    }

    fun isViewCreated(): Boolean = _binding?.widgetGrid != null

}