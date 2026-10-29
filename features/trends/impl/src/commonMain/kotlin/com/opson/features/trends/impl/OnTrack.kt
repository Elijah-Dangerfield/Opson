package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.DayGoal
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ONE_NUMBER_LEEWAY_KCAL
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.max
import kotlin.math.roundToInt

/** How a day went against the person's own plan, in the calendar's words. */
enum class PlanStanding {
    OnPlan,
    ABitOver,
    ABitUnder,
    Over,
    Under,

    /** Logged, but some of it has no amount, so the day can't be read against the plan. */
    Partial,

    /** Something logged: all the calendar says where amounts are hidden. */
    Logged,
    NotLogged,
    Untracked,

    /** Today, still going: judged once it is closed, so lunchtime never reads as under plan. */
    InProgress,

    /** Its calories are on Later and haven't opened yet. */
    NotYet,
    Future,
}

/** The calendar's four fills, and [None] for gray: not logged, untracked, still to come. */
enum class PlanTone { On, Near, Off, Logged, None }

/** Which way the plan points: a set deficit, a set surplus, or a range or one number to stay at. */
enum class GoalDirection { Lose, Gain, Hold }

/** The full calendar against the plan, or the one that only says which days have something logged. */
enum class CalendarMode { Plan, LoggedOnly }

data class CalendarDay(val date: LocalDate, val standing: PlanStanding, val tone: PlanTone, val isToday: Boolean) {
    /** Future days have nothing to show. */
    val tappable: Boolean get() = standing != PlanStanding.Future
}

/** One month, [first] its first day; a `null` cell is a blank before it. The counts are the month's fills. */
data class CalendarMonth(
    val first: LocalDate,
    val cells: List<CalendarDay?>,
    val on: Int = 0,
    val near: Int = 0,
    val off: Int = 0,
    val logged: Int = 0,
)

/**
 * The on-track calendar (Trends). [months] oldest first, the last one holding today; [page] is the
 * one showing. [direction] picks the off color's word ("Over plan", "Under plan", "Off plan") and the
 * help text, which [explained] opens.
 */
data class OnTrackCalendar(
    val mode: CalendarMode,
    val direction: GoalDirection,
    val weekStart: DayOfWeek,
    val months: List<CalendarMonth>,
    val page: Int,
    val explained: Boolean = false,
) {
    val shown: CalendarMonth get() = months[page.coerceIn(0, months.lastIndex)]
}

/**
 * How each day went against the plan, for the calendar and the day sheet. Pure and tested at the
 * band's edges in `OnTrackTest`.
 *
 * A day is **on plan** inside the day's goal ([Targets.rangeFor], the goal Today shows), with
 * [ONE_NUMBER_LEEWAY_KCAL] either side of one number, as Today reads one number. It is **a bit
 * over** or **a bit under** up to [nearMarginKcal] past that, and **over** or **under** beyond it.
 * For a set deficit the goal is that day's Burned less the deficit, so "on plan" means the day's
 * deficit (burned less eaten) came within the leeway of the one set; a surplus the same way.
 */
object OnTrack {

    // TODO(clinician): how far past the plan still reads as "a bit off". An engineer picked it: the
    // larger of 150 kcal and a tenth of the day's goal, so a small plan isn't held to a hair and a
    // big one isn't given a whole meal. It only colors the calendar; it never moves a goal.
    const val NEAR_MIN_KCAL = 150
    const val NEAR_SHARE = 0.10

    /** This month and the two before it: what swiping back on the calendar reaches. */
    const val MONTHS = 3

    private const val DAYS_IN_WEEK = 7

    fun nearMarginKcal(goal: DayGoal): Int = max(NEAR_MIN_KCAL, (goal.targetKcal * NEAR_SHARE).roundToInt())

