package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val names = listOf(
        "Ägg hönsägg kokt" to "Egg hen boiled",
        "Ägg hönsägg rått" to "Egg hen raw",
        "Lamm lägg rå" to "Lamb shank raw",
        "Nudlar äggnudlar okokta" to "Noodles egg uncooked",
        "Våffla m. ägg" to "Waffle with egg",
        "Bröd råg fullkorn" to "Bread rye wholegrain",
        "Hårt bröd fullkorn råg fibrer ca 13%" to "Crispbread rye",
        "Bröd fullkornsbröd vete" to "Bread wholegrain wheat",
        "Havredryck fett 1,5% berikad" to "Oat drink fat 1.5% fortified",
        "Mjölk fett 3% berikad" to "Milk fat 3% fortified",
        "Mjölk fett 0,5% berikad" to "Milk fat 0.5% fortified",
        "Grädde fett 30%" to "Cream fat 30%",
        "Banan" to "Banana",
        "Smoothie m. yoghurt bär banan juice" to "Smoothie yoghurt berries banana juice",
        "Kaffe bryggt" to "Coffee brewed",
        "Kaffegrädde fett 12%" to "Coffee cream fat 12%",
    )
    private val items = names.mapIndexed { i, (sv, en) -> SearchItem("lv:$i", sv, en) }
    private val search = FoodSearch(items)

    private fun top(q: String, boost: (String) -> Double = { 0.0 }) = search.search(q, boost = boost).map { it.nameSv }

    @Test fun `diacritics fold`() {
        assertEquals("Ägg hönsägg kokt", top("agg").first())
    }

    @Test fun `short words don't match inside other words`() {
        val r = top("ägg")
        assertTrue("Lamm lägg rå" !in r)
        assertTrue("Nudlar äggnudlar okokta" in r) // a prefix is fine
    }

    @Test fun `words match in any order`() {
        assertEquals("Ägg hönsägg kokt", top("kokt ägg").first())
    }

    @Test fun `compounds split like Livsmedelsverket writes them`() {
        val r = top("rågbröd")
        assertTrue(r.containsAll(listOf("Bröd råg fullkorn", "Hårt bröd fullkorn råg fibrer ca 13%")))
        assertTrue("Bröd fullkornsbröd vete" in top("fullkornsbröd"))
    }

    @Test fun `everyday words map to the database's`() {
        assertEquals("Havredryck fett 1,5% berikad", top("havremjölk").first())
        assertEquals("Våffla m. ägg", top("våffla med ägg").first())
    }

    @Test fun `numbers match whole, with a decimal comma`() {
        assertEquals(listOf("Mjölk fett 3% berikad"), top("mjölk 3"))
        assertEquals(listOf("Mjölk fett 0,5% berikad"), top("mjölk 0,5"))
        assertTrue(top("grädde 3").isEmpty()) // 30% isn't 3%
    }

    @Test fun `English names are searched too`() {
        assertEquals("Banan", top("banana").first())
        assertEquals("Kaffe bryggt", top("coffee").first())
    }

    @Test fun `an exact name and the first word rank first`() {
        assertEquals("Banan", top("banan").first())
        assertEquals("Kaffe bryggt", top("kaffe").first())
    }

    @Test fun `often logged foods rise`() {
        val creamId = items.first { it.nameSv.startsWith("Kaffegrädde") }.id
        val boosted = top("kaffe") { if (it == creamId) FoodSearch.usageBoost(10) else 0.0 }
        assertEquals("Kaffegrädde fett 12%", boosted.first())
    }

    @Test fun `blank query finds nothing`() {
        assertTrue(top("  ").isEmpty())
    }

    @Test fun tokens() {
        assertEquals(listOf("havredryck", "fett", "1.5", "berikad"), Text.tokens("Havredryck fett 1,5%  berikad"))
        assertEquals(listOf("vaffla", "m", "agg"), Text.tokens("Våffla m. ägg"))
    }
}
