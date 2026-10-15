package com.food.core

import kotlin.math.max

enum class Sex { FEMALE, MALE }

/** What the targets are computed from. Every field is editable in the app. */
data class Profile(
    val sex: Sex,
    val age: Int,
    val weightKg: Double,
    val heightCm: Double,
    /** Physical activity level: total energy expenditure / resting. NNR uses 1.4, 1.6, 1.8. */
    val pal: Double,
)

enum class TargetKind {
    /** Energy, in kcal. */
    ENERGY,
    /** A recommended intake with an average requirement below it. */
    RI,
    /** An adequate intake (with a provisional AR), where an RI couldn't be set. */
    AI,
    /** A lower bound only, e.g. fibre "at least 25 g". */
    MIN,
    /** Keep below, e.g. sodium 2.3 g. */
    LIMIT,
    /** A range in percent of the day's energy (E%), e.g. protein 10–20 E%. */
    ENERGY_PERCENT,
}

/**
 * One nutrient's target. Amounts are in the nutrient's catalog unit. For [TargetKind.ENERGY_PERCENT]
 * [low] and [upper] are E%, and the amount is converted using the day's energy at [kjPerGram].
 *
 * - [low]: below this is "low" (the AR, or the range's bottom).
 * - [target]: the RI/AI, or the energy target.
 * - [upper]: above this is "over" (the UL, a limit, or the range's top).
 */
data class Target(
    val nutrient: String,
    val kind: TargetKind,
    val low: Double? = null,
    val target: Double? = null,
    val upper: Double? = null,
    val kjPerGram: Double? = null,
    val note: String? = null,
)

enum class Status { LOW, BELOW_TARGET, OK, OVER, NONE }

/**
 * Nordic Nutrition Recommendations 2023 for adults (18+), as published by the Nordic Council of
 * Ministers (Blomhoff et al., 2023, Nord 2023:003): Tables 8 and 11–21, Box 2, Appendix 4.
 *
 * Age bands are NNR's: 18–24, 25–50, 51–70, over 70. Pregnancy and lactation aren't covered;
 * every target can be edited instead.
 */
object Nnr2023 {

    fun band(age: Int): Int = when {
        age <= 24 -> 0
        age <= 50 -> 1
        age <= 70 -> 2
        else -> 3
    }

    /** Resting energy expenditure in MJ/d: Henry (2005) as NNR applies it (Appendix 4, Table 4). */
    fun bee(p: Profile): Double {
        val w = p.weightKg
        val h = p.heightCm / 100
        return when (p.sex) {
            Sex.FEMALE -> when (band(p.age)) {
                0 -> 0.0433 * w + 2.57 * h - 1.180
                1 -> 0.0342 * w + 2.10 * h - 0.0486
                else -> 0.0356 * w + 1.76 * h + 0.0448
            }
            Sex.MALE -> when (band(p.age)) {
                0 -> 0.0600 * w + 1.31 * h + 0.473
                1 -> 0.0476 * w + 2.26 * h - 0.574
                else -> 0.0478 * w + 2.26 * h - 1.070
            }
        }
    }

    /** Estimated energy need, MJ/d. */
    fun energyMj(p: Profile): Double = bee(p) * p.pal

    const val KJ_PER_KCAL = 4.184

    /** Energy conversion factors NNR uses for E% (kJ/g). */
    object KjPerGram {
        const val PROTEIN = 17.0
        const val FAT = 37.0
        const val CARBS = 17.0
        const val FIBRE = 8.0
        const val ALCOHOL = 29.0
    }

    /** Per band 18–24, 25–50, 51–70, >70: female values, then male. */
    private class ByBand(val female: List<Double>, val male: List<Double>) {
        fun of(p: Profile) = (if (p.sex == Sex.FEMALE) female else male)[band(p.age)]
    }

