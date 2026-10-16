package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelFoodTest {
    @Test fun `energy and sodium follow from the label`() {
        val c = LabelFood.complete(mapOf("ENERC_KCAL" to 300.0, "NACL" to 1.5))
        assertEquals(1255.2, c.getValue("ENERC_KJ"), 1e-9)
        assertEquals(600.0, c.getValue("NA"), 1e-9) // 1.5 g salt = 0.6 g sodium
        val fromKj = LabelFood.complete(mapOf("ENERC_KJ" to 1000.0))
        assertEquals(239.0, fromKj.getValue("ENERC_KCAL"), 0.1)
    }

    @Test fun `typed values are never overwritten`() {
        val c = LabelFood.complete(mapOf("ENERC_KCAL" to 300.0, "ENERC_KJ" to 1260.0))
        assertEquals(1260.0, c.getValue("ENERC_KJ"), 0.0)
    }

    @Test fun `label wins, the base food fills the gaps`() {
        val base = listOf(
            FoodNutrient("lv:79", "FAT", 26.0, Source.LV),
            FoodNutrient("lv:79", "CA", 800.0, Source.LV),
            FoodNutrient("lv:79", "VITB12", 1.5, Source.LV),
        )
        val rows = LabelFood.merge("my:1", mapOf("FAT" to 24.0, "PROT" to 21.0), base, "Ost hårdost fett 26%")
            .associateBy { it.nutrient }
        assertEquals(24.0, rows.getValue("FAT").valuePer100g, 0.0)
        assertEquals(Source.LABEL, rows.getValue("FAT").source)
        assertEquals(800.0, rows.getValue("CA").valuePer100g, 0.0)
        assertEquals(Source.LV, rows.getValue("CA").source)
        assertTrue(rows.getValue("CA").comment!!.contains("Ost hårdost fett 26%"))
        assertTrue(rows.values.all { it.foodId == "my:1" })
    }

    @Test fun `custom ids`() {
        assertEquals("my:7", FoodId.custom(7))
        assertEquals(7L, FoodId.customId("my:7"))
        assertEquals(null, FoodId.customId("lv:7"))
    }

    @Test fun `a supplement's unlisted nutrients are assumed zeros, so totals stay complete`() {
        // One capsule typed as 1 g: 150 mg magnesium, 10 mg zinc, 3.7 mg B6 per capsule.
        val rows = LabelFood.merge("my:3", mapOf("MG" to 15000.0, "ZN" to 1000.0, "VITB6" to 370.0), emptyList(), null, supplement = true)
        val by = rows.associateBy { it.nutrient }
        assertEquals(Nutrients.ALL.size, rows.size)
        assertEquals(15000.0, by.getValue("MG").valuePer100g, 1e-9)
        assertTrue(by.getValue("ENERC_KCAL").logicalZero)
        val day = Totals.of(listOf(Portion("my:3", 1.0)), mapOf("my:3" to rows.associate { it.nutrient to it.valuePer100g }))
        assertEquals(150.0, day.getValue("MG").value, 1e-9)
        assertTrue(day.getValue("ENERC_KCAL").complete)
        // A food that isn't a supplement still says unknown.
        assertEquals(3, LabelFood.merge("my:4", mapOf("MG" to 1.0, "ZN" to 1.0, "VITB6" to 1.0), emptyList(), null).size)
    }
}
