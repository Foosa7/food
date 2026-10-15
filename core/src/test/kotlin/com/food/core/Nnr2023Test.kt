package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Nnr2023Test {
    private val man30 = Profile(Sex.MALE, 30, 74.8, 180.0, 1.6)
    private val woman30 = Profile(Sex.FEMALE, 30, 64.1, 167.0, 1.6)

    @Test fun `resting energy matches NNR's reference values (Table 8)`() {
        // Reference adults 25–50 y: BEE 7.1 MJ/d (men, 74.8 kg) and 5.7 MJ/d (women, 64.1 kg).
        assertEquals(7.1, Nnr2023.bee(man30), 0.06)
        assertEquals(5.7, Nnr2023.bee(woman30), 0.06)
        // ... and PAL 1.6 gives the "average" column: 11.3 and 9.0 MJ/d.
        assertEquals(11.3, Nnr2023.energyMj(man30), 0.1)
        assertEquals(9.0, Nnr2023.energyMj(woman30), 0.1)
    }

    @Test fun `age picks NNR's band and equation`() {
        assertEquals(0, Nnr2023.band(18)); assertEquals(0, Nnr2023.band(24))
        assertEquals(1, Nnr2023.band(25)); assertEquals(1, Nnr2023.band(50))
        assertEquals(2, Nnr2023.band(51)); assertEquals(3, Nnr2023.band(71))
        val young = man30.copy(age = 20)
        assertEquals(0.0600 * 74.8 + 1.31 * 1.8 + 0.473, Nnr2023.bee(young), 1e-9)
    }

    @Test fun `targets for a man of 30`() {
        val t = Nnr2023.targets(man30)
        assertEquals(800.0, t.getValue("VITA_RE").target!!, 0.0)
        assertEquals(630.0, t.getValue("VITA_RE").low!!, 0.0)
        assertEquals(10.0, t.getValue("VITD").target!!, 0.0)
        assertEquals(100.0, t.getValue("VITD").upper!!, 0.0)
        assertEquals(110.0, t.getValue("VITC").target!!, 0.0)
        assertEquals(1.8, t.getValue("VITB6").target!!, 0.0)
        assertEquals(9.0, t.getValue("FE").target!!, 0.0)
        assertEquals(12.7, t.getValue("ZN").target!!, 0.0)
        assertEquals(90.0, t.getValue("SE").target!!, 0.0)
        assertEquals(350.0, t.getValue("MG").target!!, 0.0)
        assertNull(t.getValue("MG").upper) // the 250 mg UL is for supplements
        assertEquals(35.0, t.getValue("FIBT").low!!, 0.0)
        assertEquals(2300.0, t.getValue("NA").upper!!, 0.0)
        assertEquals(TargetKind.AI, t.getValue("VITB12").kind)
    }

    @Test fun `targets for women by age`() {
        val t = Nnr2023.targets(woman30)
        assertEquals(15.0, t.getValue("FE").target!!, 0.0)
        assertEquals(95.0, t.getValue("VITC").target!!, 0.0)
        assertEquals(25.0, t.getValue("FIBT").low!!, 0.0)
        assertEquals(8.0, Nnr2023.targets(woman30.copy(age = 60)).getValue("FE").target!!, 0.0)
        val old = Nnr2023.targets(woman30.copy(age = 80))
        assertEquals(7.0, old.getValue("FE").target!!, 0.0)
        assertEquals(20.0, old.getValue("VITD").target!!, 0.0) // 75 and over
        assertEquals(10.0, Nnr2023.targets(woman30.copy(age = 72)).getValue("VITD").target!!, 0.0)
    }

    @Test fun `thiamin and niacin scale with energy`() {
        val t = Nnr2023.targets(man30, energyKcal = 10_000 / Nnr2023.KJ_PER_KCAL) // 10 MJ
        assertEquals(1.0, t.getValue("THIA").target!!, 1e-9)
        assertEquals(16.0, t.getValue("NIAEQ").target!!, 1e-9)
    }

    @Test fun `RI status - low under AR, below target under RI, over above UL`() {
        val c = Nnr2023.targets(man30).getValue("VITC")
        assertEquals(Status.LOW, Assess.of(c, 80.0, 0.0).status)
        assertEquals(Status.BELOW_TARGET, Assess.of(c, 100.0, 0.0).status)
        assertEquals(Status.OK, Assess.of(c, 120.0, 0.0).status)
        assertEquals(50.0, Assess.of(c, 55.0, 0.0).percentOfTarget!!, 1e-9)
        val d = Nnr2023.targets(man30).getValue("VITD")
        assertEquals(Status.OVER, Assess.of(d, 120.0, 0.0).status)
    }

    @Test fun `limits only go over`() {
        val na = Nnr2023.targets(man30).getValue("NA")
        assertEquals(Status.OK, Assess.of(na, 1800.0, 0.0).status)
        assertEquals(Status.OVER, Assess.of(na, 3000.0, 0.0).status)
    }

    @Test fun `energy percent ranges use the day's energy`() {
        val t = Nnr2023.targets(man30)
        // 10 000 kJ in the day; 88.2 g protein × 17 kJ/g = 15 E%.
        val ok = Assess.of(t.getValue("PROT"), 88.2, 10_000.0)
        assertEquals(15.0, ok.energyPercent!!, 0.01)
        assertEquals(Status.OK, ok.status)
        assertEquals(Status.LOW, Assess.of(t.getValue("PROT"), 50.0, 10_000.0).status)
        assertEquals(Status.OVER, Assess.of(t.getValue("FASAT"), 40.0, 10_000.0).status) // 14.8 E%
        assertEquals(Status.NONE, Assess.of(t.getValue("PROT"), 50.0, 0.0).status)
    }

    @Test fun `carbs count energy from fibre`() {
        val carbs = Nnr2023.targets(man30).getValue("CHO_AVAIL")
        // 250 g × 17 + 30 g fibre × 8 = 4490 kJ of 10 000 = 44.9 E%: just under the range.
        val a = Assess.of(carbs, 250.0, 10_000.0, fibreG = 30.0)
        assertEquals(44.9, a.energyPercent!!, 0.001)
        assertEquals(Status.LOW, a.status)
    }

    @Test fun `energy within 10 percent of target is fine`() {
        val e = Target("ENERC_KCAL", TargetKind.ENERGY, target = 2500.0)
        assertEquals(Status.OK, Assess.of(e, 2400.0, 0.0).status)
        assertEquals(Status.BELOW_TARGET, Assess.of(e, 2000.0, 0.0).status)
        assertEquals(Status.OVER, Assess.of(e, 2900.0, 0.0).status)
    }

    @Test fun `steps give a rough PAL`() {
        assertEquals(1.4, Assess.palFromSteps(3000.0), 0.0)
        assertEquals(1.6, Assess.palFromSteps(9000.0), 0.0)
        assertEquals(1.8, Assess.palFromSteps(15000.0), 0.0)
    }
}
