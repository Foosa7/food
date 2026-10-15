package com.food.core

/** Where a nutrient value came from, shown next to every number in the app. */
enum class Source(val code: String) {
    LV("LV"),
    USDA("USDA"),
    LABEL("label"),
    MANUAL("manual");

    companion object {
        fun of(code: String) = entries.first { it.code == code }
    }
}

/** `lv:142`, `usda:171287`, `my:12`: one id space for every food the app can log. */
object FoodId {
    fun lv(nummer: Int) = "lv:$nummer"
}

data class Food(
    val id: String,
    val source: Source,
    val sourceId: String,
    val nameSv: String,
    val nameEn: String?,
    val scientificName: String?,
    /** Livsmedelsverket's "Analyserat" (analysed) or "Beräknat" (calculated, e.g. recipes). */
    val lvType: String?,
    val group: String?,
    /** Which products were analysed, often with brands. Helps match barcodes. */
    val note: String?,
    val version: String?,
    /** Inedible share (peel, bones) as % of total weight. Values are per edible part. */
    val wastePct: Double?,
    /** No longer listed by the source; kept so older log entries still add up. */
    val removed: Boolean = false,
)

/** One nutrient of one food, per 100 g edible part. */
data class FoodNutrient(
    val foodId: String,
    val nutrient: String,
    val valuePer100g: Double,
    val source: Source,
    /** The source's value type, e.g. Livsmedelsverket's BE (best estimate) or LZ. */
    val valueType: String? = null,
    /** An assumed zero ("Logisk nolla"), not a measured one. */
    val logicalZero: Boolean = false,
    val origin: String? = null,
    val method: String? = null,
    val comment: String? = null,
    val usdaFdcId: Long? = null,
)
