package com.food.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.app.FoodApp
import com.food.app.data.Activity
import com.food.app.data.Export
import com.food.app.data.Health
import com.food.app.data.LogEntry
import com.food.app.data.targetsState
import com.food.core.FoodId
import com.food.core.Meal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppRoot() }
            }
        }
    }
}

sealed interface Screen {
    data object Day : Screen
    data class Scan(val date: LocalDate, val meal: Meal) : Screen
    /** With [linkBarcode], the food picked gets that barcode. */
    data class Search(val date: LocalDate, val meal: Meal, val linkBarcode: String? = null) : Screen
    /** Adding [foodId] when [entryId] is null, otherwise editing that entry. */
    data class Food(
        val foodId: String,
        val date: LocalDate,
        val meal: Meal,
        val entryId: Long? = null,
        val barcode: String? = null,
    ) : Screen
    /** A label food: new (then logged to [date]/[meal]) or [editId]. */
    data class CustomFood(
        val editId: Long?,
        val name: String = "",
        val date: LocalDate? = null,
        val meal: Meal? = null,
        val linkBarcode: String? = null,
    ) : Screen
    data class Nutrients(val date: LocalDate) : Screen
    data class History(val nutrient: String = "ENERC_KCAL") : Screen
    data object Profile : Screen
}

@Composable
private fun AppRoot() {
    val app = FoodApp.app
    val ctx = LocalContext.current
    val loaded by app.foods.catalog.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf<Screen>(Screen.Day) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Targets: profile + edits + a week of watch activity, re-read whenever the app comes back.
    val profile by remember { app.user.settings().profile() }.collectAsStateWithLifecycle(null)
    val edits by remember { app.user.settings().edits() }.collectAsStateWithLifecycle(emptyList())
    var activity by remember { mutableStateOf<Activity?>(null) }
    var activityTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { activityTick++ }
    LaunchedEffect(activityTick, profile?.autoActivity) {
        if (profile?.autoActivity == true) activity = withContext(Dispatchers.IO) { runCatching { Health.lastWeek(ctx) }.getOrNull() }
    }
    val targets = remember(profile, edits, activity) { targetsState(profile, edits, activity) }

    val catalog = loaded?.getOrNull()
    if (catalog == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val error = loaded?.exceptionOrNull()
            if (error == null) CircularProgressIndicator()
            else Text("Couldn't open the food database: ${error.message}", Modifier.padding(24.dp))
        }
        return
    }

    fun push(s: Screen) { stack += s }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun home() { while (stack.size > 1) pop() }
    fun replace(s: Screen) { pop(); push(s) }

    BackHandler(stack.size > 1) { pop() }

    val date = LocalDate.parse(day)
    when (val s = stack.last()) {
        Screen.Day -> DayScreen(
            date = date,
            catalog = catalog,
            targets = targets,
            snackbar = snackbar,
            onDate = { day = it.toString() },
            onAdd = { meal -> push(Screen.Search(date, meal ?: Meal.at(LocalTime.now()))) },
            onScan = { push(Screen.Scan(date, Meal.at(LocalTime.now()))) },
            onOpen = { e -> push(Screen.Food(e.foodId, date, Meal.of(e.meal), e.id)) },
            onNutrients = { push(Screen.Nutrients(date)) },
            onHistory = { push(Screen.History()) },
            onProfile = { push(Screen.Profile) },
            onExport = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { Export.files(ctx, app.user) } }
                        .onSuccess { Export.share(ctx, it) }
                        .onFailure { Toast.makeText(ctx, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            },
        )
        is Screen.Scan -> ScanScreen(
            date = s.date,
            meal = s.meal,
            onCode = { code ->
                scope.launch {
                    // Known: straight to the food with its usual portion. Unknown: find the food.
                    val linked = app.user.barcodes().get(code)
                    if (stack.lastOrNull() != s) return@launch
                    replace(
                        if (linked != null) Screen.Food(linked.foodId, s.date, s.meal, barcode = code)
                        else Screen.Search(s.date, s.meal, linkBarcode = code)
                    )
                }
            },
            onBack = ::pop,
        )
        is Screen.Search -> SearchScreen(
            date = s.date,
            meal = s.meal,
            catalog = catalog,
            linkBarcode = s.linkBarcode,
            onPick = { id -> push(Screen.Food(id, s.date, s.meal, barcode = s.linkBarcode)) },
            onCreate = { name -> push(Screen.CustomFood(null, name, s.date, s.meal, s.linkBarcode)) },
            onBack = ::pop,
        )
        is Screen.Food -> FoodScreen(
            foodId = s.foodId,
            date = s.date,
            initialMeal = s.meal,
            entryId = s.entryId,
            barcode = s.barcode,
            catalog = catalog,
            onSaved = { message ->
                home()
                scope.launch { snackbar.showSnackbar(message) }
            },
            onDeleted = { deleted: LogEntry ->
                home()
                scope.launch {
                    val r = snackbar.showSnackbar("Deleted ${deleted.foodName}", actionLabel = "Undo", withDismissAction = true)
                    if (r == SnackbarResult.ActionPerformed) app.user.log().insert(deleted)
                }
            },
            onRelink = { code -> push(Screen.Search(s.date, s.meal, linkBarcode = code)) },
            onEditFood = { id -> push(Screen.CustomFood(id)) },
            onBack = ::pop,
        )
        is Screen.CustomFood -> CustomFoodScreen(
            editId = s.editId,
            initialName = s.name,
            catalog = catalog,
            onSaved = { foodId ->
                // New from search: go on to log it (and link the barcode, if one was scanned).
                if (s.date != null && s.meal != null) replace(Screen.Food(foodId, s.date, s.meal, barcode = s.linkBarcode))
                else pop()
            },
            onDeleted = { home() },
            onBack = ::pop,
        )
        is Screen.Nutrients -> NutrientsScreen(
            date = s.date,
            catalog = catalog,
            targets = targets,
            onProfile = { push(Screen.Profile) },
            onHistory = { code -> push(Screen.History(code)) },
            onBack = ::pop,
        )
        is Screen.History -> HistoryScreen(catalog = catalog, targets = targets, initial = s.nutrient, onBack = ::pop)
        Screen.Profile -> ProfileScreen(
            targets = targets,
            activity = activity,
            onActivityChanged = { activityTick++ },
            onBack = ::pop,
        )
    }
}

/** Whether a food id is one of the user's label foods. */
fun isCustom(foodId: String) = FoodId.customId(foodId) != null
