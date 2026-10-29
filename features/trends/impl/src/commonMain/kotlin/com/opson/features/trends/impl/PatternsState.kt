package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.MoodLevel
import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.MealPatternFacts
import com.opson.libraries.diary.MealPatterns
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.NewFoods
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.energyShare
import com.opson.libraries.ui.components.ChartReading
import com.opson.libraries.ui.components.ChartReadout
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_chart_hunger
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

/** What Patterns is built from: the last four weeks of days, oldest first, with the lookback before them for new foods. */
data class PatternsInputs(
    val today: LocalDate,
    val settings: Settings,
    val days: List<Day>,
    val timeZone: TimeZone,
    /** `release.checkIn`: hunger comes from the check-ins, and they are charted over the four weeks. */
    val checkInOn: Boolean = false,
)

/**
 * Patterns, top to bottom. Plain facts, never advice: the screen states them
 * and leaves the connecting to the person reading.
 */
data class PatternsState(
    val loading: Boolean = true,
    /** When you eat: `null` until some meal has a time. */
    val timing: TimingCard? = null,
    /** How big each meal is, only while calories show: even a percentage reveals an amount. */
    val sizes: List<MealSize>? = null,
    /** How often each meal happens, in place of [sizes]. */
    val often: List<MealFrequency>? = null,
    /** Hunger through the day: from the check-ins when they are on and answered, else from the meal tags. */
    val hunger: HungerCard? = null,
    val newFoods: List<String> = emptyList(),
    /** No numbers: hunger sits under New foods, below the fold (design sync 2, 5.7). */
    val hungerLast: Boolean = false,
    /** The check-ins over the four weeks, a bar a day, one of hunger, energy, mood or sleep at a time. */
    val checkIns: CheckInsCard? = null,
    /** No meal logged in the four weeks: one card says what will show up, not four rows of zeros. */
    val empty: Boolean = false,
)

/** One meal on the 6 am to 9 pm timeline: a dot per logged meal, the usual window behind them. */
data class TimelineRow(val slot: MealSlot, val dots: List<Float>, val usual: ClosedFloatingPointRange<Float>?)

data class TimingCard(val rows: List<TimelineRow>, val facts: List<TimingFact>)

sealed interface TimingFact {
    /** `Breakfast, usually` / `8:10 AM`. */
    data class Usually(val slot: MealSlot, val time: LocalTime) : TimingFact

    /** `Dinner on Fri and Sat` / `About 1 h later`. */
    data class LaterOn(val slot: MealSlot, val days: List<DayOfWeek>, val minutes: Int) : TimingFact

    /** `Longest gap` / `Lunch → dinner, 6 h`. */
    data class LongestGap(val from: MealSlot, val to: MealSlot, val minutes: Int) : TimingFact
}

/** One meal's average energy and its share of the day's. [fraction] sizes its part of the bar. */
data class MealSize(val slot: MealSlot, val energy: Display, val share: Display, val fraction: Float)

data class MealFrequency(val slot: MealSlot, val days: Int, val of: Int)

/** One weekday's bar: how hungry that weekday was, `0..1`, or `null` with no answer for it. */
data class HungerDay(val day: DayOfWeek, val level: Float?)

/** Where the hunger came from, said beside the card's title. */
enum class HungerSource {
    /** Close the day's check-in, one answer a day ("From your check-ins"). */
    CheckIns,

    /** The hunger chips on each food. */
    MealTags,
}

/**
 * A bar per weekday, Monday first, and the skipped-breakfast fact when the
 * answers say so: the weekdays it happened on, most often first.
 */
data class HungerCard(
    val days: List<HungerDay>,
    val skippedBreakfast: List<DayOfWeek>?,
    val source: HungerSource,
    /** Each weekday's bar inspected, `null` with no answer: the weekday and the nearest answer to its average. */
    val readouts: List<ChartReadout?> = emptyList(),
)

/** What the check-ins card can chart. */
enum class CheckInAspect { Hunger, Energy, Mood, Sleep }

/**
 * One aspect over the four weeks, oldest day first: [levels] `0..1` (taller is hungrier for
 * hunger, and better for the rest), `null` for a day with no answer; [readouts] the day and its
 * answer in words.
 */
data class CheckInSeries(val aspect: CheckInAspect, val levels: List<Float?>, val readouts: List<ChartReadout?>)

