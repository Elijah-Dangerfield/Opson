package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.food_report_basis_weight
import opson.libraries.resources.generated.resources.food_report_calories
import opson.libraries.resources.generated.resources.food_report_camera_off
import opson.libraries.resources.generated.resources.food_report_carbs
import opson.libraries.resources.generated.resources.food_report_failed
import opson.libraries.resources.generated.resources.food_report_fat
import opson.libraries.resources.generated.resources.food_report_gone
import opson.libraries.resources.generated.resources.food_report_intro
import opson.libraries.resources.generated.resources.food_report_note
import opson.libraries.resources.generated.resources.food_report_note_hint
import opson.libraries.resources.generated.resources.food_report_offline
import opson.libraries.resources.generated.resources.food_report_photo_cd
import opson.libraries.resources.generated.resources.food_report_photo_hint
import opson.libraries.resources.generated.resources.food_report_photo_title
import opson.libraries.resources.generated.resources.food_report_protein
import opson.libraries.resources.generated.resources.food_report_reason_duplicate
import opson.libraries.resources.generated.resources.food_report_reason_name
import opson.libraries.resources.generated.resources.food_report_reason_not_food
import opson.libraries.resources.generated.resources.food_report_reason_other
import opson.libraries.resources.generated.resources.food_report_reason_serving
import opson.libraries.resources.generated.resources.food_report_send
import opson.libraries.resources.generated.resources.food_report_sending
import opson.libraries.resources.generated.resources.food_report_serving
import opson.libraries.resources.generated.resources.food_report_serving_hint
import opson.libraries.resources.generated.resources.food_report_title
import opson.libraries.resources.generated.resources.food_report_unit_g
import opson.libraries.resources.generated.resources.food_report_too_many
import opson.libraries.resources.generated.resources.food_report_values_for
import opson.libraries.resources.generated.resources.food_report_values_for_100
import opson.libraries.resources.generated.resources.food_report_values_title
import opson.libraries.resources.generated.resources.food_report_what
import opson.libraries.resources.generated.resources.logger_amount_kcal
import opson.libraries.resources.generated.resources.submit_open_settings
import opson.libraries.resources.generated.resources.submit_photo_failed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.opson.libraries.ui.components.CheckRow
import com.opson.libraries.ui.components.HorizontalDivider
import opson.libraries.resources.generated.resources.food_report_reason_numbers
import opson.libraries.resources.generated.resources.food_report_reason_missing
import opson.libraries.resources.generated.resources.food_report_reason_missing_sub
import opson.libraries.resources.generated.resources.food_report_photos
import opson.libraries.resources.generated.resources.food_report_front_title
import opson.libraries.resources.generated.resources.food_report_front_hint
import opson.libraries.resources.generated.resources.food_report_front_cd

/**
 * "Report a problem", stateless: what is wrong, the right numbers when the reason is about them,
 * a photo of the label, a note and Send. The entry point puts it in a `BottomSheet` with
 * `scrollableContent` and launches the camera and the library from the view model's events.
 */
@Composable
fun FoodReportSheetContent(
    state: FoodReportState,
    onAction: (FoodReportAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.food_report_title), overline = state.name, onClose = onClose)
        Text(stringResource(Res.string.food_report_intro), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            SectionLabel(stringResource(Res.string.food_report_what))
            FoodRowsCard {
                state.reasons.forEachIndexed { index, reason ->
                    CheckRow(
                        title = stringResource(reason.label),
                        subtitle = reason.detail?.let { stringResource(it) },
                        checked = reason in state.picked,
                        onCheckedChange = { onAction(FoodReportAction.ReasonToggled(reason)) },
                        showDivider = index < state.reasons.lastIndex,
                    )
                }
            }
        }
        state.values?.let { Values(it, onAction) }
        Photos(state, onAction)
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            SectionLabel(stringResource(Res.string.food_report_note))
            TextArea(
                value = state.note,
                onValueChange = { onAction(FoodReportAction.NoteTyped(it)) },
                placeholder = stringResource(Res.string.food_report_note_hint),
            )
        }
        state.problem?.let { NoteBanner(stringResource(it.message)) }
        ButtonPrimary(
            onClick = { onAction(FoodReportAction.SendTapped) },
            enabled = state.canSend,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (state.sending) Res.string.food_report_sending else Res.string.food_report_send)) }
    }
}

