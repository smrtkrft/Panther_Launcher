package ch.smartkraft.pantherlauncher.ui.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGroupingTest {

    private data class App(val name: String, val tag: String = "", val pinned: Boolean = false)

    private val apps = listOf(
        App("Camera"),
        App("Chess", tag = "Games"),
        App("Clock"),
        App("Gmail", tag = "Work"),
        App("Maps", pinned = true),
        App("Slack", tag = "work"),
        App("Solitaire", tag = "Games"),
        App("Terminal")
    )

    private fun rows(open: String?, order: List<String> = emptyList()) =
        AppGrouping.rows(apps, { it.pinned }, { it.tag }, "Other", open, order)

    private fun describe(rows: List<DrawerRow<App>>) = rows.map {
        when (it) {
            is DrawerRow.Header -> "[${it.name} ${it.count}${if (it.expanded) " open" else ""}]"
            is DrawerRow.App -> it.item.name
        }
    }

    private fun names(order: List<String> = emptyList(), list: List<App> = apps.filter { !it.pinned }) =
        AppGrouping.categories(list, { it.tag }, "Other", order).map { "${it.name} ${it.apps.size}" }

    @Test
    fun closedCategoriesShowOnlyTheirHeaders() {
        assertEquals(listOf("Maps", "[Games 2]", "[Work 2]", "[Other 3]"), describe(rows(null)))
    }

    @Test
    fun onlyTheOpenCategoryShowsItsApps() {
        assertEquals(
            listOf("Maps", "[Games 2 open]", "Chess", "Solitaire", "[Work 2]", "[Other 3]"),
            describe(rows("games"))
        )
    }

    @Test
    fun everyAppBelongsToExactlyOnePlace() {
        val listed = AppGrouping.categories(apps.filter { !it.pinned }, { it.tag }, "Other").flatMap { it.apps }.map { it.name }
        assertEquals(apps.filter { !it.pinned }.map { it.name }.sorted(), listed.sorted())
    }

    @Test
    fun tagsThatDifferOnlyInCaseAreOneCategory() {
        assertEquals(listOf("Games 2", "Work 2", "Other 3"), names())
    }

    @Test
    fun theSavedOrderComesFirstWithItsSpelling() {
        assertEquals(listOf("WORK 2", "Games 2", "Other 3"), names(order = listOf("WORK")))
        assertEquals(listOf("Work 2", "Games 2", "Other 3"), names(order = listOf("Work", "Games", "Other").filter { it != "Games" }))
    }

    @Test
    fun namesInTheOrderWithoutAppsAreSkipped() {
        assertEquals(listOf("Games 2", "Work 2", "Other 3"), names(order = listOf("Reading", "Games")))
    }

    @Test
    fun aTagNamedLikeOtherJoinsOther() {
        val list = apps.filter { !it.pinned } + App("Notes", tag = "other")
        assertEquals(listOf("Games 2", "Work 2", "Other 4"), names(list = list))
    }

    @Test
    fun otherDisappearsWhenEveryAppHasATag() {
        val list = listOf(App("A", tag = "X"), App("B", tag = "Y"))
        assertEquals(listOf("X 1", "Y 1"), names(list = list))
        assertTrue(AppGrouping.categories(list, { it.tag }, "Other").none { it.isOther })
    }
}
