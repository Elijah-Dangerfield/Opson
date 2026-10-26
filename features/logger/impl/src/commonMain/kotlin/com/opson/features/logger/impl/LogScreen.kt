package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import com.opson.features.logger.LogTab
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.ui.DiaryItemRow
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.FoodRowsNote
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.CircularLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_back_cd
import opson.libraries.resources.generated.resources.logger_copy
import opson.libraries.resources.generated.resources.logger_copy_yesterday
import opson.libraries.resources.generated.resources.logger_describe_failed
import opson.libraries.resources.generated.resources.logger_describe_go
import opson.libraries.resources.generated.resources.logger_describe_offline
import opson.libraries.resources.generated.resources.logger_describe_placeholder
import opson.libraries.resources.generated.resources.logger_describe_prompt
import opson.libraries.resources.generated.resources.logger_log_adding_to
import opson.libraries.resources.generated.resources.logger_name_only
import opson.libraries.resources.generated.resources.logger_number_add
import opson.libraries.resources.generated.resources.logger_number_placeholder
import opson.libraries.resources.generated.resources.logger_or_number
import opson.libraries.resources.generated.resources.logger_parsed_sub
import opson.libraries.resources.generated.resources.logger_saved_add
import opson.libraries.resources.generated.resources.logger_saved_empty
import opson.libraries.resources.generated.resources.logger_tab_describe
import opson.libraries.resources.generated.resources.logger_tab_saved
import org.jetbrains.compose.resources.stringResource

/**
 * The Log screen: "Adding to {meal}", the meal chips, and the Describe and
 * Saved tabs. Stateless; [LogViewModel] owns what it shows.
 */
@Composable
fun LogScreen(
    state: LogState,
    onAction: (LogAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { insets ->
        LogScreenBody(state, onAction, Modifier.padding(insets))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogScreenBody(state: LogState, onAction: (LogAction) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.row),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        RoundIconButton(
            icon = AppIcons.ChevronLeft.described(stringResource(Res.string.logger_back_cd)),
            onClick = { onAction(LogAction.BackTapped) },
            size = AppTheme.spacing.touchTarget,
            containerColor = AppTheme.colors.surface,
            contentColor = AppTheme.colors.ink,
        )
        Column {
            Text(text = stringResource(Res.string.logger_log_adding_to), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            Text(text = stringResource(state.slot.label), typography = AppTheme.type.h1, color = AppTheme.colors.ink)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            state.slots.forEach { slot ->
                PillChip(
                    label = stringResource(slot.label),
                    selected = slot == state.slot,
                    onClick = { onAction(LogAction.SlotPicked(slot)) },
                )
            }
        }
        SegmentedControl(
            options = LogTab.entries,
            selected = state.tab,
            onSelect = { onAction(LogAction.TabPicked(it)) },
            label = {
                when (it) {
                    LogTab.Describe -> stringResource(Res.string.logger_tab_describe)
                    LogTab.Saved -> stringResource(Res.string.logger_tab_saved)
                }
            },
        )
        if (state.loading) return@Column
        when (state.tab) {
            LogTab.Describe -> DescribeTab(state, onAction)
            LogTab.Saved -> SavedTab(state, onAction)
        }
    }
}

@Composable
private fun DescribeTab(state: LogState, onAction: (LogAction) -> Unit) {
    val describe = state.describe
    val meal = stringResource(state.slot.sentenceName)
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(stringResource(Res.string.logger_describe_prompt))
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalAlignment = Alignment.CenterVertically) {
            SingleLineField(
                value = describe.text,
                onValueChange = { onAction(LogAction.DescribeTyped(it)) },
                placeholder = stringResource(Res.string.logger_describe_placeholder),
                imeAction = ImeAction.Go,
                keyboardActions = KeyboardActions(onGo = { onAction(LogAction.GoTapped) }),
                modifier = Modifier.weight(1f),
            )
            ButtonPrimary(onClick = { onAction(LogAction.GoTapped) }, size = ButtonSize.Medium) {
                Text(stringResource(Res.string.logger_describe_go))
            }
        }
    }
    when (val status = describe.status) {
        DescribeStatus.Idle -> Unit
        DescribeStatus.Parsing -> CircularLoadingIndicator()
        DescribeStatus.Offline -> Note(stringResource(Res.string.logger_describe_offline))
        DescribeStatus.Failed -> Note(stringResource(Res.string.logger_describe_failed))
        is DescribeStatus.Parsed -> EdgeCard(edgeToEdge = false) {
            status.items.forEachIndexed { index, item ->
                DiaryItemRow(
                    name = item.name,
                    sub = stringResource(Res.string.logger_parsed_sub, item.amount),
                    calories = item.calories,
                    onClick = null,
                    showDivider = index < status.items.lastIndex,
                )
            }
            ButtonPrimary(onClick = { onAction(LogAction.AddParsedTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.logger_add_to, meal))
            }
        }
    }
    if (describe.canNumber) NumberCard(describe, onAction)
    ButtonGhost(onClick = { onAction(LogAction.SaveNameOnlyTapped) }) {
        Text(stringResource(Res.string.logger_name_only))
    }
}

@Composable
private fun NumberCard(describe: DescribeUi, onAction: (LogAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val inPreview = LocalInspectionMode.current
    LaunchedEffect(describe.focusNumber) {
        if (describe.focusNumber && !inPreview) Catching { focusRequester.requestFocus() }
    }
    EdgeCard(edgeToEdge = false) {
        SectionLabel(stringResource(Res.string.logger_or_number))
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                value = describe.number,
                onValueChange = { onAction(LogAction.NumberTyped(it)) },
                placeholder = stringResource(Res.string.logger_number_placeholder),
                allowDecimal = false,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            ButtonSecondary(onClick = { onAction(LogAction.AddNumberTapped) }, size = ButtonSize.Medium) {
                Text(stringResource(Res.string.logger_number_add))
            }
        }
    }
}

@Composable
private fun SavedTab(state: LogState, onAction: (LogAction) -> Unit) {
    state.saved.dayBefore?.let { row ->
        EdgeCard(edgeToEdge = false, onClick = { onAction(LogAction.CopyDayBeforeTapped) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.logger_copy_yesterday, stringResource(row.slot.sentenceName)),
                        typography = AppTheme.type.bodyBold,
                        color = AppTheme.colors.ink,
                    )
                    Text(text = row.names, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
                }
                Text(text = stringResource(Res.string.logger_copy), typography = AppTheme.type.bodyBold, color = AppTheme.colors.accent)
            }
        }
    }
    FoodRowsCard {
        if (state.saved.meals.isEmpty()) FoodRowsNote(stringResource(Res.string.logger_saved_empty))
        state.saved.meals.forEachIndexed { index, meal ->
            ListRow(
                title = meal.name,
                subtitle = meal.foods,
                showDivider = index < state.saved.meals.lastIndex,
            ) {
                NutrientText(
                    metric = Metric.Calories,
                    display = meal.calories,
                    typography = AppTheme.type.secondarySmall.Tabular,
                    color = AppTheme.colors.muted,
                    maxLines = 1,
                )
                ButtonSecondary(onClick = { onAction(LogAction.SavedMealAddTapped(meal.id)) }, size = ButtonSize.Small) {
                    Text(stringResource(Res.string.logger_saved_add))
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}
