package com.opson.features.trends.impl.recap

import com.opson.features.trends.impl.Balance
import com.opson.features.trends.impl.CalendarMode
import com.opson.features.trends.impl.OnTrack
import com.opson.features.trends.impl.PlanTone
import com.opson.features.trends.impl.RunningTotals
import com.opson.features.trends.impl.ScaleDirection
import com.opson.features.trends.impl.TrendsCalendar
import com.opson.features.trends.impl.TrendsReadouts
import com.opson.features.trends.impl.TrendsStateBuilder
import com.opson.features.trends.impl.WeightCurves
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.MoodLevel
import com.opson.libraries.diary.NewFoods
import com.opson.libraries.diary.RecapKind
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ShowingUp
import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalCatalog
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.nutrition.shownOn
import com.opson.libraries.nutrition.ui.chartReading
import com.opson.libraries.ui.components.ChartReadout
import com.opson.libraries.ui.components.IntakeBar
import com.opson.libraries.goals.GoalOutcome
import com.opson.libraries.goals.GoalStatuses
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Everything one recap is built from. [days] covers the period and the lookback before it (for
 * new foods), oldest first, and may have holes; [burnedByDay] is each day's Burned with
 * `release.goalsAndEnergy`, null without it. [marks] are every day's showing-up marks, for the run
 * and the milestones; [celebrations] is how celebrations speak, Off while they are turned off or
 * not released.
 */
data class RecapInputs(
    val today: LocalDate,
    val period: RecapPeriod,
    val settings: Settings,
    val days: List<Day>,
    val weighIns: List<WeighIn>,
    val timeZone: TimeZone,
    val burnedByDay: Map<LocalDate, Burned>? = null,
    /** Each day's steps from the health app while a steps goal is active (`release.health`); null otherwise. */
    val stepsByDay: Map<LocalDate, Int>? = null,
    val checkInOn: Boolean = false,
    val marks: List<DayMark> = emptyList(),
    val celebrations: CelebrationStyle = CelebrationStyle.Off,
)

/**
 * A recap, top to bottom (`release.recaps`, owner feedback 2026-10-30). Every section is null when
 * the mode, the flags or the week have nothing honest to put in it. After numbers have been hard
 * ([gentle]) a recap is about showing up and how the days felt, never an amount: the eaten, burned,
 * balance, weight and plan sections are never built. Nothing here grades a week.
 */
data class RecapState(
    val loading: Boolean = true,
    val period: RecapPeriod? = null,
    /** An earlier period to step back to, and a later one that has started. */
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** Still going: the period holds today. Its totals are so far. */
    val inProgress: Boolean = false,
    val gentle: Boolean = false,
    val showingUp: ShowingUpSection? = null,
    val eaten: EatenSection? = null,
    val burned: BurnedSection? = null,
    val balance: BalanceSection? = null,
    val weight: WeightSection? = null,
    val plan: PlanSection? = null,
    /** The period against each active goal the mode offers, the one setting the target first. */
    val goals: List<GoalWeek> = emptyList(),
    val checkIns: CheckInsSection? = null,
    val topFoods: List<TopFood> = emptyList(),
    val newFoods: List<String> = emptyList(),
    val milestones: MilestonesSection? = null,
) {
    /** Nothing logged and nothing checked in: one calm line instead of empty cards. */
    val empty: Boolean get() = showingUp?.logged == 0 && checkIns == null
}

/** How each day of the period went, as a strip of dots, and the counts the totals rest on. */
data class ShowingUpSection(
    val logged: Int,
    /** Marked "skip this day" (untracked): left out of everything, and said so. */
    val skipped: Int,
    /** Past days with nothing logged. */
    val notLogged: Int,
    /** Days still to come in a period that holds today. */
    val ahead: Int,
    val days: List<RecapDayDot>,
    /** "Totals count only the 5 days you logged.": shown when a numbers section is, and some day was left out. */
    val countingNote: Boolean,
)

/** One day of the strip: its label (`Mon`, or the day of the month) and how it went. */
data class RecapDayDot(val date: LocalDate, val label: String, val status: RecapDayStatus)

enum class RecapDayStatus { Logged, Skipped, NotLogged, Ahead }

/**
 * Eaten over the logged days: the [total], the [average] per logged day and the range it sits
 * against, and a bar a day. [days] is how many days the amounts count; a logged day whose
 * calories haven't opened (Later) is a gap.
 */
