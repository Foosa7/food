package com.food.app.data

import com.food.core.Energy
import com.food.core.EnergyTarget
import com.food.core.Nnr2023
import com.food.core.Profile
import com.food.core.Sex
import com.food.core.Target
import com.food.core.TargetEdit
import com.food.core.Targets
import java.time.LocalDate

/** The targets in effect, and how the energy part was worked out. */
data class TargetsState(
    val profile: Profile,
    val energy: EnergyTarget,
    val targets: Map<String, Target>,
    /** NNR's own values for the same profile, to show next to edited ones. */
    val nnr: Map<String, Target>,
)

fun ProfileRow.toProfile(today: LocalDate = LocalDate.now()) = Profile(
    sex = if (sex == "female") Sex.FEMALE else Sex.MALE,
    age = (today.year - birthYear).coerceAtLeast(18),
    weightKg = weightKg,
    heightCm = heightCm,
    pal = pal,
)

fun targetsState(row: ProfileRow?, edits: List<TargetEditRow>, activity: Activity?): TargetsState? {
    row ?: return null
    val profile = row.toProfile()
    val energy = Energy.target(profile, row.autoActivity, activity?.days ?: 0, activity?.avgSteps, activity?.avgBurnedKcal)
    val nnr = Nnr2023.targets(profile.copy(pal = energy.pal), energy.kcal)
    val edited = Targets.withEdits(nnr, edits.associate { it.nutrient to TargetEdit(it.low, it.target, it.upper) })
    // An edited energy target replaces the computed one.
    return TargetsState(profile, energy, edited, nnr)
}
