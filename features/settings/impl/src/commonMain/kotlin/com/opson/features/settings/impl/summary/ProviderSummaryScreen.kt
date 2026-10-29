package com.opson.features.settings.impl.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.pdf.PdfStyle
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.MonthGrid
import com.opson.libraries.ui.components.MonthGridCell
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.dialog.Dialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.common_back
import opson.libraries.resources.generated.resources.summary_failed
import opson.libraries.resources.generated.resources.summary_include
import opson.libraries.resources.generated.resources.summary_include_check_ins
import opson.libraries.resources.generated.resources.summary_include_hidden
import opson.libraries.resources.generated.resources.summary_include_hunger
import opson.libraries.resources.generated.resources.summary_include_meals
import opson.libraries.resources.generated.resources.summary_include_medication
import opson.libraries.resources.generated.resources.summary_include_notes
import opson.libraries.resources.generated.resources.summary_include_numbers
import opson.libraries.resources.generated.resources.summary_include_weight
import opson.libraries.resources.generated.resources.summary_intro
import opson.libraries.resources.generated.resources.summary_nothing_picked
import opson.libraries.resources.generated.resources.summary_preview
import opson.libraries.resources.generated.resources.summary_preview_hidden
import opson.libraries.resources.generated.resources.summary_range
import opson.libraries.resources.generated.resources.summary_range_custom
import opson.libraries.resources.generated.resources.summary_range_four_weeks
import opson.libraries.resources.generated.resources.summary_range_next
import opson.libraries.resources.generated.resources.summary_range_pick
import opson.libraries.resources.generated.resources.summary_range_prev
import opson.libraries.resources.generated.resources.summary_range_two_weeks
import opson.libraries.resources.generated.resources.summary_range_week
import opson.libraries.resources.generated.resources.summary_share
import opson.libraries.resources.generated.resources.summary_sharing
import opson.libraries.resources.generated.resources.summary_title
import opson.libraries.resources.generated.resources.summary_warning_body
import opson.libraries.resources.generated.resources.summary_warning_include
import opson.libraries.resources.generated.resources.summary_warning_leave_out
import opson.libraries.resources.generated.resources.summary_warning_title
import opson.libraries.resources.generated.resources.today_cal_initial_fri
import opson.libraries.resources.generated.resources.today_cal_initial_mon
import opson.libraries.resources.generated.resources.today_cal_initial_sat
import opson.libraries.resources.generated.resources.today_cal_initial_sun
import opson.libraries.resources.generated.resources.today_cal_initial_thu
import opson.libraries.resources.generated.resources.today_cal_initial_tue
import opson.libraries.resources.generated.resources.today_cal_initial_wed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A summary for your provider: the range, what goes in, the preview, and Share as PDF. The
 * preview is drawn from the same lines the PDF is written from, less the numbers the mode keeps
 * out of sight, and every number in it carries its metric's test tag for the leak test.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderSummaryScreen(
    state: ProviderSummaryState,
    onAction: (ProviderSummaryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.summary_title),
            backLabel = stringResource(Res.string.common_back),
            onBack = { onAction(ProviderSummaryAction.BackTapped) },
        )
        Text(
            text = stringResource(Res.string.summary_intro),
            typography = AppTheme.type.body,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SectionLabel(stringResource(Res.string.summary_range))
        EdgeCard(
            edgeToEdge = false,
            contentPadding = SettingsDefaults.CardInset,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRangeChoice.entries.forEach { choice ->
                    ChoiceChip(
                        label = stringResource(choice.label),
                        selected = state.range == choice,
                        onClick = { onAction(ProviderSummaryAction.RangePicked(choice)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
            Text(text = state.rangeLabel, typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
            state.calendar?.let { RangeCalendar(it, onAction) }
        }

        SectionLabel(stringResource(Res.string.summary_include))
        SettingsCard {
            state.parts.forEachIndexed { index, row ->
                SettingsRow(
                    title = stringResource(row.part.label),
                    icon = null,
                    subtitle = if (row.hiddenByMode) stringResource(Res.string.summary_include_hidden) else null,
                    showDivider = index < state.parts.lastIndex,
                ) {
                    AppSwitch(checked = row.on, onCheckedChange = { onAction(ProviderSummaryAction.PartToggled(row.part)) })
                }
            }
        }

        SectionLabel(stringResource(Res.string.summary_preview))
        Preview(state)

        FlatButton(
            text = stringResource(if (state.sharing) Res.string.summary_sharing else Res.string.summary_share),
            onClick = { onAction(ProviderSummaryAction.ShareTapped) },
            tone = FlatButtonTone.Accent,
            centered = true,
            enabled = state.canShare,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            state.nothingPicked -> Note(stringResource(Res.string.summary_nothing_picked))
            state.failed -> Note(stringResource(Res.string.summary_failed))
        }
    }
    state.confirming?.let { HiddenNumbersDialog(onAction) }
}

@Composable
private fun RangeCalendar(calendar: SummaryCalendar, onAction: (ProviderSummaryAction) -> Unit) {
    Text(text = stringResource(Res.string.summary_range_pick), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIconButton(
            icon = AppIcons.ChevronLeft.described(stringResource(Res.string.summary_range_prev)),
            onClick = { onAction(ProviderSummaryAction.PrevMonthTapped) },
            size = AppTheme.spacing.touchTarget,
            containerColor = AppTheme.colors.surface,
            contentColor = if (calendar.canGoPrev) AppTheme.colors.ink else AppTheme.colors.line,
            enabled = calendar.canGoPrev,
        )
        Text(text = calendar.title, typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
        RoundIconButton(
            icon = AppIcons.ChevronRight.described(stringResource(Res.string.summary_range_next)),
            onClick = { onAction(ProviderSummaryAction.NextMonthTapped) },
            size = AppTheme.spacing.touchTarget,
            containerColor = AppTheme.colors.surface,
            contentColor = if (calendar.canGoNext) AppTheme.colors.ink else AppTheme.colors.line,
            enabled = calendar.canGoNext,
        )
    }
    MonthGrid(
        weekdayLabels = WEEKDAY_INITIALS.map { stringResource(it) },
        cells = calendar.cells.map { cell ->
            cell?.let {
                MonthGridCell(
                    date = it.date,
                    contentDescription = DateCopy.longMonthDay(it.date),
                    selected = it.inRange,
                    isToday = it.isToday,
                    enabled = it.enabled,
                )
            }
        },
        onDayClick = { onAction(ProviderSummaryAction.DayTapped(it)) },
    )
}

/** The summary as it will read, on a page-like card. */
@Composable
private fun Preview(state: ProviderSummaryState) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = SettingsDefaults.CardInset,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.previewHidesNumbers) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(icon = AppIcons.VisibilityOffOutline, size = 20.dp, color = AppTheme.colors.muted)
                Text(text = stringResource(Res.string.summary_preview_hidden), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
        }
        state.preview.forEach { line -> PreviewLine(line) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewLine(line: SummaryLine) {
    val typography = when (line.style) {
        PdfStyle.Title -> AppTheme.type.dayLabel
        PdfStyle.Heading -> AppTheme.type.cardTitle
        PdfStyle.Label -> AppTheme.type.bodyStrong
        PdfStyle.Body -> AppTheme.type.body
        PdfStyle.Subtitle, PdfStyle.Small -> AppTheme.type.secondary
    }
    val color = if (line.style.muted) AppTheme.colors.muted else AppTheme.colors.ink
    val top = when (line.style) {
        PdfStyle.Heading -> 12.dp
        PdfStyle.Label -> 4.dp
        else -> 0.dp
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = if (line.indented) 14.dp else 0.dp, top = top),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        line.parts.forEach { part ->
            Text(
                text = part.text.resolve(),
                typography = typography,
                color = color,
                modifier = part.metric?.let { Modifier.testTag(it.testTag()) } ?: Modifier,
            )
        }
    }
}

/** Asked once a visit, when a switch would put in numbers the mode keeps out of sight. */
@Composable
private fun HiddenNumbersDialog(onAction: (ProviderSummaryAction) -> Unit) {
    Dialog(onDismissRequest = { onAction(ProviderSummaryAction.IncludeHiddenDeclined) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Icon(icon = AppIcons.VisibilityOutline, size = 28.dp, color = AppTheme.colors.muted)
            Text(
                text = stringResource(Res.string.summary_warning_title),
                typography = AppTheme.type.dayLabel,
                color = AppTheme.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.summary_warning_body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                FlatButton(
                    text = stringResource(Res.string.summary_warning_leave_out),
                    onClick = { onAction(ProviderSummaryAction.IncludeHiddenDeclined) },
                    tone = FlatButtonTone.AccentText,
                    typography = AppTheme.type.button,
                )
                FlatButton(
                    text = stringResource(Res.string.summary_warning_include),
                    onClick = { onAction(ProviderSummaryAction.IncludeHiddenConfirmed) },
                    tone = FlatButtonTone.Text,
                    typography = AppTheme.type.button,
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
}

private val SummaryRangeChoice.label: StringResource
    get() = when (this) {
        SummaryRangeChoice.Week -> Res.string.summary_range_week
        SummaryRangeChoice.TwoWeeks -> Res.string.summary_range_two_weeks
        SummaryRangeChoice.FourWeeks -> Res.string.summary_range_four_weeks
        SummaryRangeChoice.Custom -> Res.string.summary_range_custom
    }

private val SummaryPart.label: StringResource
    get() = when (this) {
        SummaryPart.MealsAndTimes -> Res.string.summary_include_meals
        SummaryPart.Notes -> Res.string.summary_include_notes
        SummaryPart.Hunger -> Res.string.summary_include_hunger
        SummaryPart.CheckIns -> Res.string.summary_include_check_ins
        SummaryPart.Weight -> Res.string.summary_include_weight
        SummaryPart.Numbers -> Res.string.summary_include_numbers
        SummaryPart.Medication -> Res.string.summary_include_medication
    }

private val WEEKDAY_INITIALS: List<StringResource> = listOf(
    Res.string.today_cal_initial_sun,
    Res.string.today_cal_initial_mon,
    Res.string.today_cal_initial_tue,
    Res.string.today_cal_initial_wed,
    Res.string.today_cal_initial_thu,
    Res.string.today_cal_initial_fri,
    Res.string.today_cal_initial_sat,
)

/** All numbers, two weeks with every part on: what a provider would see in full. */
@Preview
@Composable
private fun ProviderSummaryPreview() {
    SettingsPreview { ProviderSummaryScreen(ProviderSummaryFixtures.state(), onAction = {}) }
}

/** No numbers with calories and weight switched in: the preview says they're only in the PDF. */
@Preview
@Composable
private fun ProviderSummaryHiddenPreview() {
    SettingsPreview { ProviderSummaryScreen(ProviderSummaryFixtures.hiddenState(), onAction = {}) }
}

/** The once-a-visit question before hidden numbers go in. */
@Preview
@Composable
private fun ProviderSummaryWarningPreview() {
    SettingsPreview { ProviderSummaryScreen(ProviderSummaryFixtures.warningState(), onAction = {}) }
}

/** Pick dates, a range chosen across the month. */
/** No numbers, on a medication: doses and side effects are in, since no mode hides a dose. */
@Preview
@Composable
private fun ProviderSummaryMedicationPreview() {
    SettingsPreview { ProviderSummaryScreen(ProviderSummaryFixtures.medicationState(), onAction = {}) }
}

@Preview
@Composable
private fun ProviderSummaryCustomDarkPreview() {
    SettingsPreview(dark = true) { ProviderSummaryScreen(ProviderSummaryFixtures.customState(), onAction = {}) }
}
