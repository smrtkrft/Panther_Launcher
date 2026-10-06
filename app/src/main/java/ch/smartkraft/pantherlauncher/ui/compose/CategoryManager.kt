package ch.smartkraft.pantherlauncher.ui.compose

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ch.smartkraft.common.getLocalizedString
import ch.smartkraft.common.showShortToast
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.data.AppListItem
import ch.smartkraft.pantherlauncher.data.Constants
import ch.smartkraft.pantherlauncher.data.Prefs
import ch.smartkraft.pantherlauncher.ui.adapter.AppCategory
import ch.smartkraft.pantherlauncher.ui.adapter.AppGrouping
import ch.smartkraft.pantherlauncher.ui.components.DialogManager
import ch.smartkraft.pantherlauncher.ui.compose.SettingsComposable.PageHeader
import ch.smartkraft.pantherlauncher.ui.compose.SettingsComposable.SettingsSelect
import ch.smartkraft.pantherlauncher.ui.compose.SettingsComposable.SettingsTitle

/**
 * Settings screen for the category view of the drawer: which apps stay above the categories,
 * how many categories there may be, and the categories themselves (rename, choose apps, create,
 * remove). The categories are computed the same way the drawer computes them.
 */
object CategoryManager {

    @Composable
    fun Screen(
        context: Context,
        prefs: Prefs,
        dialogBuilder: DialogManager,
        apps: List<AppListItem>,
        titleFontSize: TextUnit,
        onBack: () -> Unit,
        onChanged: () -> Unit
    ) {
        var version by remember { mutableIntStateOf(0) }
        val systemCategories = remember { HashMap<String, String?>() }
        val otherName = getLocalizedString(R.string.category_other)

        fun systemCategoryOf(app: AppListItem): String? = systemCategories.getOrPut(app.activityPackage) {
            try {
                val info = context.packageManager.getApplicationInfo(app.activityPackage, 0)
                ApplicationInfo.getCategoryTitle(context, info.category)?.toString()
            } catch (_: Exception) {
                null
            }
        }

        fun labelOf(app: AppListItem) = prefs.getAppAlias(app.settingsKey).ifBlank { app.activityLabel }
        fun changed() {
            version++
            onChanged()
        }

        val pinned = remember(version) { prefs.pinnedApps }
        val sortedApps = remember(apps, version) { apps.sortedBy { labelOf(it).lowercase() } }
        val unpinned = remember(sortedApps, pinned) { sortedApps.filter { it.settingsKey !in pinned } }
        val categories = remember(unpinned, version) {
            AppGrouping.categories(
                unpinned, { it.tag }, ::systemCategoryOf, otherName,
                extraCategories = prefs.customCategories,
                displayName = { prefs.categoryDisplayName(it) },
                maxCategories = prefs.maxCategories
            )
        }

        BackHandler { onBack() }
        PageHeader(
            iconRes = R.drawable.ic_back,
            title = getLocalizedString(R.string.manage_categories),
            onClick = onBack
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ---- Above the categories --------------------------------------------------------
        SettingsTitle(text = getLocalizedString(R.string.categories_top_section), fontSize = titleFontSize)
        SettingsSelect(
            title = getLocalizedString(R.string.categories_top_apps),
            option = pinned.size.toString(),
            fontSize = titleFontSize,
            onClick = {
                val checked = sortedApps.indices.filter { sortedApps[it].settingsKey in pinned }.toSet()
                dialogBuilder.showMultiChoiceDialog(
                    context, getLocalizedString(R.string.categories_top_apps), sortedApps.map(::labelOf), checked
                ) { chosen ->
                    prefs.pinnedApps = chosen.map { sortedApps[it].settingsKey }.toSet()
                    changed()
                }
            }
        )
        SettingsSelect(
            title = getLocalizedString(R.string.categories_max),
            option = prefs.maxCategories.let { if (it == 0) getLocalizedString(R.string.categories_unlimited) else it.toString() },
            fontSize = titleFontSize,
            onClick = {
                dialogBuilder.showSliderBottomSheet(
                    context = context,
                    title = getLocalizedString(R.string.categories_max_hint),
                    minValue = 0,
                    maxValue = Constants.MAX_CATEGORY_COUNT,
                    currentValue = prefs.maxCategories,
                    onValueSelected = { value ->
                        prefs.maxCategories = value.toInt()
                        changed()
                    }
                )
            }
        )

        // ---- The categories --------------------------------------------------------------
        SettingsTitle(text = getLocalizedString(R.string.categories_section), fontSize = titleFontSize)
        for (category in categories) {
            SettingsSelect(
                title = category.name,
                option = category.apps.size.toString(),
                fontSize = titleFontSize,
                onClick = { showActions(context, prefs, dialogBuilder, category, unpinned, ::labelOf, ::systemCategoryOf, otherName, ::changed) }
            )
        }
        SettingsSelect(
            title = getLocalizedString(R.string.categories_new),
            option = "+",
            fontSize = titleFontSize,
            onClick = {
                dialogBuilder.showTextInputDialog(context, getLocalizedString(R.string.categories_new), "", getLocalizedString(R.string.categories_name_hint)) { name ->
                    if (name.isEmpty()) return@showTextInputDialog
                    if (categories.any { it.name.equals(name, ignoreCase = true) }) {
                        context.showShortToast(getLocalizedString(R.string.categories_exists))
                    } else {
                        prefs.customCategories = prefs.customCategories + name
                        changed()
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }

    private fun showActions(
        context: Context,
        prefs: Prefs,
        dialogBuilder: DialogManager,
        category: AppCategory<AppListItem>,
        apps: List<AppListItem>,
        labelOf: (AppListItem) -> String,
        systemCategoryOf: (AppListItem) -> String?,
        otherName: String,
        changed: () -> Unit
    ) {
        val isOther = category.original == otherName
        val rename = getLocalizedString(R.string.categories_rename)
        val choose = getLocalizedString(R.string.categories_choose_apps)
        val reset = getLocalizedString(R.string.categories_reset_name)
        val remove = getLocalizedString(R.string.categories_remove)
        val options = buildList {
            add(rename)
            if (!isOther) add(choose)
            if (category.name != category.original) add(reset)
            if (!category.fromSystem && !isOther) add(remove)
        }
        dialogBuilder.showSingleChoiceBottomSheet(
            context = context,
            options = options.toTypedArray(),
            title = category.name,
            onItemSelected = { chosen ->
                when (chosen) {
                    rename -> dialogBuilder.showTextInputDialog(context, rename, category.name) { name ->
                        prefs.renameCategory(category.original, name)
                        changed()
                    }

                    reset -> {
                        prefs.renameCategory(category.original, "")
                        changed()
                    }

                    choose -> chooseApps(context, prefs, dialogBuilder, category, apps, labelOf, systemCategoryOf, otherName, changed)

                    remove -> {
                        // Its apps lose the tag and fall back to the category Android gives them
                        category.apps.forEach { prefs.setAppTag(it.settingsKey, "", it.user) }
                        prefs.customCategories = prefs.customCategories.filterNot { it.equals(category.original, ignoreCase = true) }.toSet()
                        prefs.renameCategory(category.original, "")
                        changed()
                    }
                }
            }
        )
    }

    private fun chooseApps(
        context: Context,
        prefs: Prefs,
        dialogBuilder: DialogManager,
        category: AppCategory<AppListItem>,
        apps: List<AppListItem>,
        labelOf: (AppListItem) -> String,
        systemCategoryOf: (AppListItem) -> String?,
        otherName: String,
        changed: () -> Unit
    ) {
        val members = category.apps.map { it.settingsKey }.toSet()
        val checked = apps.indices.filter { apps[it].settingsKey in members }.toSet()
        dialogBuilder.showMultiChoiceDialog(context, category.name, apps.map(labelOf), checked) { chosen ->
            apps.forEachIndexed { index, app ->
                val wasIn = index in checked
                val isIn = index in chosen
                when {
                    isIn && !wasIn -> prefs.setAppTag(app.settingsKey, category.original, app.user)
                    // An app Android places here can only leave through a tag of its own
                    !isIn && wasIn -> {
                        val staysBySystem = systemCategoryOf(app).equals(category.original, ignoreCase = true)
                        prefs.setAppTag(app.settingsKey, if (staysBySystem) otherName else "", app.user)
                    }
                }
            }
            changed()
        }
    }
}
