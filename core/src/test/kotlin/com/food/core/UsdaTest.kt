package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsdaTest {

    // SR Legacy 170392, "Cabbage, kimchi", as FoodData Central has it.
    private val kimchi = Usda.food(
        FdcFood(170392, "sr_legacy_food", "Cabbage, kimchi", "Vegetables and Vegetable Products", "2019-04-01"),
        listOf(
            FdcValue("208", 15.0, "NC"), FdcValue("268", 65.0, "NC"), FdcValue("203", 1.1, "T"), FdcValue("204", 0.5, "T"),
            FdcValue("205", 2.4, "NC"), FdcValue("291", 1.6, "T"), FdcValue("269", 1.06, "BFSN"),
            FdcValue("319", 0.0, "T"), FdcValue("321", 55.0, "T"), FdcValue("322", 1.0, "BFSN"), FdcValue("334", 0.0, "BFSN"),
            FdcValue("307", 498.0, "T"), FdcValue("401", 0.0, "T"), FdcValue("417", 52.0, "BFSN"),
            FdcValue("607", 0.0), FdcValue("608", 0.0), FdcValue("609", 0.0), FdcValue("610", 0.0),
            FdcValue("406", 1.1, "T"), FdcValue("328", 0.0, "Z"), FdcValue("430", 43.6, "BFSN"),
        ),
    )
    private fun v(code: String) = kimchi.nutrients.firstOrNull { it.nutrient == code }

    @Test fun `ids, names and where it came from`() {
        assertEquals("usda:170392", kimchi.food.id)
        assertEquals(Source.USDA, kimchi.food.source)
        assertEquals("Cabbage, kimchi", kimchi.food.nameSv)
        assertEquals("USDA FoodData Central, SR Legacy", kimchi.food.note)
        assertEquals(170392L, v("PROT")!!.usdaFdcId)
        assertEquals("SR Legacy", v("PROT")!!.origin)
        assertEquals("T", v("PROT")!!.valueType)
    }

    @Test fun `available carbs are by difference minus fibre`() {
        assertEquals(0.8, v("CHO_AVAIL")!!.valuePer100g, 1e-9)
        assertEquals(1.6, v("FIBT")!!.valuePer100g, 1e-9)
    }

    @Test fun `vitamin A becomes retinol equivalents, not RAE`() {
        assertEquals(55.0 / 6 + 1.0 / 12, v("VITA_RE")!!.valuePer100g, 1e-9)
        assertEquals(55.0, v("CARTBTOT")!!.valuePer100g, 1e-9)
    }

    @Test fun `salt from sodium, short fatty acids summed, energy in both units`() {
        assertEquals(1.245, v("NACL")!!.valuePer100g, 1e-9)
        assertEquals(0.0, v("F4-10:0")!!.valuePer100g, 1e-9)
        assertEquals(65.0, v("ENERC_KJ")!!.valuePer100g, 1e-9)
        assertEquals(15.0, v("ENERC_KCAL")!!.valuePer100g, 1e-9)
    }

    @Test fun `nutrients the catalog doesn't have aren't invented, and unknown ones stay unknown`() {
        assertTrue(kimchi.nutrients.all { it.nutrient in Nutrients.byCode })
        assertNull(v("NIAEQ")) // no tryptophan value
        assertNull(v("ID"))
    }

    @Test fun `Foundation's other numbers are used when the usual one is missing`() {
        val f = Usda.food(
            FdcFood(1, "foundation_food", "Fish, salmon, Atlantic, farm raised, raw", "Finfish and Shellfish Products", null),
            listOf(FdcValue("958", 199.0), FdcValue("675", 0.9), FdcValue("851", 0.2), FdcValue("324", 400.0), FdcValue("205", 1.0)),
        )
        val by = f.nutrients.associateBy { it.nutrient }
        assertEquals(199.0, by.getValue("ENERC_KCAL").valuePer100g, 1e-9)
        assertEquals("USDA nutrient 958", by.getValue("ENERC_KCAL").method)
        assertEquals(199.0 * 4.184, by.getValue("ENERC_KJ").valuePer100g, 1e-9)
        assertEquals(0.9, by.getValue("F18:2").valuePer100g, 1e-9)
        assertEquals(10.0, by.getValue("VITD").valuePer100g, 1e-9)
        // 1 g carbs and no fibre value: available carbs can't be known.
        assertFalse("CHO_AVAIL" in by)
    }

    @Test fun `csv with quotes, commas and empty fields`() {
        val rows = Csv.records(sequenceOf("\"id\",\"description\",\"x\"", "\"1\",\"Cheese, \"\"blue\"\"\",\"\"")).toList()
        assertEquals(listOf(mapOf("id" to "1", "description" to "Cheese, \"blue\"", "x" to "")), rows)
    }

    @Test fun `English names count as fermented too`() {
        assertTrue(Fermented.byName("Cabbage, kimchi"))
        assertTrue(Fermented.byName("Yogurt, Greek, plain, nonfat"))
        assertFalse(Fermented.byName("Yogurt, frozen, flavors other than chocolate"))
    }
}
