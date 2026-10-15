package com.food.core

enum class NutrientGroup { ENERGY, MACRO, CARBS, FATS, FATTY_ACIDS, VITAMINS, MINERALS, OTHER }

/**
 * One nutrient as the app knows it. [code] is ours and stable. Where Livsmedelsverket reports
 * the nutrient, [lvCode] + [lvUnit] is its key there: never the code alone, because energy
 * in kJ and in kcal share the code ENERC.
 */
data class NutrientDef(
    val code: String,
    val nameSv: String,
    val nameEn: String,
    val unit: String,
    val group: NutrientGroup,
    val lvCode: String? = null,
    val lvUnit: String? = null,
)

object Nutrients {
    private fun lv(code: String, sv: String, en: String, unit: String, group: NutrientGroup,
                   lvCode: String = code, lvUnit: String = unit) =
        NutrientDef(code, sv, en, unit, group, lvCode, lvUnit)

    /** In display order. */
    val ALL: List<NutrientDef> = listOf(
        lv("ENERC_KCAL", "Energi", "Energy", "kcal", NutrientGroup.ENERGY, lvCode = "ENERC"),
        lv("ENERC_KJ", "Energi (kJ)", "Energy (kJ)", "kJ", NutrientGroup.ENERGY, lvCode = "ENERC"),

        lv("PROT", "Protein", "Protein", "g", NutrientGroup.MACRO),
        lv("FAT", "Fett, totalt", "Fat, total", "g", NutrientGroup.MACRO),
        // Available carbohydrates, fibre excluded. USDA's "by difference" carbs include fibre
        // and get their own code, CHO_DIFF, when the USDA data comes in: never this one.
        lv("CHO_AVAIL", "Kolhydrater, tillgängliga", "Carbohydrates, available", "g", NutrientGroup.MACRO, lvCode = "CHO"),
        lv("FIBT", "Fiber", "Fibre", "g", NutrientGroup.MACRO),
        lv("ALC", "Alkohol", "Alcohol", "g", NutrientGroup.MACRO),
        lv("WATER", "Vatten", "Water", "g", NutrientGroup.MACRO),
        lv("ASH", "Aska", "Ash", "g", NutrientGroup.MACRO),

        lv("SUGAR", "Sockerarter, totalt", "Sugars, total", "g", NutrientGroup.CARBS),
        lv("SUGAD", "Tillsatt socker", "Added sugar", "g", NutrientGroup.CARBS),
        lv("SUGFR", "Fritt socker", "Free sugars", "g", NutrientGroup.CARBS),
        lv("MNSAC", "Monosackarider", "Monosaccharides", "g", NutrientGroup.CARBS),
        lv("DISAC", "Disackarider", "Disaccharides", "g", NutrientGroup.CARBS),
        lv("SUCS", "Sackaros", "Sucrose", "g", NutrientGroup.CARBS),
        lv("WHOLET", "Fullkorn totalt", "Whole grains, total", "g", NutrientGroup.CARBS),
        lv("WHOLE_PSEUDO", "Fullkorn från pseudocerealier", "Whole grains from pseudocereals", "g", NutrientGroup.CARBS, lvCode = "WHOLE_x"),

        lv("FASAT", "Mättat fett", "Saturated fat", "g", NutrientGroup.FATS),
        lv("FAMS", "Enkelomättat fett", "Monounsaturated fat", "g", NutrientGroup.FATS),
        lv("FAPU", "Fleromättat fett", "Polyunsaturated fat", "g", NutrientGroup.FATS),
        lv("CHORL", "Kolesterol", "Cholesterol", "mg", NutrientGroup.FATS),

        lv("F4-10:0", "Fettsyra 4:0–10:0", "Fatty acids 4:0–10:0", "g", NutrientGroup.FATTY_ACIDS),
        lv("F12:0", "Laurinsyra C12:0", "Lauric acid C12:0", "g", NutrientGroup.FATTY_ACIDS),
        lv("F14:0", "Myristinsyra C14:0", "Myristic acid C14:0", "g", NutrientGroup.FATTY_ACIDS),
        lv("F16:0", "Palmitinsyra C16:0", "Palmitic acid C16:0", "g", NutrientGroup.FATTY_ACIDS),
        lv("F16:1", "Palmitoljesyra C16:1", "Palmitoleic acid C16:1", "g", NutrientGroup.FATTY_ACIDS),
        lv("F18:0", "Stearinsyra C18:0", "Stearic acid C18:0", "g", NutrientGroup.FATTY_ACIDS),
        lv("F18:1", "Oljesyra C18:1", "Oleic acid C18:1", "g", NutrientGroup.FATTY_ACIDS),
        lv("F18:2", "Linolsyra C18:2", "Linoleic acid C18:2 (omega-6)", "g", NutrientGroup.FATTY_ACIDS),
        lv("F18:3", "Linolensyra C18:3", "Linolenic acid C18:3 (omega-3)", "g", NutrientGroup.FATTY_ACIDS),
        lv("F20:0", "Arakidinsyra C20:0", "Arachidic acid C20:0", "g", NutrientGroup.FATTY_ACIDS),
        lv("F20:4", "Arakidonsyra C20:4", "Arachidonic acid C20:4 (omega-6)", "g", NutrientGroup.FATTY_ACIDS),
        lv("F20:5", "EPA C20:5", "EPA C20:5 (omega-3)", "g", NutrientGroup.FATTY_ACIDS),
        lv("F22:5", "DPA C22:5", "DPA C22:5 (omega-3)", "g", NutrientGroup.FATTY_ACIDS),
        lv("F22:6", "DHA C22:6", "DHA C22:6 (omega-3)", "g", NutrientGroup.FATTY_ACIDS),

        // Livsmedelsverket gives vitamin A as retinol equivalents (β-carotene ÷ 6). USDA uses
        // retinol activity equivalents (÷ 12), so the two must not share a code.
        lv("VITA_RE", "Vitamin A", "Vitamin A (RE)", "µg", NutrientGroup.VITAMINS, lvCode = "VITA", lvUnit = "RE/µg"),
        lv("RETOL", "Retinol", "Retinol", "µg", NutrientGroup.VITAMINS),
        lv("CARTBTOT", "Betakaroten", "Beta-carotene", "µg", NutrientGroup.VITAMINS),
        lv("VITD", "Vitamin D", "Vitamin D", "µg", NutrientGroup.VITAMINS),
        lv("VITD_25OH", "Vitamin D inkl. 25-OH-D3", "Vitamin D incl. 25-OH-D3", "µg", NutrientGroup.VITAMINS, lvCode = "VITD_x"),
        lv("VITE", "Vitamin E", "Vitamin E", "mg", NutrientGroup.VITAMINS),
        lv("THIA", "Tiamin (B1)", "Thiamin (B1)", "mg", NutrientGroup.VITAMINS, lvCode = "THIACLHCL"),
        lv("RIBF", "Riboflavin (B2)", "Riboflavin (B2)", "mg", NutrientGroup.VITAMINS),
        lv("NIA", "Niacin (B3)", "Niacin (B3)", "mg", NutrientGroup.VITAMINS),
        lv("NIAEQ", "Niacinekvivalenter", "Niacin equivalents", "mg", NutrientGroup.VITAMINS, lvUnit = "NE/mg"),
        lv("VITB6", "Vitamin B6", "Vitamin B6", "mg", NutrientGroup.VITAMINS),
        lv("FOL", "Folat", "Folate, total", "µg", NutrientGroup.VITAMINS),
        lv("VITB12", "Vitamin B12", "Vitamin B12", "µg", NutrientGroup.VITAMINS),
        lv("VITC", "Vitamin C", "Vitamin C", "mg", NutrientGroup.VITAMINS),

        lv("CA", "Kalcium", "Calcium", "mg", NutrientGroup.MINERALS),
        lv("P", "Fosfor", "Phosphorus", "mg", NutrientGroup.MINERALS),
        lv("MG", "Magnesium", "Magnesium", "mg", NutrientGroup.MINERALS),
        lv("K", "Kalium", "Potassium", "mg", NutrientGroup.MINERALS),
        lv("NA", "Natrium", "Sodium", "mg", NutrientGroup.MINERALS),
        lv("NACL", "Salt", "Salt", "g", NutrientGroup.MINERALS),
        lv("FE", "Järn", "Iron", "mg", NutrientGroup.MINERALS),
        lv("ZN", "Zink", "Zinc", "mg", NutrientGroup.MINERALS),
        lv("ID", "Jod", "Iodine", "µg", NutrientGroup.MINERALS),
        lv("SE", "Selen", "Selenium", "µg", NutrientGroup.MINERALS),
    )

    val byCode: Map<String, NutrientDef> = ALL.associateBy { it.code }

    private val byLvKey: Map<String, NutrientDef> =
        ALL.filter { it.lvCode != null }.associateBy { lvKey(it.lvCode!!, it.lvUnit!!) }

    /** Livsmedelsverket's code for the inedible share of a food. A food property, not a nutrient. */
    const val LV_WASTE = "WASTE"

    fun forLv(euroFIRkod: String, enhet: String): NutrientDef? = byLvKey[lvKey(euroFIRkod, enhet)]

    /** Code + unit, with the micro sign and the Greek mu treated as the same letter. */
    fun lvKey(code: String, unit: String) = "$code|${unit.trim().replace('μ', 'µ')}"
}
