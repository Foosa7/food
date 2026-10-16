package com.food.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** One food as eaten on a day, for the digest. */
data class DigestFood(val meal: Meal, val name: String, val grams: Double, val fermented: Boolean)

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
    /** What the user is aiming for, in their own words. */
    val goals: String = "",
    /** The model's own notes from last time. */
    val notes: String = "",
    /** Earlier analyses, oldest first: when, and what was said about progress. */
    val earlier: List<Pair<String, String>> = emptyList(),
)

/**
 * Everything the model reads, as plain text: the person, every target, weekly averages of every
 * nutrient, and the last two weeks day by day with each food. Pure, so it's tested.
 */
object DietDigest {
    const val WEEKS = 12
    const val DAYS = 14

    /** Long-chain omega-3, the part fish gives and plants don't. Not a database nutrient. */
    const val EPA_DHA = "EPA+DHA"

    private val wf = WeekFields.ISO
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun build(input: DigestInput): String = buildString {
        val days = input.days.filter { !it.date.isAfter(input.today) }.sortedBy { it.date }
        val targets = input.targets.orEmpty()

        appendLine("Today is ${dayFmt.format(input.today)} ${input.today.year}, week ${week(input.today)}.")
        appendLine()
        appendLine("# The person")
        val p = input.profile
        if (p == null) {
            appendLine("No profile set up yet, so there are no personal targets. Judge against general adult NNR 2023 values.")
        } else {
            appendLine("${p.sex.name.lowercase()}, ${p.age} years, ${num(p.weightKg)} kg, ${num(p.heightCm)} cm, activity level (PAL) ${num(p.pal)}.")
            targets["ENERC_KCAL"]?.target?.let { appendLine("Energy target ${it.roundToLong()} kcal a day${input.energyNote?.let { n -> " ($n)" }.orEmpty()}.") }
        }
        if (input.goals.isNotBlank()) {
            appendLine()
            appendLine("What they're aiming for, in their words:")
            appendLine(input.goals.trim())
        }

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

        appendLine()
        appendLine("# Targets (NNR 2023${if (targets.isNotEmpty()) ", some may be edited by them" else ""})")
        if (targets.isEmpty()) appendLine("None: no profile.")
        for (def in Nutrients.ALL) {
            val t = targets[def.code] ?: continue
            val desc = describe(t, def.unit)
            // Most notes start by repeating the range; keep only what they add.
            val note = t.note?.let { n -> if (n.startsWith(desc, ignoreCase = true)) n.substring(desc.length).trimStart('.', ' ') else n }
            appendLine("- ${def.nameEn}: $desc${note?.takeIf { it.isNotBlank() }?.let { ". $it" }.orEmpty()}")
        }

        weekly(days, targets, input.today)
        daily(days.filter { it.date in recent })
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
        for (def in Nutrients.ALL) {
            if (def.code == "ENERC_KJ") continue
            val cells = weeks.map { (_, list) -> cell(average(list, def.code), def.unit, targets[def.code], average(list, "ENERC_KJ").value, average(list, "FIBT").value) }
            if (cells.all { it == "–" || it == "?" }) continue
            appendLine("| ${def.nameEn} (${def.unit}) | ${cells.joinToString(" | ")} |")
        }
        appendLine("| $EPA_DHA, long-chain omega-3 (g) | ${weeks.joinToString(" | ") { (_, list) -> cell(epaDha(list), "g", null, 0.0, 0.0) }} |")
        appendLine("| Fermented servings, live cultures (whole week) | ${weeks.joinToString(" | ") { (_, list) -> fermentedServings(list).toString() }} |")
    }

    private fun StringBuilder.daily(days: List<DigestDay>) {
        appendLine()
        appendLine("# The last $DAYS days, day by day")
        for (d in days) {
            appendLine()
            appendLine("## ${dayFmt.format(d.date)} (week ${week(d.date)})")
            for (meal in Meal.entries) {
                val foods = d.foods.filter { it.meal == meal }
                if (foods.isEmpty()) continue
                appendLine("${meal.label}: " + foods.joinToString("; ") { "${it.name} ${num(it.grams)} g${if (it.fermented) " [fermented]" else ""}" })
            }
            val parts = Nutrients.ALL.mapNotNull { def ->
                val t = d.totals[def.code] ?: return@mapNotNull null
                if (def.code == "ENERC_KJ" || t.missing == t.portions) null
                else if (t.value == 0.0 && t.complete && def.group == NutrientGroup.FATTY_ACIDS) null
                else "${def.nameEn} ${if (t.complete) "" else "≥"}${num(t.value)} ${def.unit}"
            }
            appendLine("Totals: " + parts.joinToString(" · "))
            appendLine("$EPA_DHA: ${epaDha(listOf(d)).let { "${if (it.complete) "" else "≥"}${num(it.value)} g" }}")
        }
    }

    /** One week's cell: the average, its share of energy for E% targets, and how it compares. */
    private fun cell(t: Total, unit: String, target: Target?, energyKj: Double, fibre: Double): String {
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

    /** A target in words: "10–20% of energy", "at least 35 g", "1000 mg (low under 750, max 2500)". */
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
            append("${num(t.target ?: 0.0)} $unit a day (${if (t.kind == TargetKind.RI) "RI" else "AI"}")
            t.low?.let { append(", low under ${num(it)}") }
            t.upper?.let { append(", upper limit ${num(it)}") }
            append(")")
        }
    }

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
