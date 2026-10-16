package com.food.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** One food as eaten on a day. [foodId] finds its values in [DigestInput.foods]. */
data class DigestFood(val meal: Meal, val name: String, val grams: Double, val fermented: Boolean, val foodId: String = name)

/** A food's values per 100 g, what it is, and where its numbers come from. */
data class DigestFoodInfo(val name: String, val source: String, val per100g: Map<String, Double>)

/** A logged day: what was eaten and the totals of every nutrient. */
data class DigestDay(val date: LocalDate, val foods: List<DigestFood>, val totals: Map<String, Total>)

data class DigestInput(
    val today: LocalDate,
    val profile: Profile?,
    /** How the energy target was set, e.g. "from the watch's calories burned". */
    val energyNote: String?,
    val targets: Map<String, Target>?,
    /** Logged days only; a day that isn't here is unknown, never zero. */
    val days: List<DigestDay>,
    /** Every food in [days], by id. */
    val foods: Map<String, DigestFoodInfo> = emptyMap(),
    /** NNR's own values for the same person, to show next to any the user edited. */
    val nnr: Map<String, Target>? = null,
    /** What the user is aiming for, in their own words. */
    val goals: String = "",
    /** The model's own notes from last time. */
    val notes: String = "",
    /** Earlier analyses, oldest first: when, and what was said about progress. */
    val earlier: List<Pair<String, String>> = emptyList(),
)

/**
 * Everything the model reads, as plain text: the person and what NNR says they need, weekly
 * averages of every nutrient, where each nutrient comes from, every food's values, and the last
 * two weeks portion by portion. Long on purpose: the model should never have to guess what a
 * food gave. Pure, so it's tested.
 */
object DietDigest {
    const val WEEKS = 12
    const val DAYS = 14

    /** Biggest sources named per nutrient; the rest are summed into one. */
    const val TOP_SOURCES = 10

    /** Long-chain omega-3, the part fish gives and plants don't. Not a database nutrient. */
    const val EPA_DHA = "EPA+DHA"

    private val wf = WeekFields.ISO
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    /** The nutrients written out; kJ only repeats kcal. */
    private val SHOWN = Nutrients.ALL.filter { it.code != "ENERC_KJ" }

    fun build(input: DigestInput): String = buildString {
        val days = input.days.filter { !it.date.isAfter(input.today) }.sortedBy { it.date }
        val targets = input.targets.orEmpty()

        appendLine("Today is ${dayFmt.format(input.today)} ${input.today.year}, week ${week(input.today)}.")
        person(input)

        if (input.notes.isNotBlank()) {
            appendLine()
            appendLine("# Your notes from last time")
            appendLine(input.notes.trim())
        }
        if (input.earlier.isNotEmpty()) {
            appendLine()
            appendLine("# What you said before")
            input.earlier.forEach { (at, progress) -> appendLine("- $at: $progress") }
        }

        needs(input)

        appendLine()
        appendLine("# Logging")
        if (days.isEmpty()) {
            appendLine("Nothing logged yet.")
            return@buildString
        }
        appendLine("${days.size} days logged, ${dayFmt.format(days.first().date)} to ${dayFmt.format(days.last().date)}.")
        val logged = days.map { it.date }.toSet()
        val recent = (0 until DAYS).map { input.today.minusDays(it.toLong()) }.reversed()
        val missing = recent.filter { it !in logged }
        if (missing.isNotEmpty()) appendLine("Not logged in the last $DAYS days (unknown, not zero): ${missing.joinToString { dayFmt.format(it) }}.")

        weekly(days, targets, input.today)
        val lastDays = days.filter { it.date in recent }
        sources("Last $DAYS days (${lastDays.size} logged)", lastDays, input.foods, targets)
        if (days.size > lastDays.size) sources("All ${days.size} logged days in these $WEEKS weeks", days, input.foods, targets)
        foods(days, input.foods)
        daily(lastDays, input.foods)
    }

    private fun StringBuilder.person(input: DigestInput) {
        appendLine()
        appendLine("# The person")
        val p = input.profile
        if (p == null) {
            appendLine("No profile set up yet, so there are no personal targets. Judge against general adult NNR 2023 values.")
        } else {
            appendLine("${p.sex.name.lowercase()}, ${p.age} years, ${num(p.weightKg)} kg, ${num(p.heightCm)} cm.")
        }
        if (input.goals.isNotBlank()) {
            appendLine()
            appendLine("What they're aiming for, in their words:")
            appendLine(input.goals.trim())
        }
    }

