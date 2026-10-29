package com.opson.features.medication.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.MedicationName
import com.opson.libraries.medication.amount
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_summary_as_needed
import opson.libraries.resources.generated.resources.medication_summary_daily
import opson.libraries.resources.generated.resources.medication_summary_weekly
import org.jetbrains.compose.resources.stringResource

/**
 * The settings page shape (`SettingsPage` in `:features:settings:impl`, which this module can't
 * reach): a scrolling column of cards on the page colour, with the page's side padding.
 */
@Composable
internal fun MedicationPageScaffold(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = AppTheme.spacing.page)
                .padding(bottom = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(MedicationDefaults.CardGap),
            content = content,
        )
    }
}

/** A card on the page, its rows inset as the settings cards are. */
@Composable
internal fun MedicationCardBox(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AppTheme.spacing.row),
    content: @Composable ColumnScope.() -> Unit,
) {
    EdgeCard(
        modifier = modifier,
        edgeToEdge = false,
        contentPadding = MedicationDefaults.CardInset,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = modifier)
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, modifier = modifier)
}

internal object MedicationDefaults {
    val CardGap = 12.dp
    val CardInset = PaddingValues(16.dp)
}

/** One theme per preview, as the settings pages do (every preview is a design-sync screenshot). */
@Composable
internal fun MedicationPreview(dark: Boolean = false, content: @Composable () -> Unit) {
    PreviewContent(darkTheme = dark) { content() }
}

@Composable
fun MedicationName.text(): String = when (this) {
    is MedicationName.Listed -> stringResource(label)
    is MedicationName.Own -> text
}

/** "5 mg", "40 units". */
@Composable
fun doseText(dose: Double, unit: DoseUnit): String = stringResource(unit.amount, DoseFormat.number(dose))

@Composable
internal fun ScheduleSummary.text(): String = when (kind) {
    ScheduleKind.Weekly -> stringResource(
        Res.string.medication_summary_weekly,
        day?.let(DateCopy::longWeekday).orEmpty(),
        time?.clock().orEmpty(),
    )
    ScheduleKind.Daily -> stringResource(Res.string.medication_summary_daily, time?.clock().orEmpty())
    ScheduleKind.AsNeeded -> stringResource(Res.string.medication_summary_as_needed)
}

internal fun LocalTime.clock(): String = DateCopy.time(hour, minute)

/** "Thu, Oct 23". */
internal fun LocalDate.short(): String = DateCopy.weekdayMonthDay(this)
