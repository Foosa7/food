package com.food.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.food.core.Lv
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Everything you've entered, as a readable JSON file plus a copy of user.db, handed to the
 * share sheet (Drive, email, a computer). The food database itself isn't included; it's public.
 */
object Export {

    suspend fun files(ctx: Context, user: UserDb): List<File> {
        val dir = File(ctx.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        val stamp = LocalDate.now().toString()

        val json = JSONObject()
            .put("app", "food")
            .put("exported_at", Instant.now().toString())
            .put("nutrient_data", "${Lv.ATTRIBUTION} (${Lv.LICENSE}); values per 100 g edible part")
            .put("profile", user.settings().profileNow()?.let {
                JSONObject().put("sex", it.sex).put("birth_year", it.birthYear).put("weight_kg", it.weightKg)
                    .put("height_cm", it.heightCm).put("auto_activity", it.autoActivity).put("pal", it.pal)
            } ?: JSONObject.NULL)
            .put("target_edits", JSONArray(user.settings().editsNow().map {
                JSONObject().put("nutrient", it.nutrient).put("low", it.low ?: JSONObject.NULL)
                    .put("target", it.target ?: JSONObject.NULL).put("upper", it.upper ?: JSONObject.NULL)
            }))
            .put("log", JSONArray(user.log().all().map {
                JSONObject().put("date", it.date).put("meal", it.meal).put("food_id", it.foodId).put("food_name", it.foodName)
                    .put("amount", it.amount).put("unit", it.unit).put("grams", it.grams).put("via", it.via)
                    .put("logged_at", Instant.ofEpochMilli(it.createdAt).toString())
            }))
            .put("barcodes", JSONArray(user.barcodes().all().map {
                JSONObject().put("ean", it.ean).put("food_id", it.foodId).put("label", it.label)
                    .put("amount", it.amount).put("unit", it.unit)
            }))
            .put("custom_foods", run {
                val values = user.customFoods().allNutrients().groupBy { it.foodId }
                JSONArray(user.customFoods().all().first().map { f ->
                    JSONObject().put("id", "my:${f.id}").put("name", f.name)
                        .put("base_food_id", f.baseFoodId ?: JSONObject.NULL)
                        .put("per_100g", JSONObject(values[f.id].orEmpty().associate { it.nutrient to it.value }))
                })
            })
        val jsonFile = File(dir, "food-export-$stamp.json").apply { writeText(json.toString(2)) }

        // Flush the write-ahead log so the copy is complete on its own.
        user.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbFile = File(dir, "food-user-$stamp.db")
        ctx.getDatabasePath("user.db").copyTo(dbFile, overwrite = true)
        return listOf(jsonFile, dbFile)
    }

    fun share(ctx: Context, files: List<File>) {
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "food export ${LocalDate.now()}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Export your food data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
