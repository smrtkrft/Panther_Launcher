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

    private fun names(
        extra: List<String> = emptyList(),
        rename: Map<String, String> = emptyMap(),
        max: Int = 0
    ) = AppGrouping.categories(
        apps.filter { !it.pinned }, { it.tag }, { it.system }, "Other",
        extraCategories = extra, displayName = { rename[it] ?: it }, maxCategories = max
    ).map { "${it.name} ${it.apps.size}" }

    @Test
    fun aCreatedCategoryAppearsEvenWithoutApps() {
        assertEquals(listOf("Games 2", "Reading 0", "Work 2", "Photos 1", "Other 2"), names(extra = listOf("Reading")))
        // A created name that an existing category already uses does not double it
        assertEquals(listOf("Games 2", "Work 2", "Photos 1", "Other 2"), names(extra = listOf("WORK")))
    }

    @Test
    fun renamingKeepsTheAppsAndMergesEqualNames() {
        assertEquals(listOf("Games 2", "Work 2", "Pictures 1", "Other 2"), names(rename = mapOf("Photos" to "Pictures")))
        assertEquals(listOf("Games 2", "Fun 3", "Other 2"), names(rename = mapOf("Work" to "Fun", "Photos" to "fun")))
        assertEquals(listOf("Games 2", "Work 2", "Photos 1", "Rest 2"), names(rename = mapOf("Other" to "Rest")))
    }

    @Test
    fun aLimitMovesTheSmallestCategoriesIntoOther() {
        assertEquals(listOf("Games 2", "Work 2", "Other 3"), names(max = 3))
        assertEquals(listOf("Other 7"), names(max = 1))
        // Already within the limit: nothing changes
        assertEquals(listOf("Games 2", "Work 2", "Photos 1", "Other 2"), names(max = 4))
    }

    @Test
    fun everyAppIsStillListedOnceWithRenamesAndALimit() {
        val categories = AppGrouping.categories(
            apps.filter { !it.pinned }, { it.tag }, { it.system }, "Other",
            extraCategories = listOf("Empty"), displayName = { if (it == "Work") "Games" else it }, maxCategories = 2
        )
        val listed = categories.flatMap { it.apps }.map { it.name }
        assertEquals(apps.filter { !it.pinned }.map { it.name }.sorted(), listed.sorted())
        assertEquals(2, categories.size)
    }
}
