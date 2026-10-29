package com.opson.features.today.impl.dayclose

import com.opson.features.today.impl.KeyNutrient
import com.opson.features.today.impl.MacroTile
import com.opson.features.today.impl.Toast
import com.opson.features.today.impl.TodayStateBuilder
import com.opson.features.today.impl.todayRange
import com.opson.features.today.impl.celebration.Celebrating
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.label
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EnergyRange
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalPosition
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroPercents
import com.opson.libraries.nutrition.ONE_NUMBER_LEEWAY_KCAL
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.microReference
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.nutrition.toMicronutrients
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_dayclose_title
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Close the day (design v3, 4.1 to 4.5), a recap of the day: a line on how much was logged, totals
 * or a soft line or the meals, the big-day reset, eaten against burned, the macros and a few
 * vitamins and minerals against their targets, the way into the check-in, a note, Wrap up the day,
 * and once it is wrapped up the run so far ([streak]).
 */
data class DayCloseState(
    val loading: Boolean = true,
    val title: StringResource = Res.string.today_dayclose_title,
    val day: LocalDate? = null,
    val backLabel: RelativeDay = RelativeDay.Today,
    /** The line under the title: how much of the day was logged, in words. Every mode. */
    val recap: RecapLine? = null,
    /** Delayed numbers opened early here, today only: they lock again tomorrow. */
    val revealNote: Boolean = false,
    /** Calories effectively shown on Day close. */
    val totals: DayTotals? = null,
    /** Calories softened: a word, no amount. */
    val soft: Display? = null,
    val bigDay: BigDayCard? = null,
    /** Eaten against burned and the difference: only with calories and energy out both shown. */
    val balance: EnergyBalance? = null,
    /** The macros against their targets, wherever the levels show any; left out on a big day. */
    val macros: List<MacroTile>? = null,
    /** The vitamins and minerals Today's card shows, against their references; left out on a big day. */
    val micros: List<KeyNutrient>? = null,
    /** "Meals 4 of 4" beside revealed totals. */
    val mealsLine: MealsLine? = null,
    /** Calories hidden (No numbers, Later weekly): which meals were logged, as chips. */
    val loggedMeals: Set<MealSlot>? = null,
    /**
     * "How did today feel?" (4.5), offered beside [loggedMeals]: the choices and the one picked,
     * if any. Optional; nothing is asked of anyone who skips it.
     */
    val feeling: FeelingChoice? = null,
    /**
     * The way into the check-in (`release.checkIn`), with what the day's check-in holds so far,
     * in place of [feeling]. Null when the flag is off. The check-in is its own sheet and its own
     * write; closing the day never saves it.
     */
    val checkIn: CheckIn? = null,
    /** "Expended, from your watch": energy out today, at Show only, when [balance] does not already say it. */
    val expended: Display = Display.Hidden,
    val isToday: Boolean = true,
    val note: String = "",
    /**
     * Closed already: the button saves the note and the feeling and keeps it closed, and Reopen
     * opens it to food again.
     */
    val closed: Boolean = false,
    /** Today wrapped up, with celebrations on (`release.celebrations`): the run and the next milestone, gently. */
    val streak: StreakLine? = null,
)

/** "4 days in a row. 3 more to a week." with counts; a kind line without. */
sealed interface StreakLine {
    data class Counts(val days: Int, val next: Milestone?, val toNext: Int?) : StreakLine

    data object Gentle : StreakLine

    companion object {
        fun of(celebrating: Celebrating?): StreakLine? {
            val progress = celebrating?.progress ?: return null
            if (progress.current == 0) return null
            return when (celebrating.style) {
                CelebrationStyle.Counts -> Counts(progress.current, progress.next, progress.daysToNext)
                CelebrationStyle.Gentle -> Gentle
                CelebrationStyle.Off -> null
            }
        }
    }
}

data class FeelingChoice(val options: List<DayFeeling>, val picked: DayFeeling?)

/** How much of the day was logged, said without an amount. */
enum class RecapLine { AllMeals, SomeLogged, NothingLogged }

/**
 * [singleGoal]: the day's goal is one number, so the sentence says "goal" rather than "range".
 * The macros are their own card ([DayCloseState.macros]), so they show in modes without calories.
 */
data class DayTotals(val eaten: Display, val rangeLine: RangeLine, val singleGoal: Boolean = false)

/**
 * How the day sat against the range, as a sentence; only built when calories are fully shown.
 * Against one number, [Inside] is close to it (`EnergyRange.position`).
 */
sealed interface RangeLine {
    data class Inside(val range: Display) : RangeLine
    data class Below(val range: Display) : RangeLine
    data object Above : RangeLine
}

