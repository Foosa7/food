package com.food.core

/** One Livsmedelsverket food, turned into the app's rows. */
data class ImportedFood(
    val food: Food,
    val nutrients: List<FoodNutrient>,
    /** Code|unit pairs the catalog doesn't know yet. Reported, never guessed at. */
    val unknown: List<LvNutrient>,
)

object LvImport {
    /** Livsmedelsverket's value-type code for "Logisk nolla". */
    const val LOGICAL_ZERO = "LZ"

    fun food(
        sv: LvFood,
        en: LvFood?,
        nutrients: List<LvNutrient>,
        classes: List<LvClass>,
        removed: Boolean = false,
    ): ImportedFood {
        val id = FoodId.lv(sv.nummer)
        val rows = mutableListOf<FoodNutrient>()
        val unknown = mutableListOf<LvNutrient>()
        var waste: Double? = null

        for (n in nutrients) {
            val value = n.varde ?: continue
            if (n.euroFIRkod == Nutrients.LV_WASTE) {
                waste = value
                continue
            }
            val def = Nutrients.forLv(n.euroFIRkod, n.enhet)
            if (def == null) {
                unknown += n
                continue
            }
            rows += FoodNutrient(
                foodId = id,
                nutrient = def.code,
                valuePer100g = value,
                source = Source.LV,
                valueType = n.vardetypkod,
                logicalZero = n.vardetypkod == LOGICAL_ZERO || n.vardetyp == "Logisk nolla",
                origin = n.ursprung,
                method = n.metodtyp,
                comment = n.kommentar?.takeIf { it.isNotBlank() },
            )
        }

        val dupes = rows.groupBy { it.nutrient }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "#${sv.nummer} has more than one value for $dupes" }

        val food = Food(
            id = id,
            source = Source.LV,
            sourceId = sv.nummer.toString(),
            nameSv = sv.namn.trim(),
            nameEn = en?.namn?.trim()?.takeIf { it.isNotEmpty() },
            scientificName = sv.vetenskapligtNamn?.trim()?.takeIf { it.isNotEmpty() },
            lvType = sv.livsmedelsTyp,
            group = Lv.mainGroup(classes),
            note = sv.analys?.trim()?.takeIf { it.isNotEmpty() },
            version = sv.version,
            wastePct = waste,
            removed = removed,
        )
        return ImportedFood(food, rows, unknown)
    }
}
