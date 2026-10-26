package com.opson.features.logger.impl

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import com.opson.libraries.core.Catching
import com.opson.libraries.ui.StandardBorderWidth
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.FoodRowsNote
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_describe_failed
import opson.libraries.resources.generated.resources.logger_describe_offline
import opson.libraries.resources.generated.resources.logger_describe_placeholder
import opson.libraries.resources.generated.resources.logger_describe_reading
import opson.libraries.resources.generated.resources.logger_describe_title
import opson.libraries.resources.generated.resources.logger_described_estimate
import opson.libraries.resources.generated.resources.logger_described_sub
import opson.libraries.resources.generated.resources.logger_edit_line_cd
import opson.libraries.resources.generated.resources.logger_name_only
import opson.libraries.resources.generated.resources.logger_number_add
import opson.libraries.resources.generated.resources.logger_number_caption
import opson.libraries.resources.generated.resources.logger_number_placeholder
import opson.libraries.resources.generated.resources.logger_or_number
import opson.libraries.resources.generated.resources.logger_read_as
import opson.libraries.resources.generated.resources.logger_saved_add
import opson.libraries.resources.generated.resources.logger_saved_empty
import opson.libraries.resources.generated.resources.logger_saved_title
import org.jetbrains.compose.resources.stringResource

/** Describe it: free text, read into editable lines that are picks while this pane is open. */
@Composable
internal fun DescribePane(state: LoggerState, onAction: (LoggerAction) -> Unit) {
    val describe = state.describe
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(
            title = stringResource(Res.string.logger_describe_title),
            overline = forMeal(state.slot),
            onBack = { onAction(LoggerAction.BackTapped) },
        )
        DescribeBox(describe, onAction)
        when (describe.status) {
            DescribeStatus.Idle, DescribeStatus.Parsed -> Unit
            DescribeStatus.Parsing -> Note(stringResource(Res.string.logger_describe_reading))
            DescribeStatus.Offline -> Note(stringResource(Res.string.logger_describe_offline))
            DescribeStatus.Failed -> Note(stringResource(Res.string.logger_describe_failed))
        }
        if (describe.lines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                SectionLabel(stringResource(Res.string.logger_read_as))
                FoodRowsCard {
                    describe.lines.forEachIndexed { index, line ->
                        DescribedRow(line, onAction, showDivider = index < describe.lines.lastIndex)
                    }
                }
            }
            ButtonPrimary(onClick = { onAction(LoggerAction.AddToMealTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.logger_add_to, stringResource(state.slot.sentenceName)))
            }
        }
        if (describe.canNumber) NumberCard(describe, onAction)
        if (describe.text.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                InlineLink(stringResource(Res.string.logger_name_only)) { onAction(LoggerAction.SaveNameTapped) }
            }
        }
    }
}

@Composable
private fun DescribeBox(describe: DescribeUi, onAction: (LoggerAction) -> Unit) {
    val voice = describe.voice
    val focusRequester = remember { FocusRequester() }
    val inPreview = LocalInspectionMode.current
    LaunchedEffect(voice?.focusText) {
        // "Type instead": the keyboard comes up where the words go. No window in a preview, nothing to focus.
        if (voice?.focusText == true && !inPreview) Catching { focusRequester.requestFocus() }
    }
    val shape = AppTheme.shapes.inputLarge
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.colors.surface)
            .border(StandardBorderWidth, AppTheme.colors.accent.color, shape)
            .padding(bottom = AppTheme.spacing.row),
    ) {
        TextArea(
            value = describe.text,
            onValueChange = { onAction(LoggerAction.DescribeTyped(it)) },
            placeholder = stringResource(Res.string.logger_describe_placeholder),
            minLines = 2,
            // The box around it carries the outline, so the field's own melts into the fill.
            borderColor = AppTheme.colors.surface,
            modifier = Modifier.focusRequester(focusRequester),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.section),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            DescribePromptLine(voice, modifier = Modifier.weight(1f))
            // The design's mic, behind release.voice.
            voice?.let { MicButton(it, onAction) }
        }
    }
    voice?.notice?.let { VoiceNoticeCard(it, onAction) }
}

@Composable
private fun DescribedRow(line: DescribedLine, onAction: (LoggerAction) -> Unit, showDivider: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.chip),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = line.name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
                Text(
                    text = if (line.amount.isEmpty()) {
                        stringResource(Res.string.logger_described_estimate)
                    } else {
                        stringResource(Res.string.logger_described_sub, line.amount)
                    },
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
            NutrientText(
                metric = Metric.Calories,
                display = line.calories,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
            if (line.ref != null) {
                RoundIconButton(
                    icon = AppIcons.EditOutline.described(stringResource(Res.string.logger_edit_line_cd, line.name)),
                    onClick = { onAction(LoggerAction.DescribedLineEditTapped(line)) },
                    size = AppTheme.spacing.touchTarget,
                    containerColor = AppTheme.colors.surface,
                    contentColor = AppTheme.colors.muted,
                )
            }
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

@Composable
private fun NumberCard(describe: DescribeUi, onAction: (LoggerAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val inPreview = LocalInspectionMode.current
    LaunchedEffect(describe.focusNumber) {
        // Without a window there is nothing to focus, and requesting it throws.
        if (describe.focusNumber && !inPreview) Catching { focusRequester.requestFocus() }
    }
    FoodRowsCard {
        Column(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            SectionLabel(stringResource(Res.string.logger_or_number))
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
                NumberField(
                    value = describe.number,
                    onValueChange = { onAction(LoggerAction.NumberTyped(it)) },
                    placeholder = stringResource(Res.string.logger_number_placeholder),
                    allowDecimal = false,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                ButtonSecondary(onClick = { onAction(LoggerAction.AddNumberTapped) }, size = ButtonSize.Medium) {
                    Text(stringResource(Res.string.logger_number_add))
                }
            }
            Text(text = stringResource(Res.string.logger_number_caption), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
}

/** Saved meals: picking one puts its foods in the basket and goes back to search. */
@Composable
internal fun SavedPane(state: LoggerState, onAction: (LoggerAction) -> Unit) {
    val meals = state.saved.meals
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(
            title = stringResource(Res.string.logger_saved_title),
            overline = forMeal(state.slot),
            onBack = { onAction(LoggerAction.BackTapped) },
        )
        FoodRowsCard {
            if (meals.isEmpty()) FoodRowsNote(stringResource(Res.string.logger_saved_empty))
            meals.forEachIndexed { index, meal ->
                ListRow(
                    title = meal.name,
                    subtitle = meal.foods,
                    showDivider = index < meals.lastIndex,
                ) {
                    NutrientText(
                        metric = Metric.Calories,
                        display = meal.calories,
                        typography = AppTheme.type.secondary.Tabular,
                        color = AppTheme.colors.muted,
                        maxLines = 1,
                    )
                    ButtonSecondary(onClick = { onAction(LoggerAction.SavedMealPicked(meal.id)) }, size = ButtonSize.Small) {
                        Text(stringResource(Res.string.logger_saved_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}