data class EatenSection(
    val total: Display,
    val average: Display,
    val range: Display,
    val singleGoal: Boolean,
    val days: Int,
    val bars: List<IntakeBar>,
    val band: ClosedFloatingPointRange<Float>,
    val readouts: List<ChartReadout?>,
)

/** What made up total burned (TDEE) over the logged days, in the order Burned adds it up. */
enum class BurnedPart { Resting, Activity, Exercise, Digesting }

/** One part of Burned: its amount over the logged days, and its share of the bar. */
data class BurnedShare(val part: BurnedPart, val amount: Display, val share: Float)

/** Total burned (TDEE) over the logged days, its daily average, and its parts. [days] had a Burned. */
data class BurnedSection(val total: Display, val average: Display, val parts: List<BurnedShare>, val days: Int)

/** Burned less eaten over the days both are known, as the running total says it, and the rough weight it comes to. */
data class BalanceSection(val amount: Display, val side: Balance, val weight: Display?, val unit: WeightUnit, val days: Int, val of: Int)

/** What the smoothed weight did over the period: [change] at Show, or a trend word when softened. */
data class WeightSection(val change: Display, val direction: ScaleDirection, val softened: Boolean)

/**
 * The days against targets the person set by hand: on plan, or close to it, out of the logged days
 * whose amounts are all known, by Trends' calendar rules. Never a count of days off plan. Only
 * while no goal sets the target; a goal's own days are [GoalWeek].
 */
data class PlanSection(val onPlan: Int, val close: Int, val of: Int)

/**
 * One goal over the period, each day wrapped up as Wrap up the day wraps it ([GoalStatuses.wrapUp],
 * [GoalOutcomes]): [onPlan] days on plan, [aBit] a bit over or under, out of the [counted] days it
 * could judge (skipped days, days with nothing logged and unknown amounts don't count either way).
 * Never a count of big days, well under or not this time. [weekOnTrack] is true when a weekly
 * average's week ended on track, said only then; [streak] the goal's run at the end, where goal
 * streaks are on and celebrations say numbers.
 */
data class GoalWeek(
    val type: GoalType,
    val onPlan: Int,
    val aBit: Int,
    val counted: Int,
    val weekOnTrack: Boolean = false,
    val streak: Int? = null,
)

/**
 * The check-ins' answers over the period, each the step nearest its average, and how many days
 * had one. Words only, so it shows in every mode.
 */
data class CheckInsSection(
    val days: Int,
    val hunger: StringResource?,
    val energy: StringResource?,
    val mood: StringResource?,
    val sleep: StringResource?,
)

/** A food logged on several days. [days] is left out after numbers have been hard. */
data class TopFood(val name: String, val days: Int?)

/** The run at the period's end and the milestones reached in it; [counts] false says them without numbers. */
data class MilestonesSection(val run: Int, val reached: List<Milestone>, val counts: Boolean)

/**
 * Builds [RecapState]. Pure: the view model, the previews, the tests and the leak test all call it.
 *
 * How skipped days count (owner feedback): totals add up only the logged days, and averages are
 * per logged day, so a day marked skipped or left empty never reads as a day of eating nothing.
 * Every amount resolves on [Surface.Week] with the period's last day as the context day, and a day
 * whose calories haven't opened yet (Later) stays out, as on Trends.
 */
object RecapStateBuilder {

    /** Foods logged on fewer days than this aren't "top": one lunch isn't a habit. */
    const val TOP_FOOD_MIN_DAYS = 2
    const val TOP_FOODS = 5

    private const val SCALE_HEADROOM = 1.2
    private const val SCALE_STEP = 100.0
    private const val MONTH_LABEL_EVERY = 7

