package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.EnergyRange
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * [day]'s energy range, the one question Today and Day close ask. It starts from the day's own
 * goal, so a "different by day" group (a training day) is the range that day. Watch activity adds to it only
 * on today, only with energy read ([expended] is null unless `release.health` is on and a store is
 * connected), and never on a day left out of the math (marked untracked). How much it adds is
 * [Settings.exerciseInRange], capped in `Targets.today`; it can only ever add.
 */
fun todayRange(settings: Settings, day: Day, today: LocalDate, expended: EnergyExpended?): EnergyRange {
    val energy = expended?.takeIf { day.date == today && !day.untracked }
    return settings.targets.today(
        adjustment = settings.exerciseInRange,
        movementKcal = energy?.movementKcal.wholeKcal(),
        workoutKcal = energy?.workoutKcal.wholeKcal(),
        goal = settings.targets.rangeFor(day.date, settings.numbers.preset),
    )
}

/** A reading the store could not make sense of (NaN, infinite) adds nothing rather than crashing. */
private fun Double?.wholeKcal(): Int = this?.takeIf { it.isFinite() }?.roundToInt() ?: 0
