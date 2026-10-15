package com.food.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.food.core.Status
import com.food.core.Target
import com.food.core.TargetKind

private val Amber = Color(0xFFC98A00)

@Composable
fun statusColor(s: Status?): Color = when (s) {
    Status.LOW, Status.OVER -> MaterialTheme.colorScheme.error
    Status.BELOW_TARGET -> Amber
    Status.OK -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outline
}

fun statusLabel(s: Status?, kind: TargetKind?): String? = when (s) {
    Status.LOW -> if (kind == TargetKind.ENERGY_PERCENT) "Under range" else "Low"
    Status.BELOW_TARGET -> "Below target"
    Status.OVER -> when (kind) {
        TargetKind.LIMIT -> "Over limit"
        TargetKind.ENERGY_PERCENT -> "Over range"
        TargetKind.ENERGY -> "Above target"
        else -> "Over upper limit"
    }
    else -> null
}

/** "of 110 mg", "10–20% of energy", "under 2.3 g", "at least 35 g". */
fun targetText(t: Target, unit: String): String = when (t.kind) {
    TargetKind.ENERGY -> "of ${t.target?.let(Format::kcal)} kcal"
    TargetKind.ENERGY_PERCENT -> {
        val low = t.low
        val upper = t.upper
        when {
            low != null && upper != null -> "${Format.value(low)}–${Format.value(upper)}% of energy"
            upper != null -> "under ${Format.value(upper)}% of energy"
            else -> "over ${Format.value(low ?: 0.0)}% of energy"
        }
    }
    TargetKind.LIMIT -> if (t.upper == 0.0) "best avoided" else "under ${Format.value(t.upper ?: 0.0)} $unit"
    TargetKind.MIN -> "at least ${Format.value(t.low ?: 0.0)} $unit"
    else -> "of ${Format.value(t.target ?: 0.0)} $unit" + (t.upper?.let { " · max ${Format.value(it)}" } ?: "")
}
