package com.opson.libraries.ui.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
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
import kotlinx.datetime.LocalTime

/**
 * A time picked in a dialog. The dial is the platform's Material time picker; only the confirmed
 * time leaves the dialog, as one callback. The amount sheet's Time, Modes' reveal time and
 * Reminders' meal times use it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    okLabel: String,
    cancelLabel: String,
    onPicked: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                typography = AppTheme.type.sheetTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
            ThemedPicker(picker)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip, Alignment.End),
            ) {
                FlatButton(text = cancelLabel, onClick = onDismiss, tone = FlatButtonTone.Text, centered = true)
                FlatButton(
                    text = okLabel,
                    onClick = { onPicked(LocalTime(picker.hour, picker.minute)) },
                    tone = FlatButtonTone.AccentText,
                    centered = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemedPicker(picker: TimePickerState) {
    val colors = AppTheme.colors
    // AppTheme paints raw Material red on purpose, so the one Material component here
    // gets a scheme built from the app's own colours.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colors.accent.color,
            onPrimary = colors.onAccent.color,
            primaryContainer = colors.accentTint.color,
            onPrimaryContainer = colors.accent.color,
            tertiaryContainer = colors.accentTint.color,
            onTertiaryContainer = colors.accent.color,
            surface = colors.bg.color,
            onSurface = colors.ink.color,
            onSurfaceVariant = colors.muted.color,
            surfaceContainerHighest = colors.soft.color,
            outline = colors.line.color,
        ),
        typography = Typography(),
    ) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.soft.color,
                    clockDialSelectedContentColor = colors.onAccent.color,
                    clockDialUnselectedContentColor = colors.ink.color,
                    selectorColor = colors.accent.color,
                    containerColor = colors.bg.color,
                    periodSelectorBorderColor = colors.line.color,
                    periodSelectorSelectedContainerColor = colors.accentTint.color,
                    periodSelectorUnselectedContainerColor = colors.bg.color,
                    periodSelectorSelectedContentColor = colors.accent.color,
                    periodSelectorUnselectedContentColor = colors.muted.color,
                    timeSelectorSelectedContainerColor = colors.accentTint.color,
                    timeSelectorUnselectedContainerColor = colors.soft.color,
                    timeSelectorSelectedContentColor = colors.accent.color,
                    timeSelectorUnselectedContentColor = colors.ink.color,
                ),
            )
        }
    }
}
