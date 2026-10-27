package com.opson.features.today.impl.dayclose

import com.opson.features.today.impl.MacroTile
import com.opson.features.today.impl.TodayStateBuilder
import com.opson.features.today.impl.todayRange
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayHunger
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.Tone
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalPosition
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.ui.time.RelativeDay
import com.opson.libraries.health.EnergyExpended
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_dayclose_title
import opson.libraries.resources.generated.resources.today_dayclose_title_warm
import org.jetbrains.compose.resources.StringResource

/** Day close (design v3, 4.1 to 4.5): totals or a soft line or the meals, the big-day reset, a note, Close the day. */
data class DayCloseState(
    val loading: Boolean = true,
    val title: StringResource = Res.string.today_dayclose_title,
    val day: LocalDate? = null,
    val backLabel: RelativeDay = RelativeDay.Today,
    /** Delayed numbers opened early here, today only: they lock again tomorrow. */
    val revealNote: Boolean = false,
    /** Calories effectively shown on Day close. */
    val totals: DayTotals? = null,
    /** Calories softened: a word, no amount. */
    val soft: Display? = null,
    val bigDay: BigDayCard? = null,
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
     * The check-in (design sync 2, 4.1, `release.checkIn`): offered in every mode, in place of
     * [feeling]. What is picked so far; null when the flag is off.
     */
    val checkIn: CheckIn? = null,
    /** "Expended, from your watch": energy out today, at Show only. */
    val expended: Display = Display.Hidden,
    val isToday: Boolean = true,
    val note: String = "",
)

data class FeelingChoice(val options: List<DayFeeling>, val picked: DayFeeling?)

/** [macros] are left out on a big day, where the reset card is the message. */
/** [singleGoal]: the day's goal is one number, so the sentence says "goal" rather than "range". */
data class DayTotals(val eaten: Display, val rangeLine: RangeLine, val macros: List<MacroTile>, val singleGoal: Boolean = false)

/**
 * How the day sat against the range, as a sentence; only built when calories are fully shown.
 * Against one number, [Inside] is close to it (`EnergyRange.position`).
 */
sealed interface RangeLine {
    data class Inside(val range: Display) : RangeLine
    data class Below(val range: Display) : RangeLine
    data object Above : RangeLine
}

/** Tomorrow's range, unchanged by today. [singleGoal]: tomorrow's goal is one number. */
data class BigDayCard(val tomorrow: Display, val commitment: Commitment, val singleGoal: Boolean = false)

/** The two buttons that save nothing, on purpose (`PROTOTYPE_NOTES.md`). */
enum class Commitment { Hidden, Offered, Committed }

data class MealsLine(val done: Int, val total: Int)

sealed interface DayCloseAction {
    data class InputsArrived(val inputs: DayCloseInputs, val policy: VisibilityPolicy) : DayCloseAction
    data class NoteTyped(val text: String) : DayCloseAction

    /** A feeling chip; the picked one again clears it. */
    data class FeelingTapped(val feeling: DayFeeling) : DayCloseAction

    // The check-in: each row optional; the picked one again clears it.
    data class CheckInHungerTapped(val hunger: DayHunger) : DayCloseAction
    data class CheckInEnergyTapped(val energy: Energy) : DayCloseAction
    data class CheckInSleepTapped(val sleep: Sleep) : DayCloseAction
    data class CheckInMoodTapped(val mood: CheckInMood) : DayCloseAction

    /** "Why not cut back?" on the big-day card. */
    data object WhyNotCutBackTapped : DayCloseAction

    /** "If eating feels hard right now, talk to someone". Never logged. */
    data object SupportTapped : DayCloseAction
    data object CommitmentTapped : DayCloseAction
    data object DoneTapped : DayCloseAction
    data object BackTapped : DayCloseAction
}

sealed interface DayCloseEvent {
    data object Close : DayCloseEvent
    data object OpenWhyNotCutBack : DayCloseEvent
    data object OpenSupport : DayCloseEvent
}

data class DayCloseInputs(
    val today: LocalDate,
    val day: Day,
    val settings: Settings,
    /** Today's energy from a connected health app; null when not connected or `release.health` is off. */
    val expended: EnergyExpended? = null,
    /** `release.checkIn`, and the check-in switch in Reminders on ([Settings.checkIn]). */
    val checkInOn: Boolean = false,
)

/**
 * Day close as a pure function. Surface [Surface.DayClose], where delayed numbers open (weekly
 * ones open only on the week view, so Later weekly lands on the logged meals). The big-day card
 * needs calories fully shown and the day past its range, so No numbers can never infer one; tomorrow
 * is tomorrow's own goal from `Targets.rangeFor`, which reads the settings and the date and
 * nothing eaten, so nothing here can move it.
 */
object DayCloseStateBuilder {
    fun build(
        inputs: DayCloseInputs,
        policy: VisibilityPolicy,
        note: String,
        committed: Boolean,
        feeling: DayFeeling? = null,
        checkIn: CheckIn? = null,
    ): DayCloseState {
        val day = inputs.day
        val today = inputs.today
        val settings = inputs.settings
        val isToday = day.date == today
        val range = todayRange(settings, day, today, inputs.expended)
        val totals = day.totals()
        val eaten = totals.kcal.known
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
                macros = if (big) emptyList() else macros(day, today, policy),
                singleGoal = range.single,
            )
        } else {
            null
        }
        val tomorrow = settings.targets.rangeFor(day.date.plus(1, DateTimeUnit.DAY), settings.numbers.preset)
        val revealed = isToday && shown && policy.level(Metric.Calories) == Visibility.Delay
        return DayCloseState(
            loading = false,
            title = if (settings.tone == Tone.Warm) Res.string.today_dayclose_title_warm else Res.string.today_dayclose_title,
            day = day.date,
            backLabel = RelativeDay.of(day.date, today),
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
            mealsLine = if (revealed) MealsLine(day.doneMealCount, MealSlot.meals.size) else null,
            loggedMeals = if (eatenDisplay is Display.Hidden) MealSlot.meals.filterTo(LinkedHashSet()) { day.isMealDone(it) } else null,
            // The check-in asks every mode; with it on, Blind's single feeling steps aside.
            feeling = if (eatenDisplay is Display.Hidden && !inputs.checkInOn) FeelingChoice(DayFeeling.entries, feeling) else null,
            checkIn = if (inputs.checkInOn) checkIn ?: CheckIn() else null,
            expended = if (isToday) {
                policy.resolve(Metric.EnergyOut, inputs.expended?.totalKcal, Surface.DayClose, day.formatContext(today))
            } else {
                Display.Hidden
            },
            isToday = isToday,
            note = note,
        )
    }

    private fun macros(day: Day, today: LocalDate, policy: VisibilityPolicy): List<MacroTile> {
        val totals = day.totals()
        return listOf(
            Metric.Protein to totals.protein.known,
            Metric.Carbs to totals.carbs.known,
            Metric.Fat to totals.fat.known,
            Metric.Fiber to totals.fiber.known,
        ).map { (metric, grams) ->
            MacroTile(metric, TodayStateBuilder.macroLabel(metric), policy.resolve(metric, grams, Surface.DayClose, day.formatContext(today)))
        }.filter { it.display !is Display.Hidden }
    }
}
