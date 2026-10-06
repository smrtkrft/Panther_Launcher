package ch.smartkraft.pantherlauncher.ui.adapter

/** One row of the grouped drawer: a category header, or an app inside the open category. */
sealed class DrawerRow<out T> {
    data class Header(val name: String, val count: Int, val expanded: Boolean) : DrawerRow<Nothing>()
    data class App<T>(val item: T) : DrawerRow<T>()
}

/**
 * A category of the drawer. [original] is the name the apps carry (their tag or the Android
 * category), [name] is what the user sees after renaming; [fromSystem] tells whether Android
 * supplied the name.
 */
data class AppCategory<T>(val original: String, val name: String, val apps: List<T>, val fromSystem: Boolean)

/**
 * Turns the flat app list into the rows of the category view.
 *
 * Every app appears exactly once: pinned apps at the top without a header, every other app in one
 * category. An app's own tag decides its category; without a tag the category the app declares
 * to Android is used, and what is left goes to [otherName]. Only [openCategory] shows its apps.
 */
object AppGrouping {

    fun <T> rows(
        apps: List<T>,
        isPinned: (T) -> Boolean,
        tagOf: (T) -> String,
        systemCategoryOf: (T) -> String?,
        otherName: String,
        openCategory: String?,
        extraCategories: Collection<String> = emptyList(),
        displayName: (String) -> String = { it },
        maxCategories: Int = 0
    ): List<DrawerRow<T>> {
        val rows = mutableListOf<DrawerRow<T>>()
        val (pinned, rest) = apps.partition(isPinned)
        pinned.forEach { rows.add(DrawerRow.App(it)) }

        for (category in categories(rest, tagOf, systemCategoryOf, otherName, extraCategories, displayName, maxCategories)) {
            val expanded = category.name.equals(openCategory, ignoreCase = true)
            rows.add(DrawerRow.Header(category.name, category.apps.size, expanded))
            if (expanded) category.apps.forEach { rows.add(DrawerRow.App(it)) }
        }
        return rows
    }

    /**
     * The categories in the order the drawer shows them. [extraCategories] are names the user
     * created; they appear even without apps. [displayName] renames a category by its original
     * name; two categories with the same new name become one. With [maxCategories] above zero the
     * smallest categories beyond that number move into [otherName].
     */
    fun <T> categories(
        apps: List<T>,
        tagOf: (T) -> String,
        systemCategoryOf: (T) -> String?,
        otherName: String,
        extraCategories: Collection<String> = emptyList(),
        displayName: (String) -> String = { it },
        maxCategories: Int = 0
    ): List<AppCategory<T>> {
        // name shown -> apps; a tag that differs only in case is the same category
        val tagged = linkedMapOf<String, Pair<String, MutableList<T>>>()
        val bySystem = linkedMapOf<String, MutableList<T>>()
        val other = mutableListOf<T>()

        for (app in apps) {
            val tag = tagOf(app).trim()
            val system = systemCategoryOf(app)?.trim().orEmpty()
            when {
                tag.isNotEmpty() -> tagged.getOrPut(tag.lowercase()) { tag to mutableListOf() }.second.add(app)
                system.isNotEmpty() -> bySystem.getOrPut(system) { mutableListOf() }.add(app)
                else -> other.add(app)
            }
        }
        for (extra in extraCategories.map { it.trim() }.filter { it.isNotEmpty() }) {
            tagged.getOrPut(extra.lowercase()) { extra to mutableListOf() }
        }

        // A tag with the same name as an Android category joins it instead of standing beside it
        val groups = mutableListOf<AppCategory<T>>()
        val systemLeft = bySystem.toMutableMap()
        tagged.values.sortedBy { it.first.lowercase() }.forEach { (name, tagApps) ->
            val sameSystem = systemLeft.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            val systemApps = sameSystem?.let { systemLeft.remove(it) }.orEmpty()
            groups.add(AppCategory(sameSystem ?: name, name, tagApps + systemApps, sameSystem != null))
        }
        systemLeft.entries.sortedBy { it.key.lowercase() }.forEach { groups.add(AppCategory(it.key, it.key, it.value, true)) }

        // Renaming may give two categories the same name; they merge
        val renamed = linkedMapOf<String, AppCategory<T>>()
        for (group in groups) {
            val shown = displayName(group.original).trim().ifEmpty { group.original }
            val key = shown.lowercase()
            val existing = renamed[key]
            renamed[key] = if (existing == null) group.copy(name = shown)
            else existing.copy(apps = existing.apps + group.apps, fromSystem = existing.fromSystem && group.fromSystem)
        }
        var named = renamed.values.toList()

        // Too many categories: the smallest ones go to "Other"
        val otherApps = other.toMutableList()
        if (maxCategories > 0 && named.size + (if (otherApps.isNotEmpty()) 1 else 0) > maxCategories) {
            val keep = named.sortedByDescending { it.apps.size }.take((maxCategories - 1).coerceAtLeast(0)).toSet()
            named.filter { it !in keep }.forEach { otherApps += it.apps }
            named = named.filter { it in keep }
        }

        val result = named.toMutableList()
        if (otherApps.isNotEmpty()) {
            val shown = displayName(otherName).trim().ifEmpty { otherName }
            result.add(AppCategory(otherName, shown, otherApps, true))
        }
        return result
    }
}