    /** What NNR 2023 says someone of their sex, age, size and activity needs, nutrient by nutrient. */
    private fun StringBuilder.needs(input: DigestInput) {
        val p = input.profile ?: return
        val targets = input.targets ?: return
        val kcal = targets["ENERC_KCAL"]?.target
        val beeKcal = Nnr2023.bee(p) * 1000 / Nnr2023.KJ_PER_KCAL
        appendLine()
        appendLine("# What an adult like them needs (NNR 2023)")
        appendLine(
            "NNR's values for a ${p.sex.name.lowercase()} aged ${p.age} (NNR age band ${BANDS[Nnr2023.band(p.age)]}), " +
                "${num(p.weightKg)} kg, ${num(p.heightCm)} cm."
        )
        appendLine("Resting energy ${beeKcal.roundToLong()} kcal a day (Henry's equation, as NNR uses it).")
        if (kcal != null) appendLine(
            "Energy need about ${kcal.roundToLong()} kcal a day, activity level (PAL) ${num(kcal / beeKcal)}" +
                (input.energyNote?.let { ": $it" }.orEmpty()) + "."
        )
        appendLine(
            "RI = recommended intake, enough for nearly everyone. AI = adequate intake, used where an RI couldn't be set. " +
                "AR = average requirement: a usual intake below it is probably too low. UL = upper limit from all food. " +
                "Grams for %-of-energy ranges are at their energy need."
        )
        for (def in SHOWN) {
            val t = targets[def.code] ?: continue
            val desc = describe(t, def.unit)
            val grams = gramsAt(t, kcal)?.let { (lo, hi) ->
                when {
                    lo != null && hi != null -> ", which is ${num(lo)}–${num(hi)} g a day"
                    hi != null -> ", which is under ${num(hi)} g a day"
                    lo != null -> ", which is over ${num(lo)} g a day"
                    else -> ""
                }
            }.orEmpty()
            // Most notes start by repeating the range; keep only what they add.
            val note = t.note?.removeSuffix("Edited.")?.trim()
                ?.let { n ->
                    val rest = n.substring(minOf(desc.length, n.length))
                    if (n.startsWith(desc, ignoreCase = true) && (rest.isEmpty() || rest[0] == '.' || rest[0] == ',')) rest.trimStart('.', ' ') else n
                }
                ?.trimEnd('.')
            val nnr = input.nnr?.get(def.code)
            val edited = nnr != null && (nnr.low != t.low || nnr.target != t.target || nnr.upper != t.upper)
            appendLine(
                "- ${def.nameEn}: $desc$grams${note?.takeIf { it.isNotBlank() }?.let { if (it.startsWith(",")) it else ". $it" }.orEmpty()}" +
                    if (edited) ". They set this themselves; NNR's own value is ${describe(nnr!!, def.unit)}." else ""
            )
        }
    }

    /** An E% range in grams a day at [kcal]; null for other kinds. */
    private fun gramsAt(t: Target, kcal: Double?): Pair<Double?, Double?>? {
        if (t.kind != TargetKind.ENERGY_PERCENT || kcal == null) return null
        val kjPerG = t.kjPerGram ?: return null
        fun g(pct: Double?) = pct?.let { it / 100 * kcal * Nnr2023.KJ_PER_KCAL / kjPerG }
        return g(t.low) to g(t.upper)
    }

    private fun StringBuilder.weekly(days: List<DigestDay>, targets: Map<String, Target>, today: LocalDate) {
        val weeks = days.groupBy { weekStart(it.date) }.toSortedMap().entries.toList().takeLast(WEEKS)
        appendLine()
        appendLine("# Weekly averages (over logged days only)")
        appendLine("≥ = some foods had no value, so at least this. ? = no food had a value. %E = share of the day's energy. LOW / below / OVER compare with the target.")
        val header = weeks.joinToString(" | ") { (start, list) ->
            "W${week(start)}${if (start == weekStart(today)) " so far" else ""} (${list.size} d)"
        }
        appendLine("| Nutrient | $header |")
        for (def in SHOWN) {
            val cells = weeks.map { (_, list) -> cell(average(list, def.code), targets[def.code], average(list, "ENERC_KJ").value, average(list, "FIBT").value) }
            if (cells.all { it == "–" || it == "?" }) continue
            appendLine("| ${def.nameEn} (${def.unit}) | ${cells.joinToString(" | ")} |")
        }
        appendLine("| $EPA_DHA, long-chain omega-3 (g) | ${weeks.joinToString(" | ") { (_, list) -> cell(epaDha(list), null, 0.0, 0.0) }} |")
        appendLine("| Fermented servings, live cultures (whole week) | ${weeks.joinToString(" | ") { (_, list) -> fermentedServings(list).toString() }} |")
    }

