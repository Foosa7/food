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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.app.FoodApp
import com.food.app.ai.AiSettings
import com.food.app.ai.Analyst
import com.food.app.ai.Kimi
import com.food.app.data.Catalog
import com.food.app.data.LogEntry
import com.food.app.data.TargetsState
import com.food.core.Analysis
import com.food.core.NutrientVerdict
import com.food.core.Verdicts
import com.food.core.Fermented
import com.food.core.Nutrients
import com.food.core.Status
import com.food.core.TargetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Progress worked out on the phone, then the model's short take on the whole log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(catalog: Catalog, targets: TargetsState?, onProfile: () -> Unit) {
    val app = FoodApp.app
    val ctx = LocalContext.current
    val state by Analyst.state.collectAsStateWithLifecycle()
    val saved by Analyst.saved.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Analyst.load(ctx) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf(AiSettings.key(ctx).isNotBlank()) }

    val today = LocalDate.now()
    val entries by remember { app.user.log().between(today.minusDays(13).toString(), today.toString()) }
        .collectAsStateWithLifecycle(emptyList())
    val weeks by produceState<Pair<Week, Week>?>(null, entries, catalog.version) {
        value = withContext(Dispatchers.IO) {
            val values = app.foods.values(entries.map { it.foodId })
            val cut = today.minusDays(6).toString()
            val (now, before) = entries.partition { it.date >= cut }
            Week(Intake(now, values), now, catalog) to Week(Intake(before, values), before, catalog)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis") },
                actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, "AI settings") } },
            )
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 32.dp)) {
            if (targets == null) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onProfile)) {
                    Text(
                        "Set up your profile so the targets fit you. Tap here.",
                        Modifier.padding(16.dp),
                    )
                }
            }
            item { weeks?.let { (now, before) -> Progress(now, before, targets) } }
            item {
                AiCard(
                    state = state,
                    latest = saved?.lastOrNull(),
                    hasKey = hasKey,
                    onRun = { Analyst.run(ctx, catalog, targets) },
                    onSettings = { settings = true },
                )
            }
            val earlier = saved.orEmpty().dropLast(1).takeLast(8).reversed()
            if (earlier.isNotEmpty()) {
                item {
                    Text("Earlier", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                items(earlier, key = { it.at + it.model }) { a ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(when_(a), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(a.progress, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (settings) AiSettingsDialog(onDismiss = { settings = false; hasKey = AiSettings.key(ctx).isNotBlank() })
}

/** One rolling week, summed up for the progress card. */
private class Week(val intake: Intake, entries: List<LogEntry>, catalog: Catalog) {
    val days = intake.days
    val fermented = Fermented.servings(entries.map { catalog.fermented(it.foodId, it.foodName) to it.grams })
    fun avg(code: String) = intake.totals.getValue(code)
    val epaDha get() = avg("F20:5").value + avg("F22:6").value
}

@Composable
private fun Progress(now: Week, before: Week, targets: TargetsState?) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Progress", style = MaterialTheme.typography.titleMedium)
            Text(
                "Average logged day, last 7 days and the 7 before. Unlogged days don't count.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Spacer(Modifier.weight(1f))
                Text("Last 7", Modifier.width(76.dp), style = MaterialTheme.typography.labelMedium)
                Text("Before", Modifier.width(76.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Line("Days logged", null, "${now.days}", "${before.days}")
            val t = targets?.targets
            for ((label, code) in listOf("Energy" to "ENERC_KCAL", "Protein" to "PROT", "Fibre" to "FIBT", "Salt" to "NACL", "Vitamin D" to "VITD")) {
                val unit = if (code == "ENERC_KCAL") "kcal" else Nutrients.byCode.getValue(code).unit
                Line(
                    label,
                    t?.get(code)?.let { targetText(it, unit) },
                    value(now, code, unit),
                    value(before, code, unit),
                    t?.get(code)?.takeIf { now.days > 0 }?.let { now.intake.assess(it).status },
                )
            }
            Line("Omega-3, EPA+DHA", "from fish; aim for 0.25 g+ a day",
                if (now.days > 0) "${Format.value(now.epaDha)} g" else "–", if (before.days > 0) "${Format.value(before.epaDha)} g" else "–")
            Line("Fermented foods", "servings with live cultures, whole week", "${now.fermented}", "${before.fermented}")
            if (t != null && now.days > 0) {
                val assessed = t.values.filter { it.kind != TargetKind.ENERGY }.map { now.intake.assess(it).status }.filter { it != Status.NONE }
                val ok = assessed.count { it == Status.OK }
                Line("Nutrients on target", "of ${assessed.size} with a target", "$ok", before.takeIf { it.days > 0 }?.let { b ->
                    t.values.filter { it.kind != TargetKind.ENERGY }.count { b.intake.assess(it).status == Status.OK }.toString()
                } ?: "–")
            }
        }
    }
}

private fun value(w: Week, code: String, unit: String): String {
    if (w.days == 0) return "–"
    val total = w.avg(code)
    val v = if (code == "ENERC_KCAL") Format.kcal(total.value) else Format.value(total.value)
    return "${if (total.complete) "" else "≥"}$v $unit"
}

@Composable
private fun Line(label: String, sub: String?, now: String, before: String, status: Status? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(now, Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (status != null && status != Status.OK && status != Status.NONE) statusColor(status) else MaterialTheme.colorScheme.onSurface)
        Text(before, Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AiCard(state: Analyst.State, latest: Analysis?, hasKey: Boolean, onRun: () -> Unit, onSettings: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("✦ Kimi's take", style = MaterialTheme.typography.titleMedium)
            when {
                !hasKey -> {
                    Text("Add your Kimi API key to get a short analysis of everything you've logged: every macro and micro against your targets, week by week.")
                    Button(onClick = onSettings, modifier = Modifier.padding(top = 8.dp)) { Text("Add key") }
                    return@Column
                }
                state is Analyst.State.Running -> {
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(state) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        Text("Reading your log… ${(now - state.since) / 1000}s. You can leave this tab.")
                    }
                }
                else -> {
                    if (state is Analyst.State.Failed) Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedButton(onClick = onRun, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(if (latest == null) "Analyse my diet" else "Update")
                    }
                }
            }
            if (latest == null) return@Column
            Text(when_(latest), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(latest.progress, style = MaterialTheme.typography.bodyLarge)
            Bullets("Try this", latest.suggestions)
            NutrientVerdicts(latest.nutrients)
            // Analyses from before the nutrient-by-nutrient answer.
            Bullets("Going well", latest.good)
            Bullets("To improve", latest.improve)
            if (latest.notes.isNotBlank()) {
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (open) "Hide Kimi's notes" else "Kimi's notes for next time")
                }
                if (open) Text(latest.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Every nutrient, grouped by how it's going. */
@Composable
private fun NutrientVerdicts(list: List<NutrientVerdict>) {
    if (list.isEmpty()) return
    val groups = listOf(
        Verdicts.GOOD to "Doing well",
        Verdicts.LOW to "Can improve",
        Verdicts.OVER to "Too much",
        Verdicts.UNKNOWN to "Not enough data",
    )
    val by = list.groupBy { v -> v.verdict.lowercase().trim().takeIf { it in Verdicts.ALL } ?: Verdicts.UNKNOWN }
    for ((verdict, title) in groups) {
        val items = by[verdict].orEmpty()
        if (items.isEmpty()) continue
        val color = when (verdict) {
            Verdicts.GOOD -> MaterialTheme.colorScheme.primary
            Verdicts.LOW -> statusColor(Status.BELOW_TARGET)
            Verdicts.OVER -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        }
        Text("$title (${items.size})", style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
        items.forEach { v ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Text(
                    buildString {
                        append(v.nutrient)
                        if (v.intake.isNotBlank()) append(" · ${v.intake}")
                        if (v.target.isNotBlank()) append(" · need ${v.target}")
                    },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                )
                if (v.comment.isNotBlank()) Text(v.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Bullets(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
    items.forEach { Row(Modifier.padding(vertical = 2.dp)) { Text("•  "); Text(it, style = MaterialTheme.typography.bodyMedium) } }
}

private val stamp = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

/** "Fri 16 Oct, 14:05 · kimi-k3 · 12 days · 34s" */
private fun when_(a: Analysis): String {
    val at = runCatching { stamp.format(LocalDateTime.parse(a.at)) }.getOrDefault(a.at)
    return "$at · ${a.model} · ${a.days} days logged · ${a.seconds}s"
}

@Composable
private fun AiSettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(AiSettings.key(ctx)) }
    var model by remember { mutableStateOf(AiSettings.model(ctx)) }
    var effort by remember { mutableStateOf(AiSettings.effort(ctx)) }
    var goals by remember { mutableStateOf(AiSettings.goals(ctx)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("Kimi API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Stays on this phone. Each analysis sends Moonshot a summary of what you logged and your profile.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Model", style = MaterialTheme.typography.labelLarge)
                Kimi.PRESETS.forEach { (id, label) ->
                    FilterChip(selected = model == id, onClick = { model = id }, label = { Text(label) })
                }
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Thinking effort", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kimi.EFFORTS.forEach { e -> FilterChip(selected = effort == e, onClick = { effort = e }, label = { Text(e) }) }
                }
                OutlinedTextField(
                    value = goals, onValueChange = { goals = it },
                    label = { Text("What you're aiming for") },
                    placeholder = { Text("e.g. omega-3 from fish, lots of protein, more gut-friendly food") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AiSettings.setKey(ctx, key); AiSettings.setModel(ctx, model); AiSettings.setEffort(ctx, effort); AiSettings.setGoals(ctx, goals)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
