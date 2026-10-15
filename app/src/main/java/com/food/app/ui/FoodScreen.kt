package com.food.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.food.app.FoodApp
import com.food.app.data.Barcode
import com.food.app.data.Catalog
import com.food.app.data.LogEntry
import com.food.core.AmountUnit
import com.food.core.Amounts
import com.food.core.Food
import com.food.core.FoodId
import com.food.core.FoodNutrient
import com.food.core.Lv
import com.food.core.Meal
import com.food.core.NutrientDef
import com.food.core.NutrientGroup
import com.food.core.Nutrients
import com.food.core.Source
import com.food.core.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private class Loaded(
    val food: Food?,
    val nutrients: Map<String, FoodNutrient>,
    val entry: LogEntry?,
    val last: LogEntry?,
    /** What [barcode] is linked to now, if anything. */
    val mapping: Barcode?,
)

/**
 * Add [foodId] to a day, or edit entry [entryId]. Shows every nutrient and where it came from.
 * With a [barcode]: if it's already linked to this food, its usual portion is filled in and one
 * tap logs it; if not, this links it (with a product name and usual portion) as it logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(
    foodId: String,
    date: LocalDate,
    initialMeal: Meal,
    entryId: Long?,
    barcode: String?,
    catalog: Catalog,
    onSaved: (message: String) -> Unit,
    onDeleted: (LogEntry) -> Unit,
    onRelink: (String) -> Unit,
    onEditFood: (customId: Long) -> Unit,
    onBack: () -> Unit,
) {
    val app = FoodApp.app
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(AmountUnit.G.code) }
    var meal by rememberSaveable { mutableStateOf(initialMeal.code) }
    var label by rememberSaveable { mutableStateOf("") }
    var initialised by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<NutrientDef, FoodNutrient?>?>(null) }

    // Reloads when a label food is edited (the catalog changes then).
    LaunchedEffect(foodId, entryId, catalog.version) {
        loaded = withContext(Dispatchers.IO) {
            val entry = entryId?.let { app.user.log().get(it) }
            Loaded(
                food = app.foods.food(foodId),
                nutrients = app.foods.nutrients(foodId).associateBy { it.nutrient },
                entry = entry,
                last = if (entry == null) app.user.log().last(foodId) else null,
                mapping = barcode?.let { app.user.barcodes().get(it) },
            )
        }
        if (!initialised) {
            // Editing: what was logged. A known barcode: its usual portion. Otherwise what you
            // had last time, else 100 g.
            val l = loaded
            val known = l?.mapping?.takeIf { it.foodId == foodId }
            val amount = l?.entry?.amount ?: known?.amount ?: l?.last?.amount
            amountText = amount?.let(Format::amount) ?: "100"
            unit = l?.entry?.unit ?: known?.unit ?: l?.last?.unit ?: AmountUnit.G.code
            label = l?.mapping?.label.orEmpty()
            initialised = true
        }
    }

    val l = loaded
    val amount = Format.parseAmount(amountText)
    val grams = amount?.let { Amounts.grams(it, AmountUnit.of(unit)) }
    val name = l?.food?.nameSv ?: l?.entry?.foodName ?: ""
    val known = barcode != null && l?.mapping?.foodId == foodId
    val linking = barcode != null && !known && entryId == null

    /** Saves the entry (unless [logIt] is false) and, when linking, the barcode. */
    fun save(logIt: Boolean = true) {
        val g = grams ?: return
        val a = amount ?: return
        val now = System.currentTimeMillis()
        val productName = label.trim().ifEmpty { name }
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                if (linking) app.user.barcodes().put(
                    Barcode(barcode!!, foodId, productName, a, unit, l?.mapping?.createdAt ?: now, now)
                )
                val old = l?.entry
                when {
                    old != null -> {
                        app.user.log().update(old.copy(meal = meal, grams = g, amount = a, unit = unit, updatedAt = now))
                        "Saved ${old.foodName}"
                    }
                    logIt -> {
                        app.user.log().insert(
                            LogEntry(
                                date = date.toString(), meal = meal, foodId = foodId,
                                // A scanned product reads better under your own name for it.
                                foodName = if (barcode != null) productName else name,
                                grams = g, amount = a, unit = unit,
                                via = if (barcode != null) "barcode" else "manual",
                                createdAt = now, updatedAt = now,
                            )
                        )
                        "Logged $productName" + if (linking) " · barcode saved" else ""
                    }
                    else -> "Barcode saved for $productName"
                }
            }
            onSaved(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(if (entryId == null) "Add food" else "Edit entry") },
                actions = {
                    FoodId.customId(foodId)?.let { id ->
                        if (l?.food != null) TextButton(onClick = { onEditFood(id) }) { Text("Edit food") }
                    }
                    val entry = l?.entry
                    if (entry != null) IconButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { app.user.log().delete(entry) }
                            onDeleted(entry)
                        }
                    }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
    ) { pad ->
        if (l == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 32.dp),
        ) {
            item { Header(l.food, name) }
            if (barcode != null) item {
                BarcodeCard(barcode, known, l.mapping, label, { label = it }, onRelink = { onRelink(barcode) })
            }
            item {
                AmountRow(
                    amountText = amountText, onAmount = { amountText = it },
                    unit = unit, onUnit = { unit = it },
                    valid = amount != null,
                )
            }
            item { MealChips(meal) { meal = it } }
            item { QuickTotals(l.nutrients, grams) }
            item {
                Button(
                    onClick = ::save,
                    enabled = grams != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        when {
                            l.entry != null -> "Save"
                            linking -> "Save barcode & add to ${Meal.of(meal).label}"
                            else -> "Add to ${Meal.of(meal).label}"
                        }
                    )
                }
            }
            if (linking) item {
                OutlinedButton(
                    onClick = { save(logIt = false) },
                    enabled = grams != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("Only save the barcode") }
            }
            if (l.food == null) {
                item {
                    Text(
                        "This food is no longer in the database, so its nutrients are unknown.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                for (group in NutrientGroup.entries) {
                    val defs = Nutrients.ALL.filter { it.group == group }
                    if (defs.isEmpty()) continue
                    item(key = group.name) { GroupHeader(group) }
                    items(defs, key = { it.code }) { def ->
                        NutrientRow(def, l.nutrients[def.code], grams) { detail = def to l.nutrients[def.code] }
                    }
                }
                item { Attribution(l.food) }
            }
        }
    }

    detail?.let { (def, value) -> SourceDialog(def, value) { detail = null } }
}

