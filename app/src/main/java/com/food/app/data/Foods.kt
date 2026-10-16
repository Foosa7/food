package com.food.app.data

import com.food.core.Fermented
import com.food.core.Food
import com.food.core.FoodId
import com.food.core.FoodNutrient
import com.food.core.FoodSearch
import com.food.core.LabelFood
import com.food.core.SearchItem
import com.food.core.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything search needs, rebuilt whenever a label food changes. */
class Catalog(
    val rows: List<FoodRow>,
    val meta: Map<String, String>,
    val version: Long,
    /** Label foods ticked as fermented. */
    private val fermentedIds: Set<String> = emptySet(),
) {
    val byId = rows.associateBy { it.id }
    val search = FoodSearch(rows.map { SearchItem(it.id, it.nameSv, it.nameEn, it.group) })

    /** Live cultures: ticked on a label food, or a fermented food by name (yours, or the database's). */
    fun fermented(foodId: String, loggedName: String): Boolean =
        foodId in fermentedIds || Fermented.byName(loggedName) || byId[foodId]?.let { Fermented.byName(it.nameSv) } == true
}

/**
 * Every food the app can log: Livsmedelsverket's (foods.db) and the user's label foods
 * (user.db, ids `my:N`). A label food's values are the label's, over its base food's.
 */
class Foods(private val ref: FoodsDb, private val user: UserDb, scope: CoroutineScope) {

    private val lvRows by lazy { ref.rows() }
    private val meta by lazy { ref.meta() }

    val catalog: StateFlow<Result<Catalog>?> = user.customFoods().all()
        .map { custom ->
            runCatching {
                val values = user.customFoods().allNutrients().groupBy { it.foodId }
                val lvById = lvRows.associateBy { it.id }
                val mine = custom.map { f ->
                    val label = values[f.id].orEmpty().associate { it.nutrient to it.value }
                    val kcal = LabelFood.complete(label)["ENERC_KCAL"] ?: f.baseFoodId?.let { lvById[it]?.kcal }
                    FoodRow(FoodId.custom(f.id), f.name, null, MY_FOODS, kcal)
                }
                Catalog(mine + lvRows, meta, System.nanoTime(), custom.filter { it.fermented }.map { FoodId.custom(it.id) }.toSet())
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Includes removed foods, so old log entries still open. */
    suspend fun food(id: String): Food? {
        val customId = FoodId.customId(id) ?: return ref.food(id)
        val f = user.customFoods().get(customId) ?: return null
        val base = f.baseFoodId?.let { ref.food(it) }
        return Food(
            id = id, source = Source.LABEL, sourceId = customId.toString(),
            nameSv = f.name, nameEn = null, scientificName = null, lvType = null, group = MY_FOODS,
            note = base?.let { "Based on ${it.nameSv}" }, version = null, wastePct = null,
        )
    }

    suspend fun nutrients(id: String): List<FoodNutrient> {
        val customId = FoodId.customId(id) ?: return ref.nutrients(id)
        val f = user.customFoods().get(customId) ?: return emptyList()
        val label = user.customFoods().nutrients(customId).associate { it.nutrient to it.value }
        val base = f.baseFoodId?.let { ref.nutrients(it) }.orEmpty()
        val baseName = f.baseFoodId?.let { ref.food(it)?.nameSv }
        return LabelFood.merge(id, label, base, baseName)
    }

    /** foodId → (nutrient → value per 100 g), the shape [com.food.core.Totals] takes. */
    suspend fun values(ids: Collection<String>, nutrients: Collection<String>? = null): Map<String, Map<String, Double>> {
        val (mine, lv) = ids.distinct().partition { FoodId.customId(it) != null }
        val out = HashMap(ref.values(lv, nutrients))
        for (id in mine) {
            val rows = nutrients(id).filter { nutrients == null || it.nutrient in nutrients }
            if (rows.isNotEmpty() || FoodId.customId(id)?.let { user.customFoods().get(it) } != null) {
                out[id] = rows.associate { it.nutrient to it.valuePer100g }
            }
        }
        return out
    }

    companion object {
        const val MY_FOODS = "My foods"
    }
}