    private fun b(f: Double, m: Double) = ByBand(List(4) { f }, List(4) { m })
    private fun b(f: List<Double>, m: List<Double>) = ByBand(f, m)

    // Table 12 (RI) and Table 17 (AR), vitamins.
    private val VITA_RI = b(listOf(700.0, 700.0, 700.0, 650.0), listOf(800.0, 800.0, 800.0, 750.0))
    private val VITA_AR = b(listOf(540.0, 540.0, 530.0, 510.0), listOf(630.0, 630.0, 610.0, 590.0))
    private const val THIA_RI_PER_MJ = 0.1
    private const val THIA_AR_PER_MJ = 0.07
    private val RIBF_RI = b(1.6, 1.6)
    private val RIBF_AR = b(1.3, 1.3)
    private const val NIA_RI_PER_MJ = 1.6
    private const val NIA_AR_PER_MJ = 1.3
    private val B6_RI = b(listOf(1.6, 1.6, 1.6, 1.6), listOf(1.8, 1.8, 1.8, 1.7))
    private val B6_AR = b(1.3, 1.5)
    private val FOL_RI = b(330.0, 330.0)
    private val FOL_AR = b(250.0, 250.0)
    private val VITC_RI = b(95.0, 110.0)
    private val VITC_AR = b(75.0, 90.0)

    // Table 13 (AI) and Table 18 (provisional AR), vitamins.
    private val VITE_AI = b(listOf(10.0, 10.0, 9.0, 9.0), listOf(11.0, 11.0, 11.0, 11.0))
    private val VITE_AR = b(8.0, 9.0)
    private val B12_AI = b(4.0, 4.0)
    private val B12_AR = b(3.2, 3.2)

    // Table 14 (RI) and Table 19 (AR), minerals. Iron and zinc assume ~600 mg/d phytate.
    private val CA_RI = b(listOf(1000.0, 950.0, 950.0, 950.0), listOf(1000.0, 950.0, 950.0, 950.0))
    private val CA_AR = b(listOf(870.0, 750.0, 750.0, 750.0), listOf(870.0, 750.0, 750.0, 750.0))
    private val FE_RI = b(listOf(15.0, 15.0, 8.0, 7.0), listOf(9.0, 9.0, 9.0, 9.0))
    private val FE_AR = b(listOf(9.0, 9.0, 6.0, 6.0), listOf(7.0, 7.0, 7.0, 7.0))
    private val ZN_RI = b(listOf(9.7, 9.7, 9.5, 9.3), listOf(12.7, 12.7, 12.4, 12.1))
    private val ZN_AR = b(listOf(8.1, 8.1, 7.9, 7.7), listOf(10.6, 10.6, 10.4, 10.1))

    // Table 15 (AI) and Table 20 (provisional AR), minerals.
    private val P_AI = b(listOf(550.0, 520.0, 520.0, 520.0), listOf(550.0, 520.0, 520.0, 520.0))
    private val P_AR = b(listOf(440.0, 420.0, 420.0, 420.0), listOf(440.0, 420.0, 420.0, 420.0))
    private val K_AI = b(3500.0, 3500.0)
    private val K_AR = b(2800.0, 2800.0)
    private val MG_AI = b(300.0, 350.0)
    private val MG_AR = b(240.0, 280.0)
    private val ID_AI = b(150.0, 150.0)
    private val ID_AR = b(120.0, 120.0)
    private val SE_AI = b(listOf(75.0, 75.0, 75.0, 75.0), listOf(90.0, 90.0, 90.0, 85.0))
    private val SE_AR = b(60.0, 70.0)

