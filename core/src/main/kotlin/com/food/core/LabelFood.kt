package com.food.core

/**
 * A food typed in from a package label, optionally based on a database food. Label values win;
 * the base food fills in everything the label doesn't say (micronutrients, mostly), keeping its
 * own source so the app can show which number came from where.
 */
object LabelFood {
    /** The nutrients on an EU nutrition declaration, in label order. */
    val LABEL_FIELDS = listOf("ENERC_KCAL", "FAT", "FASAT", "CHO_AVAIL", "SUGAR", "FIBT", "PROT", "NACL")

    /** Grams of sodium per gram of salt. EU labels give salt as sodium × 2.5. */
    const val SODIUM_PER_SALT = 0.4

    /**
     * Fills in what follows from the label: kcal ↔ kJ, and sodium from salt. Typed values are
     * never overwritten.
     */
    fun complete(typed: Map<String, Double>): Map<String, Double> {
        val out = typed.toMutableMap()
        typed["ENERC_KCAL"]?.let { kcal -> out.putIfAbsent("ENERC_KJ", kcal * Nnr2023.KJ_PER_KCAL) }
        typed["ENERC_KJ"]?.let { kj -> out.putIfAbsent("ENERC_KCAL", kj / Nnr2023.KJ_PER_KCAL) }
        typed["NACL"]?.let { salt -> out.putIfAbsent("NA", salt * SODIUM_PER_SALT * 1000) }
        typed["NA"]?.let { na -> out.putIfAbsent("NACL", na / 1000 / SODIUM_PER_SALT) }
        return out
    }

    /**
     * The label's values as rows, over the base food's rows. A [supplement] has nothing but what
     * its label lists, so every other nutrient is an assumed zero rather than unknown: a capsule
     * mustn't turn a day's energy into a lower bound.
     */
    fun merge(foodId: String, label: Map<String, Double>, base: List<FoodNutrient>, baseName: String?, supplement: Boolean = false): List<FoodNutrient> {
        val completed = complete(label)
        val derived = completed.keys - label.keys
        val fromLabel = completed.map { (code, v) ->
            FoodNutrient(
                foodId = foodId, nutrient = code, valuePer100g = v, source = Source.LABEL,
                comment = if (code in derived) "Worked out from the label's ${if (code.startsWith("ENERC")) "energy" else "salt"}" else null,
            )
        }
        val fromBase = base.filter { it.nutrient !in completed }.map {
            it.copy(foodId = foodId, comment = listOfNotNull("From the base food${baseName?.let { n -> ", $n" } ?: ""}", it.comment).joinToString(". "))
        }
        val known = (fromLabel + fromBase).map { it.nutrient }.toSet()
        val zeros = if (!supplement) emptyList() else Nutrients.ALL.filter { it.code !in known }.map {
            FoodNutrient(foodId, it.code, 0.0, Source.LABEL, logicalZero = true, comment = "A supplement: not on its label, so taken as zero")
        }
        return fromLabel + fromBase + zeros
    }
}
