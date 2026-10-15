package com.food.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.app.FoodApp
import com.food.app.data.Catalog
import com.food.app.data.LogEntry
import com.food.core.FoodsSchema
import com.food.core.Lv
import com.food.core.Meal
import com.food.core.Portion
import com.food.core.Total
import com.food.core.Totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The nutrients summed on the day screen; the full breakdown comes with the daily view. */
private val SUMMARY = listOf("ENERC_KCAL", "PROT", "FAT", "CHO_AVAIL", "FIBT")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    date: LocalDate,
    catalog: Catalog,
    snackbar: SnackbarHostState,
    onDate: (LocalDate) -> Unit,
    onAdd: (Meal?) -> Unit,
    onOpen: (LogEntry) -> Unit,
) {
    val app = FoodApp.app
    val entries by remember(date) { app.user.log().day(date.toString()) }.collectAsStateWithLifecycle(emptyList())
    val values by produceState(emptyMap<String, Map<String, Double>>(), entries) {
        value = withContext(Dispatchers.IO) { app.foods.values(entries.map { it.foodId }, SUMMARY) }
    }
    var about by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    fun totals(list: List<LogEntry>) = Totals.of(list.map { Portion(it.foodId, it.grams) }, values, SUMMARY)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { onDate(today) }) {
                        Text(Format.weekDay(date))
                        Text(
                            if (date == today) "Today · ${Format.longDate(date)}" else Format.longDate(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onDate(date.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                    }
                    IconButton(onClick = { onDate(date.plusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
                    }
                    IconButton(onClick = { about = true }) { Icon(Icons.Filled.Info, "About") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add food") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 96.dp)) {
            item { Summary(totals(entries), entries.isEmpty()) }
            for (meal in Meal.entries) {
                val inMeal = entries.filter { it.meal == meal.code }
                item(key = meal.code) { MealHeader(meal, totals(inMeal), inMeal.isNotEmpty()) { onAdd(meal) } }
                items(inMeal, key = { it.id }) { e -> EntryRow(e, values[e.foodId]?.get("ENERC_KCAL")) { onOpen(e) } }
            }
        }
    }

    if (about) AboutDialog(catalog) { about = false }
}

@Composable
private fun Summary(t: Map<String, Total>, empty: Boolean) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            val kcal = t.getValue("ENERC_KCAL")
            Text(
                "${atLeast(kcal)}${Format.kcal(kcal.value)} kcal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (empty) {
                Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Macro("Protein", t.getValue("PROT"))
                    Macro("Fat", t.getValue("FAT"))
                    Macro("Carbs", t.getValue("CHO_AVAIL"))
                    Macro("Fibre", t.getValue("FIBT"))
                }
            }
        }
    }
}

@Composable
private fun Macro(label: String, t: Total) {
    Column {
        Text("${atLeast(t)}${Format.value(t.value)} g", style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A total that's missing some foods' values is a lower bound. */
private fun atLeast(t: Total) = if (t.complete) "" else "≥ "

@Composable
private fun MealHeader(meal: Meal, t: Map<String, Total>, any: Boolean, onAdd: () -> Unit) {
    Column {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(meal.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (any) {
                val kcal = t.getValue("ENERC_KCAL")
                Text(
                    "${atLeast(kcal)}${Format.kcal(kcal.value)} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add to ${meal.label}") }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun EntryRow(e: LogEntry, kcalPer100g: Double?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.foodName, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${Format.amount(e.amount)} ${e.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            kcalPer100g?.let { "${Format.kcal(it * e.grams / 100)} kcal" } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun AboutDialog(catalog: Catalog, onDismiss: () -> Unit) {
    val synced = catalog.meta[FoodsSchema.Meta.LV_SYNCED_AT]
        ?.let { runCatching { Format.longDate(Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate()) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("About the data") },
        text = {
            Text(
                "Nutrient values come from ${Lv.ATTRIBUTION} (Swedish Food Agency), " +
                    "licensed ${Lv.LICENSE}. ${catalog.rows.size} foods" +
                    (synced?.let { ", downloaded $it" } ?: "") + ".\n\n" +
                    "All values are per 100 g of the edible part. Everything you log stays on this phone."
            )
        },
    )
}
