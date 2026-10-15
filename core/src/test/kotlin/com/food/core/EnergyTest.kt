package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyTest {
    private val p = Profile(Sex.MALE, 30, 74.8, 180.0, 1.6)
    private val beeKcal = Nnr2023.bee(p) * 1000 / Nnr2023.KJ_PER_KCAL

    @Test fun `watch calories win when there are enough days`() {
        val t = Energy.target(p, auto = true, days = 7, avgSteps = 9000.0, avgBurnedKcal = 2900.0)
        assertEquals(2900.0, t.kcal, 0.0)
        assertTrue(t.how.contains("calories burned"))
    }

    @Test fun `steps alone give a PAL`() {
        val t = Energy.target(p, auto = true, days = 7, avgSteps = 11000.0, avgBurnedKcal = null)
        assertEquals(1.7, t.pal, 0.0)
        assertEquals(beeKcal * 1.7, t.kcal, 1e-9)
    }

    @Test fun `too few days falls back to the chosen PAL`() {
        val t = Energy.target(p, auto = true, days = 2, avgSteps = 11000.0, avgBurnedKcal = 3000.0)
        assertEquals(1.6, t.pal, 0.0)
        assertTrue(t.how.contains("not enough watch data"))
    }

    @Test fun `manual ignores the watch`() {
        val t = Energy.target(p.copy(pal = 1.8), auto = false, days = 7, avgSteps = 3000.0, avgBurnedKcal = 2000.0)
        assertEquals(beeKcal * 1.8, t.kcal, 1e-9)
    }

    @Test fun `edits replace only what was edited`() {
        val nnr = Nnr2023.targets(p)
        val t = Targets.withEdits(nnr, mapOf("VITD" to TargetEdit(null, 20.0, null), "CAFF" to TargetEdit(null, null, 400.0)))
        assertEquals(20.0, t.getValue("VITD").target!!, 0.0)
        assertEquals(7.5, t.getValue("VITD").low!!, 0.0)
        assertEquals(100.0, t.getValue("VITD").upper!!, 0.0)
        assertEquals(TargetKind.LIMIT, t.getValue("CAFF").kind)
        assertEquals(nnr.getValue("VITC"), t.getValue("VITC"))
    }
}
