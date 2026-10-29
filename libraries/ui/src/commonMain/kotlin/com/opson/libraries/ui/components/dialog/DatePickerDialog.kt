package com.opson.libraries.ui.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A date picked in a dialog, as [TimePickerDialog] picks a time: the platform's Material date
 * picker in the app's colours, and only the confirmed date leaves it. [range] bounds what can be
 * picked, so a birthday can't be in the future; it opens on [initial], or on [opensOn] with
 * nothing selected yet. Dates are calendar days, converted at UTC midnight, so no time zone moves
 * one to the day before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    title: String,
    initial: LocalDate?,
    opensOn: LocalDate,
    range: ClosedRange<LocalDate>,
    okLabel: String,
    cancelLabel: String,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toMillis(),
        initialDisplayedMonthMillis = (initial ?: opensOn).toMillis(),
        yearRange = range.start.year..range.endInclusive.year,
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toDate() in range

            override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
        },
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                typography = AppTheme.type.sheetTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            ThemedDatePicker(picker)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip, Alignment.End),
            ) {
                FlatButton(text = cancelLabel, onClick = onDismiss, tone = FlatButtonTone.Text, centered = true)
                FlatButton(
                    text = okLabel,
                    onClick = { picker.selectedDateMillis?.toDate()?.let(onPicked) ?: onDismiss() },
                    tone = FlatButtonTone.AccentText,
                    centered = true,
                )
            }
        }
    }
}

private fun LocalDate.toMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemedDatePicker(picker: DatePickerState) {
    val colors = AppTheme.colors
    // As TimePickerDialog: AppTheme paints raw Material red on purpose, so this Material component
    // gets a scheme built from the app's own colours.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colors.accent.color,
            onPrimary = colors.onAccent.color,
            primaryContainer = colors.accentTint.color,
            onPrimaryContainer = colors.accent.color,
            secondaryContainer = colors.accentTint.color,
            onSecondaryContainer = colors.accent.color,
            surface = colors.bg.color,
            onSurface = colors.ink.color,
            onSurfaceVariant = colors.muted.color,
            surfaceContainerHigh = colors.bg.color,
            outline = colors.line.color,
        ),
        typography = Typography(),
    ) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
            DatePicker(
                state = picker,
                title = null,
                headline = null,
                showModeToggle = true,
                colors = DatePickerDefaults.colors(
                    containerColor = colors.bg.color,
                    selectedDayContainerColor = colors.accent.color,
                    selectedDayContentColor = colors.onAccent.color,
                    todayContentColor = colors.accent.color,
                    todayDateBorderColor = colors.accent.color,
                    selectedYearContainerColor = colors.accent.color,
                    selectedYearContentColor = colors.onAccent.color,
                    dayContentColor = colors.ink.color,
                    yearContentColor = colors.ink.color,
                    navigationContentColor = colors.ink.color,
                    weekdayContentColor = colors.muted.color,
                ),
            )
        }
    }
}
