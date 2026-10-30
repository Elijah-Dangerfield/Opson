package com.opson.features.trends.impl.recap

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.CelebrationsRelease
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.DayMarks
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.NewFoods
import com.opson.libraries.diary.RecapKind
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * One recap, stepping back and forth a week or a month at a time. Opening one counts as seeing it,
 * so Today's "Your week is ready" goes once the person has looked, however they got here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class RecapViewModel(
    @Assisted kind: String,
    @Assisted startIso: String,
    private val diary: DiaryRepository,
    private val weights: WeightRepository,
    private val settingsRepository: SettingsRepository,
    private val policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val burnedRepository: BurnedRepository,
    goalsAndEnergy: GoalsAndEnergyEnabled,
    checkInEnabled: CheckInEnabled,
    private val marks: DayMarks,
    private val banner: RecapBannerStore,
    celebrations: CelebrationsRelease = CelebrationsRelease.Off,
) : SEAViewModel<RecapState, RecapEvent, RecapAction>(initialStateArg = RecapState()) {

    private val period = MutableStateFlow(
        initialPeriod(RecapKind.fromWire(kind) ?: RecapKind.Week, startIso, calendar.today(), settingsRepository.settings.value.weekStart),
    )

    init {
        // Read once, as the page opens, like every release flag.
        val goalsOn = goalsAndEnergy()
        val checkInOn = checkInEnabled()
        val celebrationsOn = celebrations.isOn()
        val records = period.flatMapLatest { shown ->
            val today = calendar.today()
            val end = minOf(shown.end, today)
            val since = shown.start.minus(WEIGH_IN_LOOKBACK_DAYS, DateTimeUnit.DAY).atStartOfDayIn(calendar.timeZone())
            combine(
                diary.observeDays(shown.start.minus(NewFoods.LOOKBACK_DAYS, DateTimeUnit.DAY), end),
                weights.observeWeighIns(since = since),
                if (goalsOn && shown.start <= end) burnedRepository.burnedBetween(shown.start, end) else flowOf(null),
                if (celebrationsOn) marks.observe() else flowOf(emptyList()),
            ) { days, weighIns, burned, dayMarks -> Records(shown, today, days, weighIns, burned, dayMarks) }
        }
        combine(records, settingsRepository.settings, policySource.policy) { records, settings, policy ->
            RecapAction.InputsArrived(
                RecapInputs(
                    today = records.today,
                    period = records.period,
                    settings = settings,
                    days = records.days,
                    weighIns = records.weighIns,
                    timeZone = calendar.timeZone(),
                    burnedByDay = records.burned,
                    checkInOn = checkInOn,
                    marks = records.marks,
                    celebrations = if (celebrationsOn) settings.celebrationStyle else CelebrationStyle.Off,
                ),
                policy,
            )
        }.collectIn(viewModelScope) { takeAction(it) }
        viewModelScope.launch { period.collect { banner.seen(it) } }
    }

    override suspend fun handleAction(action: RecapAction) {
        when (action) {
            is RecapAction.InputsArrived -> action.updateState { RecapStateBuilder.build(action.inputs, action.policy) }
            RecapAction.PreviousTapped -> period.value = period.value.previous()
            RecapAction.NextTapped -> if (state.canGoForward) period.value = period.value.next()
            is RecapAction.KindPicked -> if (action.kind != period.value.kind) {
                period.value = switched(period.value, action.kind, calendar.today(), settingsRepository.settings.value.weekStart)
            }
            is RecapAction.EnergyExplainerTapped -> sendEvent(RecapEvent.OpenEnergyExplainer(action.topic))
            RecapAction.BackTapped -> sendEvent(RecapEvent.Close)
        }
    }

    private class Records(
        val period: RecapPeriod,
        val today: LocalDate,
        val days: List<Day>,
        val weighIns: List<WeighIn>,
        val burned: Map<LocalDate, Burned>?,
        val marks: List<DayMark>,
    )

    internal companion object {
        /** Enough weigh-ins before the period to seed the smoothed line, as Trends reads. */
        const val WEIGH_IN_LOOKBACK_DAYS = 60

        /** The period a route names, or the last one over by [today] when it names none or one not begun. */
        fun initialPeriod(kind: RecapKind, startIso: String, today: LocalDate, weekStart: DayOfWeek): RecapPeriod {
            val start = Catching { LocalDate.parse(startIso) }.getOrNull()
            if (start != null && start <= today) return RecapPeriod.of(kind, start, weekStart)
            return when (kind) {
                RecapKind.Week -> RecapPeriod.lastWeek(today, weekStart)
                RecapKind.Month -> RecapPeriod.lastMonth(today)
            }
        }

        /**
         * Week to month: the month the week ended in. Month to week: its last week, or the last
         * week over by [today] while the month is still going.
         */
        fun switched(from: RecapPeriod, to: RecapKind, today: LocalDate, weekStart: DayOfWeek): RecapPeriod = when (to) {
            RecapKind.Month -> RecapPeriod.monthOf(minOf(from.end, today))
            RecapKind.Week -> if (from.end < today) RecapPeriod.weekOf(from.end, weekStart) else RecapPeriod.lastWeek(today, weekStart)
        }
    }
}

sealed interface RecapAction {
    data class InputsArrived(val inputs: RecapInputs, val policy: VisibilityPolicy) : RecapAction
    data object PreviousTapped : RecapAction
    data object NextTapped : RecapAction
    data class KindPicked(val kind: RecapKind) : RecapAction

    /** One of the shared energy (i) sheets, `EnergyExplainerRoute.topic`. */
    data class EnergyExplainerTapped(val topic: String) : RecapAction
    data object BackTapped : RecapAction
}

sealed interface RecapEvent {
    data object Close : RecapEvent
    data class OpenEnergyExplainer(val topic: String) : RecapEvent
}
