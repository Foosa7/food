package com.food.core

import kotlin.math.ln

data class SearchItem(val id: String, val nameSv: String, val nameEn: String?, val group: String? = null)

/**
 * Local search over Swedish and English names. The whole database is ~2,600 names, so it
 * lives in memory and a query is a plain scan.
 *
 * - Diacritics are folded, so "agg" finds "Ägg".
 * - Every word of the query has to match a word of the name (any order).
 * - Compounds are split the way Livsmedelsverket writes them: "rågbröd" finds "Bröd råg".
 * - A few everyday words map to the database's: "havremjölk" → "havredryck", "med" → "m".
 * - Exact words beat prefixes beat substrings; the name's first word and short names rank
 *   higher; foods you log often get a boost.
 */
class FoodSearch(items: List<SearchItem>) {

    private class Name(val tokens: List<String>, val folded: String)
    private class Entry(val item: SearchItem, val sv: Name, val en: Name?)

    private val entries = items.map { item ->
        Entry(item, name(item.nameSv), item.nameEn?.let(::name))
    }

    /** [boost] adds to the score, e.g. from how often a food was logged ([usageBoost]). */
    fun search(query: String, limit: Int = 50, boost: (String) -> Double = { 0.0 }): List<SearchItem> {
        val q = queryTokens(query)
        if (q.isEmpty()) return emptyList()
        val foldedQuery = q.joinToString(" ")
        return entries.mapNotNull { e ->
            val sv = score(q, foldedQuery, e.sv)
            val en = e.en?.let { score(q, foldedQuery, it) }?.let { it - EN_PENALTY }
            val best = listOfNotNull(sv, en).maxOrNull() ?: return@mapNotNull null
            e to best + boost(e.item.id)
        }
            .sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }
                .thenBy { it.first.item.nameSv.length }
                .thenBy { it.first.item.nameSv })
            .take(limit)
            .map { it.first.item }
    }

    private fun score(query: List<String>, foldedQuery: String, name: Name): Double? {
        var total = 0.0
        for (q in query) total += tokenScore(q, name.tokens) ?: return null
        if (name.folded == foldedQuery) total += EXACT_NAME
        return total - LENGTH_PENALTY * name.tokens.size
    }

    /** The best match of one query word anywhere in the name, or null if it matches nothing. */
    private fun tokenScore(q: String, tokens: List<String>): Double? {
        var best: Double? = null
        tokens.forEachIndexed { i, t ->
            val s = match(q, t) ?: return@forEachIndexed
            val withPos = s + if (i == 0) FIRST_WORD else 0.0
            if (best == null || withPos > best!!) best = withPos
        }
        return best ?: compound(q, tokens)
    }

    private fun match(q: String, t: String): Double? = when {
        t == q -> EXACT
        // "3" must not match "30"; "3" may match "3.5".
        q.first().isDigit() -> if (t.startsWith("$q.")) PREFIX else null
        // One letter only as a whole word ("m" for "m.", "med").
        q.length == 1 -> null
        t.startsWith(q) -> PREFIX
        // Inside a word only for longer words, so "ägg" doesn't find "lägg".
        q.length >= 4 && t.contains(q) -> INSIDE
        else -> null
    }

    /** "rågbröd" = "råg" + "bröd", "fullkornsbröd" = "fullkorn" + (s) + "bröd". */
    private fun compound(q: String, tokens: List<String>): Double? {
        if (q.length < 6) return null
        for (cut in 3..q.length - 3) {
            val head = q.substring(0, cut)
            val tail = q.substring(cut)
            val heads = if (head.endsWith("s") && head.length > 3) listOf(head, head.dropLast(1)) else listOf(head)
            val tailOk = tokens.any { t -> t == tail || t.startsWith(tail) }
            if (tailOk && heads.any { h -> tokens.any { t -> t == h || t.startsWith(h) } }) return COMPOUND
        }
        return null
    }

    private fun name(s: String): Name {
        val tokens = Text.tokens(s)
        return Name(tokens, tokens.joinToString(" "))
    }

    private fun queryTokens(query: String) = Text.tokens(query).map { SYNONYMS[it] ?: it }

    companion object {
        private const val EXACT = 10.0
        private const val PREFIX = 7.0
        private const val COMPOUND = 6.0
        private const val INSIDE = 4.0
        private const val FIRST_WORD = 3.0
        private const val EXACT_NAME = 20.0
        private const val LENGTH_PENALTY = 0.15
        private const val EN_PENALTY = 0.5

        /** Everyday words → the words Livsmedelsverket uses. Keys and values are folded. */
        private val SYNONYMS = mapOf(
            "havremjolk" to "havredryck",
            "sojamjolk" to "sojadryck",
            "mandelmjolk" to "mandeldryck",
            "rismjolk" to "risdryck",
            "med" to "m",
            "utan" to "u",
            "och" to "o",
        )

        /** Up to about +5.5 for a food logged 15 times. */
        fun usageBoost(timesLogged: Int): Double = if (timesLogged <= 0) 0.0 else 2.0 * ln(1.0 + timesLogged)
    }
}

object Text {
    private val decimalComma = Regex("(\\d),(\\d)")
    private val split = Regex("[^\\p{L}\\p{N}.]+")

    /** Lowercase, å/ä → a, ö → o, é → e and so on. */
    fun fold(s: String): String {
        val lower = s.lowercase()
        val sb = StringBuilder(lower.length)
        for (c in lower) sb.append(FOLD[c] ?: c)
        return sb.toString()
    }

    /** "Fett 1,5% berikad" → [fett, 1.5, berikad]. */
    fun tokens(s: String): List<String> =
        fold(s).replace(decimalComma, "$1.$2")
            .split(split)
            .map { it.trim('.') }
            .filter { it.isNotEmpty() }

    private val FOLD = mapOf(
        'å' to 'a', 'ä' to 'a', 'à' to 'a', 'á' to 'a', 'â' to 'a',
        'ö' to 'o', 'ø' to 'o', 'ó' to 'o', 'ô' to 'o',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ü' to 'u', 'ú' to 'u', 'í' to 'i', 'ï' to 'i', 'ç' to 'c', 'ñ' to 'n', 'æ' to 'a',
    )
}
