package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

// TODO(dietitian): GOAL_FLOOR_KCAL is a hard floor an ED-informed dietitian may set before
// launch. Do not invent a value, not even a "safe-looking" one. While it is null the only hard
// limit is MIN_GOAL_KCAL, and a goal under the low-goal line (GoalSafety.lowGoalKcal) gets a soft
// note on Targets instead of a lock. That was the owner's call on 2026-10-29, after the old rule
// (nothing below where the person started) locked everyone's low end at their first range.
val GOAL_FLOOR_KCAL: Int? = null

// TODO(clinician): the lowest and highest any calorie goal can be. An engineer picked both, as
// typo guards rather than advice: 800 is where a very-low-calorie diet begins (NICE CG189 counts
// 800 kcal a day or less as one), which is only ever done under medical supervision, and 6,000 is
// past what all but a few athletes eat. Between them nothing is refused; GoalSafety says when a
// goal is low.
const val MIN_GOAL_KCAL: Int = 800
const val MAX_GOAL_KCAL: Int = 6000

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
 * The user's energy range and macro goals. [startingKcal] and [startingMinKcal]
 * are the target and low end the user began with. They are a record, not a
 * limit: the only floor is [floorKcal]. Each end of the range steps on its own
 * ([step]); [energyKcal], the aim, is the middle of the range, and no end goes
 * under [floorKcal] or over [MAX_GOAL_KCAL].
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
    /** The lowest any end of the goal can be: the dietitian's floor once set, [MIN_GOAL_KCAL] until then. */
    val floorKcal: Int get() = GOAL_FLOOR_KCAL ?: MIN_GOAL_KCAL

    val canLower: Boolean
        get() = minKcal - STEP_KCAL >= floorKcal

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
     * The least a goal Opson works out (a deficit or surplus against the burn) can come to: the
     * floor, and never under the lower of the low-goal lines. Someone can still set a lower
     * number of their own; Opson just never arrives at one for them because a day burned little.
     */
    val workedOutFloorKcal: Int get() = max(floorKcal, LOW_GOAL_FEMALE_KCAL)

    /**
     * [surplus] over what the user burns, or over [baseKcal] (the aim, or a day group's middle)
     * while the burn is not known, rounded to 10 and never under [workedOutFloorKcal].
     */
    fun surplusGoal(baseKcal: Int, burnKcal: Int?, surplus: Int): Int {
        val rounded = (((burnKcal ?: baseKcal) + surplus) / 10.0).roundToInt() * 10
        val floor = ((workedOutFloorKcal + 9) / 10) * 10
        return max(rounded, floor)
    }

    /** What the user burns less [deficit], rounded to 10 and never under [workedOutFloorKcal]. */
    fun deficitGoal(burnKcal: Int, deficit: Int): Int {
        val rounded = ((burnKcal - deficit) / 10.0).roundToInt() * 10
        val floor = ((workedOutFloorKcal + 9) / 10) * 10
        return max(rounded, floor)
    }

    /** Whether moving [end] of the range (or of day group [group]) by one step [up] is allowed. */
    fun canStep(end: RangeEnd, up: Boolean, group: Int? = null): Boolean = stepBlock(end, up, group) == null

    /** Why that step is not allowed, or null when it is: the floor, the ceiling, or ends that would touch. */
    fun stepBlock(end: RangeEnd, up: Boolean, group: Int? = null): StepBlock? {
        val (min, max) = ends(group) ?: return StepBlock.Gap
        val (nextMin, nextMax) = moved(min, max, end, up)
        return when {
            !up && nextMin < floorKcal -> StepBlock.Floor
            up && nextMax > MAX_GOAL_KCAL -> StepBlock.Ceiling
            nextMax - nextMin < STEP_KCAL -> StepBlock.Gap
            else -> null
        }
    }

    /**
     * Moves [end] of the base range, or of day group [group], by 50 kcal. `null` when that would
     * take the low end under [floorKcal], the high end over [MAX_GOAL_KCAL], or bring the ends
     * closer than one step. With [scaleMacros] (ratio mode) a base range step scales protein,
     * carbs and fat with the aim.
     */
    fun step(end: RangeEnd, up: Boolean, group: Int? = null, scaleMacros: Boolean = false): Targets? {
        val (min, max) = stepped(end, up, group) ?: return null
        if (group == null) return withEnds(min, max, scaleMacros)
        val byDay = style as TargetStyle.ByDay
        val groups = byDay.groups.toMutableList()
        groups[group] = groups[group].copy(minKcal = min, maxKcal = max)
        return copy(style = byDay.copy(groups = groups))
    }

    /**
     * Why [end] of the base range, or of day group [group], can't be typed as [kcal]: under the
     * floor, over [MAX_GOAL_KCAL], or closer than one step to the other end. Null when it can.
     */
    fun endBlock(end: RangeEnd, kcal: Int, group: Int? = null): StepBlock? {
        val (min, max) = ends(group) ?: return StepBlock.Gap
        val (nextMin, nextMax) = if (end == RangeEnd.Low) kcal to max else min to kcal
        return when {
            nextMin < floorKcal -> StepBlock.Floor
            nextMax > MAX_GOAL_KCAL -> StepBlock.Ceiling
            nextMax - nextMin < STEP_KCAL -> StepBlock.Gap
            else -> null
        }
    }

    /**
     * [end] of the base range, or of day group [group], typed as [kcal] (owner feedback,
     * 2026-10-30: "I should be able to type the numbers"). The same limits as a step
     * ([endBlock]); `null` past one. [scaleMacros] as in [step].
     */
    fun withEnd(end: RangeEnd, kcal: Int, group: Int? = null, scaleMacros: Boolean = false): Targets? {
        if (endBlock(end, kcal, group) != null) return null
        val (min, max) = ends(group) ?: return null
        val (nextMin, nextMax) = if (end == RangeEnd.Low) kcal to max else min to kcal
        if (group == null) return withEnds(nextMin, nextMax, scaleMacros)
        return withGroup(group) { it.copy(minKcal = nextMin, maxKcal = nextMax) }
    }

    /** A set deficit, the style's or day group [group]'s, typed as [kcal]: `null` under 0 or past [maxDeficitKcal]. */
    fun withDeficit(kcal: Int, group: Int? = null): Targets? {
        val current = deficitOf(group) ?: return null
        if (kcal < 0 || kcal > maxDeficitKcal(group)) return null
        if (group == null) return copy(style = TargetStyle.Deficit(kcal, current.startingKcal))
        return withGroup(group) { it.copy(shape = GroupShape.Deficit(kcal, current.startingKcal)) }
    }

    /** A surplus, the style's or day group [group]'s, typed as [kcal]: `null` under 0 or past [maxSurplusKcal]. */
    fun withSurplus(kcal: Int, group: Int? = null): Targets? {
        val current = surplusOf(group) ?: return null
        if (kcal < 0 || kcal > maxSurplusKcal(group)) return null
        if (group == null) return copy(style = TargetStyle.Surplus(kcal, current.startingKcal))
        return withGroup(group) { it.copy(shape = GroupShape.Surplus(kcal, current.startingKcal)) }
    }

    /** Day group [group]'s one number typed as [kcal]: `null` under the floor or over [MAX_GOAL_KCAL]. */
    fun withGroupNumber(kcal: Int, group: Int): Targets? {
        if (kcal !in floorKcal..MAX_GOAL_KCAL) return null
        return withGroup(group) { groupAround(it, kcal) }
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

    /**
     * Different by day, unless it already is: one group holding every day, on the base range.
     * Targets adds an empty second group straight after ([addDayGroup]) so days can be picked at once.
     */
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

    /**
     * Whether one number (the aim, or day group [group]'s middle) may step [up]. The number itself
     * stops at the floor and the ceiling; the range kept behind it never holds it back.
     */
    fun canStepNumber(up: Boolean, group: Int? = null): Boolean {
        val number = if (group == null) energyKcal else groupAt(group)?.midKcal ?: return false
        val next = number + if (up) STEP_KCAL else -STEP_KCAL
        return next in floorKcal..MAX_GOAL_KCAL
    }

    /**
     * Moves one number by 50 kcal: the aim under [TargetStyle.Exact] (macros scaling in ratio
     * mode as [raise] does), or day group [group]'s middle. The range kept behind it follows
     * ([withNumber], [groupAround]). `null` where [canStepNumber] says no.
     */
    fun stepNumber(up: Boolean, group: Int? = null, scaleMacros: Boolean = false): Targets? {
        if (!canStepNumber(up, group)) return null
        val delta = if (up) STEP_KCAL else -STEP_KCAL
        if (group == null) return withNumber(energyKcal + delta, scaleMacros)
        return withGroup(group) { groupAround(it, it.midKcal + delta) }
    }

    /**
     * One number set to [kcal], held between the floor and [MAX_GOAL_KCAL]. The range kept for
     * a switch back keeps its width, centred on the number where the bounds allow and pushed
     * inside them where they don't, so the number can go right to either bound. [scaleMacros]
     * as in [raise].
     */
    fun withNumber(kcal: Int, scaleMacros: Boolean = false): Targets {
        val number = kcal.coerceIn(floorKcal, MAX_GOAL_KCAL)
        val width = (maxKcal - minKcal).coerceIn(0, MAX_GOAL_KCAL - floorKcal)
        val low = (number - width / 2).coerceIn(floorKcal, MAX_GOAL_KCAL - width)
        val moved = copy(energyKcal = number, minKcal = low, maxKcal = low + width, ratioBasis = null)
        return if (scaleMacros) scaledTo(moved, number) else moved
    }

    /**
     * [group] with its middle at [number]. A group's one number is its middle, so near a bound
     * the range narrows around it rather than stopping it. A group already squeezed against a
     * bound widens back towards the base range's width as it leaves.
     */
    private fun groupAround(group: DayGroup, number: Int): DayGroup {
        val own = group.maxKcal - group.minKcal
        val squeezed = group.minKcal <= floorKcal || group.maxKcal >= MAX_GOAL_KCAL
        val width = if (squeezed) max(own, maxKcal - minKcal) else own
        val half = minOf(width / 2, number - floorKcal, MAX_GOAL_KCAL - number)
        return group.copy(minKcal = number - half, maxKcal = number + half)
    }

    private fun groupAt(group: Int): DayGroup? = (style as? TargetStyle.ByDay)?.groups?.getOrNull(group)

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

    /**
     * A tap on [day] in group [group]'s card. A day another group holds moves here; a day this
     * group holds goes back to the first group, which keeps whatever no other group has. The
     * first group's own days have nowhere else to go, so a tap on one changes nothing. Whatever
     * is tapped, every day stays in exactly one group.
     */
    fun toggleDay(day: DayOfWeek, group: Int): Targets {
        val byDay = style as? TargetStyle.ByDay ?: return this
        val holder = byDay.groups.getOrNull(group) ?: return this
        return when {
            day !in holder.days -> moveDay(day, group)
            group == 0 -> this
            else -> moveDay(day, 0)
        }
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

    /**
     * A new, empty group on the first group's range, as a plain range. `null` at
     * [TargetStyle.MAX_GROUPS]. An empty group is harmless: no day reads it, and [spanGoal] skips it.
     */
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
     * One step up, unless the high end would pass [MAX_GOAL_KCAL]; then the targets as they are.
     * With [scaleMacros] (ratio mode) protein, carbs and fat move with the energy target so the
     * split stays the same, the prototype's `GOAL[k] * baseT / GOAL.target`. Gram mode leaves
     * them. Fiber is a floor for gut health, not a share of energy, so it never scales.
     */
    fun raise(scaleMacros: Boolean = false): Targets =
        if (maxKcal + STEP_KCAL > MAX_GOAL_KCAL) this else shiftedBy(STEP_KCAL, scaleMacros)

    /** `null` when one more step down would cross the floor. [scaleMacros] as in [raise]. */
    fun lower(scaleMacros: Boolean = false): Targets? = if (canLower) shiftedBy(-STEP_KCAL, scaleMacros) else null

    /**
     * Sets the target directly, the range moving with it, held so neither end passes the floor
     * or [MAX_GOAL_KCAL]. Under [TargetStyle.Exact] the number is what is held, not the range
     * kept behind it ([withNumber]). [scaleMacros] as in [raise].
     */
    fun withEnergy(kcal: Int, scaleMacros: Boolean = false): Targets {
        if (style == TargetStyle.Exact) return withNumber(kcal, scaleMacros)
        val lowest = energyKcal + (floorKcal - minKcal)
        val highest = energyKcal + (MAX_GOAL_KCAL - maxKcal)
        return shiftedBy(kcal.coerceIn(lowest, max(lowest, highest)) - energyKcal, scaleMacros)
    }

    /**
     * A plain range of exactly [minKcal] to [maxKcal] around [aimKcal], each held between the floor
     * and [MAX_GOAL_KCAL]: what a goal writes ([GoalTargets.apply]). Nothing of the range before
     * carries over, so the same goal always lands on the same ends. [scaleMacros] as in [raise].
     */
    fun withBand(aimKcal: Int, minKcal: Int, maxKcal: Int, scaleMacros: Boolean = false): Targets {
        val aim = aimKcal.coerceIn(floorKcal, MAX_GOAL_KCAL)
        val low = minKcal.coerceIn(floorKcal, aim)
        val high = maxKcal.coerceIn(aim, MAX_GOAL_KCAL)
        val moved = copy(style = TargetStyle.Range, energyKcal = aim, minKcal = low, maxKcal = high, ratioBasis = null)
        return if (scaleMacros) scaledTo(moved, aim) else moved
    }

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