    /** For each nutrient, which foods gave how much of it, per average logged day. */
    private fun StringBuilder.sources(title: String, days: List<DigestDay>, foods: Map<String, DigestFoodInfo>, targets: Map<String, Target>) {
        if (days.isEmpty()) return
        appendLine()
        appendLine("# Where each nutrient comes from: $title")
        appendLine("Per average logged day, biggest source first, with its share of the total. Foods with no value for a nutrient aren't counted in it.")
        val portions = days.flatMap { it.foods }
        for (def in SHOWN) {
            val byFood = portions.groupBy { it.foodId }.mapValues { (id, list) ->
                val per100 = foods[id]?.per100g?.get(def.code) ?: return@mapValues null
                list.sumOf { scale(per100, it.grams) } / days.size
            }.filterValues { it != null && it > 0 }.mapValues { it.value!! }
            val total = byFood.values.sum()
            if (total <= 0) continue
            val sorted = byFood.entries.sortedByDescending { it.value }
            val named = sorted.take(TOP_SOURCES).joinToString(" · ") { (id, v) ->
                "${foods[id]?.name ?: id} ${num(v)} (${pct(v, total)})"
            }
            val rest = sorted.drop(TOP_SOURCES)
            val others = if (rest.isEmpty()) "" else " · ${rest.size} more foods ${num(rest.sumOf { it.value })} (${pct(rest.sumOf { it.value }, total)})"
            val target = targets[def.code]?.let { "; target ${describe(it, def.unit)}" }.orEmpty()
            appendLine("- ${def.nameEn}: ${num(total)} ${def.unit} a day$target. $named$others")
        }
        val epa = portions.groupBy { it.foodId }.mapValues { (id, list) ->
            val v = foods[id]?.per100g ?: return@mapValues 0.0
            list.sumOf { scale((v["F20:5"] ?: 0.0) + (v["F22:6"] ?: 0.0), it.grams) } / days.size
        }.filterValues { it > 0 }
        if (epa.isNotEmpty()) {
            val total = epa.values.sum()
            appendLine("- $EPA_DHA: ${num(total)} g a day. " + epa.entries.sortedByDescending { it.value }
                .joinToString(" · ") { (id, v) -> "${foods[id]?.name ?: id} ${num(v)} (${pct(v, total)})" })
        }
    }