    /** Where [eatenKcal] sits against [goal], with one number's leeway and [nearMarginKcal] past it. */
    fun standing(eatenKcal: Double, goal: DayGoal): PlanStanding {
        val leeway = if (goal.minKcal == goal.maxKcal) ONE_NUMBER_LEEWAY_KCAL else 0
        val low = goal.minKcal - leeway
        val high = goal.maxKcal + leeway
        val margin = nearMarginKcal(goal)
        return when {
            eatenKcal > high + margin -> PlanStanding.Over
            eatenKcal > high -> PlanStanding.ABitOver
            eatenKcal >= low -> PlanStanding.OnPlan
            eatenKcal >= low - margin -> PlanStanding.ABitUnder
            else -> PlanStanding.Under
        }
    }

    /**
     * The fill. Clearly off in the direction away from the goal is [PlanTone.Off]: over for a
     * deficit, under for a surplus, either way for a range. Clearly past it the other way (far
     * under a deficit) is only [PlanTone.Near]: it isn't the plan either, and the calendar must
     * never make eating very little look like the best day.
     */
    fun tone(standing: PlanStanding, direction: GoalDirection): PlanTone = when (standing) {
        PlanStanding.OnPlan -> PlanTone.On
        PlanStanding.ABitOver, PlanStanding.ABitUnder -> PlanTone.Near
        PlanStanding.Over -> if (direction == GoalDirection.Gain) PlanTone.Near else PlanTone.Off
        PlanStanding.Under -> if (direction == GoalDirection.Lose) PlanTone.Near else PlanTone.Off
        PlanStanding.Logged -> PlanTone.Logged
        PlanStanding.Partial, PlanStanding.NotLogged, PlanStanding.Untracked, PlanStanding.InProgress,
        PlanStanding.NotYet, PlanStanding.Future,
        -> PlanTone.None
    }

    /**
     * Which way [date]'s goal points. A deficit or surplus only counts when it is in force, the
     * way [Targets.rangeFor] decides: a deficit with no burn known, or either in a mode that
     * doesn't offer it, falls back to the range, and so does this.
     */
    fun direction(targets: Targets, date: LocalDate, preset: PresetId, burnKcal: Int?): GoalDirection {
        if (targets.rangeFor(date, preset, burnKcal) !is DayGoal.About) return GoalDirection.Hold
        return when (val style = targets.style) {
            is TargetStyle.Deficit -> GoalDirection.Lose
            is TargetStyle.Surplus -> GoalDirection.Gain
            is TargetStyle.ByDay -> when (style.groups.firstOrNull { date.dayOfWeek in it.days }?.shape) {
                is GroupShape.Deficit -> GoalDirection.Lose
                is GroupShape.Surplus -> GoalDirection.Gain
                else -> GoalDirection.Hold
            }
            else -> GoalDirection.Hold
        }
    }

    /** The calendar's overall direction: today's, which is what the person set most recently. */
    fun planDirection(settings: Settings, today: LocalDate, burnKcal: Int?): GoalDirection =
        direction(settings.targets, today, settings.numbers.preset, burnKcal)

    /** Whether any day's goal is aimed at Burned: a set deficit or surplus, on its own or in a day group. */
    fun usesBurn(targets: Targets): Boolean = when (val style = targets.style) {
        is TargetStyle.Deficit, is TargetStyle.Surplus -> true
        is TargetStyle.ByDay -> style.groups.any { it.shape is GroupShape.Deficit || it.shape is GroupShape.Surplus }
        else -> false
    }

    /**
     * [CalendarMode.Plan] only where calories show in full on the week view, energy out too when
     * a goal is aimed at Burned (the colors would say what was burned), and never for someone who
     * said numbers have been hard: for them a day colored over plan is exactly the pressure they
     * asked Opson to spare them. Everyone else gets the logged and not-logged calendar, which has
     * no amount in it.
     */
    fun mode(settings: Settings, policy: VisibilityPolicy, context: FormatContext): CalendarMode {
        val plan = !settings.onboarding.hard &&
            policy.effectiveLevel(Metric.Calories, Surface.Week, context) == Visibility.Show &&
            (!usesBurn(settings.targets) || policy.effectiveLevel(Metric.EnergyOut, Surface.Week, context) == Visibility.Show)
        return if (plan) CalendarMode.Plan else CalendarMode.LoggedOnly
    }

