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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.app.FoodApp
import com.food.app.data.Catalog
import com.food.app.data.LogEntry
import com.food.app.data.TargetsState
import com.food.core.Assess
import com.food.core.Assessment
import com.food.core.Nnr2023
import com.food.core.NutrientDef
import com.food.core.NutrientGroup
import com.food.core.Nutrients
import com.food.core.Portion
import com.food.core.Status
import com.food.core.Target
import com.food.core.Total
import com.food.core.Totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** One day's totals, or the average of the logged days in a range. */
class Intake(val entries: List<LogEntry>, val values: Map<String, Map<String, Double>>) {
    val days: Int = entries.map { it.date }.distinct().size

    /** Average over logged days only: a day with nothing logged is unknown, not zero. */
    val totals: Map<String, Total> = run {
        val perDay = entries.groupBy { it.date }.values.map { list ->
            Totals.of(list.map { Portion(it.foodId, it.grams) }, values)
        }
        Nutrients.ALL.associate { def ->
            val ts = perDay.map { it.getValue(def.code) }
            def.code to Total(
                def.code,
                if (ts.isEmpty()) 0.0 else ts.sumOf { it.value } / ts.size,
                ts.sumOf { it.portions },
                ts.sumOf { it.missing },
            )
        }
    }

    val energyKj get() = totals.getValue("ENERC_KJ").value
    val fibre get() = totals.getValue("FIBT").value

    fun assess(t: Target): Assessment = Assess.of(t, totals[t.nutrient]?.value ?: 0.0, energyKj, fibre)

