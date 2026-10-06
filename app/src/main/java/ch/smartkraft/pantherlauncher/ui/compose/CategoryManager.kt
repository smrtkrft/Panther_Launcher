package ch.smartkraft.pantherlauncher.ui.compose

import android.content.Context
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
import ch.smartkraft.pantherlauncher.data.Prefs
import ch.smartkraft.pantherlauncher.ui.adapter.AppCategory
import ch.smartkraft.pantherlauncher.ui.adapter.AppGrouping
import ch.smartkraft.pantherlauncher.ui.components.DialogManager
import ch.smartkraft.pantherlauncher.ui.compose.SettingsComposable.PageHeader
import ch.smartkraft.pantherlauncher.ui.compose.SettingsComposable.SectionCard
import ch.smartkraft.pantherlauncher.ui.compose.SettingsComposable.SettingsSelect
import ch.smartkraft.pantherlauncher.ui.compose.SettingsComposable.SettingsTitle

/**
 * Settings screen for the category view of the drawer. A category is nothing but the tag its
 * apps share, so the screen edits tags: create a category by choosing its apps, rename it,
 * change its apps, move it up or down, remove it. A category without apps no longer exists.
 * The apps kept above the categories are the pinned ones.
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
        val otherName = getLocalizedString(R.string.category_other)

        fun labelOf(app: AppListItem) = prefs.getAppAlias(app.settingsKey).ifBlank { app.activityLabel }
        fun changed() {
            version++
            onChanged()
        }

        val pinned = remember(version) { prefs.pinnedApps }
        val sortedApps = remember(apps, version) { apps.sortedBy { labelOf(it).lowercase() } }
        val unpinned = remember(sortedApps, pinned) { sortedApps.filter { it.settingsKey !in pinned } }
        val categories = remember(unpinned, version) {
            AppGrouping.categories(unpinned, { it.tag }, otherName, prefs.categoryOrder)
        }
        // Names that lost their apps leave the saved order
        val named = categories.filter { !it.isOther }.map { it.name }
        if (named != prefs.categoryOrder) prefs.categoryOrder = named

        BackHandler { onBack() }
        PageHeader(
            iconRes = R.drawable.ic_back,
            title = getLocalizedString(R.string.manage_categories),
            onClick = onBack
        )
        Spacer(modifier = Modifier.height(16.dp))

        SettingsTitle(text = getLocalizedString(R.string.categories_top_apps), fontSize = titleFontSize)
        SectionCard {
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
        }

        SettingsTitle(text = getLocalizedString(R.string.categories_section), fontSize = titleFontSize)
        SectionCard {
            categories.forEach { category ->
                SettingsSelect(
                    title = category.name,
                    option = category.apps.size.toString(),
                    fontSize = titleFontSize,
                    onClick = { showActions(context, prefs, dialogBuilder, category, categories, unpinned, ::labelOf, ::changed) }
                )
            }
            SettingsSelect(
                title = getLocalizedString(R.string.categories_new),
                option = "+",
                fontSize = titleFontSize,
                onClick = { createCategory(context, prefs, dialogBuilder, categories, unpinned, ::labelOf, ::changed) }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    private fun nameTaken(name: String, categories: List<AppCategory<AppListItem>>) =
        categories.any { it.name.equals(name, ignoreCase = true) }

    /** A category comes into being with its first apps; without a choice nothing is created. */
    private fun createCategory(
        context: Context,
        prefs: Prefs,
        dialogBuilder: DialogManager,
        categories: List<AppCategory<AppListItem>>,
        apps: List<AppListItem>,
        labelOf: (AppListItem) -> String,
        changed: () -> Unit
    ) {
        dialogBuilder.showTextInputDialog(context, getLocalizedString(R.string.categories_new), "", getLocalizedString(R.string.categories_name_hint)) { name ->
            if (name.isEmpty()) return@showTextInputDialog
            if (nameTaken(name, categories)) {
                context.showShortToast(getLocalizedString(R.string.categories_exists))
                return@showTextInputDialog
            }
            dialogBuilder.showMultiChoiceDialog(context, getLocalizedString(R.string.categories_choose_apps_for, name), apps.map(labelOf), emptySet()) { chosen ->
                if (chosen.isEmpty()) {
                    context.showShortToast(getLocalizedString(R.string.categories_not_created))
                } else {
                    chosen.forEach { prefs.setAppTag(apps[it].settingsKey, name, apps[it].user) }
                    prefs.categoryOrder = prefs.categoryOrder + name
                    changed()
                }
            }
        }
    }

    private fun showActions(
        context: Context,
        prefs: Prefs,
        dialogBuilder: DialogManager,
        category: AppCategory<AppListItem>,
        categories: List<AppCategory<AppListItem>>,
        apps: List<AppListItem>,
        labelOf: (AppListItem) -> String,
        changed: () -> Unit
    ) {
        val rename = getLocalizedString(R.string.categories_rename)
        val choose = getLocalizedString(R.string.categories_choose_apps)
        val up = getLocalizedString(R.string.categories_move_up)
        val down = getLocalizedString(R.string.categories_move_down)
        val remove = getLocalizedString(R.string.categories_remove)
        val named = categories.filter { !it.isOther }
        val index = named.indexOf(category)
        // "Other" only collects the untagged apps; it is managed through the other categories
        if (category.isOther) return
        val options = buildList {
            add(rename); add(choose)
            if (index > 0) add(up)
            if (index in 0 until named.size - 1) add(down)
            add(remove)
        }
        dialogBuilder.showSingleChoiceBottomSheet(
            context = context,
            options = options.toTypedArray(),
            title = category.name,
            onItemSelected = { chosen ->
                when (chosen) {
                    rename -> dialogBuilder.showTextInputDialog(context, rename, category.name) { name ->
                        if (name.isEmpty() || name == category.name) return@showTextInputDialog
                        if (nameTaken(name, categories) && !name.equals(category.name, ignoreCase = true)) {
                            context.showShortToast(getLocalizedString(R.string.categories_exists))
                            return@showTextInputDialog
                        }
                        category.apps.forEach { prefs.setAppTag(it.settingsKey, name, it.user) }
                        prefs.categoryOrder = prefs.categoryOrder.map { if (it.equals(category.name, ignoreCase = true)) name else it }
                        if (prefs.openDrawerCategory.equals(category.name, ignoreCase = true)) prefs.openDrawerCategory = name
                        changed()
                    }

                    choose -> chooseApps(context, prefs, dialogBuilder, category, apps, labelOf, changed)

                    up, down -> {
                        val order = named.map { it.name }.toMutableList()
                        val to = if (chosen == up) index - 1 else index + 1
                        order.add(to, order.removeAt(index))
                        prefs.categoryOrder = order
                        changed()
                    }

                    remove -> {
                        // Its apps lose the tag and go to "Other"
                        category.apps.forEach { prefs.setAppTag(it.settingsKey, "", it.user) }
                        prefs.categoryOrder = prefs.categoryOrder.filterNot { it.equals(category.name, ignoreCase = true) }
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
        changed: () -> Unit
    ) {
        val members = category.apps.map { it.settingsKey }.toSet()
        val checked = apps.indices.filter { apps[it].settingsKey in members }.toSet()
        dialogBuilder.showMultiChoiceDialog(context, category.name, apps.map(labelOf), checked) { chosen ->
            apps.forEachIndexed { index, app ->
                val wasIn = index in checked
                val isIn = index in chosen
                // An app taken out loses its tag and goes to "Other"
                if (isIn != wasIn) prefs.setAppTag(app.settingsKey, if (isIn) category.name else "", app.user)
            }
            changed()
        }
    }
}
