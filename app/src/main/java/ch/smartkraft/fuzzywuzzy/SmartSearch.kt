package ch.smartkraft.fuzzywuzzy

import java.text.Normalizer
import java.util.Locale

/**
 * Second search mode. Unlike [FuzzyFinder.calculateFuzzyScore] the result does not depend on the
 * length of the label, so a few typed letters are enough for long names, and a single typo is
 * tolerated.
 */
object SmartSearch {

    private const val TIER_LABEL_PREFIX = 6000
    private const val TIER_WORD_PREFIX = 5000
    private const val TIER_WORD_INITIALS = 4000
    private const val TIER_SUBSTRING = 3000
    private const val TIER_SUBSEQUENCE = 2000
    private const val TIER_TYPO = 1000

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val camelCase = Regex("(?<=\\p{Ll})(?=\\p{Lu})")

    /**
     * Returns 0 when [label] does not match [query]; otherwise a positive score, higher is better.
     */
    fun score(label: String, query: String): Int {
        val needle = normalize(query).replace(separators, "")
        if (needle.isEmpty()) return 0

        val words = splitWords(label)
        if (words.isEmpty()) return 0
        val compact = words.joinToString("")

        // Shorter labels win inside the same tier: "Maps" before "Maps Go" for "map".
        val lengthPenalty = compact.length.coerceAtMost(99)

        if (compact.startsWith(needle)) return TIER_LABEL_PREFIX - lengthPenalty

        val wordIndex = words.indexOfFirst { it.startsWith(needle) }
        if (wordIndex >= 0) return TIER_WORD_PREFIX - wordIndex * 100 - lengthPenalty

        if (matchesWordInitials(words, needle)) return TIER_WORD_INITIALS - lengthPenalty

        val position = compact.indexOf(needle)
        if (position >= 0) return TIER_SUBSTRING - position.coerceAtMost(9) * 100 - lengthPenalty

        val span = subsequenceSpan(words, needle)
        if (span > 0) return TIER_SUBSEQUENCE - (span - needle.length).coerceAtMost(9) * 100 - lengthPenalty

        if (matchesWithTypo(words, needle)) return TIER_TYPO - lengthPenalty

        return 0
    }

    private fun normalize(input: String): String {
        val lower = input.lowercase(Locale.ROOT).replace('ı', 'i')
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(diacritics, "")
    }

    private fun splitWords(label: String): List<String> {
        return label.replace(camelCase, " ")
            .split(separators)
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
    }

    /**
     * True when the query is made of the beginnings of consecutive words:
     * "gm" and "goma" both match "Google Maps".
     */
    private fun matchesWordInitials(words: List<String>, needle: String): Boolean {
        if (words.size < 2) return false
        for (start in words.indices) {
            if (consumeWords(words, start, needle, 0, 0)) return true
        }
        return false
    }

    private fun consumeWords(words: List<String>, wordIdx: Int, needle: String, needleIdx: Int, used: Int): Boolean {
        if (needleIdx == needle.length) return used >= 2
        if (wordIdx == words.size) return false
        val word = words[wordIdx]
        var taken = 0
        while (taken < word.length && needleIdx + taken < needle.length && word[taken] == needle[needleIdx + taken]) {
            taken++
        }
        // Try the longest prefix first, then shorter ones.
        for (length in taken downTo 1) {
            if (consumeWords(words, wordIdx + 1, needle, needleIdx + length, used + 1)) return true
        }
        return false
    }

    /**
     * Letters of the query appear in order, starting at the beginning of a word ("yt" in
     * "YouTube"). Returns the length of the shortest such stretch, or 0 when there is none.
     */
    private fun subsequenceSpan(words: List<String>, needle: String): Int {
        if (needle.length < 2) return 0
        val compact = words.joinToString("")
        var best = 0
        var wordStart = 0
        for (word in words) {
            if (compact[wordStart] == needle[0]) {
                var needleIdx = 0
                var i = wordStart
                while (i < compact.length && needleIdx < needle.length) {
                    if (compact[i] == needle[needleIdx]) needleIdx++
                    i++
                }
                if (needleIdx == needle.length) {
                    val span = i - wordStart
                    if (best == 0 || span < best) best = span
                }
            }
            wordStart += word.length
        }
        return best
    }

    /**
     * One wrong, missing, extra or swapped letter is accepted from three typed letters on,
     * two from seven on. The first letter has to be right.
     */
    private fun matchesWithTypo(words: List<String>, needle: String): Boolean {
        if (needle.length < 3) return false
        val allowed = if (needle.length >= 7) 2 else 1
        val candidates = words + words.joinToString("")
        return candidates.any { word ->
            word[0] == needle[0] && (-allowed..allowed).any { delta ->
                val length = needle.length + delta
                length in 1..word.length && editDistance(word.substring(0, length), needle, allowed) <= allowed
            }
        }
    }

    /** Damerau-Levenshtein distance; stops early once every cell of a row exceeds [limit]. */
    private fun editDistance(a: String, b: String, limit: Int): Int {
        var beforePrevious = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, beforePrevious[j - 2] + 1)
                }
                current[j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > limit) return limit + 1
            val recycled = beforePrevious
            beforePrevious = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }
}