@Composable
private fun Values(values: ReportValues, onAction: (FoodReportAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(stringResource(Res.string.food_report_values_title))
        if (values.numbers) {
            val grams = stringResource(Res.string.food_report_unit_g)
            Text(
                values.basis?.let { stringResource(Res.string.food_report_values_for, it.text()) } ?: stringResource(Res.string.food_report_values_for_100),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                Number(Res.string.food_report_calories, values.kcal, FoodReportStateBuilder.KCAL, stringResource(Res.string.logger_amount_kcal), onAction, Modifier.weight(1f))
                Number(Res.string.food_report_protein, values.protein, FoodReportStateBuilder.PROTEIN, grams, onAction, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                Number(Res.string.food_report_carbs, values.carbs, FoodReportStateBuilder.CARBS, grams, onAction, Modifier.weight(1f))
                Number(Res.string.food_report_fat, values.fat, FoodReportStateBuilder.FAT, grams, onAction, Modifier.weight(1f))
            }
        }
        Text(stringResource(Res.string.food_report_serving), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
        SingleLineField(
            value = values.serving,
            onValueChange = { onAction(FoodReportAction.FieldTyped(FoodReportStateBuilder.SERVING, it)) },
            placeholder = stringResource(Res.string.food_report_serving_hint),
            imeAction = ImeAction.Next,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun Number(label: StringResource, value: String, key: String, unit: String, onAction: (FoodReportAction) -> Unit, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Text(stringResource(label), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
        NumberField(
            value = value,
            onValueChange = { onAction(FoodReportAction.FieldTyped(key, it)) },
            unit = unit,
            imeAction = ImeAction.Next,
        )
    }
}

/** Photos of the front and the back of the pack, each optional, each with its own buttons. */
@Composable
private fun Photos(state: FoodReportState, onAction: (FoodReportAction) -> Unit) {
    val camera = state.canTakePhoto && !state.cameraOff
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(stringResource(Res.string.food_report_photos))
        FoodRowsCard {
            PackPhotoSlot(
                photo = state.front,
                title = stringResource(Res.string.food_report_front_title),
                hint = stringResource(Res.string.food_report_front_hint),
                description = stringResource(Res.string.food_report_front_cd),
                onTake = { onAction(FoodReportAction.TakePhotoTapped(PackSide.Front)) }.takeIf { camera },
                onChoose = { onAction(FoodReportAction.ChooseFromLibraryTapped(PackSide.Front)) },
                onRemove = { onAction(FoodReportAction.RemovePhotoTapped(PackSide.Front)) },
            )
            HorizontalDivider()
            PackPhotoSlot(
                photo = state.photo,
                title = stringResource(Res.string.food_report_photo_title),
                hint = stringResource(Res.string.food_report_photo_hint),
                description = stringResource(Res.string.food_report_photo_cd),
                onTake = { onAction(FoodReportAction.TakePhotoTapped(PackSide.Label)) }.takeIf { camera },
                onChoose = { onAction(FoodReportAction.ChooseFromLibraryTapped(PackSide.Label)) },
                onRemove = { onAction(FoodReportAction.RemovePhotoTapped(PackSide.Label)) },
            )
        }
        if (state.cameraOff) {
            Text(stringResource(Res.string.food_report_camera_off), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            ButtonGhost(onClick = { onAction(FoodReportAction.OpenSettingsTapped) }, size = ButtonSize.Small) {
                Text(stringResource(Res.string.submit_open_settings))
            }
        }
        if (state.photoFailed) Text(stringResource(Res.string.submit_photo_failed), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    }
}

/** "1 cup (81 g)", or the amount alone when it already says its weight. */
@Composable
private fun ReportBasis.text(): String = grams?.let { stringResource(Res.string.food_report_basis_weight, amount, it) } ?: amount

private val FoodReportReason.label: StringResource
    get() = when (this) {
        FoodReportReason.WrongNumbers -> Res.string.food_report_reason_numbers
        FoodReportReason.MissingInfo -> Res.string.food_report_reason_missing
        FoodReportReason.WrongServing -> Res.string.food_report_reason_serving
        FoodReportReason.WrongName -> Res.string.food_report_reason_name
        FoodReportReason.Duplicate -> Res.string.food_report_reason_duplicate
        FoodReportReason.NotFood -> Res.string.food_report_reason_not_food
        FoodReportReason.Other -> Res.string.food_report_reason_other
    }

/** A line under a reason that needs one: what counts as missing. */
private val FoodReportReason.detail: StringResource?
    get() = when (this) {
        FoodReportReason.MissingInfo -> Res.string.food_report_reason_missing_sub
        else -> null
    }

private val ReportProblem.message: StringResource
    get() = when (this) {
        ReportProblem.Offline -> Res.string.food_report_offline
        ReportProblem.Failed -> Res.string.food_report_failed
        ReportProblem.TooMany -> Res.string.food_report_too_many
        ReportProblem.Gone -> Res.string.food_report_gone
    }
