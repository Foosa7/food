package com.food.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.food.app.FoodApp
import com.food.app.data.Catalog
import com.food.app.data.CustomFood
import com.food.core.FoodId
import com.food.core.LabelFood
import com.food.core.Nutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A food from its package label: the EU nutrition declaration per 100 g, and optionally a
 * database food to borrow everything else (vitamins, minerals) from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodScreen(
    editId: Long?,
    initialName: String,
    catalog: Catalog,
    onSaved: (foodId: String) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val app = FoodApp.app
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var baseId by rememberSaveable { mutableStateOf<String?>(null) }
    val fields = remember { mutableStateMapOf<String, String>() }
    var existing by remember { mutableStateOf<CustomFood?>(null) }
    var more by rememberSaveable { mutableStateOf(false) }
    var pickingBase by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(editId) {
        if (editId == null) return@LaunchedEffect
        val (f, values) = withContext(Dispatchers.IO) {
            app.user.customFoods().get(editId) to app.user.customFoods().nutrients(editId)
        }
        existing = f
        if (f != null) {
            name = f.name; baseId = f.baseFoodId
            values.forEach { fields[it.nutrient] = Format.value(it.value) }
            if (values.any { it.nutrient !in LabelFood.LABEL_FIELDS }) more = true
        }
    }

    val parsed = fields.mapNotNull { (k, v) -> v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { k to it } }.toMap()
    val hasEnergy = "ENERC_KCAL" in parsed || "ENERC_KJ" in parsed || baseId != null
    val valid = name.isNotBlank() && hasEnergy

    fun save() {
        if (!valid) return
        val now = System.currentTimeMillis()
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                val f = (existing ?: CustomFood(name = name.trim(), baseFoodId = baseId, createdAt = now, updatedAt = now))
                    .copy(name = name.trim(), baseFoodId = baseId, updatedAt = now)
                app.user.customFoods().save(f, parsed)
            }
            onSaved(FoodId.custom(id))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(if (editId == null) "Food from a label" else "Edit food") },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 48.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name") },
                        placeholder = { Text("e.g. Port Salut, Lidl") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { pickingBase = true }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Based on (optional)", style = MaterialTheme.typography.labelLarge)
                        Text(baseId?.let { catalog.byId[it]?.nameSv } ?: "None. Tap to pick the closest database food.")
                        Text(
                            "It fills in what the label doesn't list, like vitamins and minerals. The label's own numbers always win.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (baseId != null) TextButton(onClick = { baseId = null }, contentPadding = PaddingValues(0.dp)) { Text("Remove") }
                    }
                }
            }
            item {
                Text("Per 100 g, as on the label", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
                Text(
                    "Kolhydrater on EU labels already leaves out fibre, the same as the database.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(LabelFood.LABEL_FIELDS) { code -> ValueField(code, fields) }
            item {
                TextButton(onClick = { more = !more }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (more) "Fewer nutrients" else "More from the label (vitamins, minerals…)")
                }
            }
            if (more) items(Nutrients.ALL.map { it.code }.filter { it !in LabelFood.LABEL_FIELDS }) { code -> ValueField(code, fields) }
            item {
                if (!hasEnergy) Text("Enter the energy, or pick a base food.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                Button(onClick = ::save, enabled = valid, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Save food") }
            }
        }
    }

    if (pickingBase) BasePicker(catalog, onPick = { baseId = it; pickingBase = false }, onDismiss = { pickingBase = false })

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${existing?.name}?") },
        text = { Text("Days you've already logged it keep its name, but its nutrients will no longer count.") },
        confirmButton = {
            TextButton(onClick = {
                val f = existing ?: return@TextButton
                scope.launch {
                    withContext(Dispatchers.IO) { app.user.customFoods().remove(f) }
                    confirmDelete = false
                    onDeleted()
                }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ValueField(code: String, fields: MutableMap<String, String>) {
    val def = Nutrients.byCode.getValue(code)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(def.nameEn, Modifier.weight(1f))
        OutlinedTextField(
            value = fields[code].orEmpty(),
            onValueChange = { fields[code] = it },
            singleLine = true,
            suffix = { Text(def.unit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(150.dp),
        )
    }
}

@Composable
private fun BasePicker(catalog: Catalog, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val results = remember(q) {
        catalog.search.search(q, 40).filter { FoodId.customId(it.id) == null }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column {
                TextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text("Search, e.g. hårdost 26") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(results, key = { it.id }) { r ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(r.id) }.padding(16.dp, 10.dp)) {
                            Text(r.nameSv, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            r.nameEn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Cancel") }
            }
        }
    }
}