    fun targets(p: Profile, energyKcal: Double = energyMj(p) * 1000 / KJ_PER_KCAL): Map<String, Target> {
        val mj = energyKcal * KJ_PER_KCAL / 1000
        val out = linkedMapOf<String, Target>()
        fun put(t: Target) { out[t.nutrient] = t }
        fun ri(code: String, ri: ByBand, ar: ByBand, ul: Double? = null, note: String? = null) =
            put(Target(code, TargetKind.RI, ar.of(p), ri.of(p), ul, note = note))
        fun ai(code: String, ai: ByBand, ar: ByBand, ul: Double? = null, note: String? = null) =
            put(Target(code, TargetKind.AI, ar.of(p), ai.of(p), ul, note = note))

        put(Target("ENERC_KCAL", TargetKind.ENERGY, target = energyKcal))

        // Box 2 (adults) and Table 11.
        put(Target("PROT", TargetKind.ENERGY_PERCENT, 10.0, null, 20.0, KjPerGram.PROTEIN,
            note = "10–20% of energy. At least ${fmt(0.83 * p.weightKg)} g (0.83 g/kg) for your weight."))
        put(Target("FAT", TargetKind.ENERGY_PERCENT, 25.0, null, 40.0, KjPerGram.FAT, note = "25–40% of energy."))
        put(Target("CHO_AVAIL", TargetKind.ENERGY_PERCENT, 45.0, null, 60.0, KjPerGram.CARBS,
            note = "45–60% of energy, counting energy from fibre."))
        put(Target("FIBT", TargetKind.MIN, low = if (p.sex == Sex.FEMALE) 25.0 else 35.0,
            target = if (p.sex == Sex.FEMALE) 25.0 else 35.0, note = "At least 25–35 g a day (3 g/MJ)."))
        put(Target("FASAT", TargetKind.ENERGY_PERCENT, null, null, 10.0, KjPerGram.FAT, note = "Under 10% of energy."))
        put(Target("FAMS", TargetKind.ENERGY_PERCENT, 10.0, null, 20.0, KjPerGram.FAT, note = "10–20% of energy."))
        put(Target("FAPU", TargetKind.ENERGY_PERCENT, 5.0, null, 10.0, KjPerGram.FAT, note = "5–10% of energy."))
        put(Target("SUGFR", TargetKind.ENERGY_PERCENT, null, null, 10.0, KjPerGram.CARBS,
            note = "Added and free sugars: under 10% of energy."))
        put(Target("ALC", TargetKind.LIMIT, upper = 0.0, note = "NNR 2023 recommends avoiding alcohol."))

        // Vitamins.
        ri("VITA_RE", VITA_RI, VITA_AR, note = "Upper limit 3000 µg applies to retinol, shown on its own row.")
        put(Target("RETOL", TargetKind.LIMIT, upper = 3000.0, note = "Upper limit for preformed vitamin A (retinol)."))
        put(Target("VITD", TargetKind.RI, 7.5, if (p.age >= 75) 20.0 else 10.0, 100.0,
            note = "20 µg/d with little or no sun exposure."))
        put(Target("VITE", TargetKind.AI, VITE_AR.of(p), VITE_AI.of(p), 300.0))
        put(Target("THIA", TargetKind.RI, THIA_AR_PER_MJ * mj, THIA_RI_PER_MJ * mj, note = "0.1 mg per MJ of energy."))
        ri("RIBF", RIBF_RI, RIBF_AR)
        put(Target("NIAEQ", TargetKind.RI, NIA_AR_PER_MJ * mj, NIA_RI_PER_MJ * mj, note = "1.6 NE per MJ of energy."))
        ri("VITB6", B6_RI, B6_AR, ul = 12.0)
        ri("FOL", FOL_RI, FOL_AR, note = "NNR gives folate as DFE; the database gives total folate, close for food folate.")
        ai("VITB12", B12_AI, B12_AR)
        ri("VITC", VITC_RI, VITC_AR)

        // Minerals.
        ri("CA", CA_RI, CA_AR, ul = 2500.0)
        ai("P", P_AI, P_AR, ul = 3000.0)
        ai("K", K_AI, K_AR)
        // The magnesium UL (250 mg) is for supplements only, so it isn't applied to food.
        ai("MG", MG_AI, MG_AR)
        ri("FE", FE_RI, FE_AR, ul = 60.0,
            note = if (p.sex == Sex.FEMALE) "15 mg while menstruating; 8 mg after menopause (51–70)." else null)
        ri("ZN", ZN_RI, ZN_AR, ul = 25.0)
        ai("ID", ID_AI, ID_AR, ul = 600.0)
        ai("SE", SE_AI, SE_AR, ul = 255.0)
        put(Target("NA", TargetKind.LIMIT, upper = 2300.0, note = "Keep under 2.3 g sodium a day (Table 16)."))
        put(Target("NACL", TargetKind.LIMIT, upper = 5.75, note = "Under 5.75 g salt, the same as 2.3 g sodium."))
        return out
    }

