package com.food.sync

import com.food.core.FoodsSchema
import com.food.core.ImportedFood
import com.food.core.Lv
import com.food.core.LvFood
import com.food.core.LvImport
import com.food.core.Nutrients
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/** Rebuilds foods.db from the raw cache, from scratch, so the result never depends on history. */
object LvBuild {

    data class Result(val foods: List<ImportedFood>, val listed: Int, val out: File, val syncedAt: Instant)

    /** [usda] foods go in next to Livsmedelsverket's; [usdaDatasets] says which downloads they came from. */
    fun run(cache: File, out: File, now: Instant = Instant.now(), usda: List<ImportedFood> = emptyList(), usdaDatasets: List<String> = emptyList()): Result {
        val listed = readFoods(File(cache, "list-sv.json")).map { it.nummer }.toSet()
        val sv = readFoods(File(cache, "seen-sv.json").takeIf { it.exists() } ?: File(cache, "list-sv.json"))
        val en = readFoods(File(cache, "seen-en.json").takeIf { it.exists() } ?: File(cache, "list-en.json"))
            .associateBy { it.nummer }

        val missing = sv.filter { it.nummer in listed && !File(cache, "n/${it.nummer}.json").exists() }
        check(missing.isEmpty()) { "no cached nutrients for ${missing.map { it.nummer }}; run fetch first" }

        val foods = sv.filter { File(cache, "n/${it.nummer}.json").exists() }.map { food ->
            LvImport.food(
                sv = food,
                en = en[food.nummer],
                nutrients = Lv.parseNutrients(File(cache, "n/${food.nummer}.json").readText()),
                classes = File(cache, "k/${food.nummer}.json").takeIf { it.exists() }
                    ?.let { Lv.parseClasses(it.readText()) }.orEmpty(),
                removed = food.nummer !in listed,
            )
        }

        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, out.name + ".tmp")
        tmp.delete()
        DriverManager.getConnection("jdbc:sqlite:${tmp.path}").use { db ->
            write(db, foods + usda, listed.size, now, usdaDatasets)
        }
        check(tmp.renameTo(out)) { "could not write $out" }
        // The app reads this small file to tell whether its bundled foods.db is newer than the one
        // on the phone, without opening a 14 MB database.
        File(out.parentFile, "foods.version").writeText(now.toString())
        return Result(foods, listed.size, out, now)
    }

    private fun write(db: Connection, foods: List<ImportedFood>, listed: Int, now: Instant, usdaDatasets: List<String>) {
        db.autoCommit = false
        db.createStatement().use { s ->
            FoodsSchema.DDL.forEach(s::execute)
            s.execute("PRAGMA user_version = ${FoodsSchema.VERSION}")
        }

        db.prepareStatement("INSERT INTO meta VALUES (?, ?)").use { st ->
            for ((k, v) in listOf(
                FoodsSchema.Meta.SCHEMA to FoodsSchema.VERSION.toString(),
                FoodsSchema.Meta.LV_SYNCED_AT to now.toString(),
                FoodsSchema.Meta.LV_FOODS to listed.toString(),
                FoodsSchema.Meta.ATTRIBUTION to Lv.ATTRIBUTION,
                FoodsSchema.Meta.LICENSE to Lv.LICENSE,
                FoodsSchema.Meta.USDA_DATASETS to usdaDatasets.joinToString(", "),
            )) { st.setString(1, k); st.setString(2, v); st.addBatch() }
            st.executeBatch()
        }

        db.prepareStatement("INSERT INTO nutrient VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { st ->
            Nutrients.ALL.forEachIndexed { i, n ->
                st.setString(1, n.code); st.setString(2, n.nameSv); st.setString(3, n.nameEn)
                st.setString(4, n.unit); st.setString(5, n.group.name); st.setInt(6, i)
                st.setString(7, n.lvCode); st.setString(8, n.lvUnit)
                st.addBatch()
            }
            st.executeBatch()
        }

        val foodSt = db.prepareStatement("INSERT INTO food VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        val valueSt = db.prepareStatement("INSERT INTO food_nutrient VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        for ((f, rows) in foods) {
            foodSt.setString(1, f.id); foodSt.setString(2, f.source.code); foodSt.setString(3, f.sourceId)
            foodSt.setString(4, f.nameSv); foodSt.setString(5, f.nameEn); foodSt.setString(6, f.scientificName)
            foodSt.setString(7, f.lvType); foodSt.setString(8, f.group); foodSt.setString(9, f.note)
            foodSt.setString(10, f.version); foodSt.setObject(11, f.wastePct); foodSt.setInt(12, if (f.removed) 1 else 0)
            foodSt.addBatch()
            for (r in rows) {
                valueSt.setString(1, r.foodId); valueSt.setString(2, r.nutrient); valueSt.setDouble(3, r.valuePer100g)
                valueSt.setString(4, r.source.code); valueSt.setString(5, r.valueType)
                valueSt.setInt(6, if (r.logicalZero) 1 else 0); valueSt.setString(7, r.origin)
                valueSt.setString(8, r.method); valueSt.setString(9, r.comment); valueSt.setObject(10, r.usdaFdcId)
                valueSt.addBatch()
            }
        }
        foodSt.executeBatch(); valueSt.executeBatch()
        foodSt.close(); valueSt.close()
        db.commit()
        db.autoCommit = true
        db.createStatement().use { it.execute("VACUUM") }
    }

    private fun readFoods(file: File): List<LvFood> =
        Lv.json.decodeFromString(ListSerializer(LvFood.serializer()), file.readText())
}
