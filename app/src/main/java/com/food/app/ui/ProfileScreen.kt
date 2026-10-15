package com.food.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.app.FoodApp
import com.food.app.data.Activity
import com.food.app.data.Health
import com.food.app.data.ProfileRow
import com.food.app.data.TargetEditRow
import com.food.app.data.TargetsState
import com.food.core.Assess
import com.food.core.Nnr2023
import com.food.core.Nutrients
import com.food.core.Target
import com.food.core.TargetKind
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Profile fields (all editable), where activity comes from, and every target with its own edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(targets: TargetsState?, activity: Activity?, onActivityChanged: () -> Unit, onBack: () -> Unit) {
    val app = FoodApp.app
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val row by remember { app.user.settings().profile() }.collectAsStateWithLifecycle(null)
    val edits by remember { app.user.settings().edits() }.collectAsStateWithLifecycle(emptyList())

    var sex by rememberSaveable { mutableStateOf("") }
    var birthYear by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var auto by rememberSaveable { mutableStateOf(true) }
    var pal by rememberSaveable { mutableStateOf("1.6") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf<Set<String>?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(row) {
        val r = row
        if (r != null && !loaded) {
            sex = r.sex; birthYear = r.birthYear.toString(); weight = Format.amount(r.weightKg)
            height = Format.amount(r.heightCm); auto = r.autoActivity; pal = Format.amount(r.pal)
            loaded = true
        }
    }
    LaunchedEffect(Unit) { granted = runCatching { Health.granted(ctx) }.getOrDefault(emptySet()) }
    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        granted = it
        onActivityChanged()
    }

    val year = birthYear.toIntOrNull()?.takeIf { it in 1900..LocalDate.now().year - 18 }
    val w = Format.parseAmount(weight)?.takeIf { it in 30.0..300.0 }
    val h = Format.parseAmount(height)?.takeIf { it in 120.0..230.0 }
    val p = Format.parseAmount(pal)?.takeIf { it in 1.2..2.4 }
    val valid = sex.isNotEmpty() && year != null && w != null && h != null && p != null
    val changed = row?.let { r ->
        r.sex != sex || r.birthYear != year || r.weightKg != w || r.heightCm != h || r.autoActivity != auto || r.pal != p
    } ?: true

    fun save() {
        if (!valid) return
        scope.launch {
            app.user.settings().putProfile(ProfileRow(1, sex, year!!, w!!, h!!, auto, p!!, System.currentTimeMillis()))
            onActivityChanged()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Profile & targets") },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 48.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("These pick your Nordic Nutrition Recommendations 2023 targets.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("female" to "Female", "male" to "Male").forEachIndexed { i, (code, label) ->
                            SegmentedButton(selected = sex == code, onClick = { sex = code }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Birth year", birthYear, { birthYear = it }, year == null && birthYear.isNotEmpty(), Modifier.weight(1f), decimal = false)
                        NumberField("Weight, kg", weight, { weight = it }, w == null && weight.isNotEmpty(), Modifier.weight(1f))
                        NumberField("Height, cm", height, { height = it }, h == null && height.isNotEmpty(), Modifier.weight(1f))
                    }
                }
            }
            item { ActivityCard(auto, { auto = it }, pal, { pal = it }, p == null, granted, activity, askHealth = { askHealth.launch(Health.PERMISSIONS) }) }
            item {
                Button(onClick = ::save, enabled = valid && changed, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (row == null) "Save profile" else if (changed) "Save changes" else "Saved")
                }
            }
            if (targets != null) {
                item { EnergyCard(targets) }
                item {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Targets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (edits.isNotEmpty()) TextButton(onClick = { scope.launch { app.user.settings().clearEdits() } }) { Text("Reset all to NNR") }
                    }
                    Text("Tap one to change it.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(16.dp, 8.dp))
                }
                val edited = edits.map { it.nutrient }.toSet()
                items(targets.targets.values.toList(), key = { it.nutrient }) { t ->
                    val def = Nutrients.byCode[t.nutrient] ?: return@items
                    Row(Modifier.fillMaxWidth().clickable { editing = t.nutrient }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(def.nameEn)
                            t.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                            Text(targetText(t, def.unit).removePrefix("of "))
                            if (t.nutrient in edited) Text("edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    val code = editing
    if (code != null && targets != null) {
        EditTargetDialog(
            current = targets.targets.getValue(code),
            nnr = targets.nnr[code],
            onSave = { e -> scope.launch { app.user.settings().putEdit(e) }; editing = null },
            onReset = { scope.launch { app.user.settings().clearEdit(code) }; editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit, error: Boolean, modifier: Modifier = Modifier, decimal: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true, isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ActivityCard(
    auto: Boolean, onAuto: (Boolean) -> Unit,
    pal: String, onPal: (String) -> Unit, palError: Boolean,
    granted: Set<String>?, activity: Activity?, askHealth: () -> Unit,
) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Activity from my watch", style = MaterialTheme.typography.titleSmall)
                    Text("Steps and calories burned from Fitbit, via Health Connect", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = auto, onCheckedChange = onAuto)
            }
            if (auto) when {
                !Health.available(ctx) -> Text("Health Connect isn't available on this phone, so the activity level below is used.", style = MaterialTheme.typography.bodySmall)
                granted != null && Health.STEPS !in granted -> Button(onClick = askHealth) { Text("Connect Health Connect") }
                activity == null -> Text("Reading…", style = MaterialTheme.typography.bodySmall)
                activity.days == 0 -> Text("No watch days in the last week yet.", style = MaterialTheme.typography.bodySmall)
                else -> Text(
                    "Last ${activity.days} days: ${Math.round(activity.avgSteps ?: 0.0)} steps a day" +
                        (activity.avgBurnedKcal?.let { ", ${Format.kcal(it)} kcal burned a day" } ?: "") +
                        if (granted != null && Health.CALORIES !in granted) ". Allow calories burned for a better estimate." else ".",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(if (auto) "Activity level when there's no watch data" else "Activity level", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Assess.PAL_LEVELS.forEach { (v, label) ->
                    FilterChip(selected = Format.parseAmount(pal) == v, onClick = { onPal(Format.amount(v)) }, label = { Text("$label ${Format.amount(v)}") })
                }
            }
            NumberField("Or your own (1.2–2.4)", pal, onPal, palError, Modifier.width(200.dp))
        }
    }
}

@Composable
private fun EnergyCard(t: TargetsState) {
    val bee = Nnr2023.bee(t.profile) * 1000 / Nnr2023.KJ_PER_KCAL
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("${Format.kcal(t.targets["ENERC_KCAL"]?.target ?: t.energy.kcal)} kcal a day", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(t.energy.how, style = MaterialTheme.typography.bodySmall)
            Text("Resting energy ${Format.kcal(bee)} kcal (NNR's Henry equation) × activity ${"%.2f".format(t.energy.pal)}. Age ${t.profile.age}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditTargetDialog(current: Target, nnr: Target?, onSave: (TargetEditRow) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val def = Nutrients.byCode.getValue(current.nutrient)
    val pctRange = current.kind == TargetKind.ENERGY_PERCENT
    val unit = if (pctRange) "% of energy" else def.unit
    var low by remember { mutableStateOf(current.low?.let(Format::value).orEmpty()) }
    var target by remember { mutableStateOf(current.target?.let(Format::value).orEmpty()) }
    var upper by remember { mutableStateOf(current.upper?.let(Format::value).orEmpty()) }
    val showTarget = current.kind !in setOf(TargetKind.ENERGY_PERCENT, TargetKind.LIMIT)
    val showLow = current.kind != TargetKind.ENERGY && current.kind != TargetKind.LIMIT

    fun parse(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
    fun differs(v: Double?, n: Double?) = if (v == null || v == n) null else v

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(def.nameEn) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                nnr?.let { Text("NNR 2023: ${targetText(it, def.unit)}", style = MaterialTheme.typography.bodySmall) }
                if (showLow) NumberField(if (pctRange) "From, $unit" else "Low below (AR), $unit", low, { low = it }, false)
                if (showTarget) NumberField("Target, $unit", target, { target = it }, false)
                NumberField(if (pctRange) "To, $unit" else if (current.kind == TargetKind.LIMIT) "Limit, $unit" else "Upper limit, $unit", upper, { upper = it }, false)
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(TargetEditRow(current.nutrient,
                    differs(parse(low), nnr?.low), differs(parse(target), nnr?.target), differs(parse(upper), nnr?.upper)))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onReset) { Text("Use NNR value") } },
    )
}
