package com.food.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DietDigestTest {

    private val profile = Profile(Sex.MALE, 34, 80.0, 180.0, 1.6)
    private val targets = Nnr2023.targets(profile)

    private fun day(date: String, vararg foods: Pair<DigestFood, Map<String, Double>>): DigestDay {
        val values = foods.associate { (f, v) -> f.name to v }
        val totals = Totals.of(foods.map { (f, _) -> Portion(f.name, f.grams) }, values)
        return DigestDay(LocalDate.parse(date), foods.map { it.first }, totals)
    }

    private val mackerel = DigestFood(Meal.LUNCH, "Makrill i tomatsås", 125.0, false) to
        mapOf("ENERC_KCAL" to 186.0, "ENERC_KJ" to 778.0, "PROT" to 11.7, "FAT" to 13.9, "F20:5" to 0.7, "F22:6" to 1.3, "VITD" to 5.0)
    private val kefir = DigestFood(Meal.BREAKFAST, "Kefir", 250.0, true) to
        mapOf("ENERC_KCAL" to 60.0, "ENERC_KJ" to 251.0, "PROT" to 3.4, "FAT" to 3.0, "F20:5" to 0.0, "F22:6" to 0.0)
    private val kimchiDash = DigestFood(Meal.DINNER, "Kimchi", 10.0, true) to
        mapOf("ENERC_KCAL" to 20.0, "ENERC_KJ" to 84.0, "PROT" to 1.5, "FAT" to 0.5, "F20:5" to 0.0, "F22:6" to 0.0)

    private fun info(p: Pair<DigestFood, Map<String, Double>>, source: String = "Livsmedelsverket") =
        p.first.foodId to DigestFoodInfo(p.first.name, source, p.second)

    private val input = DigestInput(
        today = LocalDate.parse("2026-10-16"),
        profile = profile,
        energyNote = null,
        targets = targets,
        days = listOf(
            day("2026-10-14", mackerel, kefir),
            day("2026-10-15", mackerel, kimchiDash),
            day("2026-10-06", kefir),
        ),
        foods = mapOf(info(mackerel), info(kefir), info(kimchiDash, "their own label food")),
        nnr = targets,
        goals = "Omega-3 from fish",
    )

    @Test fun `every target is listed with its numbers`() {
        val text = DietDigest.build(input)
        assertTrue(text, text.contains("- Protein: 10–20% of energy, which is 68.7–137 g a day. At least 66 g (0.83 g/kg) for your weight\n"))
        assertTrue(text.contains("- Saturated fat: under 10% of energy, which is under 31.6 g a day\n"))
        assertTrue(text.contains("- Fibre: at least 35 g"))
        assertTrue(text.contains("- Vitamin C: RI 110 mg a day, AR 90\n"))
        assertTrue(text.contains("- Calcium: RI 950 mg a day, AR 750, UL 2500\n"))
        assertTrue(text.contains("aged 34 (NNR age band 25–50), 80 kg, 180 cm"))
        assertTrue(text.contains("Energy need about 2792 kcal a day, activity level (PAL) 1.6."))
        assertTrue(text, text.contains("- Salt: under 5.75 g. Under 5.75 g salt, the same as 2.3 g sodium\n"))
        assertTrue(text, text.contains("which is 309–412 g a day, counting energy from fibre\n"))
        assertTrue(text.contains("Omega-3 from fish"))
    }

    @Test fun `weeks average over logged days and say how many`() {
        val text = DietDigest.build(input)
        assertTrue(text, text.contains("| Nutrient | W41 (1 d) | W42 so far (2 d) |"))
        // Week 42: two days of 125 g mackerel, one with kefir: (14.625 + 8.5 + 14.625 + 0.15) / 2.
        val protein = text.lines().first { it.startsWith("| Protein (g)") }
        assertTrue(protein, protein.contains("| 19 (25%E) OVER |"))
        assertFalse(text.contains("Energy (kJ)"))
    }

    @Test fun `missing values make a lower bound, not a zero`() {
        val text = DietDigest.build(input)
        val vitD = text.lines().first { it.startsWith("| Vitamin D (µg)") }
        assertTrue(vitD, vitD.contains("| ? | ≥6.25 LOW |"))
        // Nothing logged had a value for vitamin C: no row rather than a row of zeros.
        assertFalse(text.contains("| Vitamin C"))
    }

    @Test fun `unlogged days are named as unknown`() {
        val text = DietDigest.build(input)
        assertTrue(text.contains("unknown, not zero"))
        assertTrue(text.contains("Fri 16 Oct"))
        assertFalse(text.lines().first { it.startsWith("Not logged") }.contains("Thu 15 Oct"))
    }

    @Test fun `omega-3 and fermented servings get their own rows`() {
        val text = DietDigest.build(input)
        val epa = text.lines().first { it.startsWith("| EPA+DHA") }
        assertTrue(epa, epa.contains("| 0 | 2.5 |"))  // (2.5 + 2.5) / 2 days
        // The kefir counts; a 10 g dash of kimchi doesn't.
        assertTrue(text.contains("| Fermented servings, live cultures (whole week) | 1 | 1 |"))
        assertTrue(text.contains("Kefir 250 g [fermented]"))
    }

    @Test fun `old days fall out of the day-by-day part`() {
        val text = DietDigest.build(input)
        assertFalse(text.contains("## Mon 6 Oct"))
        assertTrue(text.contains("## Wed 14 Oct"))
    }

    @Test fun `no profile still makes a digest`() {
        val text = DietDigest.build(input.copy(profile = null, targets = null))
        assertTrue(text.contains("No profile set up yet"))
        assertTrue(text.contains("| Protein (g)"))
    }

    @Test fun `fermented foods by name`() {
        assertTrue(Fermented.byName("Filmjölk fett 3% berikad"))
        assertTrue(Fermented.byName("Jongga kimchi"))
        assertTrue(Fermented.byName("Surkål konserv. m. lag"))
        assertFalse(Fermented.byName("Korv isterband fermenterad kött 59%"))
        assertFalse(Fermented.byName("Smoothie m. yoghurt bär banan juice"))
        assertFalse(Fermented.byName("Makrill filé i tomatsås konserv."))
    }

    @Test fun `a reply in a code fence still parses`() {
        val reply = """
            Here you go:
            ```json
            {"progress": "Protein is up.", "good": ["Fish twice a week"], "improve": [], "suggestions": ["Add kimchi"], "notes": "n", "extra": 1}
            ```
        """.trimIndent()
        val a = Analyses.parse(reply)
        assertNotNull(a)
        assertEquals("Protein is up.", a!!.progress)
        assertEquals(listOf("Add kimchi"), a.suggestions)
        assertNull(Analyses.parse("Sorry, no."))
        assertNull(Analyses.parse("{}"))
    }

    @Test fun `saved analyses read back, skipping a broken line`() {
        val a = Analysis(progress = "p", at = "2026-10-16T10:00", model = "kimi-k3")
        val text = Analyses.line(a) + "\n{broken\n" + Analyses.line(a.copy(progress = "q")) + "\n"
        assertEquals(listOf("p", "q"), Analyses.lines(text).map { it.progress })
    }

    @Test fun `each portion says what it gave`() {
        val text = DietDigest.build(input)
        assertTrue(text, text.contains("- Lunch · Makrill i tomatsås 125 g: Energy 233 kcal · Protein 14.6 g · Fat, total 17.4 g"))
        assertTrue(text.contains("Vitamin D 6.25 µg"))
        assertTrue(text.contains("- Breakfast · Kefir 250 g [fermented]: Energy 150 kcal"))
    }

    @Test fun `every food is listed once with its values per 100 g`() {
        val text = DietDigest.build(input)
        val i = text.indexOf("## Makrill i tomatsås\n")
        assertTrue(text, i > 0)
        assertTrue(text.contains("Livsmedelsverket · logged 2 times, 250 g in all\nEnergy 186 kcal · Protein 11.7 g"))
        assertTrue(text.contains("their own label food · logged 1 time, 10 g in all · fermented, live cultures"))
    }

    @Test fun `each nutrient names its sources with their share`() {
        val text = DietDigest.build(input)
        assertTrue(text.contains("# Where each nutrient comes from: Last 14 days (3 logged)"))
        val recent = text.substringAfter("# Where each nutrient comes from: Last 14 days (3 logged)").substringBefore("# Where")
        // Protein per day over the three days: mackerel 29.25 / 3, kefir 17 / 3, kimchi 0.15 / 3.
        val protein = recent.lines().first { it.startsWith("- Protein:") }
        assertTrue(protein, protein.startsWith("- Protein: 15.5 g a day; target 10–20% of energy. Makrill i tomatsås 9.75 (63%) · Kefir 5.67 (37%) · Kimchi 0.05 (0%)"))
        assertTrue(recent, recent.contains("- EPA+DHA: 1.67 g a day. Makrill i tomatsås 1.67 (100%)"))
        // Every logged day is in the last 14, so the whole-period section would only repeat it.
        assertFalse(text.contains("# Where each nutrient comes from: All"))
        val older = DietDigest.build(input.copy(days = input.days + day("2026-09-20", kefir)))
        assertTrue(older.contains("# Where each nutrient comes from: All 4 logged days in these 12 weeks"))
    }

    @Test fun `an edited target shows NNR's own value too`() {
        val edited = Targets.withEdits(targets, mapOf("VITD" to TargetEdit(null, 20.0, null)))
        val text = DietDigest.build(input.copy(targets = edited))
        assertTrue(text, text.contains("- Vitamin D: RI 20 µg a day, AR 7.5, UL 100. 20 µg/d with little or no sun exposure. They set this themselves; NNR's own value is RI 10 µg a day, AR 7.5, UL 100."))
    }

    @Test fun `a supplement says so, with the pieces taken`() {
        val capsule = DigestFood(Meal.SNACK, "Myprotein Zinc & Magnesium", 1.0, false, "my:3", amount = "1 st", supplement = true) to
            mapOf("MG" to 15000.0, "ZN" to 1000.0, "VITB6" to 370.0, "ENERC_KCAL" to 0.0)
        val text = DietDigest.build(input.copy(days = input.days + day("2026-10-16", capsule), foods = input.foods + info(capsule)))
        assertTrue(text, text.contains("- Snack · Myprotein Zinc & Magnesium 1 st (1 g) [supplement]: Energy 0 kcal · Vitamin B6 3.7 mg · Magnesium 150 mg · Zinc 10 mg"))
        assertTrue(text.contains("logged 1 time, 1 g in all · a supplement"))
    }
}
