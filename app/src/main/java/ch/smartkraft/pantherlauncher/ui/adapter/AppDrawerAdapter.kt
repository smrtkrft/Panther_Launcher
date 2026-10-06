/**
 * Prepare the data for the app drawer, which is the list of all the installed applications.
 */

package ch.smartkraft.pantherlauncher.ui.adapter

import android.annotation.SuppressLint
import ch.smartkraft.pantherlauncher.databinding.AdapterAppCategoryBinding
import ch.smartkraft.pantherlauncher.databinding.AdapterSearchResultBinding
import ch.smartkraft.pantherlauncher.data.CATEGORY_PREFIX
import android.view.Gravity
import android.os.Process
import android.content.pm.ApplicationInfo
import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.biometric.BiometricPrompt
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import ch.smartkraft.common.AppLogger
import ch.smartkraft.common.getLocalizedString
import ch.smartkraft.common.showLongToast
import ch.smartkraft.common.isSystemApp
import ch.smartkraft.common.showKeyboard
import ch.smartkraft.fuzzywuzzy.FuzzyFinder
import ch.smartkraft.fuzzywuzzy.FuzzyFinder.filterItems
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.data.AppListItem
import ch.smartkraft.pantherlauncher.data.Constants
import ch.smartkraft.pantherlauncher.data.Constants.AppDrawerFlag
import ch.smartkraft.pantherlauncher.data.Prefs
import ch.smartkraft.pantherlauncher.databinding.AdapterAppDrawerBinding
import ch.smartkraft.pantherlauncher.helper.IconCacheTarget
import ch.smartkraft.pantherlauncher.helper.IconPackHelper.getSafeAppIcon
import ch.smartkraft.pantherlauncher.helper.ShortcutHelper
import ch.smartkraft.pantherlauncher.helper.dp2px
import ch.smartkraft.pantherlauncher.helper.emptyString
import ch.smartkraft.pantherlauncher.helper.getSystemIcons
import ch.smartkraft.pantherlauncher.helper.utils.BiometricHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AppDrawerAdapter(
    private val context: Context,
    private val fragment: Fragment,
    internal var flag: AppDrawerFlag,
    private val gravity: Int,
    private val appClickListener: (AppListItem) -> Unit,
    private val appDeleteListener: (AppListItem) -> Unit,
    private val appRenameListener: (String, String) -> Unit,
    private val appTagListener: (String, String, UserHandle) -> Unit,
    private val appHideListener: (AppDrawerFlag, AppListItem) -> Unit,
    private val appInfoListener: (AppListItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    private lateinit var prefs: Prefs
    private var appFilter = createAppFilter()
    private var openedContextMenuPosition = RecyclerView.NO_POSITION
    var appsList: MutableList<AppListItem> = mutableListOf()
    var appFilteredList: MutableList<AppListItem> = mutableListOf()
    private lateinit var binding: AdapterAppDrawerBinding
    private lateinit var biometricHelper: BiometricHelper

    // Add icon cache
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val iconLoadingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private companion object {
        const val TYPE_APP = 0
        const val TYPE_CATEGORY = 1
        const val TYPE_RESULT = 2
    }

    private var isBangSearch = false

    /** Files and contacts for a query that matched no app; set by the drawer, null for pickers. */
    var extraSearch: ((String) -> List<AppListItem>)? = null

    /** Called after every search with the query and whether the list ended up empty. */
    var onResultsShown: ((String, Boolean) -> Unit)? = null

    // ---- Category view -------------------------------------------------------------------
    private val settings by lazy { Prefs(context) }
    private var currentQuery = ""
    private val headers = HashMap<String, DrawerRow.Header>()
    private val systemCategories = HashMap<String, String?>()

    /** The category view is only used for the plain app drawer, not for pickers or hidden apps. */
    private val categoryMode: Boolean
        get() = flag == AppDrawerFlag.LaunchApp && settings.drawerCategories

    /** The category an app declares to Android, as the system names it; null when it declares none. */
    private fun systemCategoryOf(packageName: String): String? = systemCategories.getOrPut(packageName) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            ApplicationInfo.getCategoryTitle(context, info.category)?.toString()
        } catch (_: Exception) {
            null
        }
    }

    /** What is shown without a search: the grouped rows in category view, otherwise every app. */
    private fun listWithoutSearch(): MutableList<AppListItem> {
        if (!categoryMode) return appsList
        val pinned = settings.pinnedApps
        val rows = AppGrouping.rows(
            apps = appsList,
            isPinned = { it.settingsKey in pinned },
            tagOf = { it.tag },
            systemCategoryOf = { systemCategoryOf(it.activityPackage) },
            otherName = getLocalizedString(R.string.category_other),
            openCategory = settings.openDrawerCategory
        )
        headers.clear()
        return rows.map { row ->
            when (row) {
                is DrawerRow.App -> row.item
                is DrawerRow.Header -> {
                    headers[row.name] = row
                    AppListItem(row.name, "", CATEGORY_PREFIX + row.name, Process.myUserHandle(), customTag = "")
                }
            }
        }.toMutableList()
    }

    /** Opens a category and closes the one that was open; tapping the open one closes it. */
    @SuppressLint("NotifyDataSetChanged")
    private fun toggleCategory(name: String) {
        settings.openDrawerCategory = if (settings.openDrawerCategory.equals(name, ignoreCase = true)) "" else name
        appFilteredList = listWithoutSearch()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val item = appFilteredList.getOrNull(position)
        return when {
            item?.isCategoryHeader == true -> TYPE_CATEGORY
            item?.isSearchExtra == true -> TYPE_RESULT
            else -> TYPE_APP
        }
    }

    inner class ResultHolder(val row: AdapterSearchResultBinding) : RecyclerView.ViewHolder(row.root)

    private fun bindResult(holder: ResultHolder, item: AppListItem) {
        val size = settings.appSize.toFloat()
        holder.row.resultRow.gravity = gravity or Gravity.CENTER_VERTICAL
        holder.row.resultIcon.setImageResource(if (item.isContactResult) R.drawable.ic_contacts else R.drawable.ic_file)
        holder.row.resultIcon.imageTintList = android.content.res.ColorStateList.valueOf(settings.appColor)
        holder.row.resultTitle.apply {
            text = item.activityLabel
            textSize = size
            setTextColor(settings.appColor)
        }
        holder.row.resultSubtitle.apply {
            isVisible = item.customTag.isNotEmpty()
            text = item.customTag
            textSize = size * 0.7f
            setTextColor(settings.appColor)
        }
        holder.row.root.setOnClickListener { appClickListener(item) }
    }

    inner class CategoryHolder(val row: AdapterAppCategoryBinding) : RecyclerView.ViewHolder(row.root)

    private fun bindCategory(holder: CategoryHolder, item: AppListItem) {
        val header = headers[item.activityLabel]
        val labelSize = settings.categorySize.toFloat()
        val color = settings.categoryColor
        holder.row.categoryRow.gravity = gravity or Gravity.CENTER_VERTICAL
        holder.row.categoryName.apply {
            isAllCaps = settings.categoryUppercase
            text = item.activityLabel
            textSize = labelSize
            setTextColor(color)
        }
        holder.row.categoryCount.apply {
            isVisible = settings.categoryShowCount
            text = (header?.count ?: 0).toString()
            textSize = labelSize
            setTextColor(color)
        }
        holder.row.categoryChevron.apply {
            textSize = labelSize
            setTextColor(color)
            rotation = if (header?.expanded == true) 90f else 0f
        }
        holder.row.root.setOnClickListener { toggleCategory(item.activityLabel) }
    }
    // ----------------------------------------------------------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_CATEGORY) {
            return CategoryHolder(AdapterAppCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
        if (viewType == TYPE_RESULT) {
            return ResultHolder(AdapterSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
        binding = AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        prefs = Prefs(parent.context)
        biometricHelper = BiometricHelper(fragment.requireActivity())
        val fontColor = prefs.appColor
        binding.appTitle.setTextColor(fontColor)

        binding.appTitle.textSize = prefs.appSize.toFloat()
        val padding: Int = prefs.textPaddingSize
        binding.appTitle.setPadding(0, padding, 0, padding)
        return ViewHolder(binding)
    }

    /** The app shown at [position]; null for a category header. */
    fun getItemAt(position: Int): AppListItem? = appFilteredList.getOrNull(position)?.takeUnless { it.isCategoryHeader || it.isSearchExtra }

    @SuppressLint("RecyclerView")
    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        if (appFilteredList.isEmpty() || position !in appFilteredList.indices) {
            AppLogger.d("AppListDebug", "⚠️ onBindViewHolder called but appFilteredList is empty or position out of bounds")
            return
        }
        if (viewHolder is CategoryHolder) {
            bindCategory(viewHolder, appFilteredList[position])
            return
        }
        if (viewHolder is ResultHolder) {
            bindResult(viewHolder, appFilteredList[position])
            return
        }
        val holder = viewHolder as ViewHolder

        val appModel = appFilteredList[holder.absoluteAdapterPosition]
        AppLogger.d("AppListDebug", "🔧 Binding position=$position, label=${appModel.activityLabel}, package=${appModel.activityPackage}")

        // Pass icon cache and loading scope to bind
        holder.bind(flag, gravity, appModel, appClickListener, appInfoListener, appDeleteListener, iconCache, iconLoadingScope, prefs)

        holder.appHide.setOnClickListener {
            AppLogger.d("AppListDebug", "❌ Hide clicked for ${appModel.activityLabel} (${appModel.activityPackage})")

            appFilteredList.removeAt(holder.absoluteAdapterPosition)
            appsList.remove(appModel)
            notifyItemRemoved(holder.absoluteAdapterPosition)

            AppLogger.d("AppListDebug", "📤 notifyItemRemoved at ${holder.absoluteAdapterPosition}")
            appHideListener(flag, appModel)
        }

        holder.appLock.setOnClickListener {
            val appName = appModel.settingsKey

            if (prefs.lockedApps.contains(appName)) {
                // Removing a lock needs authentication
                biometricHelper.startBiometricAuth(appModel, object : BiometricHelper.CallbackApp {
                    override fun onAuthenticationSucceeded(appListItem: AppListItem) {
                        AppLogger.d("AppListDebug", "🔓 Auth succeeded for $appName - unlocking")
                        holder.appLock.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock_off, 0, 0)
                        holder.appLock.text = getLocalizedString(R.string.lock)
                        prefs.lockedApps = prefs.lockedApps.apply { remove(appName) }
                    }

                    override fun onAuthenticationFailed() {
                        AppLogger.e("Authentication", getLocalizedString(R.string.text_authentication_failed))
                    }

                    override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
                        val msg = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED -> getLocalizedString(R.string.text_authentication_cancel)
                            else -> getLocalizedString(R.string.text_authentication_error).format(errorMessage, errorCode)
                        }
                        AppLogger.e("Authentication", msg)
                    }
                })
            } else if (!biometricHelper.canAuthenticate()) {
                // A lock that can never be confirmed would only make the app unreachable
                context.showLongToast(getLocalizedString(R.string.app_lock_needs_screen_lock))
            } else {
                AppLogger.d("AppListDebug", "🔒 Locking $appName")
                holder.appLock.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock, 0, 0)
                holder.appLock.text = getLocalizedString(R.string.unlock)
                prefs.lockedApps = prefs.lockedApps.apply { add(appName) }
            }
        }

        holder.appSaveRename.setOnClickListener {
            val currentText = holder.appRenameEdit.text.toString().trim()

            when {
                currentText.isEmpty() -> { // Reset state
                    AppLogger.d("AppListDebug", "✏️ Resetting ${appModel.activityPackage} to default")
                    appRenameListener(appModel.settingsKey, emptyString()) // empty string = default
                }

                currentText != prefs.getAppAlias(appModel.settingsKey) -> { // Rename state
                    AppLogger.d("AppListDebug", "✏️ Renaming ${appModel.settingsKey} to $currentText")
                    appRenameListener(appModel.settingsKey, currentText)
                }
            }

            notifyItemChanged(holder.absoluteAdapterPosition)
            AppLogger.d("AppListDebug", "🔁 notifyItemChanged at ${holder.absoluteAdapterPosition}")
        }

        holder.appSaveCancel.setOnClickListener {
            AppLogger.d("AppListDebug", "✏️ Cancel rename for ${appModel.activityPackage}")

            notifyItemChanged(holder.absoluteAdapterPosition)
            AppLogger.d("AppListDebug", "🔁 notifyItemChanged at ${holder.absoluteAdapterPosition}")
        }

        holder.appSaveTag.setOnClickListener {
            val name = holder.appTagEdit.text.toString().trim()
            AppLogger.d("AppListDebug", "✏️ Tagging ${appModel.activityPackage} to $name")
            appModel.customTag = name
            notifyItemChanged(holder.absoluteAdapterPosition)
            AppLogger.d("AppListDebug", "🔁 notifyItemChanged at ${holder.absoluteAdapterPosition}")
            appTagListener(appModel.settingsKey, appModel.customTag, appModel.user)
        }

        autoLaunch(position)
    }

    override fun getItemCount(): Int = appFilteredList.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                val searchChars = charSearch.toString().trim().lowercase()
                currentQuery = searchChars
                val isTagSearch = searchChars.startsWith("#")
                val query = if (isTagSearch) searchChars.substringAfter("#") else searchChars

                val filtered = filterItems(
                    itemsList = appsList,
                    query = query,
                    prefs = Prefs(context),
                    scoreProvider = { app, q ->
                        if (isTagSearch) FuzzyFinder.scoreString(app.tag, q, Constants.MAX_FILTER_STRENGTH)
                        else FuzzyFinder.scoreApp(context, app, q, Constants.MAX_FILTER_STRENGTH)
                    },
                    labelProvider = { app ->
                        if (isTagSearch) app.tag else prefs.getAppAlias(app.settingsKey)
                            .takeIf { it.isNotBlank() }
                            ?: app.activityLabel
                    },
                    loggerTag = "appScore"
                )

                // No app matched: continue in files and contacts when the drawer allows it
                val values = if (filtered.isEmpty() && !isTagSearch && query.isNotEmpty()) {
                    extraSearch?.invoke(query)?.toMutableList() ?: filtered
                } else filtered

                return FilterResults().apply { this.values = values }
            }


            @SuppressLint("NotifyDataSetChanged")
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results?.values is MutableList<*>) {
                    // Without a search the category view shows its groups; a search always gives a flat list
                    appFilteredList = if (constraint.isNullOrBlank()) listWithoutSearch() else results.values as MutableList<AppListItem>
                    notifyDataSetChanged()
                    onResultsShown?.invoke(constraint?.toString().orEmpty(), appFilteredList.isEmpty())
                } else {
                    return
                }
            }
        }
    }

    private fun autoLaunch(position: Int) {
        val only = appFilteredList.singleOrNull()
        val lastMatch = only != null && !only.isCategoryHeader && !only.isSearchExtra
        val openApp = flag == AppDrawerFlag.LaunchApp
        val autoOpenApp = prefs.autoOpenApp
        if (lastMatch && openApp && autoOpenApp) {
            try { // Automatically open the app when there's only one search result
                if (isBangSearch.not()) appClickListener(appFilteredList[position])
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setAppList(appsList: MutableList<AppListItem>) {
        this.appsList = appsList
        if (currentQuery.isEmpty()) {
            this.appFilteredList = listWithoutSearch()
            notifyDataSetChanged()
        } else {
            // A refresh while the user is typing must not replace the results with every app
            filter.filter(currentQuery)
        }
    }

    fun launchFirstInList() {
        appFilteredList.firstOrNull { !it.isCategoryHeader }?.let { appClickListener(it) }
    }

    fun getFirstInList(): String? = appFilteredList.firstOrNull { !it.isCategoryHeader }?.activityLabel

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // Optionally clear icon to avoid wrong icons on recycled views
        (holder as? ViewHolder)?.clearIcon()
    }

    inner class ViewHolder(
        itemView: AdapterAppDrawerBinding
    ) : RecyclerView.ViewHolder(itemView.root) {
        val appHide: TextView = itemView.appHide
        val appLock: TextView = itemView.appLock
        val appRenameEdit: EditText = itemView.appRenameEdit
        val appSaveRename: ImageView = itemView.appSaveRename
        val appSaveCancel: ImageView = itemView.appSaveCancel
        val appTagEdit: EditText = itemView.appTagEdit
        val appSaveTag: TextView = itemView.appSaveTag

        private val appHideLayout: LinearLayout = itemView.appHideLayout
        private val appRenameLayout: LinearLayout = itemView.appRenameLayout
        private val appTagLayout: LinearLayout = itemView.appTagLayout
        private val appRename: TextView = itemView.appRename
        private val appTag: TextView = itemView.appTag
        private val appPin: TextView = itemView.appPin
        private val appTitle: TextView = itemView.appTitle
        private val appTitleFrame: FrameLayout = itemView.appTitleFrame
        private val appClose: TextView = itemView.appClose
        private val appInfo: TextView = itemView.appInfo
        private val appDelete: TextView = itemView.appDelete

        @SuppressLint("RtlHardcoded", "NewApi")
        fun bind(
            flag: AppDrawerFlag,
            appLabelGravity: Int,
            appListItem: AppListItem,
            appClickListener: (AppListItem) -> Unit,
            appInfoListener: (AppListItem) -> Unit,
            appDeleteListener: (AppListItem) -> Unit,
            iconCache: ConcurrentHashMap<String, Drawable>,
            iconLoadingScope: CoroutineScope,
            prefs: Prefs
        ) = with(itemView) {

            val contextMenuFlags = prefs.getMenuFlags("CONTEXT_MENU_FLAGS", "0011111")

            // ----------------------------
            // 1️⃣ Hide optional layouts (state-driven)
            appHideLayout.isVisible = absoluteAdapterPosition == openedContextMenuPosition
            appRenameLayout.isVisible = false
            appTagLayout.isVisible = false

            val packageName = appListItem.activityPackage
            val settingsKey = appListItem.settingsKey

            // ----------------------------
            // 2️⃣ Precompute lock/pin/hide state
            val isLocked = prefs.lockedApps.contains(settingsKey)
            val isPinned = prefs.pinnedApps.contains(settingsKey)
            val isHidden = (flag == AppDrawerFlag.HiddenApps)

            // ----------------------------
            // 3️⃣ Setup lock/pin/hide buttons
            appLock.apply {
                isVisible = contextMenuFlags[1]
                setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (isLocked) R.drawable.padlock else R.drawable.padlock_off,
                    0,
                    0
                )
                text = if (isLocked) getLocalizedString(R.string.unlock) else getLocalizedString(R.string.lock)
            }

            appPin.apply {
                isVisible = contextMenuFlags[0]
                setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (isPinned) R.drawable.pin_off else R.drawable.pin,
                    0,
                    0
                )
                text = if (isPinned) getLocalizedString(R.string.unpin) else getLocalizedString(R.string.pin)
            }

            appHide.apply {
                isVisible = contextMenuFlags[2]
                setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (isHidden) R.drawable.visibility else R.drawable.visibility_off,
                    0,
                    0
                )
                text = if (isHidden) getLocalizedString(R.string.show) else getLocalizedString(R.string.hide)
            }

            // ----------------------------
            // 4️⃣ Setup rename/tag layouts
            appRename.apply {
                isVisible = contextMenuFlags[3]
                setOnClickListener {
                    appRenameEdit.hint = appListItem.activityLabel
                    appRenameLayout.isVisible = true
                    appHideLayout.isVisible = false
                    appRenameEdit.showKeyboard()
                    appRenameEdit.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }

            appTag.apply {
                isVisible = contextMenuFlags[4]
                setOnClickListener {
                    appTagEdit.hint = appListItem.activityLabel
                    appTagLayout.isVisible = true
                    appHideLayout.isVisible = false
                    appTagEdit.showKeyboard()
                    appTagEdit.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }

            appRenameEdit.apply {
                val activityLabel = prefs.getAppAlias(settingsKey).takeIf { it.isNotBlank() }
                    ?: appListItem.activityLabel

                text = Editable.Factory.getInstance().newEditable(activityLabel)
            }

            appTagEdit.apply {
                text = Editable.Factory.getInstance().newEditable(appListItem.customTag)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable) {}
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                        appSaveTag.text = if (text.toString() == appListItem.customTag) getLocalizedString(R.string.cancel)
                        else getLocalizedString(R.string.tag)
                    }
                })
            }

            appClose.setOnClickListener {
                appHideLayout.isVisible = false
                openedContextMenuPosition = RecyclerView.NO_POSITION

                val sidebarContainer = (context as? Activity)
                    ?.findViewById<View>(R.id.sidebar_container)

                sidebarContainer?.isVisible = prefs.showAZSidebar
            }

            // ----------------------------
            // 5️⃣ App title
            appTitle.text = prefs.getAppAlias(settingsKey).takeIf { it.isNotBlank() } ?: appListItem.activityLabel
            val params = appTitle.layoutParams as FrameLayout.LayoutParams
            params.gravity = appLabelGravity
            appTitle.layoutParams = params
            val padding = dp2px(resources, 24)
            appTitle.updatePadding(left = padding, right = padding)

            // ----------------------------
            // 6️⃣ Icon loading off main thread
            val placeholderIcon = AppCompatResources.getDrawable(context, R.drawable.ic_default_app)
            val cachedIcon = iconCache[settingsKey]
            setAppTitleIcon(appTitle, cachedIcon ?: placeholderIcon, prefs)

            if (cachedIcon == null && packageName.isNotBlank() && prefs.iconPackAppList != Constants.IconPacks.Disabled) {
                // 1. Tag the view with the package name to prevent "wrong icon" bugs
                appTitle.tag = settingsKey

                iconLoadingScope.launch {
                    val icon = withContext(Dispatchers.IO) {
                        val shortcutIcon = if (appListItem.isShortcut) ShortcutHelper.getIcon(context, appListItem) else null
                        val nonNullDrawable: Drawable = shortcutIcon ?: getSafeAppIcon(
                            context = context,
                            packageName = packageName,
                            useIconPack = prefs.customIconPackAppList.isNotEmpty() &&
                                    prefs.iconPackAppList == Constants.IconPacks.Custom,
                            iconPackTarget = IconCacheTarget.APP_LIST
                        )
                        getSystemIcons(context, prefs, IconCacheTarget.APP_LIST, nonNullDrawable) ?: nonNullDrawable
                    }

                    // 2. Update cache (Ensure iconCache is thread-safe, e.g., ConcurrentHashMap)
                    iconCache[settingsKey] = icon

                    // 3. ONLY update the UI if the view is still intended for THIS package
                    // This prevents the wrong icon from appearing after scrolling
                    if (appTitle.tag == settingsKey) {
                        setAppTitleIcon(appTitle, icon, prefs)
                    }
                }
            }

            // ----------------------------
            // 7️⃣ Click listeners
            val sidebarContainer = (context as? Activity)?.findViewById<View>(R.id.sidebar_container)
            appTitleFrame.apply {
                setOnClickListener { appClickListener(appListItem) }
                setOnLongClickListener {
                    val openApp = flag == AppDrawerFlag.LaunchApp || flag == AppDrawerFlag.HiddenApps
                    if (openApp) {
                        try {
                            appDelete.alpha = if (!appListItem.isShortcut && context.isSystemApp(packageName)) 0.3f else 1f
                            val currentPos = absoluteAdapterPosition

                            // Close previously opened menu
                            if (openedContextMenuPosition != RecyclerView.NO_POSITION &&
                                openedContextMenuPosition != currentPos
                            ) {
                                notifyItemChanged(openedContextMenuPosition)
                            }

                            // Open this one
                            appHideLayout.isVisible = true
                            sidebarContainer?.isVisible = false
                            openedContextMenuPosition = currentPos

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
            }

            appInfo.setOnClickListener { appInfoListener(appListItem) }
            appDelete.setOnClickListener { appDeleteListener(appListItem) }

            // ----------------------------
            // 8️⃣ Lock/Pin toggle actions
            appPin.setOnClickListener {
                val updated = prefs.pinnedApps.toMutableSet()
                if (isPinned) updated.remove(settingsKey) else updated.add(settingsKey)
                prefs.pinnedApps = updated
            }

            itemView.setOnClickListener {
                if (openedContextMenuPosition != RecyclerView.NO_POSITION &&
                    openedContextMenuPosition != absoluteAdapterPosition
                ) {
                    this@AppDrawerAdapter.closeOpenedMenu()
                }
            }
        }


        // Helper to set icon on appTitle with correct size and alignment
        private fun setAppTitleIcon(appTitle: TextView, icon: Drawable?, prefs: Prefs) {
            if (icon == null || prefs.iconPackAppList == Constants.IconPacks.Disabled) {
                appTitle.setCompoundDrawables(null, null, null, null)
                return
            }
            val iconSize = (prefs.appSize * 1.4).toInt()
            val iconPadding = (iconSize / 1.2).toInt()
            icon.setBounds(
                0,
                0,
                ((iconSize * 1.6).toInt()),
                ((iconSize * 1.6).toInt())
            )
            when (prefs.drawerAlignment) {
                Constants.Gravity.Left -> {
                    appTitle.setCompoundDrawables(icon, null, null, null)
                    appTitle.compoundDrawablePadding = iconPadding
                }

                Constants.Gravity.Right -> {
                    appTitle.setCompoundDrawables(null, null, icon, null)
                    appTitle.compoundDrawablePadding = iconPadding
                }

                else -> appTitle.setCompoundDrawables(null, null, null, null)
            }
        }

        // Clear icon when view is recycled
        fun clearIcon() {
            appTitle.setCompoundDrawables(null, null, null, null)
        }
    }

    fun closeOpenedMenu() {
        if (openedContextMenuPosition != RecyclerView.NO_POSITION) {
            val oldPosition = openedContextMenuPosition
            openedContextMenuPosition = RecyclerView.NO_POSITION
            notifyItemChanged(oldPosition)
            // Redraw all visible items just in case
            notifyItemRangeChanged(0, itemCount)
        }
    }
}
