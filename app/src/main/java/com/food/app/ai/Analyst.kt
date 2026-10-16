package com.food.app.ai

import android.content.Context
import com.food.app.FoodApp
import com.food.app.data.Catalog
import com.food.app.data.TargetsState
import com.food.core.AmountUnit
import com.food.core.Analyses
import com.food.core.Analysis
import com.food.core.DietDigest
import com.food.core.DigestDay
import com.food.core.DigestFood
import com.food.core.DigestFoodInfo
import com.food.core.DigestInput
import com.food.core.Meal
import com.food.core.Portion
import com.food.core.Source
import com.food.core.Totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Reads the whole log the way [DietDigest] lays it out, asks the model for a short take, and
 * keeps every answer in `files/ai/analyses.jsonl`. The model's notes carry over to the next
 * run, so it follows the diet over time rather than starting fresh each time.
 */
object Analyst {
    sealed interface State {
        data object Idle : State
        data class Running(val since: Long) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _saved = MutableStateFlow<List<Analysis>?>(null)
    /** Oldest first; null until read from disk. */
    val saved: StateFlow<List<Analysis>?> = _saved.asStateFlow()

    private fun file(ctx: Context) = File(ctx.filesDir, "ai/analyses.jsonl")

    suspend fun load(ctx: Context) {
        if (_saved.value != null) return
        _saved.value = withContext(Dispatchers.IO) { file(ctx).takeIf { it.exists() }?.let { Analyses.lines(it.readText()) }.orEmpty() }
    }

