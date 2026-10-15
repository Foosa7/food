package com.food.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period

/** A week of activity from Health Connect, where Fitbit (or another watch) writes it. */
data class Activity(val days: Int, val avgSteps: Double?, val avgBurnedKcal: Double?)

object Health {
    val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    val CALORIES = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    val PERMISSIONS = setOf(STEPS, CALORIES)

    /** A day with fewer steps is treated as the watch not being worn. */
    private const val WORN_STEPS = 1000

    fun available(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(ctx: Context): Set<String> =
        if (!available(ctx)) emptySet()
        else HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()

    /**
     * The last 7 complete days (today is still going, so it's left out). Only days the watch
     * was worn count. Each metric is asked for only if its permission was granted.
     */
    suspend fun lastWeek(ctx: Context, today: LocalDate = LocalDate.now()): Activity? {
        val granted = granted(ctx)
        if (STEPS !in granted) return null
        val client = HealthConnectClient.getOrCreate(ctx)
        val metrics = buildSet {
            add(StepsRecord.COUNT_TOTAL)
            if (CALORIES in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        val days = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = metrics,
                timeRangeFilter = TimeRangeFilter.between(today.minusDays(7).atStartOfDay(), today.atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val worn = days.filter { (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) >= WORN_STEPS }
        if (worn.isEmpty()) return Activity(0, null, null)
        val kcal = worn.mapNotNull { it.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories }
        return Activity(
            days = worn.size,
            avgSteps = worn.map { it.result[StepsRecord.COUNT_TOTAL]!!.toDouble() }.average(),
            avgBurnedKcal = kcal.takeIf { it.size == worn.size }?.average(),
        )
    }
}
