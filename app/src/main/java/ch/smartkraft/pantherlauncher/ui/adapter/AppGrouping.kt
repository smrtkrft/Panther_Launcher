package ch.smartkraft.pantherlauncher.ui.adapter

/** One row of the grouped drawer: a category header, or an app inside the open category. */
sealed class DrawerRow<out T> {
    data class Header(val name: String, val count: Int, val expanded: Boolean) : DrawerRow<Nothing>()
    data class App<T>(val item: T) : DrawerRow<T>()
}

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
        openCategory: String?
    ): List<DrawerRow<T>> {
        val rows = mutableListOf<DrawerRow<T>>()
        val (pinned, rest) = apps.partition(isPinned)
        pinned.forEach { rows.add(DrawerRow.App(it)) }

        // name shown -> apps; a tag that differs only in case is the same category
        val tagged = linkedMapOf<String, Pair<String, MutableList<T>>>()
        val bySystem = linkedMapOf<String, MutableList<T>>()
        val other = mutableListOf<T>()

        for (app in rest) {
            val tag = tagOf(app).trim()
            val system = systemCategoryOf(app)?.trim().orEmpty()
            when {
                tag.isNotEmpty() -> tagged.getOrPut(tag.lowercase()) { tag to mutableListOf() }.second.add(app)
                system.isNotEmpty() -> bySystem.getOrPut(system) { mutableListOf() }.add(app)
                else -> other.add(app)
            }
        }

        // A tag with the same name as an Android category joins it instead of standing beside it
        val groups = mutableListOf<Pair<String, List<T>>>()
        val systemLeft = bySystem.toMutableMap()
        tagged.values.sortedBy { it.first.lowercase() }.forEach { (name, tagApps) ->
            val sameSystem = systemLeft.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            groups.add(name to (tagApps + sameSystem?.let { systemLeft.remove(it) }.orEmpty()))
        }
        systemLeft.entries.sortedBy { it.key.lowercase() }.forEach { groups.add(it.key to it.value) }
        if (other.isNotEmpty()) groups.add(otherName to other)

        for ((name, groupApps) in groups) {
            val expanded = name.equals(openCategory, ignoreCase = true)
            rows.add(DrawerRow.Header(name, groupApps.size, expanded))
            if (expanded) groupApps.forEach { rows.add(DrawerRow.App(it)) }
        }
        return rows
    }
}
