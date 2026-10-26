package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

// TODO(dietitian): GOAL_FLOOR_KCAL must be set by an ED-informed dietitian
// before launch. Do not invent a value, not even a "safe-looking" one: a floor
// picked by an engineer is exactly the unreviewed number this exists to
// prevent. While it is null, nothing can lower the energy target below where
// the user started, so the target can only go up.
val GOAL_FLOOR_KCAL: Int? = null

// TODO(dietitian): MAX_DEFICIT_KCAL caps "A set deficit" and must be set by the
// same dietitian before launch, with the floor above. Do not invent a value.
// While it is null, a deficit can never grow past the one it started at.
val MAX_DEFICIT_KCAL: Int? = null

// TODO(clinician): the most watch activity may add to today's range, and the share "Some of it"
// adds. An engineer picked both. The cap only stops one bad reading (a 5,000 kcal glitch) from
// moving the range absurdly, and sits above an ordinary active day so normal movement is never
// clipped. Neither can lower a range: activity only ever adds, and the base is the floor.
const val EXERCISE_CAP_KCAL: Int = 1000
const val EXERCISE_SOME_SHARE: Double = 0.5

/**
 * The user's energy range and macro goals. [startingKcal] is the target the
 * user began with, and is the floor until [GOAL_FLOOR_KCAL] is set.
 * [startingMinKcal] is the low end they began with, the low end's floor until
 * then. Each end of the range steps on its own ([step]); [energyKcal], the aim,
 * is the middle of the range and never drops below [floorKcal] either.
 *
 * [style] is how the goal is shaped (a range, a set deficit, different by
 * day). Ask [rangeFor] for any one day's goal rather than reading the fields.
 *
 * [ratioBasis] is bookkeeping for ratio mode, not something a screen reads:
 * the split the current grams were scaled from. Scaling each 50 kcal step from
 * the previous step's rounded grams drifts (ten raises put fat 3 g off the
 * prototype's `GOAL[k] * baseT / GOAL.target`), so ratio steps always scale from
 * this fixed point instead. Any change that does not scale clears it.
 */