    private fun fmt(v: Double) = Math.round(v).toString()
}

/** How a day's (or an average day's) intake compares with a target. */
data class Assessment(val status: Status, val percentOfTarget: Double?, val energyPercent: Double? = null)

object Assess {
    /**
     * [value] in the nutrient's unit, [energyKj] the same day's total energy (for E% targets).
     * [fibreG] lets carbs count energy from fibre, as NNR's carbohydrate range does.
     */
    fun of(t: Target, value: Double, energyKj: Double, fibreG: Double = 0.0): Assessment = when (t.kind) {
        TargetKind.ENERGY_PERCENT -> {
            if (energyKj <= 0) Assessment(Status.NONE, null, null) else {
                val kj = value * t.kjPerGram!! + if (t.nutrient == "CHO_AVAIL") fibreG * Nnr2023.KjPerGram.FIBRE else 0.0
                val e = kj / energyKj * 100
                val status = when {
                    t.upper != null && e > t.upper -> Status.OVER
                    t.low != null && e < t.low -> Status.LOW
                    else -> Status.OK
                }
                // Progress towards the middle of a range, or to the limit.
                val ref = if (t.low != null && t.upper != null) (t.low + t.upper) / 2 else t.upper ?: t.low
                Assessment(status, ref?.let { e / it * 100 }, e)
            }
        }
        TargetKind.LIMIT -> Assessment(
            if (t.upper != null && value > t.upper) Status.OVER else Status.OK,
            t.upper?.takeIf { it > 0 }?.let { value / it * 100 },
        )
        else -> {
            val pct = t.target?.takeIf { it > 0 }?.let { value / it * 100 }
            val status = when {
                t.upper != null && value > t.upper -> Status.OVER
                t.low != null && value < t.low -> Status.LOW
                t.target != null && value < t.target -> Status.BELOW_TARGET
                else -> Status.OK
            }
            Assessment(if (t.kind == TargetKind.ENERGY) energyStatus(value, t.target) else status, pct)
        }
    }

    /** Energy: within ±10% of the target is fine; outside it's just shown, not judged harder. */
    private fun energyStatus(value: Double, target: Double?): Status = when {
        target == null || target <= 0 -> Status.NONE
        value < target * 0.9 -> Status.BELOW_TARGET
        value > target * 1.1 -> Status.OVER
        else -> Status.OK
    }

    /**
     * A rough PAL from average daily steps, for when nothing better is known. Bands follow
     * Tudor-Locke's step categories, mapped onto NNR's 1.4 / 1.6 / 1.8.
     */
    fun palFromSteps(avgSteps: Double): Double = when {
        avgSteps < 5000 -> 1.4
        avgSteps < 7500 -> 1.5
        avgSteps < 10000 -> 1.6
        avgSteps < 12500 -> 1.7
        else -> 1.8
    }

    /** NNR's activity reference levels, for the profile picker. */
    val PAL_LEVELS = listOf(1.4 to "Low active", 1.6 to "Average", 1.8 to "Active")

    fun clampPal(pal: Double) = max(1.2, minOf(pal, 2.4))
}
