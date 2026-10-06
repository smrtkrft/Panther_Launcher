package ch.smartkraft.pantherlauncher.ui.adapter

/** One row of the grouped drawer: a category header, or an app inside the open category. */
sealed class DrawerRow<out T> {
    data class Header(val name: String, val count: Int, val expanded: Boolean) : DrawerRow<Nothing>()
    data class App<T>(val item: T) : DrawerRow<T>()
}

/** A category of the drawer: the tag its apps carry and the apps themselves. */
data class AppCategory<T>(val name: String, val apps: List<T>, val isOther: Boolean = false)

/**
 * Turns the flat app list into the rows of the category view.
 *
 * Every app appears exactly once: pinned apps at the top without a header, every other app in the
 * category its tag names, and apps without a tag in [otherName]. Only [openCategory] shows its apps.
 */
object AppGrouping {

    fun <T> rows(
        apps: List<T>,
        isPinned: (T) -> Boolean,
        tagOf: (T) -> String,
        otherName: String,
        openCategory: String?,
        order: List<String> = emptyList()
    ): List<DrawerRow<T>> {
        val rows = mutableListOf<DrawerRow<T>>()
        val (pinned, rest) = apps.partition(isPinned)
        pinned.forEach { rows.add(DrawerRow.App(it)) }

        for (category in categories(rest, tagOf, otherName, order)) {
            val expanded = category.name.equals(openCategory, ignoreCase = true)
            rows.add(DrawerRow.Header(category.name, category.apps.size, expanded))
            if (expanded) category.apps.forEach { rows.add(DrawerRow.App(it)) }
        }
        return rows
    }

    /**
     * The categories in the order the drawer shows them: first the names in [order] (as far as
     * they still have apps), then any other tag alphabetically, and [otherName] last. A tag that
     * differs only in case is the same category; a category without apps does not exist.
     */
    fun <T> categories(
        apps: List<T>,
        tagOf: (T) -> String,
        otherName: String,
        order: List<String> = emptyList()
    ): List<AppCategory<T>> {
        val tagged = linkedMapOf<String, Pair<String, MutableList<T>>>()
        val other = mutableListOf<T>()
        for (app in apps) {
            val tag = tagOf(app).trim()
            if (tag.isEmpty() || tag.equals(otherName, ignoreCase = true)) other.add(app)
            else tagged.getOrPut(tag.lowercase()) { tag to mutableListOf() }.second.add(app)
        }

        val result = mutableListOf<AppCategory<T>>()
        for (wanted in order) {
            val key = wanted.trim().lowercase()
            val group = tagged.remove(key) ?: continue
            // The saved order carries the spelling the user chose last
            result.add(AppCategory(wanted.trim(), group.second))
        }
        tagged.values.sortedBy { it.first.lowercase() }.forEach { (name, groupApps) -> result.add(AppCategory(name, groupApps)) }
        if (other.isNotEmpty()) result.add(AppCategory(otherName, other, isOther = true))
        return result
    }
}
