package com.food.app.ui

import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.food.app.FoodApp
import com.food.app.data.Catalog
import com.food.app.data.LogEntry
import com.food.core.Meal
import kotlinx.coroutines.launch
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
    data class Search(val date: LocalDate, val meal: Meal) : Screen
    /** Adding [foodId] when [entryId] is null, otherwise editing that entry. */
    data class Food(val foodId: String, val date: LocalDate, val meal: Meal, val entryId: Long? = null) : Screen
}

@Composable
private fun AppRoot() {
    val loaded by produceState<Result<Catalog>?>(null) { value = runCatching { FoodApp.app.catalog.await() } }
    val stack = remember { mutableStateListOf<Screen>(Screen.Day) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    BackHandler(stack.size > 1) { pop() }

    val date = LocalDate.parse(day)
    when (val s = stack.last()) {
        Screen.Day -> DayScreen(
            date = date,
            catalog = catalog,
            snackbar = snackbar,
            onDate = { day = it.toString() },
            onAdd = { meal -> push(Screen.Search(date, meal ?: Meal.at(LocalTime.now()))) },
            onOpen = { e -> push(Screen.Food(e.foodId, date, Meal.of(e.meal), e.id)) },
        )
        is Screen.Search -> SearchScreen(
            date = s.date,
            meal = s.meal,
            catalog = catalog,
            onPick = { id -> push(Screen.Food(id, s.date, s.meal)) },
            onBack = ::pop,
        )
        is Screen.Food -> FoodScreen(
            foodId = s.foodId,
            date = s.date,
            initialMeal = s.meal,
            entryId = s.entryId,
            onSaved = { saved ->
                home()
                scope.launch { snackbar.showSnackbar("Logged ${saved.foodName}") }
            },
            onDeleted = { deleted: LogEntry ->
                home()
                scope.launch {
                    val r = snackbar.showSnackbar("Deleted ${deleted.foodName}", actionLabel = "Undo", withDismissAction = true)
                    if (r == SnackbarResult.ActionPerformed) FoodApp.app.user.log().insert(deleted)
                }
            },
            onBack = ::pop,
        )
    }
}