    fun build(inputs: RecapInputs, policy: VisibilityPolicy): RecapState {
        val period = inputs.period
        val settings = inputs.settings
        val byDate = inputs.days.associateBy { it.date }
        val dayAt = { date: LocalDate -> byDate[date] ?: Day.empty(date) }
        val dates = period.days
        val past = dates.filter { it <= inputs.today }
        val gentle = settings.onboarding.hard
        val context = FormatContext(day = period.end)
        val opens = { metric: Metric, day: Day -> TrendsStateBuilder.opensOn(metric, day, inputs.today, settings.weekStart, policy) }
        val logged = past.map(dayAt).filter { it.countsInTrends }

        val eaten = if (gentle) null else eaten(inputs, dates, dayAt, logged, policy, context, opens)
        val burned = if (gentle) null else burned(inputs, logged, policy, context, opens)
        val showingUp = showingUp(inputs, dates, dayAt, numbersShown = eaten != null || burned != null)
        return RecapState(
            loading = false,
            period = period,
            canGoBack = true,
            canGoForward = period.next().start <= inputs.today,
            inProgress = period.end >= inputs.today,
            gentle = gentle,
            showingUp = showingUp,
            eaten = eaten,
            burned = burned,
            balance = if (gentle) null else balance(inputs, logged, policy, context, opens),
            weight = if (gentle) null else weight(inputs, policy),
            plan = if (gentle || GoalBook.goals(settings).any { it.drivesTarget }) null else plan(inputs, logged, policy, context, opens),
            goals = goals(inputs, policy, dayAt, opens),
            checkIns = if (inputs.checkInOn) checkIns(past.map(dayAt).filterNot { it.untracked }.mapNotNull { it.checkIn }) else null,
            topFoods = topFoods(logged, counts = !gentle),
            newFoods = NewFoods.between(inputs.days, period.start, minOf(period.end, inputs.today)),
            milestones = milestones(inputs),
        )
    }

    private fun showingUp(inputs: RecapInputs, dates: List<LocalDate>, dayAt: (LocalDate) -> Day, numbersShown: Boolean): ShowingUpSection {
        val dots = dates.mapIndexed { index, date ->
            val day = dayAt(date)
            val status = when {
                date > inputs.today -> RecapDayStatus.Ahead
                day.untracked -> RecapDayStatus.Skipped
                day.hasLogged -> RecapDayStatus.Logged
                else -> RecapDayStatus.NotLogged
            }
            RecapDayDot(date, dotLabel(inputs.period, date, index), status)
        }
        val logged = dots.count { it.status == RecapDayStatus.Logged }
        val skipped = dots.count { it.status == RecapDayStatus.Skipped }
        // Today, while it is still going and empty, is a day ahead rather than one not logged.
        val notLogged = dots.count { it.status == RecapDayStatus.NotLogged && it.date != inputs.today }
        val ahead = dots.count { it.status == RecapDayStatus.Ahead } + dots.count { it.status == RecapDayStatus.NotLogged && it.date == inputs.today }
        return ShowingUpSection(
            logged = logged,
            skipped = skipped,
            notLogged = notLogged,
            ahead = ahead,
            days = dots.map { if (it.date == inputs.today && it.status == RecapDayStatus.NotLogged) it.copy(status = RecapDayStatus.Ahead) else it },
            countingNote = numbersShown && logged > 0 && skipped + notLogged > 0,
        )
    }

    private fun dotLabel(period: RecapPeriod, date: LocalDate, index: Int): String = when (period.kind) {
        RecapKind.Week -> TrendsCalendar.weekdayLabel(date)
        RecapKind.Month -> if (index % MONTH_LABEL_EVERY == 0) date.day.toString() else ""
    }

    private fun eaten(
        inputs: RecapInputs,
        dates: List<LocalDate>,
        dayAt: (LocalDate) -> Day,
        logged: List<Day>,
        policy: VisibilityPolicy,
        context: FormatContext,
        opens: (Metric, Day) -> Boolean,
    ): EatenSection? {
        if (policy.effectiveLevel(Metric.Calories, Surface.Week, context) != Visibility.Show) return null
        val counted = logged.filter { opens(Metric.Calories, it) }.mapNotNull { day -> day.totals().kcal.value?.let { day.date to it } }.toMap()
        if (counted.isEmpty()) return null
        val total = counted.values.sum()
        val settings = inputs.settings
        val goal = settings.targets.spanGoal(settings.numbers.preset, inputs.burnedByDay?.get(inputs.period.end)?.totalKcal)
        val scale = ceil(maxOf(goal.maxKcal.toDouble(), counted.values.max()) * SCALE_HEADROOM / SCALE_STEP) * SCALE_STEP
        return EatenSection(
            total = policy.resolve(Metric.Calories, total, Surface.Week, context),
            average = policy.resolve(Metric.Calories, total / counted.size, Surface.Week, context),
            range = if (goal.minKcal == goal.maxKcal) {
                policy.resolve(Metric.Calories, goal.minKcal.toDouble(), Surface.Week, context)
            } else {
                policy.resolveRange(Metric.Calories, goal.minKcal.toDouble(), goal.maxKcal.toDouble(), Surface.Week, context)
            },
            singleGoal = goal.minKcal == goal.maxKcal,
            days = counted.size,
            bars = dates.mapIndexed { index, date ->
                IntakeBar(dotLabel(inputs.period, date, index), counted[date]?.let { (it / scale).toFloat() }, isCurrent = date == inputs.today)
            },
            band = (goal.minKcal / scale).toFloat()..(goal.maxKcal / scale).toFloat(),
            readouts = dates.map { date ->
                counted[date]?.let { kcal ->
                    ChartReadout(
                        TrendsReadouts.day(date, inputs.today),
                        listOfNotNull(policy.resolve(Metric.Calories, kcal, Surface.Week, context).chartReading(Metric.Calories)),
                    )
                }
            },
        )
    }

