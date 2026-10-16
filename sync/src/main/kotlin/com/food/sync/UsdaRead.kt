package com.food.sync

import com.food.core.Csv
import com.food.core.FdcFood
import com.food.core.FdcValue
import com.food.core.ImportedFood
import com.food.core.Usda
import java.io.File
import java.util.zip.ZipFile

/**
 * Reads FoodData Central's CSV downloads (the zips in data/raw/usda) as they come from
 * fdc.nal.usda.gov/download-datasets. Only the foods themselves are kept: SR Legacy and
 * Foundation Foods, not Foundation's samples and sub-samples.
 */
object UsdaRead {
    data class Result(val foods: List<ImportedFood>, val datasets: List<String>)

    fun read(dir: File): Result {
        val zips = dir.listFiles { f -> f.name.endsWith(".zip") }.orEmpty().sortedBy { it.name }
        val foods = ArrayList<ImportedFood>()
        val datasets = ArrayList<String>()
        for (zip in zips) ZipFile(zip).use { z ->
            fun records(name: String): Sequence<Map<String, String>> {
                val entry = z.entries().asSequence().firstOrNull { it.name.endsWith("/$name") } ?: return emptySequence()
                return Csv.records(z.getInputStream(entry).bufferedReader().lineSequence())
            }
            val folder = z.entries().asSequence().first().name.substringBefore('/')
            val categories = records("food_category.csv").associate { it.getValue("id") to it.getValue("description") }
            val numbers = records("nutrient.csv").associate { it.getValue("id") to it.getValue("nutrient_nbr") }
            val derivations = records("food_nutrient_derivation.csv").associate { it.getValue("id") to it.getValue("code") }
            val kept = records("food.csv").filter { it["data_type"] in Usda.DATA_TYPES }.associate { r ->
                r.getValue("fdc_id") to FdcFood(
                    fdcId = r.getValue("fdc_id").toLong(),
                    dataType = r.getValue("data_type"),
                    description = r.getValue("description"),
                    category = categories[r["food_category_id"]],
                    published = r["publication_date"]?.takeIf { it.isNotBlank() },
                )
            }
            val values = HashMap<String, MutableList<FdcValue>>()
            for (r in records("food_nutrient.csv")) {
                val id = r.getValue("fdc_id")
                if (id !in kept) continue
                val amount = r["amount"]?.toDoubleOrNull() ?: continue
                val nbr = numbers[r["nutrient_id"]] ?: continue
                values.getOrPut(id) { ArrayList() } += FdcValue(nbr, amount, derivations[r["derivation_id"]])
            }
            kept.forEach { (id, f) -> foods += Usda.food(f, values[id].orEmpty()) }
            datasets += folder.removePrefix("FoodData_Central_").replace("_csv_", " ").replace('_', ' ') + " (${kept.size} foods)"
        }
        return Result(foods, datasets)
    }
}