/**
 * Eaten against burned. [difference] is how far apart, always a positive amount, and [direction]
 * which way. [plan] is the deficit or surplus the day's goal works out to against what was burned,
 * when it is one ([BalancePlan]). [soFar]: today, while activity is still syncing.
 */
data class EnergyBalance(
    val eaten: Display,
    val burned: Display,
    val difference: Display,
    val direction: BalanceDirection,
    val plan: BalancePlan? = null,
    val soFar: Boolean = false,
)

/** Under or over what was burned, or within [DayCloseStateBuilder.EVEN_KCAL] of it. */
enum class BalanceDirection { Under, Even, Over }

/** The deficit or surplus the goal aims for, [planned] as an amount, and how the day came out against it. */
data class BalancePlan(val kind: PlanKind, val planned: Display, val result: PlanResult)

enum class PlanKind { Deficit, Surplus }

/**
 * [OnPlan] within [DayCloseStateBuilder.EVEN_KCAL] of the plan; [Beyond] further the same way
 * (a bigger deficit, a bigger surplus); [Short] the same way but less; [Opposite] the other way.
 */
enum class PlanResult { OnPlan, Beyond, Short, Opposite }

/** Tomorrow's range, unchanged by today. [singleGoal]: tomorrow's goal is one number. */
data class BigDayCard(val tomorrow: Display, val commitment: Commitment, val singleGoal: Boolean = false)

/** The two buttons that save nothing, on purpose (`PROTOTYPE_NOTES.md`). */
enum class Commitment { Hidden, Offered, Committed }

data class MealsLine(val done: Int, val total: Int)

sealed interface DayCloseAction {
    data class InputsArrived(val inputs: DayCloseInputs, val policy: VisibilityPolicy) : DayCloseAction

    /** The run and the style, while celebrations are on; null while they are off. */
    data class CelebratingArrived(val celebrating: Celebrating?) : DayCloseAction
    data class NoteTyped(val text: String) : DayCloseAction

    /** A feeling chip; the picked one again clears it. */
    data class FeelingTapped(val feeling: DayFeeling) : DayCloseAction

    /** The check-in row: opens the check-in sheet over this screen. */
    data object CheckInTapped : DayCloseAction

    /** The vitamins and minerals card's See all. */
    data object NutrientsTapped : DayCloseAction

    /** "Why not cut back?" on the big-day card. */
    data object WhyNotCutBackTapped : DayCloseAction

    /** "If eating feels hard right now, talk to someone". Never logged. */
    data object SupportTapped : DayCloseAction
    data object CommitmentTapped : DayCloseAction
    data object DoneTapped : DayCloseAction

    /** Reopen this day: it takes food again, and the screen closes back to Today. */
    data object ReopenTapped : DayCloseAction
    data object BackTapped : DayCloseAction
}

sealed interface DayCloseEvent {
    data object Close : DayCloseEvent
    data object OpenWhyNotCutBack : DayCloseEvent
    data object OpenSupport : DayCloseEvent
    data class OpenCheckIn(val day: LocalDate) : DayCloseEvent
    data class OpenNutrients(val day: LocalDate) : DayCloseEvent

    /** After wrapping up today: the run so far, when no celebration is about to say more. */
    data class ShowToast(val toast: Toast) : DayCloseEvent
}

data class DayCloseInputs(
    val today: LocalDate,
    val day: Day,
    val settings: Settings,
    /** Today's energy from a connected health app; null when not connected or `release.health` is off. */
    val expended: EnergyExpended? = null,
    /** `release.checkIn`. The check-in is always offered with it; there is no switch for it any more. */
    val checkInOn: Boolean = false,
    /** The day's Burned with `release.goalsAndEnergy`, what a set deficit or surplus aims at. */
    val burned: Burned? = null,
)

/**
 * Day close as a pure function. Surface [Surface.DayClose], where delayed numbers open (weekly
 * ones open only on the week view, so Later weekly lands on the logged meals). The big-day card
 * needs calories fully shown and the day past its range, so No numbers can never infer one; tomorrow
 * is tomorrow's own goal from `Targets.rangeFor`, which reads the settings and the date and
 * nothing eaten, so nothing here can move it. Every amount resolves through the policy, and a card
 * whose amounts a mode hides is left out whole, never drawn with gaps.
 */
object DayCloseStateBuilder {

    /** Within this of what was burned, or of the plan, reads as even, or on plan. The goal's own leeway. */
    const val EVEN_KCAL: Int = ONE_NUMBER_LEEWAY_KCAL

