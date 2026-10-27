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

// TODO(clinician): MAX_SURPLUS_KCAL caps "A set surplus" and must be set by a clinician before
// launch, as MAX_DEFICIT_KCAL is (the owner's call, 2026-10-27). Do not invent a value. A surplus
// can't put the goal under the floor, but gaining fast is not safe for everyone either. While it
// is null, a surplus can never grow past the one it started at.
val MAX_SURPLUS_KCAL: Int? = null

// TODO(clinician): how far either side of one number (an exact goal, a deficit or a surplus) still
// reads as on it. An engineer picked it, a quarter of the default range's width either side.
// Without it a single bite past one number would read as above it, and fire the big-day card,
// where a range has room built in. It only changes the words and the big-day card, never the goal
// or how it is drawn: one number is still drawn as one line.
const val ONE_NUMBER_LEEWAY_KCAL: Int = 100

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
 * [style] is how the goal is shaped (a range, one number, a set deficit, a
 * surplus, different by day). Ask [rangeFor] for any one day's goal rather than
 * reading the fields.
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

    /**
     * The most a set deficit may be, the style's or day group [group]'s: the dietitian's maximum
     * once set, until then where it started.
     */
    fun maxDeficitKcal(group: Int? = null): Int = MAX_DEFICIT_KCAL ?: deficitOf(group)?.startingKcal ?: 0

    /** The most a surplus may be, the style's or day group [group]'s: the clinician's maximum once set, until then where it started. */
    fun maxSurplusKcal(group: Int? = null): Int = MAX_SURPLUS_KCAL ?: surplusOf(group)?.startingKcal ?: 0

    private fun surplusOf(group: Int?): SetAmount? = if (group == null) {
        (style as? TargetStyle.Surplus)?.let { SetAmount(it.kcal, it.startingKcal) }
    } else {
        (shapeOf(group) as? GroupShape.Surplus)?.let { SetAmount(it.kcal, it.startingKcal) }
    }

    private fun deficitOf(group: Int?): SetAmount? = if (group == null) {
        (style as? TargetStyle.Deficit)?.let { SetAmount(it.kcal, it.startingKcal) }
    } else {
        (shapeOf(group) as? GroupShape.Deficit)?.let { SetAmount(it.kcal, it.startingKcal) }
    }

    private fun shapeOf(group: Int): GroupShape? = (style as? TargetStyle.ByDay)?.groups?.getOrNull(group)?.shape

    /** A set deficit or surplus and where it started, which is its cap until a clinician sets one. */
    private data class SetAmount(val kcal: Int, val startingKcal: Int)

    /**
     * The goal for [date]. A day group's goal under [TargetStyle.ByDay], whatever its shape; the aim itself under
     * [TargetStyle.Exact]; under [TargetStyle.Deficit], "about" what the user burns ([burnKcal],
     * [Burned.totalKcal] for that day, so it moves as activity syncs) less the deficit, never
     * below the floor; under [TargetStyle.Surplus], "about" the burn plus the surplus
     * ([surplusGoal]). A deficit falls back to the range when the burn is not known or [preset]
     * does not offer deficits, and a surplus when [preset] does not offer one, so a mode change
     * can never leave either running where the user can't see or change it.
     */
    fun rangeFor(date: LocalDate, preset: PresetId, burnKcal: Int? = null): DayGoal {
        val byDay = style as? TargetStyle.ByDay ?: return everyDayGoal(preset, burnKcal)
        val index = byDay.groups.indexOfFirst { date.dayOfWeek in it.days }
        return if (index < 0) baseGoal else groupGoal(byDay.groups[index], index, burnKcal, preset)
    }

    /**
     * The goal a chart draws across many days, where [rangeFor] answers for one. The same as any
     * day's goal for every style but [TargetStyle.ByDay], where it runs from the lowest day
     * group's goal to the highest, over the groups that hold a day; with one level it is that
     * group's own goal.
     */
    fun spanGoal(preset: PresetId, burnKcal: Int? = null): DayGoal {
        val byDay = style as? TargetStyle.ByDay ?: return everyDayGoal(preset, burnKcal)
        val goals = byDay.groups.mapIndexedNotNull { index, group ->
            if (group.days.isEmpty()) null else groupGoal(group, index, burnKcal, preset)
        }
        if (goals.isEmpty()) return baseGoal
        if (goals.distinctBy { it.minKcal to it.maxKcal }.size == 1) return goals.first()
        val min = goals.minOf { it.minKcal }
        val max = goals.maxOf { it.maxKcal }
        return DayGoal.Range(min, max, (min + max) / 2)
    }

    private val baseGoal: DayGoal get() = DayGoal.Range(minKcal, maxKcal, energyKcal)

    /** The goal of every day, for each style but [TargetStyle.ByDay]. */
    private fun everyDayGoal(preset: PresetId, burnKcal: Int?): DayGoal = when (val s = style) {
        TargetStyle.Range, is TargetStyle.ByDay -> baseGoal
        TargetStyle.Exact -> DayGoal.Exact(energyKcal)
        is TargetStyle.Surplus -> if (TargetStyle.surplusAllowed(preset)) {
            DayGoal.About(surplusGoal(energyKcal, burnKcal, s.kcal))
        } else {
            baseGoal
        }
        is TargetStyle.Deficit -> if (burnKcal == null || !TargetStyle.deficitAllowed(preset)) {
            baseGoal
        } else {
            DayGoal.About(deficitGoal(burnKcal, s.kcal))
        }
    }

    /** Day group [index]'s goal under [TargetStyle.ByDay], whichever days it holds; null outside it. */
    fun groupGoal(index: Int, preset: PresetId, burnKcal: Int? = null): DayGoal? =
        (style as? TargetStyle.ByDay)?.groups?.getOrNull(index)?.let { groupGoal(it, index, burnKcal, preset) }

    private fun groupGoal(group: DayGroup, index: Int, burnKcal: Int?, preset: PresetId): DayGoal {
        val range = DayGoal.Range(group.minKcal, group.maxKcal, group.midKcal, groupIndex = index)
        return when (val shape = group.shape) {
            GroupShape.Range -> range
            GroupShape.Exact -> DayGoal.Exact(group.midKcal, groupIndex = index)
            is GroupShape.Surplus -> if (TargetStyle.surplusAllowed(preset)) {
                DayGoal.About(surplusGoal(group.midKcal, burnKcal, shape.kcal), groupIndex = index)
            } else {
                range
            }
            is GroupShape.Deficit -> if (burnKcal == null || !TargetStyle.deficitAllowed(preset)) {
                range
            } else {
                DayGoal.About(deficitGoal(burnKcal, shape.kcal), groupIndex = index)
            }
        }
    }

    /**
     * [surplus] over what the user burns, or over [baseKcal] (the aim, or a day group's middle)
     * while the burn is not known, rounded to 10 and never under the floor.
     */
    fun surplusGoal(baseKcal: Int, burnKcal: Int?, surplus: Int): Int {
        val rounded = (((burnKcal ?: baseKcal) + surplus) / 10.0).roundToInt() * 10
        val floor = ((floorKcal + 9) / 10) * 10
        return max(rounded, floor)
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

    /** A plain range. Day groups, a deficit or a surplus are dropped; the base range stays as it is. */
    fun useRange(): Targets = copy(style = TargetStyle.Range)

    /** One number: the aim as it is, so nothing the user eats against changes on the switch. */
    fun useExact(): Targets = copy(style = TargetStyle.Exact)

    /**
     * A surplus, unless one is already set. It starts at what the aim already implies against
     * [burnKcal] (floored to 50, never negative), and at 0 when the burn is unknown, so the goal
     * is about the aim on the day it is picked. While [MAX_SURPLUS_KCAL] is unset that start is
     * also its cap, as the deficit's is.
     */
    fun useSurplus(burnKcal: Int?): Targets {
        if (style is TargetStyle.Surplus) return this
        val start = impliedSurplus(energyKcal, burnKcal)
        return copy(style = TargetStyle.Surplus(kcal = start, startingKcal = start))
    }

    private fun impliedSurplus(aimKcal: Int, burnKcal: Int?): Int {
        val implied = burnKcal?.let { max(0, aimKcal - it) / STEP_KCAL * STEP_KCAL } ?: 0
        return MAX_SURPLUS_KCAL?.let { minOf(implied, it) } ?: implied
    }

    /**
     * A set deficit, unless one is already set. It starts at what the user's aim already implies
     * against [burnKcal] (floored to 50, never negative), and at 0 when the burn is unknown, so
     * while [MAX_DEFICIT_KCAL] is unset it never asks for more than the user already had.
     */
    fun useDeficit(burnKcal: Int?): Targets {
        if (style is TargetStyle.Deficit) return this
        val start = impliedDeficit(energyKcal, burnKcal)
        return copy(style = TargetStyle.Deficit(kcal = start, startingKcal = start))
    }

    private fun impliedDeficit(aimKcal: Int, burnKcal: Int?): Int {
        val implied = burnKcal?.let { max(0, it - aimKcal) / STEP_KCAL * STEP_KCAL } ?: 0
        return MAX_DEFICIT_KCAL?.let { minOf(implied, it) } ?: implied
    }

    /** Different by day, unless it already is: one group holding every day, on the base range. */
    fun useByDay(): Targets {
        if (style is TargetStyle.ByDay) return this
        return copy(style = TargetStyle.ByDay(listOf(DayGroup(DayOfWeek.entries.toSet(), minKcal, maxKcal))))
    }

    /**
     * One step more or less deficit, on [TargetStyle.Deficit] or day group [group]'s
     * [GroupShape.Deficit]. `null` below 0 or past [maxDeficitKcal].
     */
    fun stepDeficit(up: Boolean, group: Int? = null): Targets? {
        val current = deficitOf(group) ?: return null
        val next = current.kcal + if (up) STEP_KCAL else -STEP_KCAL
        if (next < 0 || next > maxDeficitKcal(group)) return null
        if (group == null) return copy(style = TargetStyle.Deficit(next, current.startingKcal))
        return withGroup(group) { it.copy(shape = GroupShape.Deficit(next, current.startingKcal)) }
    }

    /**
     * One step more or less surplus, on [TargetStyle.Surplus] or day group [group]'s
     * [GroupShape.Surplus]. `null` below 0 or past [maxSurplusKcal].
     */
    fun stepSurplus(up: Boolean, group: Int? = null): Targets? {
        val current = surplusOf(group) ?: return null
        val next = current.kcal + if (up) STEP_KCAL else -STEP_KCAL
        if (next < 0 || next > maxSurplusKcal(group)) return null
        if (group == null) return copy(style = TargetStyle.Surplus(next, current.startingKcal))
        return withGroup(group) { it.copy(shape = GroupShape.Surplus(next, current.startingKcal)) }
    }

    /** Whether one number (the aim, or day group [group]'s middle) may step [up]: lower stops at the floor, as the range does. */
    fun canStepNumber(up: Boolean, group: Int? = null): Boolean {
        if (up) return true
        val (min, max) = ends(group) ?: return false
        return min - STEP_KCAL >= lowFloorKcal && (min + max) / 2 - STEP_KCAL >= floorKcal
    }

    /**
     * Moves one number by 50 kcal: the aim under [TargetStyle.Exact] (the whole range moving
     * with it, macros scaling in ratio mode as [raise] does), or day group [group]'s middle,
     * both its ends with it. `null` where [canStepNumber] says no.
     */
    fun stepNumber(up: Boolean, group: Int? = null, scaleMacros: Boolean = false): Targets? {
        if (!canStepNumber(up, group)) return null
        val delta = if (up) STEP_KCAL else -STEP_KCAL
        if (group == null) return shiftedBy(delta, scaleMacros)
        return withGroup(group) { it.copy(minKcal = it.minKcal + delta, maxKcal = it.maxKcal + delta) }
    }

    /**
     * Gives day group [group] its own [shape]. A deficit or surplus starts at what the group's
     * middle already implies against [burnKcal], as [useDeficit] and [useSurplus] do, whatever
     * [shape] carries. Picking the shape a group already has keeps it as it is.
     */
    fun shapeGroup(group: Int, shape: GroupShape, burnKcal: Int? = null): Targets? {
        val current = (style as? TargetStyle.ByDay)?.groups?.getOrNull(group) ?: return null
        if (current.shape::class == shape::class) return this
        val start = when (shape) {
            is GroupShape.Surplus -> impliedSurplus(current.midKcal, burnKcal).let { GroupShape.Surplus(it, it) }
            is GroupShape.Deficit -> impliedDeficit(current.midKcal, burnKcal).let { GroupShape.Deficit(it, it) }
            GroupShape.Range, GroupShape.Exact -> shape
        }
        return withGroup(group) { it.copy(shape = start) }
    }

    private fun withGroup(group: Int, transform: (DayGroup) -> DayGroup): Targets? {
        val byDay = style as? TargetStyle.ByDay ?: return null
        if (group !in byDay.groups.indices) return null
        return copy(style = byDay.copy(groups = byDay.groups.mapIndexed { index, it -> if (index == group) transform(it) else it }))
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

    /** A new, empty group on the first group's range, as a plain range. `null` at [TargetStyle.MAX_GROUPS]. */
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
     * A day's range: [goal] plus whatever the user lets watch activity add, at most
     * [EXERCISE_CAP_KCAL]. Never below [goal], whatever the readings.
     *
     * Pass the day's [rangeFor] as [goal], or a "different by day" or deficit goal is lost. The
     * default is the base range, which is the goal only under [TargetStyle.Range].
     */
    fun today(
        adjustment: ExerciseAdjustment,
        movementKcal: Int,
        workoutKcal: Int,
        goal: DayGoal = DayGoal.Range(minKcal, maxKcal, energyKcal),
    ): EnergyRange {
        val activity = adjustment.activityKcal(movementKcal, workoutKcal)
        return EnergyRange(goal.targetKcal + activity, goal.minKcal + activity, goal.maxKcal + activity, activity)
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
) {
    /** One number rather than a range (one number, a deficit, a surplus): drawn as a line, never a band. */
    val single: Boolean get() = minKcal == maxKcal

    /** Where [kcal] eaten sits against the goal. One number has [ONE_NUMBER_LEEWAY_KCAL] either side of it. */
    fun position(kcal: Double): GoalPosition {
        val leeway = if (single) ONE_NUMBER_LEEWAY_KCAL else 0
        return when {
            kcal > maxKcal + leeway -> GoalPosition.Above
            kcal >= minKcal - leeway -> GoalPosition.On
            else -> GoalPosition.Below
        }
    }
}

/** Under, on or over a day's goal: inside a range, or close to one number. */
enum class GoalPosition { Below, On, Above }

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