    /**
     * One day. [opened] is whether its calories may be read yet (Later's reveal); [burnKcal] the
     * day's Burned, `null` when unknown, which leaves a deficit on the range as Today does.
     */
    fun standingOf(
        day: Day,
        today: LocalDate,
        settings: Settings,
        mode: CalendarMode,
        opened: Boolean,
        burnKcal: Int?,
    ): PlanStanding = when {
        day.date > today -> PlanStanding.Future
        day.untracked -> PlanStanding.Untracked
        !day.hasLogged -> if (day.date == today) PlanStanding.InProgress else PlanStanding.NotLogged
        mode == CalendarMode.LoggedOnly -> PlanStanding.Logged
        !opened -> PlanStanding.NotYet
        day.date == today && !day.closedByUser -> PlanStanding.InProgress
        else -> {
            val kcal = day.totals().kcal
            val eaten = kcal.value
            if (eaten == null) {
                PlanStanding.Partial
            } else {
                val standing = standing(eaten, settings.targets.rangeFor(day.date, settings.numbers.preset, burnKcal))
                // Known amounts already past the plan stay past it; anything less might not be.
                if (kcal.isPartial && standing != PlanStanding.Over && standing != PlanStanding.ABitOver) PlanStanding.Partial else standing
            }
        }
    }

    /** The first of [today]'s month and the [MONTHS] − 1 before it, oldest first. */
    fun monthStarts(today: LocalDate): List<LocalDate> {
        val current = LocalDate(today.year, today.month, 1)
        return (MONTHS - 1 downTo 0).map { current.minus(DatePeriod(months = it)) }
    }

    /** The first day the calendar shows. */
    fun firstDay(today: LocalDate): LocalDate = monthStarts(today).first()

    /**
     * The calendar, or `null` with the card switched off. [opens] says whether a day's metric has
     * opened (Later); [burned] is each day's Burned when known.
     */
    fun calendar(
        inputs: TrendsInputs,
        policy: VisibilityPolicy,
        dayAt: (LocalDate) -> Day,
        opens: (Metric, Day) -> Boolean,
        ui: TrendsUi,
    ): OnTrackCalendar? {
        val settings = inputs.settings
        if (!settings.trendsCards.calendar) return null
        val mode = mode(settings, policy, FormatContext(day = inputs.today))
        val months = monthStarts(inputs.today).map { first ->
            val next = first.plus(DatePeriod(months = 1))
            val days = generateSequence(first) { it.plus(DatePeriod(days = 1)) }.takeWhile { it < next }.map { date ->
                val day = dayAt(date)
                val burn = inputs.burnedByDay?.get(date)?.totalKcal
                val opened = opens(Metric.Calories, day) && (!usesBurn(settings.targets) || opens(Metric.EnergyOut, day))
                val standing = standingOf(day, inputs.today, settings, mode, opened, burn)
                val direction = direction(settings.targets, date, settings.numbers.preset, burn)
                CalendarDay(date, standing, tone(standing, direction), isToday = date == inputs.today)
            }.toList()
            val blanks = (first.dayOfWeek.isoDayNumber - settings.weekStart.isoDayNumber + DAYS_IN_WEEK) % DAYS_IN_WEEK
            CalendarMonth(
                first = first,
                cells = List(blanks) { null } + days,
                on = days.count { it.tone == PlanTone.On },
                near = days.count { it.tone == PlanTone.Near },
                off = days.count { it.tone == PlanTone.Off },
                logged = days.count { it.tone == PlanTone.Logged },
            )
        }
        return OnTrackCalendar(
            mode = mode,
            direction = planDirection(settings, inputs.today, inputs.burnedByDay?.get(inputs.today)?.totalKcal ?: inputs.burnedKcal),
            weekStart = settings.weekStart,
            months = months,
            page = (ui.calendarPage ?: months.lastIndex).coerceIn(0, months.lastIndex),
            explained = ui.calendarExplained,
        )
    }
}
