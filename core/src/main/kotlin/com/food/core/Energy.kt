package com.food.core

/** How the day's energy target was arrived at, shown to the user next to it. */
data class EnergyTarget(val kcal: Double, val pal: Double, val how: String)

object Energy {
    /** Days of watch data needed before trusting an average. */
    const val MIN_DAYS = 3

    /**
     * Picks the energy target:
     *  1. auto, with calories burned from the watch: their daily average;
     *  2. auto, with steps only: NNR's equation × a PAL guessed from steps;
     *  3. otherwise NNR's equation × the PAL the user chose.
     */
    fun target(p: Profile, auto: Boolean, days: Int, avgSteps: Double?, avgBurnedKcal: Double?): EnergyTarget {
        val beeKcal = Nnr2023.bee(p) * 1000 / Nnr2023.KJ_PER_KCAL
        if (auto && days >= MIN_DAYS && avgBurnedKcal != null && avgBurnedKcal > beeKcal) {
            return EnergyTarget(avgBurnedKcal, avgBurnedKcal / beeKcal,
                "Average calories burned on your watch, last $days days")
        }
        if (auto && days >= MIN_DAYS && avgSteps != null) {
            val pal = Assess.palFromSteps(avgSteps)
            return EnergyTarget(beeKcal * pal, pal,
                "Activity level ${"%.1f".format(java.util.Locale.ROOT, pal)} from ${Math.round(avgSteps)} steps a day, last $days days")
        }
        val pal = Assess.clampPal(p.pal)
        return EnergyTarget(beeKcal * pal, pal,
            "Activity level ${"%.1f".format(java.util.Locale.ROOT, pal)}" + if (auto) " (not enough watch data yet)" else "")
    }
}

/** A user's edit of one target. A null field keeps NNR's value; [cleared] removes that bound. */
data class TargetEdit(val low: Double?, val target: Double?, val upper: Double?)

object Targets {
    /** Applies the user's edits over NNR's targets. A nutrient NNR has no target for can be given one. */
    fun withEdits(nnr: Map<String, Target>, edits: Map<String, TargetEdit>): Map<String, Target> {
        val out = LinkedHashMap(nnr)
        for ((code, e) in edits) {
            val base = nnr[code]
            out[code] = if (base == null) {
                Target(code, if (e.target != null) TargetKind.RI else TargetKind.LIMIT, e.low, e.target, e.upper,
                    note = "Your own target.")
            } else if (base.kind == TargetKind.ENERGY_PERCENT) {
                // E% ranges are edited in E%.
                base.copy(low = e.low ?: base.low, upper = e.upper ?: base.upper, note = (base.note ?: "") + " Edited.")
            } else {
                base.copy(low = e.low ?: base.low, target = e.target ?: base.target, upper = e.upper ?: base.upper,
                    note = listOfNotNull(base.note, "Edited.").joinToString(" "))
            }
        }
        return out
    }
}
