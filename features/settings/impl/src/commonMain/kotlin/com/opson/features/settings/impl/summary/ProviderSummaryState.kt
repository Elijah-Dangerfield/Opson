package com.opson.features.settings.impl.summary

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.medication.MedicationRecord
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Clock

/** The range chips. [days] is null for Pick dates. */
enum class SummaryRangeChoice(val days: Int?) {
    Week(7),
    TwoWeeks(14),
    FourWeeks(28),
    Custom(null),
}

/**
 * @property parts every switch under What goes in, and whether the mode keeps its numbers out of
 *   sight (said under it, and the reason a switch on asks first).
 * @property preview the summary as the PDF will read, less any number the mode keeps out of sight.
 * @property previewHidesNumbers some chosen numbers are only in the PDF; the preview says so.
 * @property confirming a switch whose numbers the mode hides, waiting on "Include them".
 */
data class ProviderSummaryState(
    val range: SummaryRangeChoice,
    val dates: SummaryRange,
    val rangeLabel: String,
    val calendar: SummaryCalendar?,
    val parts: List<SummaryPartRow>,
    val preview: List<SummaryLine>,
    val previewHidesNumbers: Boolean,
    val loading: Boolean,
    val nothingPicked: Boolean,
    val confirming: SummaryPart? = null,
    val sharing: Boolean = false,
    val failed: Boolean = false,
) {
    val canShare: Boolean get() = !loading && !sharing && !nothingPicked
}

data class SummaryPartRow(val part: SummaryPart, val on: Boolean, val hiddenByMode: Boolean)

/** Pick dates: one month, Sunday first like Today's calendar, the chosen days drawn selected. */
data class SummaryCalendar(
    val month: LocalDate,
    val title: String,
    /** A null is a blank before the 1st. */
    val cells: List<SummaryCalendarCell?>,
    val canGoPrev: Boolean,
    val canGoNext: Boolean,
)

data class SummaryCalendarCell(val date: LocalDate, val inRange: Boolean, val isToday: Boolean, val enabled: Boolean)

/** Everything the screen is built from. The view model holds one and rebuilds the state from it. */
data class SummaryInput(
    val today: LocalDate,
    val zone: TimeZone,
    val settings: Settings,
    val range: SummaryRangeChoice = SummaryRangeChoice.TwoWeeks,
    val dates: SummaryRange = SummaryRange.last(SummaryRangeChoice.TwoWeeks.days ?: 0, today),
    /** Pick dates: the first day is picked and the next tap is the last. */
    val pickingEnd: Boolean = false,
    val month: LocalDate = LocalDate(today.year, today.month, 1),
    val choices: SummaryChoices = SummaryChoices.defaultsFor(settings.numbers),
    /** Null until the range's days have been read. */
    val days: List<Day>? = null,
    val weighIns: List<WeighIn> = emptyList(),
    /** Null while `release.medication` is off or nothing was set up: no Medication switch at all. */
    val medication: MedicationRecord? = null,
    /** "Include them" was said once; no switch asks again on this visit. */
    val warned: Boolean = false,
    val confirming: SummaryPart? = null,
    val sharing: Boolean = false,
    val failed: Boolean = false,
)

object ProviderSummaryStateBuilder {

    /** How far back Pick dates reaches. */
    const val WINDOW_DAYS = 365

    fun build(input: SummaryInput, policy: VisibilityPolicy): ProviderSummaryState {
        val numbers = policy.settings
        val content = SummaryContentBuilder.build(input.dates, input.choices, input.days.orEmpty(), input.weighIns, input.zone, input.medication)
        val shown = previewMetrics(input.choices, numbers)
        return ProviderSummaryState(
            range = input.range,
            dates = input.dates,
            rangeLabel = "${DateCopy.range(input.dates.start, input.dates.end)}, ${input.dates.end.year}",
            calendar = if (input.range == SummaryRangeChoice.Custom) calendar(input) else null,
            parts = SummaryPart.entries.filter { it != SummaryPart.Medication || input.medication != null }
                .map { SummaryPartRow(it, input.choices.has(it), hiddenByMode = hides(it, numbers)) },
            preview = if (input.days == null) emptyList() else SummaryLines.build(content, shown, policy, units(input.settings), input.today),
            previewHidesNumbers = shown != input.choices.metrics,
            loading = input.days == null,
            nothingPicked = input.choices.isEmpty,
            confirming = input.confirming,
            sharing = input.sharing,
            failed = input.failed,
        )
    }

    /**
     * The lines the PDF is written from: every chosen number, spelled by an All numbers policy
     * on the same clock, whatever the person's own mode.
     */
    fun pdfLines(input: SummaryInput, clock: Clock): List<SummaryLine> {
        val content = SummaryContentBuilder.build(input.dates, input.choices, input.days.orEmpty(), input.weighIns, input.zone, input.medication)
        val everything = VisibilityPolicy(NumbersSettings(preset = PresetId.Full), clock, input.zone)
        return SummaryLines.build(content, input.choices.metrics, everything, units(input.settings), input.today)
    }

    /** The chosen numbers the mode shows outright. Softened, held until Later and hidden all stay off the screen. */
    fun previewMetrics(choices: SummaryChoices, numbers: NumbersSettings): Set<Metric> =
        choices.metrics.filterTo(mutableSetOf()) { numbers.level(it) == Visibility.Show }

    fun hides(part: SummaryPart, numbers: NumbersSettings): Boolean = part.metrics.any { numbers.level(it) != Visibility.Show }

    fun fileName(range: SummaryRange): String = "opson-summary-${range.start}-to-${range.end}.pdf"

    private fun units(settings: Settings) = SummaryUnits(settings.weightUnit, settings.waterUnit, settings.waterGoalMl)

    private fun calendar(input: SummaryInput): SummaryCalendar {
        val month = input.month
        val last = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        // Sunday first: Sunday is ISO 7, so it leads with no blanks.
        val blanks = month.dayOfWeek.isoDayNumber % DAYS_IN_WEEK
        val earliest = earliest(input.today)
        val days = (0 until last.day).map { offset ->
            val date = month.plus(DatePeriod(days = offset))
            SummaryCalendarCell(
                date = date,
                inRange = date >= input.dates.start && date <= input.dates.end,
                isToday = date == input.today,
                enabled = date >= earliest && date <= input.today,
            )
        }
        return SummaryCalendar(
            month = month,
            title = DateCopy.monthYear(month),
            cells = List(blanks) { null } + days,
            canGoPrev = month > LocalDate(earliest.year, earliest.month, 1),
            canGoNext = month < LocalDate(input.today.year, input.today.month, 1),
        )
    }

    fun earliest(today: LocalDate): LocalDate = today.minus(DatePeriod(days = WINDOW_DAYS - 1))

    private const val DAYS_IN_WEEK = 7
}
