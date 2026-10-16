package com.food.core

/** One food from a FoodData Central CSV download. */
data class FdcFood(val fdcId: Long, val dataType: String, val description: String, val category: String?, val published: String?)

/** One value, keyed by USDA's nutrient number (e.g. "203" protein), per 100 g. */
data class FdcValue(val nbr: String, val amount: Double, val derivation: String? = null)

/**
 * USDA FoodData Central, as a second source next to Livsmedelsverket: SR Legacy (analysed and
 * literature values, last updated 2018) and Foundation Foods (new lab analyses). Public domain.
 *
 * Values are mapped onto our codes with the same rules as Livsmedelsverket's, so they add up the
 * same way. Where USDA reports something differently it's converted, never just copied:
 * - Carbohydrates: USDA's are by difference and include fibre; available carbs = that minus fibre.
 * - Vitamin A: USDA's RAE is not Livsmedelsverket's RE, so RE is worked out from retinol and the
 *   carotenoids (β-carotene ÷ 6, other provitamin A carotenoids ÷ 12).
 * - Salt: sodium × 2.5, as on EU labels.
 * - Niacin equivalents: niacin + tryptophan ÷ 60, when tryptophan is known.
 */
object Usda {
    const val ATTRIBUTION = "USDA FoodData Central"
    const val LICENSE = "public domain (CC0)"

    val DATA_TYPES = mapOf("sr_legacy_food" to "SR Legacy", "foundation_food" to "Foundation")

    /** Our code ← USDA nutrient numbers, first one present wins. */
    private val DIRECT: List<Pair<String, List<String>>> = listOf(
        "ENERC_KCAL" to listOf("208", "958", "957"),
        "PROT" to listOf("203"),
        "FAT" to listOf("204"),
        "FIBT" to listOf("291"),
        "ALC" to listOf("221"),
        "WATER" to listOf("255"),
        "ASH" to listOf("207"),
        "SUGAR" to listOf("269", "269.3"),
        "FASAT" to listOf("606"),
        "FAMS" to listOf("645"),
        "FAPU" to listOf("646"),
        "CHORL" to listOf("601"),
        "F12:0" to listOf("611"),
        "F14:0" to listOf("612"),
        "F16:0" to listOf("613"),
        "F16:1" to listOf("626", "673"),
        "F18:0" to listOf("614"),
        "F18:1" to listOf("617", "674"),
        "F18:2" to listOf("618", "675"),
        "F18:3" to listOf("619", "851"),
        "F20:0" to listOf("615"),
        "F20:4" to listOf("620"),
        "F20:5" to listOf("629"),
        "F22:5" to listOf("631"),
        "F22:6" to listOf("621"),
        "RETOL" to listOf("319"),
        "CARTBTOT" to listOf("321"),
        "VITE" to listOf("323"),
        "THIA" to listOf("404"),
        "RIBF" to listOf("405"),
        "NIA" to listOf("406"),
        "VITB6" to listOf("415"),
        "FOL" to listOf("417", "432"),
        "VITB12" to listOf("418"),
        "VITC" to listOf("401"),
        "CA" to listOf("301"),
        "P" to listOf("305"),
        "MG" to listOf("304"),
        "K" to listOf("306"),
        "NA" to listOf("307"),
        "FE" to listOf("303"),
        "ZN" to listOf("309"),
        "ID" to listOf("314"),
        "SE" to listOf("317"),
    )