    private const val PERCENT = 100.0

    fun build(
        inputs: DayCloseInputs,
        policy: VisibilityPolicy,
        note: String,
        committed: Boolean,
        feeling: DayFeeling? = null,
        celebrating: Celebrating? = null,
    ): DayCloseState {
        val day = inputs.day
        val today = inputs.today
        val settings = inputs.settings
        val isToday = day.date == today
        val range = todayRange(settings, day, today, inputs.expended, inputs.burned)
        val eaten = day.totals().kcal.known
        val bare = day.formatContext(today, bare = true)
        val eatenDisplay = policy.resolve(Metric.Calories, eaten, Surface.DayClose, day.formatContext(today, of = range.targetKcal.toDouble(), bare = true))
        val shown = eatenDisplay is Display.Shown
        val position = range.position(eaten)
        val big = shown && position == GoalPosition.Above && settings.bigDayEvent != EventLevel.Off
        val rangeDisplay = policy.resolveRange(Metric.Calories, range.minKcal.toDouble(), range.maxKcal.toDouble(), Surface.DayClose, bare)
        val dayTotals = if (shown) {
            DayTotals(
                eaten = eatenDisplay,
                rangeLine = when (position) {
                    GoalPosition.Above -> RangeLine.Above
                    GoalPosition.On -> RangeLine.Inside(rangeDisplay)
                    GoalPosition.Below -> RangeLine.Below(rangeDisplay)
                },
                singleGoal = range.single,
            )
        } else {
            null
        }
        // Tomorrow's burn isn't known; today's Burned stands in for it, as the target moves with it.
        val tomorrow = settings.targets.rangeFor(day.date.plus(1, DateTimeUnit.DAY), settings.numbers.preset, inputs.burned?.totalKcal)
        val revealed = isToday && shown && policy.level(Metric.Calories) == Visibility.Delay
        val balance = if (shown) balance(inputs, policy, eaten, range) else null
        return DayCloseState(
            loading = false,
            title = Res.string.today_dayclose_title,
            day = day.date,
            backLabel = RelativeDay.of(day.date, today),
            recap = when {
                MealSlot.meals.all(day::isMealDone) -> RecapLine.AllMeals
                day.hasLogged -> RecapLine.SomeLogged
                else -> RecapLine.NothingLogged
            },
            revealNote = revealed,
            totals = dayTotals,
            soft = eatenDisplay as? Display.Soft,
            bigDay = if (big) {
                BigDayCard(
                    tomorrow = policy.resolveRange(
                        Metric.Calories,
                        tomorrow.minKcal.toDouble(),
                        tomorrow.maxKcal.toDouble(),
                        Surface.DayClose,
                        day.formatContext(today),
                    ),
                    commitment = when {
                        settings.bigDayEvent != EventLevel.Full -> Commitment.Hidden
                        committed -> Commitment.Committed
                        else -> Commitment.Offered
                    },
                    singleGoal = tomorrow.minKcal == tomorrow.maxKcal,
                )
            } else {
                null
            },
            balance = balance,
            // On a big day the reset card is the message (4.2), so the nutrient cards step aside.
            macros = if (big || !day.hasLogged) null else macros(day, settings, today, policy).takeIf { it.isNotEmpty() },
            // Nothing listing any of them would be a card of "Not listed", which says nothing.
            micros = if (big) null else micros(day, settings, today, policy).takeIf { rows -> rows.any { !it.notListed } },
            mealsLine = if (revealed) MealsLine(day.doneMealCount, MealSlot.meals.size) else null,
            loggedMeals = if (eatenDisplay is Display.Hidden) MealSlot.meals.filterTo(LinkedHashSet()) { day.isMealDone(it) } else null,
            // The check-in asks every mode; with it on, No numbers' single feeling steps aside.
            feeling = if (eatenDisplay is Display.Hidden && !inputs.checkInOn) FeelingChoice(DayFeeling.entries, feeling) else null,
            checkIn = if (inputs.checkInOn) day.checkIn ?: CheckIn() else null,
            expended = if (isToday && balance == null) {
                policy.resolve(Metric.EnergyOut, inputs.expended?.totalKcal, Surface.DayClose, day.formatContext(today))
            } else {
                Display.Hidden
            },
            isToday = isToday,
            note = note,
            closed = day.closedByUser,
            streak = if (isToday && day.closedByUser) StreakLine.of(celebrating) else null,
        )
    }

