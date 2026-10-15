package com.food.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.food.core.Food
import com.food.core.FoodNutrient
import com.food.core.FoodsSchema
import com.food.core.Source
import java.io.File

/** A food as the search list needs it: names and energy, nothing else. */
data class FoodRow(
    val id: String,
    val nameSv: String,
    val nameEn: String?,
    val group: String?,
    val kcal: Double?,
)

/**
 * The read-only reference database (foods.db), bundled as an asset. It's copied to the app's
 * databases folder on first start, and again whenever a newer one ships with the app: the
 * bundled `foods.version` stamp is compared with the one installed, so a newer database
 * from the in-app update is never replaced by an older bundled one.
 */
class FoodsDb(private val ctx: Context) {

    private val db: SQLiteDatabase by lazy {
        val file = install()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun install(): File {
        val file = ctx.getDatabasePath(NAME)
        val prefs = ctx.getSharedPreferences("foods_db", Context.MODE_PRIVATE)
        val bundled = ctx.assets.open(VERSION_ASSET).bufferedReader().use { it.readText().trim() }
        val installed = prefs.getString(KEY_VERSION, null)
        if (!file.exists() || installed == null || bundled > installed) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "$NAME.tmp")
            ctx.assets.open(NAME).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(file)) { "could not install $NAME" }
            prefs.edit().putString(KEY_VERSION, bundled).apply()
        }
        return file
    }

    /** Every listed food, for search. Call off the main thread: the first call installs the database. */
    fun rows(): List<FoodRow> {
        val kcal = HashMap<String, Double>()
        db.rawQuery("SELECT food_id, value FROM food_nutrient WHERE nutrient = 'ENERC_KCAL'", null).use { c ->
            while (c.moveToNext()) kcal[c.getString(0)] = c.getDouble(1)
        }
        val rows = db.rawQuery(
            "SELECT id, name_sv, name_en, food_group FROM food WHERE removed = 0 ORDER BY name_sv", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    add(FoodRow(id, c.getString(1), c.str(2), c.str(3), kcal[id]))
                }
            }
        }
        return rows
    }

    fun meta(): Map<String, String> {
        val meta = db.rawQuery("SELECT key, value FROM meta", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        check(meta[FoodsSchema.Meta.SCHEMA] == FoodsSchema.VERSION.toString()) {
            "foods.db schema ${meta[FoodsSchema.Meta.SCHEMA]}, the app expects ${FoodsSchema.VERSION}"
        }
        return meta
    }

    /** Includes removed foods, so old log entries still open. */
    fun food(id: String): Food? = db.rawQuery(
        "SELECT id, source, source_id, name_sv, name_en, scientific_name, lv_type, food_group, note, " +
            "version, waste_pct, removed FROM food WHERE id = ?", arrayOf(id),
    ).use { c ->
        if (!c.moveToFirst()) null else Food(
            id = c.getString(0),
            source = Source.of(c.getString(1)),
            sourceId = c.getString(2),
            nameSv = c.getString(3),
            nameEn = c.str(4),
            scientificName = c.str(5),
            lvType = c.str(6),
            group = c.str(7),
            note = c.str(8),
            version = c.str(9),
            wastePct = if (c.isNull(10)) null else c.getDouble(10),
            removed = c.getInt(11) == 1,
        )
    }

    fun nutrients(foodId: String): List<FoodNutrient> = db.rawQuery(
        "SELECT nutrient, value, source, value_type, logical_zero, origin, method, comment, usda_fdc_id " +
            "FROM food_nutrient WHERE food_id = ?", arrayOf(foodId),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                FoodNutrient(
                    foodId = foodId,
                    nutrient = c.getString(0),
                    valuePer100g = c.getDouble(1),
                    source = Source.of(c.getString(2)),
                    valueType = c.str(3),
                    logicalZero = c.getInt(4) == 1,
                    origin = c.str(5),
                    method = c.str(6),
                    comment = c.str(7),
                    usdaFdcId = if (c.isNull(8)) null else c.getLong(8),
                )
            )
        }
    }

    /** foodId → (nutrient → value per 100 g), the shape [com.food.core.Totals] takes. */
    fun values(foodIds: Collection<String>, nutrients: Collection<String>? = null): Map<String, Map<String, Double>> {
        if (foodIds.isEmpty()) return emptyMap()
        val ids = foodIds.distinct()
        val out = HashMap<String, HashMap<String, Double>>()
        for (chunk in ids.chunked(500)) {
            val args = ArrayList<String>(chunk)
            var sql = "SELECT food_id, nutrient, value FROM food_nutrient WHERE food_id IN (${marks(chunk.size)})"
            if (nutrients != null) {
                sql += " AND nutrient IN (${marks(nutrients.size)})"
                args += nutrients
            }
            db.rawQuery(sql, args.toTypedArray()).use { c ->
                while (c.moveToNext()) out.getOrPut(c.getString(0)) { HashMap() }[c.getString(1)] = c.getDouble(2)
            }
        }
        return out
    }

    private fun marks(n: Int) = List(n) { "?" }.joinToString(",")
    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)

    companion object {
        const val NAME = "foods.db"
        private const val VERSION_ASSET = "foods.version"
        private const val KEY_VERSION = "installed_version"
    }
}
