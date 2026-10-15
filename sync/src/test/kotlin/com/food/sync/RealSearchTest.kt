package com.food.sync

import com.food.core.FoodSearch
import com.food.core.SearchItem
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/** Search against the real foods.db; skipped until the sync has built it. */
class RealSearchTest {
    private val db = File("data/foods.db")

    private val search by lazy {
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { c ->
            c.createStatement().executeQuery("SELECT id, name_sv, name_en FROM food WHERE removed = 0").use { r ->
                buildList { while (r.next()) add(SearchItem(r.getString(1), r.getString(2), r.getString(3))) }
            }
        }.let(::FoodSearch)
    }

    private fun top(q: String, n: Int = 5) = search.search(q, n).map { it.nameSv }

    private fun expect(q: String, n: Int, ok: (String) -> Boolean) {
        val r = top(q, n)
        println("$q → $r")
        assertTrue("$q → $r", r.any(ok))
    }

    @Test fun `everyday searches find what you mean`() {
        assumeTrue(db.exists())
        expect("ägg", 3) { it.startsWith("Ägg") }
        expect("rågbröd", 5) { "råg" in it.lowercase() && "bröd" in it.lowercase() }
        expect("havremjölk", 3) { it.startsWith("Havredryck") }
        expect("smör", 2) { it.startsWith("Smör fett") }
        expect("kaffe", 3) { it.startsWith("Kaffe bryggt") }
        expect("banan", 1) { it == "Banan" }
        expect("kefir", 1) { it.startsWith("Kefir") }
        expect("mjölk 3", 3) { it.startsWith("Mjölk") && "3%" in it }
        expect("oat drink", 3) { it.startsWith("Havredryck") }
    }
}
