package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.goals.GoalToday
import com.opson.libraries.goals.GoalWrapUp
import com.opson.libraries.goals.weekAdjustmentKcal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.DayGoal
import com.opson.libraries.nutrition.EnergyRange
import com.opson.libraries.nutrition.ExerciseAdjustment
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * [day]'s energy range, the one question Today and Day close ask. It starts from the day's own
 * goal, so a "different by day" group (a training day) is the range that day. Watch activity adds to it only
 * on today, only with energy read ([expended] is null unless `release.health` is on and a store is
 * connected), and never on a day left out of the math (marked untracked). How much it adds is
 * [Settings.exerciseInRange], capped in `Targets.today`; it can only ever add.
 *
 * A set deficit or surplus is aimed at [burned] (design sync 4), which already counts the day's
 * activity, so a goal worked out from it takes no exercise adjustment on top.
 *
 * A goal averaged over the week moves the day's band by what its week asked of it
 * ([weekShiftKcal]: `GoalToday.weekAdjustmentKcal` on Today, the wrap-up's band in Day close,
 * capped in the engine); a daily goal already wrote its band to Targets, so it moves nothing.
 */
fun todayRange(
    settings: Settings,
    day: Day,
    today: LocalDate,
    expended: EnergyExpended?,
    burned: Burned? = null,
    weekShiftKcal: Int = 0,
): EnergyRange {
    val energy = expended?.takeIf { day.date == today && !day.untracked }
    val goal = settings.targets.rangeFor(day.date, settings.numbers.preset, burned?.totalKcal)
    val range = settings.targets.today(
        adjustment = if (burned != null && goal is DayGoal.About) ExerciseAdjustment.None else settings.exerciseInRange,
        movementKcal = energy?.movementKcal.wholeKcal(),
        workoutKcal = energy?.workoutKcal.wholeKcal(),
        goal = goal,
    )
    val week = if (day.untracked) 0 else weekShiftKcal
    return if (week == 0) range else range.copy(targetKcal = range.targetKcal + week, minKcal = range.minKcal + week, maxKcal = range.maxKcal + week)
}

/** How far today's goal week moves today's band: the goal setting the target, on today only. */
fun weekShiftOn(day: Day, today: LocalDate, goals: List<GoalToday>): Int =
    if (day.date == today) goals.firstOrNull { it.goal.drivesTarget }?.weekAdjustmentKcal ?: 0 else 0

/** The band a wrapped-up day had under a weekly goal setting the target, against the goal's own aim. */
fun weekShiftOn(goals: List<GoalWrapUp>): Int {
    val wrap = goals.firstOrNull { it.goal.drivesTarget } ?: return 0
    val week = wrap.week?.takeIf { it.averaging == GoalAveraging.Weekly } ?: return 0
    return (wrap.day.targetKcal ?: week.aimKcal) - week.aimKcal
}

/** A reading the store could not make sense of (NaN, infinite) adds nothing rather than crashing. */
private fun Double?.wholeKcal(): Int = this?.takeIf { it.isFinite() }?.roundToInt() ?: 0