data class Targets(
    val energyKcal: Int,
    val minKcal: Int,
    val maxKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    val startingKcal: Int = energyKcal,
    val ratioBasis: MacroSplit? = null,
    val startingMinKcal: Int = minKcal,
    val style: TargetStyle = TargetStyle.Range,
) {
    /** The aim's floor. */
    val floorKcal: Int get() = GOAL_FLOOR_KCAL ?: startingKcal

    /** The low end's floor: the dietitian's floor once set, until then where the low end started. */
    val lowFloorKcal: Int get() = GOAL_FLOOR_KCAL ?: startingMinKcal

    val canLower: Boolean
        get() = energyKcal - STEP_KCAL >= floorKcal && minKcal - STEP_KCAL >= lowFloorKcal

    /** The most a set deficit may be: the dietitian's maximum once set, until then where it started. */
    val maxDeficitKcal: Int
        get() = MAX_DEFICIT_KCAL ?: (style as? TargetStyle.Deficit)?.startingKcal ?: 0

    /**
     * The goal for [date]. A day group's range under [TargetStyle.ByDay]; under
     * [TargetStyle.Deficit], "about" what the user burns ([burnKcal], a 7-day average) less the
     * deficit, never below the floor. A deficit falls back to the range when the burn is not
     * known yet or [preset] does not offer deficits, so a mode change can never leave a
     * deficit running where the user can't see or change it.
     */
    fun rangeFor(date: LocalDate, preset: PresetId, burnKcal: Int? = null): DayGoal {
        val base = DayGoal.Range(minKcal, maxKcal, energyKcal)
        return when (val s = style) {
            TargetStyle.Range -> base
            is TargetStyle.ByDay -> {
                val index = s.groups.indexOfFirst { date.dayOfWeek in it.days }
                if (index < 0) {
                    base
                } else {
                    val group = s.groups[index]
                    DayGoal.Range(group.minKcal, group.maxKcal, group.midKcal, groupIndex = index)
                }
            }
            is TargetStyle.Deficit -> {
                if (burnKcal == null || !TargetStyle.deficitAllowed(preset)) {
                    base
                } else {
                    DayGoal.About(deficitGoal(burnKcal, s.kcal))
                }
            }
        }
    }

    /** What the user burns less [deficit], rounded to 10 and never under the floor. */
    fun deficitGoal(burnKcal: Int, deficit: Int): Int {
        val rounded = ((burnKcal - deficit) / 10.0).roundToInt() * 10
        val floor = ((floorKcal + 9) / 10) * 10
        return max(rounded, floor)
    }

    /** Whether moving [end] of the range (or of day group [group]) by one step [up] is allowed. */
    fun canStep(end: RangeEnd, up: Boolean, group: Int? = null): Boolean = stepBlock(end, up, group) == null

    /** Why that step is not allowed, or null when it is. The floor gets a lock; the gap does not. */
    fun stepBlock(end: RangeEnd, up: Boolean, group: Int? = null): StepBlock? {
        val (min, max) = ends(group) ?: return StepBlock.Gap
        val (nextMin, nextMax) = moved(min, max, end, up)
        return when {
            !up && (nextMin < lowFloorKcal || (nextMin + nextMax) / 2 < floorKcal) -> StepBlock.Floor
            nextMax - nextMin < STEP_KCAL -> StepBlock.Gap
            else -> null
        }
    }

    /**
     * Moves [end] of the base range, or of day group [group], by 50 kcal. `null` when that would
     * break the floor rule (the low end under [lowFloorKcal], the middle under [floorKcal]) or
     * bring the ends closer than one step. Raising the high end is always allowed. With
     * [scaleMacros] (ratio mode) a base range step scales protein, carbs and fat with the aim.
     */
    fun step(end: RangeEnd, up: Boolean, group: Int? = null, scaleMacros: Boolean = false): Targets? {
        val (min, max) = stepped(end, up, group) ?: return null
        if (group == null) return withEnds(min, max, scaleMacros)
        val byDay = style as TargetStyle.ByDay
        val groups = byDay.groups.toMutableList()
        groups[group] = groups[group].copy(minKcal = min, maxKcal = max)
        return copy(style = byDay.copy(groups = groups))
    }

    /** A plain range. Day groups and a deficit are dropped; the base range stays as it is. */
    fun useRange(): Targets = copy(style = TargetStyle.Range)

    /**
     * A set deficit, unless one is already set. It starts at what the user's aim already implies
     * against [burnKcal] (floored to 50, never negative), and at 0 when the burn is unknown, so
     * while [MAX_DEFICIT_KCAL] is unset it never asks for more than the user already had.
     */
    fun useDeficit(burnKcal: Int?): Targets {
        if (style is TargetStyle.Deficit) return this
        val implied = burnKcal?.let { max(0, it - energyKcal) / STEP_KCAL * STEP_KCAL } ?: 0
        val start = MAX_DEFICIT_KCAL?.let { minOf(implied, it) } ?: implied
        return copy(style = TargetStyle.Deficit(kcal = start, startingKcal = start))
    }

    /** Different by day, unless it already is: one group holding every day, on the base range. */
    fun useByDay(): Targets {
        if (style is TargetStyle.ByDay) return this
        return copy(style = TargetStyle.ByDay(listOf(DayGroup(DayOfWeek.entries.toSet(), minKcal, maxKcal))))
    }

    /** One step more or less deficit. `null` below 0 or past [maxDeficitKcal]. */
    fun stepDeficit(up: Boolean): Targets? {
        val deficit = style as? TargetStyle.Deficit ?: return null
        val next = deficit.kcal + if (up) STEP_KCAL else -STEP_KCAL
        if (next < 0 || next > maxDeficitKcal) return null
        return copy(style = deficit.copy(kcal = next))
    }

    /** Moves [day] into group [group]. A day is never removed, only moved, so it stays in exactly one group. */
    fun moveDay(day: DayOfWeek, group: Int): Targets {
        val byDay = style as? TargetStyle.ByDay ?: return this
        if (group !in byDay.groups.indices) return this
        val groups = byDay.groups.mapIndexed { index, it ->
            if (index == group) it.copy(days = it.days + day) else it.copy(days = it.days - day)
        }
        return copy(style = byDay.copy(groups = groups))
    }

    /** A new, empty group on the first group's range. `null` at [TargetStyle.MAX_GROUPS]. */
    fun addDayGroup(): Targets? {
        val byDay = style as? TargetStyle.ByDay ?: return null
        if (byDay.groups.size >= TargetStyle.MAX_GROUPS) return null
        val first = byDay.groups.first()
        return copy(style = byDay.copy(groups = byDay.groups + DayGroup(emptySet(), first.minKcal, first.maxKcal)))
    }

    /** Removes group [group], handing its days to the first group. The first group stays. */
    fun removeDayGroup(group: Int): Targets? {
        val byDay = style as? TargetStyle.ByDay ?: return null
        if (group <= 0 || group > byDay.groups.lastIndex) return null
        val removed = byDay.groups[group]
        val groups = byDay.groups.filterIndexed { index, _ -> index != group }.toMutableList()
        groups[0] = groups[0].copy(days = groups[0].days + removed.days)
        return copy(style = byDay.copy(groups = groups))
    }

    /**
     * Raising is always allowed. With [scaleMacros] (ratio mode) protein, carbs
     * and fat move with the energy target so the split stays the same, the
     * prototype's `GOAL[k] * baseT / GOAL.target`. Gram mode leaves them. Fiber
     * is a floor for gut health, not a share of energy, so it never scales.
     */
    fun raise(scaleMacros: Boolean = false): Targets = shiftedBy(STEP_KCAL, scaleMacros)

    /** `null` when one more step down would cross the floor. [scaleMacros] as in [raise]. */
    fun lower(scaleMacros: Boolean = false): Targets? = if (canLower) shiftedBy(-STEP_KCAL, scaleMacros) else null

    /** Sets the target directly, never below the floor. The range moves with it. */
    fun withEnergy(kcal: Int): Targets = shiftedBy(max(kcal, floorKcal) - energyKcal)

    /**
     * Today's range: the base plus whatever the user lets watch activity add, at most
     * [EXERCISE_CAP_KCAL]. Never below the base, whatever the readings.
     */
    fun today(adjustment: ExerciseAdjustment, movementKcal: Int, workoutKcal: Int): EnergyRange {
        val activity = adjustment.activityKcal(movementKcal, workoutKcal)
        return EnergyRange(energyKcal + activity, minKcal + activity, maxKcal + activity, activity)
    }

    /**
     * Tomorrow always starts from the base. It takes no inputs on purpose: a
     * big day, a quiet day or a long workout must never move it, and least of
     * all down.
     */
    fun tomorrow(): EnergyRange = EnergyRange(energyKcal, minKcal, maxKcal, activityKcal = 0)

    /** The ends after one step, or null when [stepBlock] says no. */
    private fun stepped(end: RangeEnd, up: Boolean, group: Int?): Pair<Int, Int>? {
        if (stepBlock(end, up, group) != null) return null
        val (min, max) = ends(group) ?: return null
        return moved(min, max, end, up)
    }

    private fun ends(group: Int?): Pair<Int, Int>? {
        if (group == null) return minKcal to maxKcal
        val g = (style as? TargetStyle.ByDay)?.groups?.getOrNull(group) ?: return null
        return g.minKcal to g.maxKcal
    }

    private fun moved(min: Int, max: Int, end: RangeEnd, up: Boolean): Pair<Int, Int> {
        val delta = if (up) STEP_KCAL else -STEP_KCAL
        return when (end) {
            RangeEnd.Low -> (min + delta) to max
            RangeEnd.High -> min to (max + delta)
        }
    }

    private fun withEnds(min: Int, max: Int, scaleMacros: Boolean): Targets {
        val energy = (min + max) / 2
        val moved = copy(energyKcal = energy, minKcal = min, maxKcal = max, ratioBasis = null)
        return if (scaleMacros) scaledTo(moved, energy) else moved
    }

    private fun shiftedBy(kcal: Int, scaleMacros: Boolean = false): Targets {
        val energy = energyKcal + kcal
        val shifted = copy(energyKcal = energy, minKcal = minKcal + kcal, maxKcal = maxKcal + kcal, ratioBasis = null)
        return if (scaleMacros) scaledTo(shifted, energy) else shifted
    }

    /** [moved] with protein, carbs and fat scaled from this target's ratio basis to [energy]. */
    private fun scaledTo(moved: Targets, energy: Int): Targets {
        val basis = ratioBasis ?: MacroSplit(energyKcal, proteinG, carbsG, fatG)
        if (basis.kcal <= 0) return moved
        fun scaled(grams: Int) = (grams * energy.toDouble() / basis.kcal).roundToInt()
        return moved.copy(
            proteinG = scaled(basis.proteinG),
            carbsG = scaled(basis.carbsG),
            fatG = scaled(basis.fatG),
            ratioBasis = basis,
        )
    }

    companion object {
        const val STEP_KCAL = 50

        /** The handoff's starting goals, until onboarding sets real ones. */
        val Default = Targets(
            energyKcal = 2100,
            minKcal = 1900,
            maxKcal = 2300,
            proteinG = 120,
            carbsG = 250,
            fatG = 70,
            fiberG = 30,
        )
    }
}

/** Protein, carbs and fat grams at an energy target: the fixed point ratio mode scales from. */
data class MacroSplit(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

data class EnergyRange(
    val targetKcal: Int,
    val minKcal: Int,
    val maxKcal: Int,
    val activityKcal: Int,
)

/** How much watch activity (everyday movement plus workouts) adds to today's target. */
enum class ExerciseAdjustment {
    All,
    Some,
    None,
    ;

    /** Never negative and never above [EXERCISE_CAP_KCAL], so activity can only ever add a bounded amount. */
    fun activityKcal(movementKcal: Int, workoutKcal: Int): Int {
        val activity = max(0, movementKcal).toLong() + max(0, workoutKcal).toLong()
        val added = when (this) {
            All -> activity.toDouble()
            Some -> activity * EXERCISE_SOME_SHARE
            None -> 0.0
        }
        return added.coerceAtMost(EXERCISE_CAP_KCAL.toDouble()).roundToInt()
    }

    companion object {
        /** The prototype's default, kept for 0.1's `exercise` key. Today's range reads `exerciseRange`, where unanswered is [None]. */
        val Default = Some
    }
}
