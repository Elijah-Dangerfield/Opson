package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.DayGoal
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.resolveRange
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.math.abs
import kotlin.time.Clock

/**
 * The day sheet from the on-track calendar. [standing] and [tone] are the calendar's for the day.
 * [eaten], [burned] and [difference] are each Hidden unless the mode shows them in full; the
 * difference is burned less eaten, its size, with [side] saying which way. [goal] is the day's plan:
 * "about 1,800" for a deficit or surplus, the range for a range.
 */
data class TrendsDayState(
    val loading: Boolean = true,
    val date: LocalDate? = null,
    val mode: CalendarMode = CalendarMode.LoggedOnly,
    val standing: PlanStanding = PlanStanding.NotLogged,
    val tone: PlanTone = PlanTone.None,
    val direction: GoalDirection = GoalDirection.Hold,
    val eaten: Display = Display.Hidden,
    val burned: Display = Display.Hidden,
    val difference: Display = Display.Hidden,
    val side: Balance = Balance.Even,
    val goal: Display = Display.Hidden,
    /** The goal is "about" a number worked out from Burned, rather than one the person set. */
    val goalAbout: Boolean = false,
) {
    /** Something to open: a day that has happened. */
    val canOpen: Boolean get() = !loading && standing != PlanStanding.Future
}

/** Builds [TrendsDayState] with the calendar's own rules, so a day and its cell always agree. */
object TrendsDayStateBuilder {

    fun build(day: Day, today: LocalDate, settings: Settings, policy: VisibilityPolicy, burned: Burned?): TrendsDayState {
        val context = FormatContext(day = day.date)
        val mode = OnTrack.mode(settings, policy, FormatContext(day = today))
        val usesBurn = OnTrack.usesBurn(settings.targets)
        fun opens(metric: Metric) = TrendsStateBuilder.opensOn(metric, day, today, settings.weekStart, policy)
        val burn = burned?.totalKcal
        val opened = opens(Metric.Calories) && (!usesBurn || opens(Metric.EnergyOut))
        val standing = OnTrack.standingOf(day, today, settings, mode, opened, burn)
        val direction = OnTrack.direction(settings.targets, day.date, settings.numbers.preset, burn)
        val base = TrendsDayState(loading = false, date = day.date, mode = mode, standing = standing, tone = OnTrack.tone(standing, direction), direction = direction)
        if (mode == CalendarMode.LoggedOnly || !opened || day.date > today || day.untracked) return base

        val eatenKcal = day.totals().kcal.value?.takeIf { day.hasLogged }
        // The week view is where Later opens; the day's own reveal is [opened].
        val shows = { metric: Metric -> policy.effectiveLevel(metric, Surface.Week, context) == Visibility.Show }
        val energyShown = shows(Metric.EnergyOut) && opens(Metric.EnergyOut)
        val eaten = eatenKcal?.let { policy.resolve(Metric.Calories, it, Surface.Week, context) } ?: Display.Hidden
        val burnedDisplay = if (energyShown && burned != null) policy.resolve(Metric.EnergyOut, burned.totalKcal.toDouble(), Surface.Week, context) else Display.Hidden
        val balance = if (energyShown && burned != null && eatenKcal != null && !day.totals().kcal.isPartial) burned.totalKcal - eatenKcal else null
        val goal = settings.targets.rangeFor(day.date, settings.numbers.preset, burn)
        return base.copy(
            eaten = eaten,
            burned = burnedDisplay,
            difference = balance?.let { policy.resolve(Metric.Calories, abs(it), Surface.Week, context) } ?: Display.Hidden,
            side = balance?.let(RunningTotals::side) ?: Balance.Even,
            goal = if (goal.minKcal == goal.maxKcal) {
                policy.resolve(Metric.Calories, goal.targetKcal.toDouble(), Surface.Week, context)
            } else {
                policy.resolveRange(Metric.Calories, goal.minKcal.toDouble(), goal.maxKcal.toDouble(), Surface.Week, context)
            },
            goalAbout = goal is DayGoal.About,
        )
    }
}

@Inject
class TrendsDayViewModel(
    @Assisted private val date: LocalDate,
    diary: DiaryRepository,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    goalsAndEnergy: GoalsAndEnergyEnabled,
    burnedRepository: BurnedRepository,
    private val selectedDay: SelectedDay,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SEAViewModel<TrendsDayState, TrendsDayEvent, TrendsDayAction>(initialStateArg = TrendsDayState(date = date)) {

    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date

    init {
        val burned = if (goalsAndEnergy()) burnedRepository.burned(date) else flowOf(null)
        combine(diary.observeDay(date), settingsRepository.settings, policySource.policy, burned) { day, settings, policy, burned ->
            TrendsDayAction.InputsArrived(TrendsDayStateBuilder.build(day, today, settings, policy, burned))
        }.collectIn(viewModelScope) { takeAction(it) }
    }

    override suspend fun handleAction(action: TrendsDayAction) {
        when (action) {
            is TrendsDayAction.InputsArrived -> action.updateState { action.state }
            TrendsDayAction.OpenDayTapped -> if (state.canOpen) {
                selectedDay.select(date)
                sendEvent(TrendsDayEvent.OpenToday)
            }
            TrendsDayAction.CloseTapped -> sendEvent(TrendsDayEvent.Dismiss)
        }
    }
}

sealed interface TrendsDayAction {
    data class InputsArrived(val state: TrendsDayState) : TrendsDayAction
    data object OpenDayTapped : TrendsDayAction
    data object CloseTapped : TrendsDayAction
}

sealed interface TrendsDayEvent {
    /** Today, on the day picked ([SelectedDay] already moved there). */
    data object OpenToday : TrendsDayEvent
    data object Dismiss : TrendsDayEvent
}
