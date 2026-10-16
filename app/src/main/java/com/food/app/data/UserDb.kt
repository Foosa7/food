package com.food.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * One thing eaten. The food's name is copied in, so an entry still reads right if the food
 * ever leaves the database. Amount and unit are what was typed; grams is what the math uses.
 */
@Entity(tableName = "log_entry", indices = [Index("date"), Index("food_id")])
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local date, yyyy-MM-dd. */
    val date: String,
    /** A [com.food.core.Meal] code. */
    val meal: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "food_name") val foodName: String,
    val grams: Double,
    val amount: Double,
    /** "g" or "ml". */
    val unit: String,
    /** How it was logged: manual, barcode, ai. */
    val via: String = "manual",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A product's barcode, linked to the database food it matches and your usual portion of it. */
@Entity(tableName = "barcode", indices = [Index("food_id")])
data class Barcode(
    /** EAN-13 or EAN-8, check digit verified. */
    @PrimaryKey val ean: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    /** Your own name for the product, e.g. "Coop kefir 1 L". */
    val label: String,
    /** The default portion, as amount + unit like a log entry. */
    val amount: Double,
    val unit: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM barcode WHERE ean = :ean")
    suspend fun get(ean: String): Barcode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(barcode: Barcode)

    @Query("SELECT * FROM barcode ORDER BY created_at")
    suspend fun all(): List<Barcode>
}

/** A food typed in from a package label, optionally based on a database food ([baseFoodId]). */
@Entity(tableName = "custom_food")
data class CustomFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "base_food_id") val baseFoodId: String?,
    /** Has live cultures (kimchi, kefir…), for the gut count; the name rule catches most anyway. */
    @ColumnInfo(defaultValue = "0") val fermented: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A value per 100 g, as typed from the label. */
@Entity(tableName = "custom_food_nutrient", primaryKeys = ["food_id", "nutrient"])
data class CustomFoodNutrient(
    @ColumnInfo(name = "food_id") val foodId: Long,
    val nutrient: String,
    val value: Double,
)

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_food ORDER BY name")
    fun all(): Flow<List<CustomFood>>

    @Query("SELECT * FROM custom_food WHERE id = :id")
    suspend fun get(id: Long): CustomFood?

    @Query("SELECT * FROM custom_food_nutrient WHERE food_id = :id")
    suspend fun nutrients(id: Long): List<CustomFoodNutrient>

    @Query("SELECT * FROM custom_food_nutrient")
    suspend fun allNutrients(): List<CustomFoodNutrient>

    @Insert
    suspend fun insert(food: CustomFood): Long

    @Update
    suspend fun update(food: CustomFood)

    @Delete
    suspend fun delete(food: CustomFood)

    @Query("DELETE FROM custom_food_nutrient WHERE food_id = :id")
    suspend fun clearNutrients(id: Long)

    @Insert
    suspend fun insertNutrients(rows: List<CustomFoodNutrient>)

    @androidx.room.Transaction
    suspend fun save(food: CustomFood, values: Map<String, Double>): Long {
        val id = if (food.id == 0L) insert(food) else food.id.also { update(food) }
        clearNutrients(id)
        insertNutrients(values.map { (code, v) -> CustomFoodNutrient(id, code, v) })
        return id
    }

    @androidx.room.Transaction
    suspend fun remove(food: CustomFood) {
        clearNutrients(food.id)
        delete(food)
    }
}

/** What the targets are computed from. One row. */
@Entity(tableName = "profile")
data class ProfileRow(
    @PrimaryKey val id: Int = 1,
    /** "female" or "male". */
    val sex: String,
    @ColumnInfo(name = "birth_year") val birthYear: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    /** Read activity from Health Connect (Fitbit) instead of [pal]. */
    @ColumnInfo(name = "auto_activity") val autoActivity: Boolean,
    val pal: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** The user's edit of one NNR target; a null field keeps NNR's value. */
@Entity(tableName = "target_edit")
data class TargetEditRow(
    @PrimaryKey val nutrient: String,
    val low: Double?,
    val target: Double?,
    val upper: Double?,
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun profile(): Flow<ProfileRow?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun profileNow(): ProfileRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProfile(p: ProfileRow)

    @Query("SELECT * FROM target_edit")
    fun edits(): Flow<List<TargetEditRow>>

    @Query("SELECT * FROM target_edit")
    suspend fun editsNow(): List<TargetEditRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEdit(e: TargetEditRow)

    @Query("DELETE FROM target_edit WHERE nutrient = :nutrient")
    suspend fun clearEdit(nutrient: String)

    @Query("DELETE FROM target_edit")
    suspend fun clearEdits()
}

data class FoodUsage(val foodId: String, val foodName: String, val times: Int, val lastAt: Long)

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entry WHERE date = :date ORDER BY created_at")
    fun day(date: String): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun get(id: Long): LogEntry?

    /** Dates are yyyy-MM-dd, so they compare as text. */
    @Query("SELECT * FROM log_entry WHERE date BETWEEN :from AND :to ORDER BY date, created_at")
    fun between(from: String, to: String): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entry ORDER BY date, created_at")
    suspend fun all(): List<LogEntry>

    /** The last time this food was logged, for its default amount and unit. */
    @Query("SELECT * FROM log_entry WHERE food_id = :foodId ORDER BY created_at DESC LIMIT 1")
    suspend fun last(foodId: String): LogEntry?

    @Query(
        "SELECT food_id AS foodId, MAX(food_name) AS foodName, COUNT(*) AS times, MAX(created_at) AS lastAt " +
            "FROM log_entry GROUP BY food_id ORDER BY lastAt DESC"
    )
    fun usage(): Flow<List<FoodUsage>>

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Update
    suspend fun update(entry: LogEntry)

    @Delete
    suspend fun delete(entry: LogEntry)
}

/** user.db: everything the user enters. Never shipped, never in git. */
@Database(
    entities = [LogEntry::class, Barcode::class, CustomFood::class, CustomFoodNutrient::class, ProfileRow::class, TargetEditRow::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class UserDb : RoomDatabase() {
    abstract fun log(): LogDao
    abstract fun barcodes(): BarcodeDao
    abstract fun customFoods(): CustomFoodDao
    abstract fun settings(): SettingsDao

    companion object {
        fun open(ctx: Context): UserDb =
            Room.databaseBuilder(ctx, UserDb::class.java, "user.db").build()
    }
}
