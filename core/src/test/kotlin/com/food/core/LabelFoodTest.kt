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
}