@Composable
private fun Header(food: Food?, name: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        food?.nameEn?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (food != null) {
            val kind = when {
                food.source == Source.LABEL -> listOfNotNull("from a label", food.note?.replaceFirstChar { it.lowercase() }).joinToString(", ")
                food.lvType == "Analyserat" -> "analysed"
                food.lvType == "Beräknat" -> "calculated from a recipe"
                else -> food.lvType
            }
            Text(
                listOfNotNull(food.group, kind).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val waste = food.wastePct ?: 0.0
            if (waste > 0) {
                Text(
                    "Weigh the edible part: ${Format.value(waste)}% of the whole (peel, bones, etc.) isn't counted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarcodeCard(
    ean: String,
    known: Boolean,
    mapping: Barcode?,
    label: String,
    onLabel: (String) -> Unit,
    onRelink: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (known && mapping != null) {
                Text(mapping.label, style = MaterialTheme.typography.titleMedium)
                Text("Barcode $ean", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRelink, contentPadding = PaddingValues(0.dp)) { Text("Wrong food? Link it to another") }
            } else {
                Text(
                    if (mapping != null) "Barcode $ean, moving from ${mapping.label}" else "New barcode $ean",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = onLabel,
                    label = { Text("Product name") },
                    placeholder = { Text("e.g. Coop kefir 1 L") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The amount below becomes its usual portion. Next time, one tap logs it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountRow(amountText: String, onAmount: (String) -> Unit, unit: String, onUnit: (String) -> Unit, valid: Boolean) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = amountText,
            onValueChange = onAmount,
            label = { Text("Amount") },
            singleLine = true,
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        SingleChoiceSegmentedButtonRow {
            AmountUnit.entries.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = unit == u.code,
                    onClick = { onUnit(u.code) },
                    shape = SegmentedButtonDefaults.itemShape(i, AmountUnit.entries.size),
                ) { Text(u.code) }
            }
        }
    }
    if (unit == AmountUnit.ML.code) {
        Text(
            "1 ml is counted as 1 g.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun MealChips(meal: String, onMeal: (String) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Meal.entries.forEach { m ->
            FilterChip(selected = meal == m.code, onClick = { onMeal(m.code) }, label = { Text(m.label) })
        }
    }
}

@Composable
private fun QuickTotals(values: Map<String, FoodNutrient>, grams: Double?) {
    fun v(code: String) = values[code]?.let { grams?.let { g -> scale(it.valuePer100g, g) } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Quick("kcal", v("ENERC_KCAL")?.let(Format::kcal))
        Quick("Protein", v("PROT")?.let { Format.value(it) + " g" })
        Quick("Fat", v("FAT")?.let { Format.value(it) + " g" })
        Quick("Carbs", v("CHO_AVAIL")?.let { Format.value(it) + " g" })
        Quick("Fibre", v("FIBT")?.let { Format.value(it) + " g" })
    }
}

@Composable
private fun Quick(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val GROUP_LABELS = mapOf(
    NutrientGroup.ENERGY to "Energy",
    NutrientGroup.MACRO to "Macronutrients",
    NutrientGroup.CARBS to "Carbohydrates",
    NutrientGroup.FATS to "Fats",
    NutrientGroup.FATTY_ACIDS to "Fatty acids",
    NutrientGroup.VITAMINS to "Vitamins",
    NutrientGroup.MINERALS to "Minerals",
    NutrientGroup.OTHER to "Other",
)

@Composable
private fun GroupHeader(group: NutrientGroup) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(GROUP_LABELS.getValue(group), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("amount  ·  per 100 g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun NutrientRow(def: NutrientDef, value: FoodNutrient?, grams: Double?, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(def.nameEn, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        when {
            value == null -> Text("no data", style = MaterialTheme.typography.bodySmall, color = muted)
            else -> {
                val forAmount = grams?.let { scale(value.valuePer100g, it) }
                Text(
                    (forAmount?.let(Format::value) ?: "—") + " ${def.unit}" + if (value.logicalZero) " *" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "  ·  ${Format.value(value.valuePer100g)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                SourceTag(value.source)
            }
        }
    }
}

@Composable
private fun SourceTag(source: Source) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            source.code,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun SourceDialog(def: NutrientDef, value: FoodNutrient?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(def.nameEn) },
        text = {
            Text(
                if (value == null) {
                    "The source has no value for this food. It's left out of totals, which then show as \"at least\"."
                } else listOfNotNull(
                    "${Format.value(value.valuePer100g)} ${def.unit} per 100 g",
                    "Source: " + when (value.source) {
                        Source.LV -> Lv.ATTRIBUTION
                        Source.USDA -> "USDA FoodData Central" + (value.usdaFdcId?.let { " #$it" } ?: "")
                        Source.LABEL -> "Product label"
                        Source.MANUAL -> "Entered by hand"
                    },
                    value.valueType?.let { "Value type: ${Lv.VALUE_TYPES[it] ?: it}" },
                    value.origin?.let { "Origin: $it" },
                    value.method?.let { "Method: $it" },
                    value.comment?.let { "Note: $it" },
                    if (value.logicalZero) "* An assumed zero (\"Logisk nolla\"), not a measurement." else null,
                ).joinToString("\n")
            )
        },
    )
}

@Composable
private fun Attribution(food: Food) {
    Column(Modifier.padding(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            "* assumed zero. Tap a row to see where its value came from.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (food.source == Source.LV) {
            food.note?.let {
                Spacer(Modifier.height(8.dp))
                Text("Analysed: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Source: ${Lv.ATTRIBUTION} (${Lv.LICENSE}), food #${food.sourceId}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Values tagged label are from the package. Values tagged LV come from the base food, " +
                    "${Lv.ATTRIBUTION} (${Lv.LICENSE}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