/**
 * The check-ins card: [series] for each aspect someone has answered at least once, in
 * [CheckInAspect] order, and the one showing. Words and bars only, so it stays in every mode.
 */
data class CheckInsCard(val series: List<CheckInSeries>, val picked: CheckInAspect, val from: LocalDate) {
    val shown: CheckInSeries get() = series.firstOrNull { it.aspect == picked } ?: series.first()

    /** [aspect] when it has answers, else as it was. */
    fun picking(aspect: CheckInAspect): CheckInsCard = if (series.any { it.aspect == aspect }) copy(picked = aspect) else this
}

/**
 * Builds [PatternsState] from [MealPatterns], which owns the arithmetic.
 * Energy and shares resolve through the policy on [Surface.Week] for today, so
 * a mode that hides calories never gets the sizes card at all.
 */
object PatternsStateBuilder {

    /** Four weeks, today included. */
    const val DAYS = 28

    private const val DAY_START = 6 * 60
    private const val DAY_END = 21 * 60
    private const val MINUTES_PER_HOUR = 60
    private const val PERCENT = 100.0

    fun build(inputs: PatternsInputs, policy: VisibilityPolicy): PatternsState {
        val from = inputs.today.minus(DAYS - 1, DateTimeUnit.DAY)
        val facts = MealPatterns.of(inputs.days, from, inputs.today, inputs.timeZone)
        if (facts.frequency.values.sum() == 0) return PatternsState(loading = false, empty = true)
        val context = FormatContext(day = inputs.today)
        // On the week view a weekly reveal (Later, weekly) opens with the check on the last day of the
        // week holding today, by the user's week start, not on whichever day today is.
        val weekEnd = TrendsCalendar.weekStartOf(inputs.today, inputs.settings.weekStart)
            .plus(TrendsCalendar.DAYS_IN_WEEK - 1, DateTimeUnit.DAY)
        val sizes = if (policy.effectiveLevel(Metric.Calories, Surface.Week, FormatContext(day = weekEnd)) == Visibility.Show) {
            // Today counts once its own calories are open. The week view is open in Delayed all
            // day, so before the reveal today's meals would move these averages, and logging one
            // would say roughly how big it was. Trends and Nutrients leave today out the same way.
            val energy = if (policy.effectiveLevel(Metric.Calories, Surface.Today, context) == Visibility.Show) {
                facts.energyPerMeal
            } else {
                MealPatterns.of(inputs.days, from, inputs.today.minus(1, DateTimeUnit.DAY), inputs.timeZone).energyPerMeal
            }
            sizes(energy, policy, context)
        } else {
            null
        }
        return PatternsState(
            loading = false,
            timing = if (facts.times.isEmpty()) {
                null
            } else {
                TimingCard(
                    rows = MealSlot.meals.map { slot ->
                        TimelineRow(
                            slot = slot,
                            dots = facts.times[slot].orEmpty().map(::position),
                            usual = facts.usual[slot]?.let { position(it.first)..position(it.last) },
                        )
                    },
                    facts = buildList {
                        val usual = MealSlot.meals.firstOrNull { it in facts.typical }
                        usual?.let { add(TimingFact.Usually(it, facts.typical.getValue(it).toTime())) }
                        if (policy.preset.mealGaps) {
                            facts.laterOn?.let { add(TimingFact.LaterOn(it.slot, it.days, it.minutes)) }
                            facts.longestGap?.let { add(TimingFact.LongestGap(it.from, it.to, it.minutes)) }
                        }
                    },
                )
            },
            sizes = sizes,
            often = if (sizes == null) MealSlot.meals.map { MealFrequency(it, facts.frequency[it] ?: 0, facts.dayCount) } else null,
            hunger = hunger(inputs, facts),
            newFoods = NewFoods.between(inputs.days, from, inputs.today),
            hungerLast = sizes == null,
            checkIns = if (inputs.checkInOn) checkIns(inputs.days, from, inputs.today) else null,
        )
    }

