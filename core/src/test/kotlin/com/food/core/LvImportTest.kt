package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses Livsmedelsverket's real response for #142 "Kefir fett 3% berikad". */
class LvImportTest {

    private fun resource(name: String) = javaClass.getResource("/lv/$name")!!.readText()

    private val sv = Lv.parseList("""{"_meta":{"totalRecords":1,"offset":0,"limit":1,"count":1},"livsmedel":${resource("list-sv.json")}}""").livsmedel.single()
    private val en = Lv.parseList("""{"_meta":{"totalRecords":1,"offset":0,"limit":1,"count":1},"livsmedel":${resource("list-en.json")}}""").livsmedel.single()
    private val nutrients = Lv.parseNutrients(resource("142-naringsvarden.json"))
    private val classes = Lv.parseClasses(resource("142-klassificeringar.json"))
    private val kefir = LvImport.food(sv, en, nutrients, classes)

    private fun value(code: String) = kefir.nutrients.single { it.nutrient == code }

    @Test fun `kefir matches the published values`() {
        assertEquals(60.0, value("ENERC_KCAL").valuePer100g, 0.0)
        assertEquals(3.4, value("PROT").valuePer100g, 0.0)
        assertEquals(3.0, value("FAT").valuePer100g, 0.0)
        assertEquals(4.8, value("CHO_AVAIL").valuePer100g, 0.0)
    }

    @Test fun `kJ and kcal share ENERC but become two nutrients`() {
        assertEquals(2, nutrients.count { it.euroFIRkod == "ENERC" })
        assertEquals(60.0, value("ENERC_KCAL").valuePer100g, 0.0)
        assertEquals(251.0, value("ENERC_KJ").valuePer100g, 0.0)
    }

    @Test fun `Livsmedelsverket carbs are available carbs, never by-difference`() {
        assertEquals(4.8, value("CHO_AVAIL").valuePer100g, 0.0)
        assertTrue(kefir.nutrients.none { it.nutrient == "CHO_DIFF" })
    }

    @Test fun `logical zero is kept apart from a measured zero`() {
        val fibre = value("FIBT")
        assertEquals(0.0, fibre.valuePer100g, 0.0)
        assertTrue(fibre.logicalZero)
        assertEquals("LZ", fibre.valueType)

        val iron = value("FE") // 0.0 too, but a best estimate
        assertEquals(0.0, iron.valuePer100g, 0.0)
        assertFalse(iron.logicalZero)
    }

    @Test fun `equivalent units map to their own codes`() {
        assertEquals(26.2, value("VITA_RE").valuePer100g, 0.0)   // "RE/µg"
        assertEquals(0.86, value("NIAEQ").valuePer100g, 0.0)     // "NE/mg"
        assertEquals(0.06, value("NIA").valuePer100g, 0.0)
        assertEquals(Nutrients.byCode["VITA_RE"], Nutrients.forLv("VITA", "RE/μg")) // Greek mu
    }

    @Test fun `waste is a food property, not a nutrient`() {
        assertEquals(0.0, kefir.food.wastePct!!, 0.0)
        assertTrue(kefir.nutrients.none { it.nutrient == Nutrients.LV_WASTE })
    }

    @Test fun `every value is per 100 g and from Livsmedelsverket`() {
        assertTrue(nutrients.all { it.viktGram == 100.0 })
        assertTrue(kefir.nutrients.all { it.source == Source.LV && it.foodId == "lv:142" })
        assertEquals(58, kefir.nutrients.size) // 59 entries minus WASTE
        assertTrue(kefir.unknown.isEmpty())
    }

    @Test fun `food fields`() {
        val f = kefir.food
        assertEquals("lv:142", f.id)
        assertEquals("Kefir fett 3% berikad", f.nameSv)
        assertEquals("Fermented milk 3% fat e.g. Kefir", f.nameEn)
        assertEquals("Naturell fil yoghurt", f.group)
        assertEquals("Analyserat", f.lvType)
        assertFalse(f.removed)
    }

    @Test fun `unknown nutrients are reported, not imported`() {
        val odd = nutrients.first().copy(euroFIRkod = "VITK", enhet = "µg", namn = "Vitamin K")
        val r = LvImport.food(sv, en, nutrients + odd, classes)
        assertEquals(listOf(odd), r.unknown)
        assertEquals(58, r.nutrients.size)
    }

    @Test fun `a missing value stays missing`() {
        val noIodine = nutrients.map { if (it.euroFIRkod == "ID") it.copy(varde = null) else it }
        val r = LvImport.food(sv, en, noIodine, classes)
        assertNull(r.nutrients.firstOrNull { it.nutrient == "ID" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `two values for one nutrient is an error`() {
        LvImport.food(sv, en, nutrients + nutrients.first { it.euroFIRkod == "PROT" }, classes)
    }

    @Test fun `catalog codes and Livsmedelsverket keys are unique`() {
        assertEquals(Nutrients.ALL.size, Nutrients.ALL.map { it.code }.toSet().size)
        val keys = Nutrients.ALL.filter { it.lvCode != null }.map { Nutrients.lvKey(it.lvCode!!, it.lvUnit!!) }
        assertEquals(keys.size, keys.toSet().size)
    }
}
