package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.label
import com.opson.libraries.foods.RecipeReportReason
import com.opson.libraries.ui.components.CheckRow
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_log_amount
import opson.libraries.resources.generated.resources.recipes_log_button
import opson.libraries.resources.generated.resources.recipes_log_meal
import opson.libraries.resources.generated.resources.recipes_log_overline
import opson.libraries.resources.generated.resources.recipes_report_intro
import opson.libraries.resources.generated.resources.recipes_report_note
import opson.libraries.resources.generated.resources.recipes_report_offensive
import opson.libraries.resources.generated.resources.recipes_report_other
import opson.libraries.resources.generated.resources.recipes_report_send
import opson.libraries.resources.generated.resources.recipes_report_sending
import opson.libraries.resources.generated.resources.recipes_report_spam
import opson.libraries.resources.generated.resources.recipes_report_title
import opson.libraries.resources.generated.resources.recipes_report_unsafe
import opson.libraries.resources.generated.resources.recipes_report_wrong
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Log a serving: the recipe's name, how much with its numbers, the meal, and Log it. Stateless. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogRecipeSheetContent(
    state: LogRecipeState,
    onAction: (LogRecipeAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = state.name, overline = stringResource(Res.string.recipes_log_overline), onClose = onClose)
        if (state.loading) return@Column
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(Res.string.recipes_log_amount))
            Stepper(
                amount = state.amount,
                onDecrease = { onAction(LogRecipeAction.FewerTapped) },
                onIncrease = { onAction(LogRecipeAction.MoreTapped) },
                canDecrease = state.canFewer,
            )
            AmountRow(label = state.amount, numbers = state.numbers, strong = false)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(Res.string.recipes_log_meal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.slots.forEach { slot ->
                    ChoiceChip(
                        label = stringResource(slot.label),
                        selected = slot == state.slot,
                        onClick = { onAction(LogRecipeAction.SlotPicked(slot)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
        }
        ButtonPrimary(onClick = { onAction(LogRecipeAction.LogTapped) }, enabled = !state.logging, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.recipes_log_button))
        }
    }
}

/** Report a shared recipe: what's wrong, a note, Send report. Stateless. */
@Composable
fun ReportRecipeSheetContent(
    state: ReportRecipeState,
    onAction: (ReportRecipeAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.recipes_report_title), overline = state.name, onClose = onClose)
        Text(stringResource(Res.string.recipes_report_intro), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.Top) {
            RecipeReportReason.entries.forEachIndexed { index, reason ->
                CheckRow(
                    title = stringResource(reason.label),
                    checked = reason == state.reason,
                    onCheckedChange = { onAction(ReportRecipeAction.ReasonPicked(reason)) },
                    showDivider = index < RecipeReportReason.entries.lastIndex,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(Res.string.recipes_report_note))
            TextArea(value = state.note, onValueChange = { onAction(ReportRecipeAction.NoteTyped(it)) })
        }
        state.problem?.let { NoteBanner(stringResource(it)) }
        ButtonPrimary(onClick = { onAction(ReportRecipeAction.SendTapped) }, enabled = state.canSend, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.sending) Res.string.recipes_report_sending else Res.string.recipes_report_send))
        }
    }
}

private val RecipeReportReason.label: StringResource
    get() = when (this) {
        RecipeReportReason.WrongNutrition -> Res.string.recipes_report_wrong
        RecipeReportReason.Unsafe -> Res.string.recipes_report_unsafe
        RecipeReportReason.Offensive -> Res.string.recipes_report_offensive
        RecipeReportReason.Spam -> Res.string.recipes_report_spam
        RecipeReportReason.Other -> Res.string.recipes_report_other
    }
