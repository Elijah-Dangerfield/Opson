package com.opson.libraries.health

import com.opson.libraries.nutrition.HealthReadings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.math.roundToInt

/**
 * What a goal's typical day reads from the health app (docs/plans/goals-v2.md): [days] as the store
 * read them, the most recent whole day's resting energy and each day's movement plus workouts.
 * One rule, so the goal flow and [BurnedRepository.healthReadings] can't disagree.
 */
fun healthReadingsOf(days: Map<LocalDate, EnergyExpended>): HealthReadings {
    val resting = days.entries.sortedByDescending { it.key }.firstNotNullOfOrNull { it.value.restingKcal?.takeIf { kcal -> kcal > 0 && kcal.isFinite() } }
    val activity = days.values.mapNotNull { day ->
        val movement = day.movementKcal?.takeIf { it.isFinite() }
        val workouts = day.workoutKcal?.takeIf { it.isFinite() }
        if (movement == null && workouts == null) null else ((movement ?: 0.0) + (workouts ?: 0.0)).roundToInt()
    }
    return HealthReadings(resting?.roundToInt(), activity)
}

/**
 * The last [BurnedRepository.HEALTH_DAYS] whole days before [today], read once;
 * [HealthReadings.Unavailable] when the read fails, so a failed read isn't taken for a store with
 * nothing in it.
 */
suspend fun HealthRepository.goalReadings(today: LocalDate, timeZone: TimeZone): HealthReadings {
    val days = energyBetween(today.minus(DatePeriod(days = BurnedRepository.HEALTH_DAYS)), today.minus(DatePeriod(days = 1)), timeZone)
    return days?.let(::healthReadingsOf) ?: HealthReadings.Unavailable
}