    private fun burned(
        inputs: RecapInputs,
        logged: List<Day>,
        policy: VisibilityPolicy,
        context: FormatContext,
        opens: (Metric, Day) -> Boolean,
    ): BurnedSection? {
        val byDay = inputs.burnedByDay ?: return null
        if (policy.effectiveLevel(Metric.EnergyOut, Surface.Week, context) != Visibility.Show) return null
        val counted = logged.filter { opens(Metric.EnergyOut, it) }.mapNotNull { byDay[it.date]?.shownOn(policy, Surface.Week, context) }
        if (counted.isEmpty()) return null
        val parts = BurnedPart.entries.associateWith { part -> counted.sumOf { part.of(it) }.toDouble() }
        val total = counted.sumOf { it.totalKcal }.toDouble()
        return BurnedSection(
            total = policy.resolve(Metric.EnergyOut, total, Surface.Week, context),
            average = policy.resolve(Metric.EnergyOut, total / counted.size, Surface.Week, context),
            parts = parts.filterValues { it > 0.0 }.map { (part, kcal) ->
                BurnedShare(part, policy.resolve(Metric.EnergyOut, kcal, Surface.Week, context), if (total > 0) (kcal / total).toFloat() else 0f)
            },
            days = counted.size,
        )
    }

    /** One day's share of [this] part, as Burned adds it up ([Burned.activityKcal]). */
    internal fun BurnedPart.of(burned: Burned): Int {
        fun kcal(value: Int?) = (value ?: 0).coerceAtLeast(0)
        return when (this) {
            BurnedPart.Resting -> burned.resting.kcal
            BurnedPart.Activity -> kcal(burned.everydayKcal) + if (burned.activityCounted) kcal(burned.movementKcal) else 0
            BurnedPart.Exercise -> if (burned.activityCounted) kcal(burned.workoutKcal) + kcal(burned.exerciseKcal) else 0
            BurnedPart.Digesting -> kcal(burned.foodKcal)
        }
    }

    /**
     * Burned less eaten, added up over the days both are fully known, the running total's rules
     * ([RunningTotals]): calories and energy out at Show, never after numbers have been hard.
     */
    private fun balance(
        inputs: RecapInputs,
        logged: List<Day>,
        policy: VisibilityPolicy,
        context: FormatContext,
        opens: (Metric, Day) -> Boolean,
    ): BalanceSection? {
        val byDay = inputs.burnedByDay ?: return null
        if (policy.effectiveLevel(Metric.Calories, Surface.Week, context) != Visibility.Show) return null
        if (policy.effectiveLevel(Metric.EnergyOut, Surface.Week, context) != Visibility.Show) return null
        val balances = logged.mapNotNull { day ->
            val kcal = day.totals().kcal
            val eaten = kcal.value
            val burn = byDay[day.date]?.totalKcal
            if (kcal.isPartial || eaten == null || burn == null) return@mapNotNull null
            if (!opens(Metric.Calories, day) || !opens(Metric.EnergyOut, day)) return@mapNotNull null
            RunningTotals.DayBalance(day.date, burn, eaten)
        }
        if (balances.isEmpty()) return null
        val total = RunningTotals.total(balances)
        val side = RunningTotals.side(total)
        val unit = inputs.settings.weightUnit
        val weightShown = policy.effectiveLevel(Metric.Weight, Surface.Weight) == Visibility.Show
        return BalanceSection(
            amount = policy.resolve(Metric.Calories, abs(total), Surface.Week, context),
            side = side,
            weight = if (weightShown && side != Balance.Even) {
                policy.resolve(Metric.Weight, RunningTotals.weightKg(total), Surface.Weight, FormatContext(weightUnit = unit))
            } else {
                null
            },
            unit = unit,
            days = balances.size,
            of = logged.size,
        )
    }

