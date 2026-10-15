package com.food.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.app.FoodApp
import com.food.app.data.Catalog
import com.food.app.data.TargetsState
import com.food.core.Nnr2023
import com.food.core.Nutrients
import com.food.core.Target
import com.food.core.TargetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.WeekFields

private const val DAYS = 28

/** One nutrient over the last four weeks: each day, and each week's average over logged days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(catalog: Catalog, targets: TargetsState?, initial: String, onBack: () -> Unit) {
    val app = FoodApp.app
    var code by rememberSaveable { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val def = Nutrients.byCode.getValue(code)
    val today = LocalDate.now()
    val from = today.minusDays(DAYS - 1L)
    val entries by remember { app.user.log().between(from.toString(), today.toString()) }.collectAsStateWithLifecycle(emptyList())
    val days by produceState<List<Pair<LocalDate, Intake>>?>(null, entries, catalog.version) {
        value = withContext(Dispatchers.IO) {
            val values = app.foods.values(entries.map { it.foodId })
            entries.groupBy { LocalDate.parse(it.date) }.map { (d, list) -> d to Intake(list, values) }
        }
    }
    val target = targets?.targets?.get(code)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("History") },
            )
        },
    ) { pad ->
        val byDay = days?.toMap().orEmpty()
        val max = (byDay.values.maxOfOrNull { it.totals.getValue(code).value } ?: 0.0)
            .coerceAtLeast(target?.target ?: target?.upper ?: 0.0).coerceAtLeast(1e-9)
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Box(Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { picking = true }) {
                        Text("${def.nameEn} (${def.unit})")
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        Nutrients.ALL.forEach { n ->
                            DropdownMenuItem(text = { Text(n.nameEn) }, onClick = { code = n.code; picking = false })
                        }
                    }
                }
            }
            if (target != null) item {
                Text("Target ${targetText(target, def.unit)}", Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Weeks(byDay, code, def.unit, today, target?.kind) }
            item {
                Text("Days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items((0 until DAYS).map { today.minusDays(it.toLong()) }, key = { it.toString() }) { d ->
                DayBar(d, byDay[d], code, def.unit, max, target)
            }
        }
    }
}

@Composable
private fun Weeks(byDay: Map<LocalDate, Intake>, code: String, unit: String, today: LocalDate, kind: TargetKind?) {
    val wf = WeekFields.ISO
    val weeks = byDay.entries.groupBy { it.key.get(wf.weekOfWeekBasedYear()) to it.key.get(wf.weekBasedYear()) }
        .toSortedMap(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Weekly averages", style = MaterialTheme.typography.titleSmall)
            if (weeks.isEmpty()) Text("Nothing logged in the last four weeks.")
            weeks.forEach { (wk, list) ->
                val avg = list.sumOf { it.value.totals.getValue(code).value } / list.size
                val pctEnergy = if (kind == TargetKind.ENERGY_PERCENT) {
                    // Each day's share of its own energy, averaged; carbs count fibre as NNR does.
                    val shares = list.mapNotNull { (_, i) ->
                        i.energyKj.takeIf { it > 0 }?.let { kj ->
                            val fibre = if (code == "CHO_AVAIL") i.fibre * Nnr2023.KjPerGram.FIBRE else 0.0
                            (i.totals.getValue(code).value * kjFor(code) + fibre) / kj * 100
                        }
                    }
                    if (shares.isEmpty()) "" else " · ${Format.value(shares.average())}% of energy"
                } else ""
                val incomplete = list.any { !it.value.totals.getValue(code).complete }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Week ${wk.first}${if (wk.first == today.get(wf.weekOfWeekBasedYear())) " (now)" else ""}", Modifier.weight(1f))
                    Text("${if (incomplete) "≥ " else ""}${Format.value(avg)} $unit$pctEnergy")
                }
                Text("${list.size} of 7 days logged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun kjFor(code: String) = when (code) {
    "PROT" -> Nnr2023.KjPerGram.PROTEIN
    "CHO_AVAIL", "SUGFR" -> Nnr2023.KjPerGram.CARBS
    else -> Nnr2023.KjPerGram.FAT
}

@Composable
private fun DayBar(d: LocalDate, i: Intake?, code: String, unit: String, max: Double, target: Target?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Format.shortDate(d), Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, color = muted)
        if (i == null) {
            Text("nothing logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            return@Row
        }
        val t = i.totals.getValue(code)
        val a = target?.let(i::assess)
        Box(Modifier.weight(1f).height(14.dp)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth((t.value / max).toFloat().coerceIn(0.01f, 1f))
                    .clip(RoundedCornerShape(4.dp)).background(statusColor(a?.status)),
            )
        }
        Text(
            "${if (t.complete) "" else "≥ "}${Format.value(t.value)} $unit",
            Modifier.width(96.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
