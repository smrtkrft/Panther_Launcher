package ch.smartkraft.pantherlauncher.ui.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGroupingTest {

    private data class App(val name: String, val tag: String = "", val system: String? = null, val pinned: Boolean = false)

    private val apps = listOf(
        App("Camera", system = "Photos"),
        App("Chess", system = "Games"),
        App("Clock"),
        App("Gmail", tag = "Work"),
        App("Maps", pinned = true),
        App("Slack", tag = "work"),
        App("Solitaire", tag = "Games"),
        App("Terminal")
    )

    private fun rows(open: String?) =
        AppGrouping.rows(apps, { it.pinned }, { it.tag }, { it.system }, "Other", open)

    private fun describe(rows: List<DrawerRow<App>>) = rows.map {
        when (it) {
            is DrawerRow.Header -> "[${it.name} ${it.count}${if (it.expanded) " open" else ""}]"
            is DrawerRow.App -> it.item.name
        }
    }

    @Test
    fun closedCategoriesShowOnlyTheirHeaders() {
        assertEquals(listOf("Maps", "[Games 2]", "[Work 2]", "[Photos 1]", "[Other 2]"), describe(rows(null)))
    }

    @Test
    fun onlyTheOpenCategoryShowsItsApps() {
        assertEquals(
            listOf("Maps", "[Games 2 open]", "Solitaire", "Chess", "[Work 2]", "[Photos 1]", "[Other 2]"),
            describe(rows("games"))
        )
    }

    @Test
    fun everyAppBelongsToExactlyOnePlace() {
        // Open each category in turn and collect what it shows, plus the pinned apps
        val names = rows(null).filterIsInstance<DrawerRow.Header>().map { it.name }
        val shown = names.flatMap { name -> rows(name).filterIsInstance<DrawerRow.App<App>>().map { it.item.name } }
            .filter { it != "Maps" } + "Maps"
        assertEquals(apps.map { it.name }.sorted(), shown.sorted())
        assertEquals(apps.size, rows(null).filterIsInstance<DrawerRow.Header>().sumOf { it.count } + 1)
    }

    @Test
    fun ownTagsComeBeforeAndroidCategoriesAndOtherIsLast() {
        val headers = rows(null).filterIsInstance<DrawerRow.Header>().map { it.name }
        assertEquals("Other", headers.last())
        assertTrue(headers.indexOf("Work") < headers.indexOf("Photos"))
    }

    @Test
    fun aTagNamedLikeAnAndroidCategoryJoinsIt() {
        assertEquals(1, rows(null).filterIsInstance<DrawerRow.Header>().count { it.name.equals("games", true) })
    }
}