    /**
     * A bar per day from [from] to [today] for each aspect answered at least once. Untracked days
     * are gaps, as everywhere in Trends. Null with no check-in in the four weeks.
     */
    fun checkIns(days: List<Day>, from: LocalDate, today: LocalDate, picked: CheckInAspect = CheckInAspect.Hunger): CheckInsCard? {
        val byDate = days.filter { !it.untracked }.associateBy { it.date }
        val dates = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= today }.toList()
        val series = CheckInAspect.entries.mapNotNull { aspect ->
            val answers = dates.map { date -> byDate[date]?.checkIn?.let { answer(aspect, it) } }
            if (answers.all { it == null }) return@mapNotNull null
            CheckInSeries(
                aspect = aspect,
                levels = answers.map { it?.first },
                readouts = dates.zip(answers) { date, answer ->
                    answer?.let { ChartReadout(DateCopy.longWeekdayMonthDay(date), listOf(ChartReading(word = it.second))) }
                },
            )
        }
        if (series.isEmpty()) return null
        return CheckInsCard(series, picked = series.firstOrNull { it.aspect == picked }?.aspect ?: series.first().aspect, from = from)
    }

    /** One answer as a bar height and its word. */
    private fun answer(aspect: CheckInAspect, checkIn: CheckIn): Pair<Float, StringResource>? = when (aspect) {
        CheckInAspect.Hunger -> checkIn.hunger?.let { it.hungriness.toFloat() to it.label }
        CheckInAspect.Energy -> checkIn.energy?.let { it.ordinal.toFloat() / Energy.entries.lastIndex to it.label }
        CheckInAspect.Mood -> checkIn.mood?.let { it.ordinal.toFloat() / MoodLevel.entries.lastIndex to it.label }
        CheckInAspect.Sleep -> checkIn.sleep?.let { it.ordinal.toFloat() / Sleep.entries.lastIndex to it.label }
    }

    /**
     * The check-ins once they are released and someone has answered the hunger row; until then,
     * the meal tags (with their questions on), so the release doesn't empty the card.
     */
    private fun hunger(inputs: PatternsInputs, facts: MealPatternFacts): HungerCard? = when {
        inputs.checkInOn && facts.checkInHungerByWeekday.isNotEmpty() -> HungerCard(
            days = DayOfWeek.entries.map { day -> HungerDay(day, facts.checkInHungerByWeekday[day]?.toFloat()) },
            skippedBreakfast = facts.checkInHungrierWithoutBreakfast,
            source = HungerSource.CheckIns,
            readouts = DayOfWeek.entries.map { day ->
                facts.checkInHungerByWeekday[day]?.let { share ->
                    // The step nearest the average, back on the 1 to 10 scale.
                    val answer = HungerLevel.of(HungerLevel.MAX - (share * (HungerLevel.MAX - HungerLevel.MIN)).roundToInt())
                    answer?.let { ChartReadout(DateCopy.longWeekday(day), listOf(ChartReading(word = it.label, label = Res.string.trends_chart_hunger))) }
                }
            },
        )
        facts.taggedMeals > 0 -> HungerCard(
            days = DayOfWeek.entries.map { day ->
                HungerDay(day, facts.hungerByWeekday[day]?.let { (it / MealPatterns.HUNGER_STEPS).toFloat() })
            },
            skippedBreakfast = facts.hungrierWithoutBreakfast,
            source = HungerSource.MealTags,
            readouts = DayOfWeek.entries.map { day ->
                facts.hungerByWeekday[day]?.let {
                    ChartReadout(DateCopy.longWeekday(day), listOf(ChartReading(word = TrendsReadouts.hungerWord(it).label)))
                }
            },
        )
        else -> null
    }

    private fun sizes(energy: Map<MealSlot, Double>, policy: VisibilityPolicy, context: FormatContext): List<MealSize>? {
        val total = energy.values.sum()
        if (total <= 0.0) return null
        return MealSlot.meals.mapNotNull { slot ->
            val kcal = energy[slot] ?: return@mapNotNull null
            val display = policy.resolve(Metric.Calories, kcal, Surface.Week, context.copy(goal = GoalStyle.None))
            val share = policy.energyShare(kcal / total * PERCENT, Surface.Week, context)
            if (display is Display.Hidden || share is Display.Hidden) null else MealSize(slot, display, share, (kcal / total).toFloat())
        }.takeIf { it.isNotEmpty() }
    }

    /** Where a minute of the day sits on the 6 am to 9 pm track; earlier and later sit at the ends. */
    private fun position(minute: Int): Float =
        ((minute - DAY_START).toFloat() / (DAY_END - DAY_START)).coerceIn(0f, 1f)

    private fun Int.toTime(): LocalTime {
        val minute = coerceIn(0, LAST_MINUTE)
        return LocalTime(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)
    }

    private const val LAST_MINUTE = 24 * 60 - 1
}
