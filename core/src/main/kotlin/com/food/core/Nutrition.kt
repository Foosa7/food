package com.food.core

import java.time.LocalTime

enum class AmountUnit(val code: String) {
    G("g"),
    ML("ml"),
    /** Capsules, tablets, bars: for foods that know what one piece weighs. */
    PIECE("st");

    companion object {
        fun of(code: String) = entries.first { it.code == code }
    }
}

object Amounts {
    /** For drinks, 1 ml is taken as 1 g. Close for water, milk, juice and coffee. */
    const val G_PER_ML = 1.0

    /** [pieceG] is what one piece of the food weighs; needed only for [AmountUnit.PIECE]. */
    fun grams(amount: Double, unit: AmountUnit, pieceG: Double? = null): Double = when (unit) {
        AmountUnit.G -> amount
        AmountUnit.ML -> amount * G_PER_ML
        AmountUnit.PIECE -> amount * requireNotNull(pieceG) { "this food has no piece weight" }
    }
}

/** Every value in the database is per 100 g edible part. */
fun scale(per100g: Double, grams: Double): Double = per100g * grams / 100.0

/** One logged food, already in grams. */
data class Portion(val foodId: String, val grams: Double)

/**
 * A day's (or a meal's) total of one nutrient. [missing] counts portions whose food has no
 * value for it: then [value] is a lower bound ("at least"), never a guess that they were zero.
 */
data class Total(val nutrient: String, val value: Double, val portions: Int, val missing: Int) {
    val complete get() = missing == 0
}

object Totals {
    /**
     * [values] is foodId → (nutrient code → value per 100 g). A food that isn't there at all
     * (say, deleted from the database) counts as missing for every nutrient.
     */
    fun of(
        portions: List<Portion>,
        values: Map<String, Map<String, Double>>,
        nutrients: Collection<String> = Nutrients.ALL.map { it.code },
    ): Map<String, Total> = nutrients.associateWith { code ->
        var sum = 0.0
        var missing = 0
        for (p in portions) {
            val v = values[p.foodId]?.get(code)
            if (v == null) missing++ else sum += scale(v, p.grams)
        }
        Total(code, sum, portions.size, missing)
    }
}

enum class Meal(val code: String, val label: String) {
    BREAKFAST("breakfast", "Breakfast"),
    LUNCH("lunch", "Lunch"),
    DINNER("dinner", "Dinner"),
    SNACK("snack", "Snack");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: SNACK

        /** A guess from the clock; the user can always change it. */
        fun at(time: LocalTime): Meal = when {
            time < LocalTime.of(4, 0) -> SNACK
            time < LocalTime.of(10, 30) -> BREAKFAST
            time < LocalTime.of(14, 30) -> LUNCH
            time < LocalTime.of(16, 30) -> SNACK
            time < LocalTime.of(21, 0) -> DINNER
            else -> SNACK
        }
    }
}