    /** Every food once, with all its values per 100 g. */
    private fun StringBuilder.foods(days: List<DigestDay>, foods: Map<String, DigestFoodInfo>) {
        val portions = days.flatMap { it.foods }.groupBy { it.foodId }
        appendLine()
        appendLine("# Every food eaten in these $WEEKS weeks, values per 100 g")
        appendLine("A nutrient that isn't listed has no value for that food (unknown, not zero).")
        for ((id, list) in portions.entries.sortedByDescending { (_, l) -> l.sumOf { it.grams } }) {
            val info = foods[id]
            appendLine()
            appendLine("## ${info?.name ?: list.first().name}")
            val names = list.map { it.name }.distinct().filter { it != info?.name }
            appendLine(
                listOfNotNull(
                    info?.source ?: "No values: this food is no longer in the database",
                    names.takeIf { it.isNotEmpty() }?.let { "logged as ${it.joinToString { n -> "\"$n\"" }}" },
                    "logged ${list.size} ${if (list.size == 1) "time" else "times"}, ${num(list.sumOf { it.grams })} g in all",
                    "fermented, live cultures".takeIf { list.any { it.fermented } },
                ).joinToString(" · ")
            )
            if (info != null) appendLine(values(info.per100g, 100.0))
        }
    }

    private fun StringBuilder.daily(days: List<DigestDay>, foods: Map<String, DigestFoodInfo>) {
        appendLine()
        appendLine("# The last $DAYS days, portion by portion")
        appendLine("Each portion with what it gave, then the day's totals.")
        for (d in days) {
            appendLine()
            appendLine("## ${dayFmt.format(d.date)} (week ${week(d.date)})")
            for (meal in Meal.entries) {
                for (f in d.foods.filter { it.meal == meal }) {
                    val given = foods[f.foodId]?.let { values(it.per100g, f.grams) } ?: "no values"
                    appendLine("- ${meal.label} · ${f.name} ${num(f.grams)} g${if (f.fermented) " [fermented]" else ""}: $given")
                }
            }
            val parts = SHOWN.mapNotNull { def ->
                val t = d.totals[def.code] ?: return@mapNotNull null
                if (t.missing == t.portions) null
                else if (t.value == 0.0 && t.complete && def.group == NutrientGroup.FATTY_ACIDS) null
                else "${def.nameEn} ${if (t.complete) "" else "≥"}${num(t.value)} ${def.unit}"
            }
            appendLine("Day total: " + parts.joinToString(" · "))
            appendLine("$EPA_DHA: ${epaDha(listOf(d)).let { "${if (it.complete) "" else "≥"}${num(it.value)} g" }}")
        }
    }

    /** Every value a food has, for [grams] of it. */
    private fun values(per100g: Map<String, Double>, grams: Double): String {
        val parts = SHOWN.mapNotNull { def -> per100g[def.code]?.let { "${def.nameEn} ${num(scale(it, grams))} ${def.unit}" } }
        return if (parts.isEmpty()) "no values" else parts.joinToString(" · ")
    }

    /** One week's cell: the average, its share of energy for E% targets, and how it compares. */
    private fun cell(t: Total, target: Target?, energyKj: Double, fibre: Double): String {
        if (t.portions == 0) return "–"
        // No food had a value at all: unknown, which isn't the same as a low intake.
        if (t.missing == t.portions) return "?"
        val v = "${if (t.complete) "" else "≥"}${num(t.value)}"
        if (target == null) return v
        val a = Assess.of(target, t.value, energyKj, fibre)
        val e = a.energyPercent?.let { " (${num(it)}%E)" }.orEmpty()
        val mark = when (a.status) {
            Status.LOW -> " LOW"
            Status.BELOW_TARGET -> " below"
            Status.OVER -> " OVER"
            else -> ""
        }
        return "$v$e$mark"
    }

    /** The average day of [days] for one nutrient; incomplete if any day was. */
    fun average(days: List<DigestDay>, code: String): Total {
        val ts = days.mapNotNull { it.totals[code] }
        if (ts.isEmpty()) return Total(code, 0.0, 0, 0)
        return Total(code, ts.sumOf { it.value } / days.size, ts.sumOf { it.portions }, ts.sumOf { it.missing })
    }

    fun epaDha(days: List<DigestDay>): Total {
        val epa = average(days, "F20:5")
        val dha = average(days, "F22:6")
        return Total(EPA_DHA, epa.value + dha.value, epa.portions, maxOf(epa.missing, dha.missing))
    }

    fun fermentedServings(days: List<DigestDay>): Int = days.sumOf { d -> Fermented.servings(d.foods.map { it.fermented to it.grams }) }

    /** A target in words: "10–20% of energy", "at least 35 g", "RI 950 mg a day, AR 750, UL 2500". */
    fun describe(t: Target, unit: String): String = when (t.kind) {
        TargetKind.ENERGY -> "${t.target?.roundToLong()} kcal"
        TargetKind.ENERGY_PERCENT -> when {
            t.low != null && t.upper != null -> "${num(t.low)}–${num(t.upper)}% of energy"
            t.upper != null -> "under ${num(t.upper)}% of energy"
            else -> "over ${num(t.low ?: 0.0)}% of energy"
        }
        TargetKind.LIMIT -> if (t.upper == 0.0) "best avoided" else "under ${num(t.upper ?: 0.0)} $unit"
        TargetKind.MIN -> "at least ${num(t.low ?: 0.0)} $unit"
        TargetKind.RI, TargetKind.AI -> buildString {
            append("${if (t.kind == TargetKind.RI) "RI" else "AI"} ${num(t.target ?: 0.0)} $unit a day")
            t.low?.let { append(", AR ${num(it)}") }
            t.upper?.let { append(", UL ${num(it)}") }
        }
    }

    private val BANDS = listOf("18–24", "25–50", "51–70", "over 70")

    private fun pct(v: Double, total: Double) = "${(v / total * 100).roundToLong()}%"
    private fun week(d: LocalDate) = d.get(wf.weekOfWeekBasedYear())
    private fun weekStart(d: LocalDate) = d.with(DayOfWeek.MONDAY)

    /** 2150, 48, 3.4, 0.27, 0.042: enough digits to be useful, no more. */
    fun num(v: Double): String {
        val a = abs(v)
        val s = when {
            a == 0.0 -> return "0"
            a >= 100 -> return v.roundToLong().toString()
            a >= 10 -> "%.1f".format(Locale.ROOT, v)
            a >= 1 -> "%.2f".format(Locale.ROOT, v)
            a >= 0.1 -> "%.2f".format(Locale.ROOT, v)
            else -> "%.3f".format(Locale.ROOT, v)
        }
        return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
    }
}