    /** The smoothed weight's move over the period, from the first weigh-in in it to the last; null under two. */
    private fun weight(inputs: RecapInputs, policy: VisibilityPolicy): WeightSection? {
        val level = policy.effectiveLevel(Metric.Weight, Surface.Weight)
        if (level == Visibility.Hide) return null
        val end = minOf(inputs.period.end, inputs.today)
        val days = (end.toEpochDays() - inputs.period.start.toEpochDays()).toInt() + 1
        val curve = WeightCurves.curve(inputs.weighIns, end, days, inputs.timeZone)
        val points = curve.points.filter { it.day >= inputs.period.start }
        if (points.size < 2) return null
        val change = points.last().trendKg - points.first().trendKg
        val direction = when {
            abs(change) < STEADY_KG -> ScaleDirection.Steady
            change < 0 -> ScaleDirection.Down
            else -> ScaleDirection.Up
        }
        val display = policy.resolve(
            Metric.Weight,
            abs(change),
            Surface.Weight,
            FormatContext(weightUnit = inputs.settings.weightUnit, trendKgPerWeek = curve.kgPerWeek),
        )
        if (display is Display.Hidden) return null
        return WeightSection(display, direction, softened = display is Display.Soft)
    }

    /**
     * On plan or close, out of the logged days the calendar can read against the plan: the
     * calendar's own rules ([OnTrack]), so the two never disagree.
     */
    private fun plan(
        inputs: RecapInputs,
        logged: List<Day>,
        policy: VisibilityPolicy,
        context: FormatContext,
        opens: (Metric, Day) -> Boolean,
    ): PlanSection? {
        val settings = inputs.settings
        if (OnTrack.mode(settings, policy, context) != CalendarMode.Plan) return null
        val tones = logged.mapNotNull { day ->
            val burn = inputs.burnedByDay?.get(day.date)?.totalKcal
            val opened = opens(Metric.Calories, day) && (!OnTrack.usesBurn(settings.targets) || opens(Metric.EnergyOut, day))
            val standing = OnTrack.standingOf(day, inputs.today, settings, CalendarMode.Plan, opened, burn)
            OnTrack.tone(standing, OnTrack.direction(settings.targets, day.date, settings.numbers.preset, burn)).takeIf { it != PlanTone.None }
        }
        if (tones.isEmpty()) return null
        return PlanSection(onPlan = tones.count { it == PlanTone.On }, close = tones.count { it == PlanTone.Near }, of = tones.size)
    }

    /**
     * Each active goal the mode offers ([GoalCatalog.offered]: after numbers have been hard only
     * the gentle ones, about showing up and meals), each day wrapped up by the goal engine as Wrap
     * up the day wraps it. Today counts once it is wrapped up; a day a calorie goal can't read yet
     * (Later) stays out.
     */
    private fun goals(
        inputs: RecapInputs,
        policy: VisibilityPolicy,
        dayAt: (LocalDate) -> Day,
        opens: (Metric, Day) -> Boolean,
    ): List<GoalWeek> {
        val settings = inputs.settings
        val period = inputs.period
        val end = minOf(period.end, inputs.today)
        if (end < period.start) return emptyList()
        val offered = GoalCatalog.offered(policy, settings.onboarding.hard, settings.bodyStage, Surface.Week)
        val active = GoalBook.goals(settings).filter { it.isActive && it.type.kind in offered }
        if (active.isEmpty()) return emptyList()
        // Every day the log holds, so a weekly average replays its week and a goal streak its run.
        val facts = inputs.days.filter { it.date <= end }.associate { day ->
            day.date to GoalStatuses.facts(day, inputs.burnedByDay?.get(day.date), inputs.stepsByDay?.get(day.date))
        }
        val weightKg = inputs.weighIns.lastOrNull { it.measuredAt.toLocalDateTime(inputs.timeZone).date <= end }?.kg
        val wrapped = period.days
            .filter { it < inputs.today || (it == inputs.today && dayAt(it).closedByUser) }
            .map { date -> GoalStatuses.wrapUp(settings, facts, date, inputs.today, weightKg) }
        val ids = active.map { it.id }.toSet()
        return wrapped.flatten().filter { it.goal.id in ids }.groupBy { it.goal.id }.values.mapNotNull { wraps ->
            val goal = wraps.first().goal
            // A day wrapped up as its own today can't miss showing up, so a past day nobody showed
            // up on is counted here as the "not this time" it was.
            val counted = wraps
                .filter { it.outcome != GoalOutcome.NotCounted || (goal.type == GoalType.TrackEveryDay && !dayAt(it.day.date).untracked && it.day.date >= goal.startDate) }
                .filter { !goal.type.setsCalories || opens(Metric.Calories, dayAt(it.day.date)) }
            if (counted.isEmpty()) return@mapNotNull null
            val last = wraps.last()
            GoalWeek(
                type = goal.type,
                onPlan = counted.count { it.outcome == GoalOutcome.OnPlan },
                aBit = counted.count { it.outcome == GoalOutcome.ABitOver || it.outcome == GoalOutcome.ABitUnder },
                counted = counted.size,
                weekOnTrack = period.kind == RecapKind.Week && last.day.date == period.end && last.weekOnTrack == true,
                streak = last.streak?.takeIf { it > 0 && inputs.celebrations == CelebrationStyle.Counts },
            )
        }.sortedByDescending { week -> active.first { it.type == week.type }.drivesTarget }
    }

