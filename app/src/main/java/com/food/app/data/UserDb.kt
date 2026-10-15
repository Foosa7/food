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
}

data class FoodUsage(val foodId: String, val foodName: String, val times: Int, val lastAt: Long)

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entry WHERE date = :date ORDER BY created_at")
    fun day(date: String): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun get(id: Long): LogEntry?

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
    entities = [LogEntry::class, Barcode::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class UserDb : RoomDatabase() {
    abstract fun log(): LogDao
    abstract fun barcodes(): BarcodeDao

    companion object {
        fun open(ctx: Context): UserDb =
            Room.databaseBuilder(ctx, UserDb::class.java, "user.db").build()
    }
}
