package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class NutritionTest {

    @Test fun `values scale from per 100 g`() {
        assertEquals(150.0, scale(60.0, 250.0), 1e-9)  // a 250 g glass of kefir, 60 kcal/100 g
        assertEquals(0.0, scale(60.0, 0.0), 1e-9)
        assertEquals(6.8, scale(3.4, 200.0), 1e-9)
    }

    @Test fun `1 ml counts as 1 g`() {
        assertEquals(250.0, Amounts.grams(250.0, AmountUnit.ML), 1e-9)
        assertEquals(35.0, Amounts.grams(35.0, AmountUnit.G), 1e-9)
        assertEquals(AmountUnit.ML, AmountUnit.of("ml"))
    }

    private val values = mapOf(
        "lv:142" to mapOf("ENERC_KCAL" to 60.0, "PROT" to 3.4, "ID" to 10.5),
        "lv:1" to mapOf("ENERC_KCAL" to 900.0, "PROT" to 0.0), // no iodine value
    )

    @Test fun `totals add up scaled portions`() {
        val t = Totals.of(listOf(Portion("lv:142", 250.0), Portion("lv:1", 10.0)), values)
        assertEquals(150.0 + 90.0, t.getValue("ENERC_KCAL").value, 1e-9)
        assertEquals(8.5, t.getValue("PROT").value, 1e-9)
        assertTrue(t.getValue("PROT").complete)
    }

    @Test fun `a missing value makes the total a lower bound, not a zero`() {
        val iodine = Totals.of(listOf(Portion("lv:142", 250.0), Portion("lv:1", 10.0)), values).getValue("ID")
        assertEquals(26.25, iodine.value, 1e-9)
        assertEquals(1, iodine.missing)
        assertFalse(iodine.complete)
    }

    @Test fun `a food gone from the database is missing everywhere`() {
        val t = Totals.of(listOf(Portion("lv:404", 100.0)), values, listOf("ENERC_KCAL"))
        assertEquals(Total("ENERC_KCAL", 0.0, 1, 1), t.getValue("ENERC_KCAL"))
    }

    @Test fun `no portions is an empty, complete total`() {
        assertEquals(Total("PROT", 0.0, 0, 0), Totals.of(emptyList(), values, listOf("PROT")).getValue("PROT"))
    }

    @Test fun `meal guessed from the clock`() {
        assertEquals(Meal.BREAKFAST, Meal.at(LocalTime.of(7, 30)))
        assertEquals(Meal.LUNCH, Meal.at(LocalTime.of(12, 0)))
        assertEquals(Meal.SNACK, Meal.at(LocalTime.of(15, 30)))
        assertEquals(Meal.DINNER, Meal.at(LocalTime.of(18, 45)))
        assertEquals(Meal.SNACK, Meal.at(LocalTime.of(23, 10)))
        assertEquals(Meal.SNACK, Meal.of("nonsense"))
    }
}