    /** Each answer's average, back to the nearest step of its scale; null with no check-in. */
    internal fun checkIns(checkIns: List<CheckIn>): CheckInsSection? {
        if (checkIns.isEmpty()) return null
        fun average(values: List<Int>): Int? = if (values.isEmpty()) null else values.average().roundToInt()
        val hunger = average(checkIns.mapNotNull { it.hunger?.value })?.let(HungerLevel::of)
        val energy = average(checkIns.mapNotNull { it.energy?.ordinal })?.let { Energy.entries[it] }
        val mood = average(checkIns.mapNotNull { it.mood?.ordinal })?.let { MoodLevel.entries[it] }
        val sleep = average(checkIns.mapNotNull { it.sleep?.ordinal })?.let { Sleep.entries[it] }
        if (hunger == null && energy == null && mood == null && sleep == null) return null
        return CheckInsSection(
            days = checkIns.size,
            hunger = hunger?.label,
            energy = energy?.label,
            mood = mood?.label,
            sleep = sleep?.label,
        )
    }

    /**
     * The foods logged on the most days, at least [TOP_FOOD_MIN_DAYS], most days first, ties to the
     * one logged most often and then by name. A food is its catalog id, custom food id or name, as
     * new foods count it; quick numbers are not foods.
     */
    internal fun topFoods(logged: List<Day>, counts: Boolean = true): List<TopFood> {
        val items = logged.flatMap { day -> day.allItems.filter { it.method != LogMethod.QuickNumber }.map { day.date to it } }
        return items
            .groupBy { (_, item) -> item.foodId?.let { "food:$it" } ?: item.customFoodId?.let { "custom:$it" } ?: "name:${item.name.trim().lowercase()}" }
            .values
            .map { entries -> Triple(entries.map { it.first }.distinct().size, entries.size, entries.last().second.name.trim()) }
            .filter { (days, _, _) -> days >= TOP_FOOD_MIN_DAYS }
            .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second }.thenBy { it.third })
            .take(TOP_FOODS)
            .map { (days, _, name) -> TopFood(name, days.takeIf { counts }) }
    }

    /** The run at the period's end and what was reached in it, while celebrations are on. */
    private fun milestones(inputs: RecapInputs): MilestonesSection? {
        if (inputs.celebrations == CelebrationStyle.Off || inputs.marks.isEmpty()) return null
        val end = minOf(inputs.period.end, inputs.today)
        val progress = ShowingUp.compute(inputs.marks.filter { it.date <= end }, end)
        val reached = progress.reached.filterValues { it >= inputs.period.start && it <= end }.keys.sortedWith(ShowingUp.celebrationOrder)
        if (progress.current == 0 && reached.isEmpty()) return null
        return MilestonesSection(run = progress.current, reached = reached, counts = inputs.celebrations == CelebrationStyle.Counts)
    }

    /** A trend that moved less than this is steady, as on the running total. */
    private const val STEADY_KG = 0.1
}
