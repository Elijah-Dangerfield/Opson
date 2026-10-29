package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.trends.NotesListLink
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class TrendsViewModel(
    diary: DiaryRepository,
    weights: WeightRepository,
    private val settingsRepository: SettingsRepository,
    private val policySource: VisibilityPolicySource,
    private val clock: Clock,
    private val health: HealthRepository,
    healthFlag: HealthReleaseFlag,
    goalsAndEnergy: GoalsAndEnergyEnabled,
    burnedRepository: BurnedRepository,
    checkInEnabled: CheckInEnabled,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    /** Today binds it (the Notes list); with nothing bound, the Notes card leaves its link off. */
    notesListLink: NotesListLink? = null,
) : SEAViewModel<TrendsState, TrendsEvent, TrendsAction>(initialStateArg = TrendsState()) {

    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date

    /** What the person picked on the page; above `init`, which can build at once. */
    private var ui = TrendsUi()

    /** The last inputs, so a pick on the page rebuilds without waiting for new data. */
    private var latest: TrendsAction.InputsArrived? = null

    init {
        // The policy source re-emits at midnight, so the date is read again on
        // each emission and the range moves with the day.
        val span = combine(settingsRepository.settings, policySource.policy) { settings, _ ->
            val today = today
            val running = runningFrom(today, settings.weightGoal.startDate)
            Span(minOf(TrendsCalendar.firstNeeded(today, settings), OnTrack.firstDay(today), running), today, running)
        }.distinctUntilChanged()

        val records = span.flatMapLatest { span ->
            val weighInsSince = span.today.minus(WEIGH_IN_DAYS, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
            combine(
                diary.observeDays(span.from, span.today),
                weights.observeWeighIns(since = weighInsSince),
            ) { days, weighIns -> Records(span.today, days, weighIns) }
        }

        val expended = if (healthFlag()) expended() else flowOf(null)

        val goalsOn = goalsAndEnergy()
        val burned = if (goalsOn) span.flatMapLatest { burnedRepository.burned(it.today) } else flowOf(null)

        // Each day's Burned for the calendar's months and the running total's days.
        val burnedByDay: Flow<Map<LocalDate, Burned>?> = if (goalsOn) {
            span.map { minOf(OnTrack.firstDay(it.today), it.runningFrom) to it.today }
                .distinctUntilChanged()
                .flatMapLatest { (from, to) -> burnedRepository.burnedBetween(from, to) }
        } else {
            flowOf(null)
        }

        // Read once when the page opens, like every release flag.
        val checkInOn = checkInEnabled()

        val notesLinked = notesListLink != null

        val energy = combine(expended, burned, burnedByDay) { expended, burned, byDay -> Energy(expended, burned?.totalKcal, byDay) }

        combine(settingsRepository.settings, policySource.policy, records, energy) { settings, policy, records, energy ->
            TrendsAction.InputsArrived(
                TrendsInputs(
                    today = records.today,
                    settings = settings,
                    days = records.days,
                    weighIns = records.weighIns,
                    timeZone = timeZone,
                    expended = energy.expended,
                    burnedKcal = energy.todayKcal,
                    notesLinked = notesLinked,
                    burnedByDay = energy.byDay,
                    checkInOn = checkInOn,
                    // The weight goal's start: "Since you started this goal", the default once there is one.
                    goalStart = settings.weightGoal.startDate?.takeIf { it <= records.today },
                ),
                policy,
            )
        }.collectIn(viewModelScope) { takeAction(it) }
    }

    /**
     * The watch's energy over the picked range, up to yesterday: today is still in progress. Read
     * again when the range or the day moves. Starts at `null`, so the page draws without waiting
     * on the health store and the Expended card joins it once the read lands.
     */
    private fun expended(): Flow<Map<LocalDate, EnergyExpended>?> =
        combine(settingsRepository.settings, policySource.policy) { settings, _ ->
            val today = today
            TrendsCalendar.span(today, settings.weekStart, settings.trendsRange).start to today.minus(1, DateTimeUnit.DAY)
        }
            .distinctUntilChanged()
            .mapLatest { (from, to) -> health.energyBetween(from, to, timeZone) }
            .onStart { emit(null) }

    override suspend fun handleAction(action: TrendsAction) {
        when (action) {
            is TrendsAction.InputsArrived -> {
                latest = action
                action.updateState { TrendsStateBuilder.build(action.inputs, action.policy, ui) }
            }
            is TrendsAction.CalendarMonthShown -> pick(action) { it.copy(calendarPage = action.page) }
            TrendsAction.CalendarHelpTapped -> pick(action) { it.copy(calendarExplained = !it.calendarExplained) }
            is TrendsAction.CalendarDayTapped -> sendEvent(TrendsEvent.OpenDay(action.date.toString()))
            is TrendsAction.RunningSpanPicked -> pick(action) { it.copy(runningSpan = action.span) }
            TrendsAction.RunningHelpTapped -> pick(action) { it.copy(runningExplained = !it.runningExplained) }
            is TrendsAction.EnergyExplainerTapped -> sendEvent(TrendsEvent.OpenEnergyExplainer(action.topic))
            is TrendsAction.RangePicked -> {
                // Not a visibility change: the range only picks which days the cards cover.
                if (action.range != state.range) settingsRepository.update { it.copy(trendsRange = action.range) }
            }
            TrendsAction.SettingsTapped -> sendEvent(TrendsEvent.OpenTrendsSettings)
            TrendsAction.PatternsTapped -> sendEvent(TrendsEvent.OpenPatterns)
            TrendsAction.NutrientsCardTapped -> sendEvent(TrendsEvent.OpenNutrients(today.toString()))
            TrendsAction.WeightCardTapped -> sendEvent(TrendsEvent.OpenWeight)
            TrendsAction.AllNotesTapped -> sendEvent(TrendsEvent.OpenNotes)
        }
    }

    /** Keeps [change] for the next inputs too, and rebuilds the page with it now. */
    private suspend fun pick(action: TrendsAction, change: (TrendsUi) -> TrendsUi) {
        ui = change(ui)
        val inputs = latest ?: return
        action.updateState { TrendsStateBuilder.build(inputs.inputs, inputs.policy, ui) }
    }

    /**
     * The first day the running total can reach back to: its longest span, ending yesterday, or
     * the weight goal's start ([goalStart]) when that is further back.
     */
    private fun runningFrom(today: LocalDate, goalStart: LocalDate?): LocalDate {
        val month = today.minus(RunningSpan.Month.days ?: 0, DateTimeUnit.DAY)
        return goalStart?.takeIf { it < month } ?: month
    }

    private data class Span(val from: LocalDate, val today: LocalDate, val runningFrom: LocalDate)

    private class Energy(val expended: Map<LocalDate, EnergyExpended>?, val todayKcal: Int?, val byDay: Map<LocalDate, Burned>?)

    private class Records(val today: LocalDate, val days: List<Day>, val weighIns: List<WeighIn>)

    private companion object {
        /** Enough history to seed the EMA before the 3 months the card can cover. */
        const val WEIGH_IN_DAYS = 120
    }
}

sealed interface TrendsAction {
    data class InputsArrived(val inputs: TrendsInputs, val policy: VisibilityPolicy) : TrendsAction
    data class RangePicked(val range: TrendsRange) : TrendsAction
    data object SettingsTapped : TrendsAction
    data object PatternsTapped : TrendsAction
    data object NutrientsCardTapped : TrendsAction
    data object WeightCardTapped : TrendsAction
    data object AllNotesTapped : TrendsAction

    /** The calendar settled on month [page], an index into its months. */
    data class CalendarMonthShown(val page: Int) : TrendsAction
    data object CalendarHelpTapped : TrendsAction
    data class CalendarDayTapped(val date: LocalDate) : TrendsAction
    data class RunningSpanPicked(val span: RunningSpan) : TrendsAction
    data object RunningHelpTapped : TrendsAction

    /** One of the energy words' (i) sheets, [com.opson.features.settings.EnergyExplainerRoute.topic]. */
    data class EnergyExplainerTapped(val topic: String) : TrendsAction
}

sealed interface TrendsEvent {
    data object OpenTrendsSettings : TrendsEvent
    data object OpenPatterns : TrendsEvent

    /** Nutrients detail on Week average, with "Trends" as the back label. */
    data class OpenNutrients(val dayIso: String) : TrendsEvent
    data object OpenWeight : TrendsEvent

    /** The Notes list, through [NotesListLink]. */
    data object OpenNotes : TrendsEvent

    /** The calendar's day sheet. */
    data class OpenDay(val dayIso: String) : TrendsEvent

    /** The shared energy (i) sheet: what total burned (TDEE) is, or how a deficit or surplus works. */
    data class OpenEnergyExplainer(val topic: String) : TrendsEvent
}
