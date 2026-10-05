package ch.smartkraft.fuzzywuzzy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchTest {

    private fun rank(query: String, vararg labels: String): List<String> {
        return labels
            .map { it to SmartSearch.score(it, query) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    @Test
    fun fewLettersAreEnoughForLongNames() {
        assertTrue(SmartSearch.score("Microsoft Authenticator", "mi") > 0)
        assertTrue(SmartSearch.score("Google Play Services for AR", "pl") > 0)
    }

    @Test
    fun emptyQueryAndUnrelatedLabelsDoNotMatch() {
        assertEquals(0, SmartSearch.score("Camera", ""))
        assertEquals(0, SmartSearch.score("Camera", "xyz"))
        assertEquals(0, SmartSearch.score("", "a"))
    }

    @Test
    fun prefixBeatsWordPrefixBeatsSubstring() {
        assertEquals(
            listOf("Maps", "Google Maps", "Bitmap Editor"),
            rank("map", "Bitmap Editor", "Google Maps", "Maps")
        )
    }

    @Test
    fun shorterLabelWinsInsideATier() {
        assertEquals(listOf("Maps", "Maps Go"), rank("map", "Maps Go", "Maps"))
    }

    @Test
    fun wordInitialsMatch() {
        assertTrue(SmartSearch.score("Google Maps", "gm") > 0)
        assertTrue(SmartSearch.score("Google Maps", "goma") > 0)
        assertTrue(SmartSearch.score("YouTube Music", "ytm") > 0)
    }

    @Test
    fun subsequenceHasToStartAtAWord() {
        assertTrue(SmartSearch.score("YouTube", "yt") > 0)
        assertTrue(SmartSearch.score("WhatsApp", "wtsp") > 0)
        assertEquals(0, SmartSearch.score("Calculator", "lt"))
    }

    @Test
    fun oneTypoIsTolerated() {
        assertTrue(SmartSearch.score("WhatsApp", "whst") > 0)      // wrong letter
        assertTrue(SmartSearch.score("Telegram", "telr") > 0)      // missing letter
        assertTrue(SmartSearch.score("Spotify", "sptoi") > 0)      // swapped letters
        assertEquals(0, SmartSearch.score("Spotify", "sxz"))
        assertEquals(0, SmartSearch.score("Spotify", "xpot"))      // first letter has to be right
    }

    @Test
    fun exactMatchesRankAboveTypoMatches() {
        assertEquals(listOf("Calendar", "Calculator"), rank("cale", "Calculator", "Calendar"))
    }

    @Test
    fun diacriticsAndTurkishLettersAreIgnored() {
        assertTrue(SmartSearch.score("Işık", "isik") > 0)
        assertTrue(SmartSearch.score("İstanbul Kart", "ist") > 0)
        assertTrue(SmartSearch.score("Müzik", "muz") > 0)
        assertTrue(SmartSearch.score("Muzik", "müz") > 0)
    }

    @Test
    fun separatorsInTheQueryAreIgnored() {
        assertTrue(SmartSearch.score("App-Name", "app name") > 0)
        assertTrue(SmartSearch.score("App Name", "app-name") > 0)
    }
}