    fun food(f: FdcFood, values: List<FdcValue>): ImportedFood {
        val id = FoodId.usda(f.fdcId)
        val dataset = DATA_TYPES[f.dataType] ?: f.dataType
        // A number can appear twice in Foundation data (two methods); the first one is kept.
        val by = LinkedHashMap<String, FdcValue>()
        values.forEach { by.putIfAbsent(it.nbr, it) }
        val rows = LinkedHashMap<String, FoodNutrient>()

        fun put(code: String, value: Double, derivation: String?, method: String? = null) {
            if (code in rows || !value.isFinite()) return
            rows[code] = FoodNutrient(
                foodId = id, nutrient = code, valuePer100g = value, source = Source.USDA,
                valueType = derivation, origin = dataset, method = method, usdaFdcId = f.fdcId,
            )
        }
        fun v(nbr: String) = by[nbr]?.amount

        for ((code, nbrs) in DIRECT) {
            val hit = nbrs.firstNotNullOfOrNull { n -> by[n] } ?: continue
            put(code, hit.amount, hit.derivation, if (hit.nbr != nbrs.first()) "USDA nutrient ${hit.nbr}" else null)
        }

        rows["ENERC_KCAL"]?.let { kcal ->
            v("268")?.let { put("ENERC_KJ", it, by["268"]?.derivation) }
                ?: put("ENERC_KJ", kcal.valuePer100g * Nnr2023.KJ_PER_KCAL, null, "kcal × 4.184")
        }

        // Available carbs: by difference minus fibre. With no fibre value it's only safe when there are no carbs.
        v("205")?.let { diff ->
            val fibre = v("291")
            if (fibre != null) put("CHO_AVAIL", maxOf(0.0, diff - fibre), null, "carbohydrate by difference − fibre")
            else if (diff == 0.0) put("CHO_AVAIL", 0.0, null, "carbohydrate by difference, no fibre")
        }

        // 4:0–10:0 summed, the way Livsmedelsverket groups them.
        val short = listOf("607", "608", "609", "610").mapNotNull(::v)
        if (short.isNotEmpty()) put("F4-10:0", short.sum(), null, "sum of USDA 4:0, 6:0, 8:0, 10:0")

        // Vitamin A as retinol equivalents, Livsmedelsverket's unit.
        val retinol = v("319")
        val beta = v("321")
        if (retinol != null || beta != null) {
            val others = (v("322") ?: 0.0) + (v("334") ?: 0.0)
            put("VITA_RE", (retinol ?: 0.0) + (beta ?: 0.0) / 6 + others / 12, null, "retinol + β-carotene/6 + other carotenoids/12")
        }

        (v("328") ?: v("324")?.let { it / 40 })?.let { put("VITD", it, by["328"]?.derivation, if ("328" in by) null else "IU ÷ 40") }

        val niacin = v("406")
        val trp = v("501")
        if (niacin != null && trp != null) put("NIAEQ", niacin + trp * 1000 / 60, null, "niacin + tryptophan/60")

        v("307")?.let { put("NACL", it * 2.5 / 1000, null, "sodium × 2.5") }

        val food = Food(
            id = id,
            source = Source.USDA,
            sourceId = f.fdcId.toString(),
            nameSv = f.description.trim(),
            nameEn = null,
            scientificName = null,
            lvType = null,
            group = f.category,
            note = "$ATTRIBUTION, $dataset",
            version = f.published,
            // USDA's values are per 100 g as described ("raw", "drained solids"); no separate waste share.
            wastePct = null,
        )
        return ImportedFood(food, rows.values.toList(), emptyList())
    }
}

/** RFC 4180 CSV, as FoodData Central writes it: every field quoted, "" for a quote inside one. */
object Csv {
    fun rows(text: Sequence<String>): Sequence<List<String>> = sequence {
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        for (line in text) {
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == ',' && !quoted -> { fields += field.toString(); field.clear() }
                    else -> field.append(c)
                }
                i++
            }
            if (quoted) { field.append('\n'); continue }
            fields += field.toString(); field.clear()
            yield(fields.toList())
            fields.clear()
        }
    }

    /** Rows as header → value maps. */
    fun records(text: Sequence<String>): Sequence<Map<String, String>> = sequence {
        var header: List<String>? = null
        for (r in rows(text)) {
            val h = header
            if (h == null) header = r else yield(h.indices.associate { h[it] to r.getOrElse(it) { "" } })
        }
    }
}
