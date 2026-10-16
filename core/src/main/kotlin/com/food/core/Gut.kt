package com.food.core

/**
 * Foods for the gut: fibre is a nutrient and already counted; this is the other half,
 * fermented foods that still carry live cultures. Livsmedelsverket doesn't flag them, so it's
 * a rule on the name, plus a checkbox on label foods for anything the rule misses.
 */
object Fermented {
    /** A spoonful on the side doesn't count as a serving. */
    const val MIN_SERVING_G = 20.0

    private val INCLUDE = listOf(
        "filmjölk", "yoghurt", "kefir", "surkål", "fermenter", "miso", "kimchi", "långfil",
        "a-fil", "kombucha", "skyr", "natto", "kvass",
        // USDA's names are English.
        "yogurt", "sauerkraut",
    )

    // Cooked, baked, frozen, dried or mostly something else: the cultures don't survive or don't matter.
    private val EXCLUDE = listOf(
        "smoothie", "barnmat", "barngröt", "gröt", "korv", "isterband", "sås", "dressing", "glass",
        "pulver", "bröd", "kaka", "paj", "tzatziki", "färskost", "te ferment",
        "frozen", "coated", "covered", "powder", "bread", "dip", "bar,", "pretzel",
    )

    fun byName(name: String): Boolean {
        val n = name.lowercase()
        return INCLUDE.any { it in n } && EXCLUDE.none { it in n }
    }

    /** How many servings of fermented food a list of portions holds. */
    fun servings(portions: List<Pair<Boolean, Double>>): Int = portions.count { (fermented, grams) -> fermented && grams >= MIN_SERVING_G }
}