    /** Runs in the app's scope, so leaving the tab doesn't stop it. */
    fun run(ctx: Context, catalog: Catalog, targets: TargetsState?) {
        if (_state.value is State.Running) return
        val app = FoodApp.app
        _state.value = State.Running(System.currentTimeMillis())
        app.scope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching {
                val key = AiSettings.key(ctx)
                check(key.isNotBlank()) { "Add your Kimi API key first (⚙ at the top)." }
                load(ctx)
                val earlier = _saved.value.orEmpty()
                val (input, days) = input(ctx, catalog, targets, earlier)
                check(days > 0) { "Log some food first: there's nothing to look at yet." }
                val model = AiSettings.model(ctx)
                val effort = AiSettings.effort(ctx)
                val reply = Kimi.chat(key, model, effort, SYSTEM, DietDigest.build(input)).getOrThrow()
                val a = Analyses.parse(reply) ?: error("The answer wasn't in the expected shape. Try again.")
                a.copy(
                    at = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString(),
                    model = model,
                    effort = effort,
                    seconds = ((System.currentTimeMillis() - started) / 1000).toInt(),
                    days = days,
                )
            }
            result.onSuccess { a ->
                withContext(Dispatchers.IO) {
                    val f = file(ctx)
                    f.parentFile?.mkdirs()
                    f.appendText(Analyses.line(a) + "\n")
                }
                _saved.value = _saved.value.orEmpty() + a
                _state.value = State.Idle
            }.onFailure { _state.value = State.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    /** The digest's input from the log: [DietDigest.WEEKS] whole weeks back. Also returns how many days are logged. */
    private suspend fun input(ctx: Context, catalog: Catalog, targets: TargetsState?, earlier: List<Analysis>): Pair<DigestInput, Int> =
        withContext(Dispatchers.IO) {
            val app = FoodApp.app
            val today = LocalDate.now()
            val from = today.with(DayOfWeek.MONDAY).minusWeeks(DietDigest.WEEKS - 1L)
            val entries = app.user.log().all().filter { it.date >= from.toString() && it.date <= today.toString() }
            val values = app.foods.values(entries.map { it.foodId })
            val days = entries.groupBy { it.date }.map { (date, list) ->
                DigestDay(
                    LocalDate.parse(date),
                    list.map {
                        DigestFood(
                            Meal.of(it.meal), it.foodName, it.grams, catalog.fermented(it.foodId, it.foodName), it.foodId,
                            amount = if (it.unit == AmountUnit.PIECE.code) "${DietDigest.num(it.amount)} st" else null,
                            supplement = it.foodId in catalog.supplementIds,
                        )
                    },
                    Totals.of(list.map { Portion(it.foodId, it.grams) }, values),
                )
            }
            // Each food once: what it is, where its numbers come from, and every value per 100 g.
            val foods = entries.map { it.foodId }.distinct().associateWith { id ->
                val f = app.foods.food(id)
                val name = f?.let { it.nameSv + (it.nameEn?.let { en -> " ($en)" }.orEmpty()) } ?: entries.first { it.foodId == id }.foodName
                val source = when {
                    f == null -> "no longer in the database"
                    f.source == Source.LABEL -> "their own food from a package label" +
                        (f.note?.removePrefix("Based on ")?.let { "; what the label doesn't list comes from the database food \"$it\"" }.orEmpty())
                    f.source == Source.USDA -> "${f.note ?: "USDA FoodData Central"}, FDC ${f.sourceId}"
                    else -> "Livsmedelsverket food ${f.sourceId}" + (f.lvType?.let { ", ${if (it.startsWith("Ber")) "calculated" else "analysed"}" }.orEmpty())
                }
                // A supplement's assumed zeros only count in the totals; Kimi reads what its label lists.
                val per100g = values[id].orEmpty().let { v -> if (f?.supplement == true) v.filterValues { it != 0.0 } else v }
                DigestFoodInfo(name, if (f?.supplement == true) "$source; a supplement" else source, per100g)
            }
            DigestInput(
                today = today,
                profile = targets?.profile,
                energyNote = targets?.energy?.how,
                targets = targets?.targets,
                days = days,
                foods = foods,
                nnr = targets?.nnr,
                goals = AiSettings.goals(ctx),
                notes = earlier.lastOrNull()?.notes.orEmpty(),
                earlier = earlier.takeLast(5).map { it.at.replace('T', ' ') to it.progress },
            ) to days.size
        }

    private val SYSTEM = """
        You are a sharp, friendly nutrition coach. You read one person's food log from their Swedish
        tracker app. You get all of it: what the Nordic Nutrition Recommendations 2023 (NNR) say an
        adult of their sex, age, size and activity needs, nutrient by nutrient; weekly averages of
        every macro- and micronutrient; for every nutrient, which foods it came from and how much;
        every food they ate with all its values per 100 g; and the last two weeks portion by portion
        with what each portion gave. Values come from Livsmedelsverket's food database, USDA
        FoodData Central, or a package label for their own foods. Use these numbers rather than general knowledge about
        foods: they are what this person actually ate.

        How to read the data:
        - A day that isn't logged is unknown, never zero. A logged day with very little energy was
          probably only partly logged: say that rather than calling out deficiencies from it.
        - "≥" means some foods had no value, so the true amount is at least that. "?" means no value at all.
        - Micronutrient targets are averages over time. Judge on the weekly averages, and look for
          trends between weeks, not single days.
        - "[fermented]" marks foods with live cultures; fibre and fermented servings together are
          their gut-food count.
        - "[supplement]" marks a supplement, taken only on the days it's logged. NNR's upper limit
          for magnesium (250 mg a day) applies to supplements alone, not food; the zinc (25 mg) and
          vitamin B6 (12 mg) upper limits count everything. Say how much of a nutrient came from
          food and how much from supplements.
        - When you name a gap or an excess, name the foods behind it from "Where each nutrient comes
          from", and when you suggest a food, say what it would add in numbers.

        What to write: specific, and short per point. Name foods they already eat. Prefer small swaps and
        additions over overhauls. Their own aim comes first if they wrote one. No diagnoses; only
        suggest a doctor or a blood test for a large gap that persists across weeks.

        Go through every nutrient, one by one. Cover every vitamin and mineral that has a target, and
        also energy, protein, fat, saturated fat, monounsaturated and polyunsaturated fat, carbohydrates,
        free sugars, fibre, salt, alcohol, and EPA+DHA (long-chain omega-3: no NNR target, but 0.25 g a day
        is EFSA's adequate intake). Keep the order of "What an adult like them needs". For each:
        - "good": the usual intake meets the RI/AI or sits within the range or under the limit.
        - "low": the usual intake is below the RI/AI or the range. Say whether it's also below the AR.
        - "over": above an upper limit, a "keep under" limit, or the range.
        - "unknown": too little data: values missing ("?" or a large "≥" gap), or too few logged days.
        Judge on the average logged day across the weeks, not one day. The comment names the foods it
        comes from (from "Where each nutrient comes from") and, for low or over, one concrete change
        with what it would add or remove in numbers. One or two sentences.

        Reply with one JSON object and nothing else:
        {
          "progress": "2-3 sentences: how their eating is going over time, with numbers. Compare with what you said before, if anything.",
          "nutrients": [
            {"nutrient": "Vitamin D", "verdict": "good | low | over | unknown", "intake": "9.1 µg a day", "target": "10 µg (RI)", "comment": "..."}
          ],
          "suggestions": ["up to 3 changes that would help most this week, each under 20 words"],
          "notes": "Your private notes for next time, under 150 words: patterns, what you suggested, what to check."
        }
    """.trimIndent()
}