    /** Which foods gave the most of [code], summed by name, per average day. */
    fun contributors(code: String): List<Pair<String, Double>> =
        entries.groupBy { it.foodName }
            .mapValues { (_, list) -> list.sumOf { e -> (values[e.foodId]?.get(code) ?: 0.0) * e.grams / 100 } / days.coerceAtLeast(1) }
            .filterValues { it > 0 }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutrientsScreen(
    date: LocalDate,
    catalog: Catalog,
    targets: TargetsState?,
    onProfile: () -> Unit,
    onHistory: (String) -> Unit,
    onBack: () -> Unit,
) {
    val app = FoodApp.app
    var week by rememberSaveable { mutableStateOf(false) }
    val from = if (week) date.minusDays(6) else date
    val entries by remember(from, date) { app.user.log().between(from.toString(), date.toString()) }
        .collectAsStateWithLifecycle(emptyList())
    val intake by produceState<Intake?>(null, entries, catalog.version) {
        value = withContext(Dispatchers.IO) { Intake(entries, app.foods.values(entries.map { it.foodId })) }
    }
    var detail by remember { mutableStateOf<NutrientDef?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text("Nutrients")
                        Text(
                            if (week) "${Format.shortDate(from)} – ${Format.shortDate(date)}, average day" else Format.shortDate(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { pad ->
        val i = intake
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 32.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    listOf(false to "This day", true to "Last 7 days").forEachIndexed { idx, (w, label) ->
                        SegmentedButton(selected = week == w, onClick = { week = w }, shape = SegmentedButtonDefaults.itemShape(idx, 2)) {
                            Text(label)
                        }
                    }
                }
            }
            if (targets == null) item { ProfilePrompt(onProfile) }
            if (i == null) return@LazyColumn
            if (i.days == 0) {
                item { Text("Nothing logged ${if (week) "in these 7 days" else "this day"}.", Modifier.padding(16.dp)) }
                return@LazyColumn
            }
            item { Overview(i, targets, week, date) }
            for (group in NutrientGroup.entries) {
                val defs = Nutrients.ALL.filter { it.group == group && it.code != "ENERC_KCAL" }
                if (defs.isEmpty()) continue
                item(key = group.name) {
                    Column(Modifier.padding(top = 16.dp)) {
                        Text(groupLabel(group), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
                items(defs, key = { it.code }) { def ->
                    val t = targets?.targets?.get(def.code)
                    NutrientTargetRow(def, i.totals.getValue(def.code), t, t?.let(i::assess)) { detail = def }
                }
            }
            item {
                Text(
                    "Targets: Nordic Nutrition Recommendations 2023. A single day says little; the 7-day " +
                        "average is what matters. Totals marked ≥ leave out foods with no value for that nutrient.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    val i = intake
    detail?.let { def ->
        if (i != null) ContributorsDialog(def, i, targets?.targets?.get(def.code), week, onHistory = { onHistory(def.code) }) { detail = null }
    }
}

@Composable
private fun ProfilePrompt(onProfile: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Set up your profile to see targets", style = MaterialTheme.typography.titleMedium)
            Text("Sex, age, weight and height pick your NNR 2023 targets. Every one can be edited.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onProfile) { Text("Set up profile") }
        }
    }
}

@Composable
private fun Overview(i: Intake, targets: TargetsState?, week: Boolean, date: LocalDate) {
    val kcal = i.totals.getValue("ENERC_KCAL")
    val t = targets?.targets?.get("ENERC_KCAL")
    val a = t?.let(i::assess)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${if (kcal.complete) "" else "≥ "}${Format.kcal(kcal.value)} kcal",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (t != null) Text("  ${targetText(t, "kcal")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val pct = a?.percentOfTarget
            if (pct != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (pct / 100).toFloat().coerceIn(0f, 1f) },
                    color = statusColor(a?.status),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            targets?.energy?.let {
                Text(it.how, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (week) Text("${i.days} of 7 days logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Where the energy came from, in % of energy.
            val kj = i.energyKj
            if (kj > 0) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    fun pct(code: String, f: Double) = Format.value(i.totals.getValue(code).value * f / kj * 100) + "%"
                    Split("Protein", pct("PROT", Nnr2023.KjPerGram.PROTEIN))
                    Split("Fat", pct("FAT", Nnr2023.KjPerGram.FAT))
                    Split("Carbs", Format.value((i.totals.getValue("CHO_AVAIL").value * Nnr2023.KjPerGram.CARBS +
                        i.fibre * Nnr2023.KjPerGram.FIBRE) / kj * 100) + "%")
                    Split("Alcohol", pct("ALC", Nnr2023.KjPerGram.ALCOHOL))
                }
            }

            if (targets != null) {
                val flagged = targets.targets.values.mapNotNull { tt ->
                    val s = i.assess(tt).status
                    if (s == Status.LOW || s == Status.OVER) Nutrients.byCode[tt.nutrient]?.let { it.nameEn to s } else null
                }
                if (flagged.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val low = flagged.filter { it.second == Status.LOW }.map { it.first }
                    val over = flagged.filter { it.second == Status.OVER }.map { it.first }
                    val soFar = !week && date == LocalDate.now()
                    if (low.isNotEmpty()) Text("${if (soFar) "Low so far" else "Low"}: ${low.joinToString(", ")}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (over.isNotEmpty()) Text("Over: ${over.joinToString(", ")}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Split(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NutrientTargetRow(def: NutrientDef, total: Total, target: Target?, a: Assessment?, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.nameEn, modifier = Modifier.weight(1f))
            val none = total.portions > 0 && total.missing == total.portions
            Text(
                if (none) "no data" else "${if (total.complete) "" else "≥ "}${Format.value(total.value)} ${def.unit}",
                fontWeight = FontWeight.Medium,
            )
        }
        if (target != null && a != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    targetText(target, def.unit) + (a.energyPercent?.let { " · now ${Format.value(it)}%" }
                        ?: a.percentOfTarget?.let { " · ${Math.round(it)}%" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f),
                )
                statusLabel(a.status, target.kind)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = statusColor(a.status))
                }
            }
            a.percentOfTarget?.let { pct ->
                LinearProgressIndicator(
                    progress = { (pct / 100).toFloat().coerceIn(0f, 1f) },
                    color = statusColor(a.status),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        if (!total.complete && total.missing < total.portions) {
            Text("${total.missing} of ${total.portions} foods have no value for this", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

@Composable
private fun ContributorsDialog(def: NutrientDef, i: Intake, target: Target?, week: Boolean, onHistory: () -> Unit, onDismiss: () -> Unit) {
    val top = i.contributors(def.code)
    val total = top.sumOf { it.second }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onDismiss(); onHistory() }) { Text("History") } },
        title = { Text(def.nameEn) },
        text = {
            Column {
                target?.note?.let { Text(it, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
                if (top.isEmpty()) Text("None of ${if (week) "these days'" else "this day's"} foods has any.")
                else {
                    Text(if (week) "Where it came from, per average day:" else "Where it came from:", style = MaterialTheme.typography.labelLarge)
                    top.take(8).forEach { (name, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("${Format.value(v)} ${def.unit} · ${Math.round(v / total * 100)}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}

fun groupLabel(group: NutrientGroup) = when (group) {
    NutrientGroup.ENERGY -> "Energy"
    NutrientGroup.MACRO -> "Macronutrients"
    NutrientGroup.CARBS -> "Carbohydrates"
    NutrientGroup.FATS -> "Fats"
    NutrientGroup.FATTY_ACIDS -> "Fatty acids"
    NutrientGroup.VITAMINS -> "Vitamins"
    NutrientGroup.MINERALS -> "Minerals"
    NutrientGroup.OTHER -> "Other"
}
