package com.opson.features.logger.impl

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import com.opson.libraries.core.Catching
import com.opson.libraries.ui.StandardBorderWidth
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.FoodRowsNote
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.FocusOnOpen
import com.opson.libraries.ui.components.text.TextArea
import com.opson.libraries.ui.components.text.TextInput
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_describe_failed
import opson.libraries.resources.generated.resources.logger_describe_offline
import opson.libraries.resources.generated.resources.logger_describe_placeholder
import opson.libraries.resources.generated.resources.logger_describe_reading
import opson.libraries.resources.generated.resources.logger_describe_title
import opson.libraries.resources.generated.resources.logger_name_only
import opson.libraries.resources.generated.resources.logger_quick_add
import opson.libraries.resources.generated.resources.logger_quick_calories
import opson.libraries.resources.generated.resources.logger_quick_carbs
import opson.libraries.resources.generated.resources.logger_quick_fat
import opson.libraries.resources.generated.resources.logger_quick_fiber
import opson.libraries.resources.generated.resources.logger_quick_fit_macros
import opson.libraries.resources.generated.resources.logger_quick_mismatch
import opson.libraries.resources.generated.resources.logger_quick_name
import opson.libraries.resources.generated.resources.logger_quick_protein
import opson.libraries.resources.generated.resources.logger_quick_title
import opson.libraries.resources.generated.resources.logger_quick_unit_g
import opson.libraries.resources.generated.resources.logger_quick_unit_kcal
import opson.libraries.resources.generated.resources.logger_quick_use_macros
import opson.libraries.resources.generated.resources.logger_saved_add
import opson.libraries.resources.generated.resources.logger_saved_empty
import opson.libraries.resources.generated.resources.logger_saved_loading_cd
import opson.libraries.resources.generated.resources.logger_saved_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Describe it: free text, read into lines the person fixes before Add logs them. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DescribePane(state: LoggerState, onAction: (LoggerAction) -> Unit) {
    val describe = state.describe
    // Back from the find-a-food search returns to the lines, not out of Describe.
    BackHandler(enabled = describe.find != null) { onAction(DescribeReviewAction.FindClosed) }
    describe.find?.let { find ->
        DescribeFindPane(find, onAction)
        return
    }
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
        if (describe.lines.isNotEmpty()) DescribedLines(describe, state.slot, onAction)
        describe.quickAdd?.let { QuickAddCard(it, onAction) }
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
    // Without voice, Describe it is a box to type in, so it opens ready. Not when it opens holding
    // words already (search's "Describe it" on a miss): those are being read, and the keyboard would
    // cover what comes back. They can land a moment after the pane, which cancels the request.
    FocusOnOpen(focusRequester, enabled = voice == null && describe.text.isEmpty())
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

/**
 * "Or just the numbers": an optional name and the fields this mode shows as numbers, two to a row.
 * When the energy and the macros disagree, the gap is spelled out with a fix either way.
 */
@Composable
private fun QuickAddCard(quick: QuickAddUi, onAction: (LoggerAction) -> Unit) {
    FoodRowsCard {
        Column(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            SectionLabel(stringResource(Res.string.logger_quick_title))
            TextInput(
                value = quick.name,
                onValueChange = { onAction(QuickAddAction.NameTyped(it)) },
                placeholder = stringResource(Res.string.logger_quick_name),
            )
            quick.fields.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                    pair.forEach { field ->
                        NumberField(
                            value = field.text,
                            onValueChange = { onAction(QuickAddAction.NumberTyped(field.metric, it)) },
                            label = stringResource(field.metric.quickLabel),
                            unit = stringResource(if (field.metric == Metric.Calories) Res.string.logger_quick_unit_kcal else Res.string.logger_quick_unit_g),
                            allowDecimal = field.metric != Metric.Calories,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            quick.mismatch?.let { QuickAddMismatchView(it, onAction) }
            ButtonSecondary(
                onClick = { onAction(QuickAddAction.AddTapped) },
                enabled = quick.canAdd,
                size = ButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.logger_quick_add))
            }
        }
    }
}

/** "These macros come to 500 kcal, not 400 kcal." and the two ways to agree, as Targets offers them. */
@Composable
private fun QuickAddMismatchView(mismatch: QuickAddMismatch, onAction: (LoggerAction) -> Unit) {
    val macroKcal = mismatch.macroKcal.words ?: return
    val kcal = mismatch.kcal.words ?: return
    Text(
        text = stringResource(Res.string.logger_quick_mismatch, macroKcal, kcal),
        typography = AppTheme.type.secondary.Tabular,
        color = AppTheme.colors.ink,
    )
    FlatButton(
        text = stringResource(Res.string.logger_quick_use_macros, macroKcal),
        onClick = { onAction(QuickAddAction.UseMacroEnergyTapped) },
        centered = true,
        modifier = Modifier.fillMaxWidth(),
    )
    FlatButton(
        text = stringResource(Res.string.logger_quick_fit_macros, kcal),
        onClick = { onAction(QuickAddAction.FitMacrosTapped) },
        tone = FlatButtonTone.Outlined,
        centered = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val Display.words: String?
    get() = (this as? Display.Shown)?.text

private val Metric.quickLabel: StringResource
    get() = when (this) {
        Metric.Protein -> Res.string.logger_quick_protein
        Metric.Carbs -> Res.string.logger_quick_carbs
        Metric.Fat -> Res.string.logger_quick_fat
        Metric.Fiber -> Res.string.logger_quick_fiber
        else -> Res.string.logger_quick_calories
    }

/** Saved meals: Add logs the whole meal at once and the sheet closes. */
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
        // Opened on Saved, the meals can still be on their way; "nothing saved yet" would be a lie.
        if (state.loading) {
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.logger_saved_loading_cd))
            return@Column
        }
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