    /**
     * Eaten against burned: Burned with `release.goalsAndEnergy` (resting energy for the whole day
     * and the activity counted), else the health app's total for today. Every amount is resolved as
     * the day's calories and its energy out are; a mode that shows either one less than in full
     * gets no card, since the difference between them would say what it hides.
     */
    private fun balance(inputs: DayCloseInputs, policy: VisibilityPolicy, eaten: Double, range: EnergyRange): EnergyBalance? {
        val day = inputs.day
        val isToday = day.date == inputs.today
        val burnedKcal = inputs.burned?.totalKcal ?: inputs.expended?.takeIf { isToday }?.totalKcal?.takeIf { it.isFinite() }?.roundToInt()
        if (burnedKcal == null || burnedKcal <= 0 || !day.hasLogged) return null
        val context = day.formatContext(inputs.today)
        val eatenShown = policy.resolve(Metric.Calories, eaten, Surface.DayClose, context) as? Display.Shown ?: return null
        val burnedShown = policy.resolve(Metric.EnergyOut, burnedKcal.toDouble(), Surface.DayClose, context) as? Display.Shown ?: return null
        val gap = burnedKcal - eaten
        val direction = when {
            abs(gap) <= EVEN_KCAL -> BalanceDirection.Even
            gap > 0 -> BalanceDirection.Under
            else -> BalanceDirection.Over
        }
        val planned = burnedKcal - range.targetKcal
        val plan = if (abs(planned) < EVEN_KCAL) {
            null
        } else {
            val kind = if (planned > 0) PlanKind.Deficit else PlanKind.Surplus
            // Measured the plan's way: a deficit as burned less eaten, a surplus as eaten less burned.
            val wanted = abs(planned).toDouble()
            val got = if (kind == PlanKind.Deficit) gap else -gap
            val result = when {
                abs(got - wanted) <= EVEN_KCAL -> PlanResult.OnPlan
                got > wanted -> PlanResult.Beyond
                got > EVEN_KCAL -> PlanResult.Short
                got < -EVEN_KCAL -> PlanResult.Opposite
                // About even with what was burned: the line above says so, and no plan line fits it.
                else -> null
            }
            result?.let {
                (policy.resolve(Metric.Calories, wanted, Surface.DayClose, context) as? Display.Shown)?.let { amount -> BalancePlan(kind, amount, it) }
            }
        }
        return EnergyBalance(
            eaten = eatenShown,
            burned = burnedShown,
            difference = policy.resolve(Metric.Calories, abs(gap), Surface.DayClose, context),
            direction = direction,
            plan = plan,
            soFar = isToday,
        )
    }

    /** "88 / 120 g", each macro against its target, as Today's macros page has them. */
    private fun macros(day: Day, settings: Settings, today: LocalDate, policy: VisibilityPolicy): List<MacroTile> {
        val totals = day.totals()
        val targets = settings.targets
        val context = day.formatContext(today)
        return listOf(
            Triple(Metric.Protein, totals.protein.known, targets.proteinG),
            Triple(Metric.Carbs, totals.carbs.known, targets.carbsG),
            Triple(Metric.Fat, totals.fat.known, targets.fatG),
            Triple(Metric.Fiber, totals.fiber.known, targets.fiberG),
        ).map { (metric, grams, target) ->
            val display = policy.resolve(metric, grams, Surface.DayClose, context.copy(of = target.toDouble(), goal = GoalStyle.Slash))
            MacroTile(metric, TodayStateBuilder.macroLabel(metric), display)
        }.filter { it.display !is Display.Hidden }
    }

    /**
     * The vitamins and minerals Today's card shows ([Settings.todayMicronutrients]), each as a
     * percent of the person's reference. A daily report, not a score: no number adds them up.
     */
    private fun micros(day: Day, settings: Settings, today: LocalDate, policy: VisibilityPolicy): List<KeyNutrient> {
        val context: FormatContext = day.formatContext(today)
        if (policy.effectiveLevel(Metric.Micros, Surface.DayClose, context) == Visibility.Hide) return emptyList()
        val references = settings.microReferences(today)
        val amounts = day.allItems.map { it.micros.orEmpty().toMicronutrients() }
        val percents = MicroPercents.of(amounts, references)
        val coverage = MicroPercents.coverage(amounts)
        return settings.todayMicronutrients.map { micro ->
            KeyNutrient(
                micro = micro,
                label = micro.label,
                reference = policy.microReference(micro, references),
                isLimit = micro.isLimit,
                display = policy.resolve(Metric.Micros, percents[micro], Surface.DayClose, context.copy(of = PERCENT)),
                notListed = coverage.getValue(micro).isNone,
            )
        }.filter { it.notListed || it.display !is Display.Hidden }
    }
}
