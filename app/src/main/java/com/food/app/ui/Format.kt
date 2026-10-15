package com.food.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Format {
    private val dayName = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val short = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    /** "Week 42 · Thursday", ISO weeks as used in Sweden. */
    fun weekDay(d: LocalDate) = "Week ${d.get(WeekFields.ISO.weekOfWeekBasedYear())} · ${dayName.format(d)}"
    fun longDate(d: LocalDate): String = dayMonth.format(d)
    fun shortDate(d: LocalDate): String = short.format(d)

    /** A nutrient amount with sensible precision: 2150, 48, 3.4, 0.27, 0.042. */
    fun value(v: Double): String {
        val a = abs(v)
        return when {
            a == 0.0 -> "0"
            a >= 100 -> v.roundToLong().toString()
            a >= 10 -> trim("%.1f".format(Locale.ROOT, v))
            a >= 1 -> trim("%.1f".format(Locale.ROOT, v))
            a >= 0.1 -> trim("%.2f".format(Locale.ROOT, v))
            else -> trim("%.3f".format(Locale.ROOT, v))
        }
    }

    /** What was typed, shown back: 250, 2.5. */
    fun amount(v: Double): String = trim("%.1f".format(Locale.ROOT, v))

    fun kcal(v: Double): String = v.roundToLong().toString()

    /** Accepts "2,5" as well as "2.5". Null unless a positive number. */
    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    private fun trim(s: String) = if ('.' in s) s.trimEnd('0').trimEnd('.') else s
}
