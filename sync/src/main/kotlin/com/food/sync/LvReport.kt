package com.food.sync

import com.food.core.ImportedFood
import com.food.core.Nutrients

/** data/report-lv.md: what Livsmedelsverket covers, so the USDA gap-fill knows what to look for. */
object LvReport {

    /**
     * Nutrients Cronometer tracks that aren't in Livsmedelsverket's catalog at all.
     * The candidates for the USDA gap-fill (milestone f).
     */
    val NOT_IN_LV = listOf(
        "Vitamin K", "Pantothenic acid (B5)", "Biotin (B7)", "Choline",
        "Copper", "Manganese", "Chromium", "Molybdenum", "Fluoride", "Chloride",
        "Trans fat", "Caffeine", "Amino acids",
    )

    fun render(result: LvBuild.Result): String {
        val foods = result.foods
        val live = foods.filterNot { it.food.removed }
        val sb = StringBuilder()
        sb.appendLine("# Livsmedelsverket sync report")
        sb.appendLine()
        sb.appendLine("Source: Livsmedelsverket, Livsmedelsdatabasen (CC BY 4.0). Values per 100 g edible part.")
        sb.appendLine()
        sb.appendLine("- Foods listed: ${result.listed}")
        live.groupingBy { it.food.lvType ?: "?" }.eachCount().toSortedMap().forEach { (type, n) ->
            sb.appendLine("  - $type: $n")
        }
        sb.appendLine("- Removed by Livsmedelsverket but kept: ${foods.count { it.food.removed }}")
        sb.appendLine("- Nutrient values: ${foods.sumOf { it.nutrients.size }}, of which assumed zeros (Logisk nolla): " +
            "${foods.sumOf { f -> f.nutrients.count { it.logicalZero } }}")
        sb.appendLine("- Foods with an inedible share (WASTE > 0): ${live.count { (it.food.wastePct ?: 0.0) > 0 }}")
        sb.appendLine("- Foods without a main group: ${live.count { it.food.group == null }}")
        sb.appendLine("- Foods without an English name: ${live.count { it.food.nameEn == null }}")

        val unknown = foods.flatMap { it.unknown }.groupBy { Nutrients.lvKey(it.euroFIRkod, it.enhet) }
        sb.appendLine()
        sb.appendLine("## Unknown nutrients")
        sb.appendLine()
        if (unknown.isEmpty()) sb.appendLine("None: every code + unit pair is in the catalog.")
        else unknown.forEach { (key, rows) -> sb.appendLine("- `$key` ${rows.first().namn}: ${rows.size} foods (NOT imported, add to Nutrients.ALL)") }

        sb.appendLine()
        sb.appendLine("## Coverage per nutrient")
        sb.appendLine()
        sb.appendLine("Missing means the food has no value at all: unknown, which the app must not count as zero.")
        sb.appendLine()
        sb.appendLine("| Nutrient | Unit | Code | Has a value | Of which assumed zero | Missing |")
        sb.appendLine("|---|---|---|---:|---:|---:|")
        for (def in Nutrients.ALL) {
            val rows = live.mapNotNull { f -> f.nutrients.firstOrNull { it.nutrient == def.code } }
            sb.appendLine("| ${def.nameEn} | ${def.unit} | `${def.code}` | ${rows.size} | ${rows.count { it.logicalZero }} | ${live.size - rows.size} |")
        }

        sb.appendLine()
        sb.appendLine("## Not in Livsmedelsverket at all")
        sb.appendLine()
        sb.appendLine("Candidates for the USDA gap-fill. Omega-3 and omega-6 totals can be summed from the fatty acids above.")
        sb.appendLine()
        NOT_IN_LV.forEach { sb.appendLine("- $it") }
        return sb.toString()
    }
}
