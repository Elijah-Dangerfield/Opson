package com.opson.features.today.impl.dayclose

import com.opson.features.today.impl.MacroTile
import com.opson.features.today.impl.TodayStateBuilder
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
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
    /** Calories hidden (Blind, Coast): which meals were logged, as chips. */
    val loggedMeals: Set<MealSlot>? = null,
    /**
     * "How did today feel?" (4.5), offered beside [loggedMeals]: the choices and the one picked,
     * if any. Optional; nothing is asked of anyone who skips it.
     */
    val feeling: FeelingChoice? = null,
    val isToday: Boolean = true,
    val note: String = "",
)

data class FeelingChoice(val options: List<DayFeeling>, val picked: DayFeeling?)

/** [macros] are left out on a big day, where the reset card is the message. */
data class DayTotals(val eaten: Display, val rangeLine: RangeLine, val macros: List<MacroTile>)

/** How the day sat against the range, as a sentence; only built when calories are fully shown. */
sealed interface RangeLine {
    data class Inside(val range: Display) : RangeLine
    data class Below(val range: Display) : RangeLine
    data object Above : RangeLine
}

/** Tomorrow's range, unchanged by today. */
data class BigDayCard(val tomorrow: Display, val commitment: Commitment)

/** The two buttons that save nothing, on purpose (`PROTOTYPE_NOTES.md`). */
enum class Commitment { Hidden, Offered, Committed }

data class MealsLine(val done: Int, val total: Int)

sealed interface DayCloseAction {
    data class InputsArrived(val inputs: DayCloseInputs, val policy: VisibilityPolicy) : DayCloseAction
    data class NoteTyped(val text: String) : DayCloseAction

    /** A feeling chip; the picked one again clears it. */
    data class FeelingTapped(val feeling: DayFeeling) : DayCloseAction
    data object CommitmentTapped : DayCloseAction
    data object DoneTapped : DayCloseAction
    data object BackTapped : DayCloseAction
}

sealed interface DayCloseEvent {
    data object Close : DayCloseEvent
}

data class DayCloseInputs(val today: LocalDate, val day: Day, val settings: Settings)

/**
 * Day close as a pure function. Surface [Surface.DayClose], where delayed numbers open (Coast's
 * open only on the week view, so Coast lands on the logged meals). The big-day card needs
 * calories fully shown and the day past its range, so Blind can never infer one; tomorrow
 * comes from `Targets.tomorrow()`, which takes no inputs, so nothing here can move it.
 */
object DayCloseStateBuilder {
    fun build(
        inputs: DayCloseInputs,
        policy: VisibilityPolicy,
        note: String,
        committed: Boolean,
        feeling: DayFeeling? = null,
    ): DayCloseState {
        val day = inputs.day
        val today = inputs.today
        val settings = inputs.settings
        val isToday = day.date == today
        val range = settings.targets.today(settings.exercise, 0, 0)
        val totals = day.totals()
        val eaten = totals.kcal.known
        val bare = day.formatContext(today, bare = true)
        val eatenDisplay = policy.resolve(Metric.Calories, eaten, Surface.DayClose, day.formatContext(today, of = range.targetKcal.toDouble(), bare = true))
        val shown = eatenDisplay is Display.Shown
        val big = shown && eaten > range.maxKcal && settings.bigDayEvent != EventLevel.Off
        val rangeDisplay = policy.resolveRange(Metric.Calories, range.minKcal.toDouble(), range.maxKcal.toDouble(), Surface.DayClose, bare)
        val dayTotals = if (shown) {
            DayTotals(
                eaten = eatenDisplay,
                rangeLine = when {
                    eaten > range.maxKcal -> RangeLine.Above
                    eaten >= range.minKcal -> RangeLine.Inside(rangeDisplay)
                    else -> RangeLine.Below(rangeDisplay)
                },
                macros = if (big) emptyList() else macros(day, today, policy),
            )
        } else {
            null
        }
        val tomorrow = settings.targets.tomorrow()
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
                )
            } else {
                null
            },
            mealsLine = if (revealed) MealsLine(day.doneMealCount, MealSlot.meals.size) else null,
            loggedMeals = if (eatenDisplay is Display.Hidden) MealSlot.meals.filterTo(LinkedHashSet()) { day.isMealDone(it) } else null,
            feeling = if (eatenDisplay is Display.Hidden) FeelingChoice(DayFeeling.entries, feeling) else null,
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
