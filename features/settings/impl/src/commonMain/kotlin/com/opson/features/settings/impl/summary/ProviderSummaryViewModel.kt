package com.opson.features.settings.impl.summary

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.DispatcherProvider
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.medication.MedicationRecord
import com.opson.libraries.medication.MedicationRecords
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.pdf.PdfWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

/**
 * A summary for your provider (`release.providerSummary`). Everything is read from the phone, so
 * it works offline; the PDF is written here and handed to the share sheet, and nothing is sent
 * anywhere else. The preview follows the mode: a number the mode keeps out of sight is only in
 * the PDF, and switching one in asks first, once a visit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class ProviderSummaryViewModel(
    private val diary: DiaryRepository,
    private val weights: WeightRepository,
    private val settings: SettingsRepository,
    private val policies: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    private val pdfWriter: PdfWriter,
    private val strings: SummaryStrings,
    private val dispatchers: DispatcherProvider,
    private val medication: MedicationRecords = MedicationRecords.None,
) : SEAViewModel<ProviderSummaryState, ProviderSummaryEvent, ProviderSummaryAction>(
    initialStateArg = ProviderSummaryStateBuilder.build(
        SummaryInput(calendar.today(), calendar.timeZone(), settings.settings.value),
        policies.policy.value,
    ),
) {
    private var input = SummaryInput(calendar.today(), calendar.timeZone(), settings.settings.value)

    /** The person switched Medication themselves, so its default never overrides them. */
    private var medicationChosen = false
    private val dates = MutableStateFlow(input.dates)

    init {
        viewModelScope.launch {
            dates.flatMapLatest { range ->
                val since = range.start.atStartOfDayIn(input.zone)
                combine(diary.observeDays(range.start, range.end), weights.observeWeighIns(since), medication.observe(range.start, range.end)) { days, weighIns, record ->
                    ProviderSummaryAction.DaysArrived(range, days, weighIns, record)
                }
            }.collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: ProviderSummaryAction) {
        when (action) {
            is ProviderSummaryAction.DaysArrived -> if (action.range == input.dates) {
                // Medication starts in when it is being tracked, the first time it is known (spec: on when the plan is on).
                val firstRecord = input.medication == null && action.medication != null && !medicationChosen
                action.update {
                    val choices = if (firstRecord) it.choices.with(SummaryPart.Medication, action.medication?.tracking == true) else it.choices
                    it.copy(days = action.days, weighIns = action.weighIns, medication = action.medication, choices = choices)
                }
            }
            is ProviderSummaryAction.RangePicked -> action.pickRange(action.range)
            is ProviderSummaryAction.DayTapped -> action.dayTapped(action.date)
            ProviderSummaryAction.PrevMonthTapped -> state.calendar?.takeIf { it.canGoPrev }?.let {
                action.update { input -> input.copy(month = input.month.minus(DatePeriod(months = 1))) }
            }
            ProviderSummaryAction.NextMonthTapped -> state.calendar?.takeIf { it.canGoNext }?.let {
                action.update { input -> input.copy(month = input.month.plus(DatePeriod(months = 1))) }
            }
            is ProviderSummaryAction.PartToggled -> action.toggle(action.part)
            ProviderSummaryAction.IncludeHiddenConfirmed -> input.confirming?.let { part ->
                action.update { it.copy(choices = it.choices.with(part, true), warned = true, confirming = null) }
            }
            ProviderSummaryAction.IncludeHiddenDeclined -> action.update { it.copy(confirming = null) }
            ProviderSummaryAction.ShareTapped -> action.share()
            ProviderSummaryAction.BackTapped -> sendEvent(ProviderSummaryEvent.Close)
        }
    }

    private suspend fun ProviderSummaryAction.update(change: (SummaryInput) -> SummaryInput) {
        input = change(input).copy(settings = settings.settings.value)
        if (input.dates != dates.value) {
            input = input.copy(days = null, weighIns = emptyList(), medication = input.medication)
            dates.value = input.dates
        }
        val next = ProviderSummaryStateBuilder.build(input, policies.policy.value)
        updateState { next }
    }

    private suspend fun ProviderSummaryAction.pickRange(range: SummaryRangeChoice) {
        val today = input.today
        update {
            when (val days = range.days) {
                null -> it.copy(range = range, pickingEnd = false, month = LocalDate(it.dates.end.year, it.dates.end.month, 1))
                else -> it.copy(range = range, pickingEnd = false, dates = SummaryRange.last(days, today), failed = false)
            }
        }
    }

    /**
     * The first tap starts a range of one day; the second ends it, either side of the first.
     * A range longer than [SummaryRange.MAX_DAYS] keeps its last day and starts as far back as it may.
     */
    private suspend fun ProviderSummaryAction.dayTapped(date: LocalDate) {
        if (input.range != SummaryRangeChoice.Custom) return
        if (date < ProviderSummaryStateBuilder.earliest(input.today) || date > input.today) return
        update {
            if (!it.pickingEnd) {
                it.copy(dates = SummaryRange(date, date), pickingEnd = true, failed = false)
            } else {
                val start = minOf(it.dates.start, date)
                val end = maxOf(it.dates.start, date)
                val capped = maxOf(start, end.minus(DatePeriod(days = SummaryRange.MAX_DAYS - 1)))
                it.copy(dates = SummaryRange(capped, end), pickingEnd = false)
            }
        }
    }

    private suspend fun ProviderSummaryAction.toggle(part: SummaryPart) {
        if (part == SummaryPart.Medication) medicationChosen = true
        val on = !input.choices.has(part)
        if (on && !input.warned && ProviderSummaryStateBuilder.hides(part, policies.policy.value.settings)) {
            update { it.copy(confirming = part) }
        } else {
            update { it.copy(choices = it.choices.with(part, on)) }
        }
    }

    private suspend fun ProviderSummaryAction.share() {
        if (!state.canShare) return
        update { it.copy(sharing = true, failed = false) }
        val document = ProviderSummaryStateBuilder.pdfLines(input, clock).toPdf(strings)
        val bytes = withContext(dispatchers.io) { Catching { pdfWriter.write(document) } }.getOrNull()
        update { it.copy(sharing = false, failed = bytes == null) }
        if (bytes != null) sendEvent(ProviderSummaryEvent.SharePdf(ProviderSummaryStateBuilder.fileName(input.dates), bytes))
    }
}

sealed interface ProviderSummaryAction {
    data class DaysArrived(
        val range: SummaryRange,
        val days: List<Day>,
        val weighIns: List<WeighIn>,
        val medication: MedicationRecord? = null,
    ) : ProviderSummaryAction

    data class RangePicked(val range: SummaryRangeChoice) : ProviderSummaryAction
    data class DayTapped(val date: LocalDate) : ProviderSummaryAction
    data object PrevMonthTapped : ProviderSummaryAction
    data object NextMonthTapped : ProviderSummaryAction
    data class PartToggled(val part: SummaryPart) : ProviderSummaryAction
    data object IncludeHiddenConfirmed : ProviderSummaryAction
    data object IncludeHiddenDeclined : ProviderSummaryAction
    data object ShareTapped : ProviderSummaryAction
    data object BackTapped : ProviderSummaryAction
}

sealed interface ProviderSummaryEvent {
    /** Bytes rather than a path: the router writes the file where each platform's share sheet can reach it. */
    class SharePdf(val name: String, val bytes: ByteArray) : ProviderSummaryEvent

    data object Close : ProviderSummaryEvent
}

/** What the share sheet is told the file is. */
const val SUMMARY_MIME_TYPE = "application/pdf"
