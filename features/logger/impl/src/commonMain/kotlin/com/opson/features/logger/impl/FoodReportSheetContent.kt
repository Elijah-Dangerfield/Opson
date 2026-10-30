package com.opson.features.logger.impl

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
import androidx.compose.ui.text.input.ImeAction
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.PhotoThumbnail
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.radio.RadioRow
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
import opson.libraries.resources.generated.resources.food_report_photo_remove
import opson.libraries.resources.generated.resources.food_report_photo_title
import opson.libraries.resources.generated.resources.food_report_protein
import opson.libraries.resources.generated.resources.food_report_reason_calories
import opson.libraries.resources.generated.resources.food_report_reason_duplicate
import opson.libraries.resources.generated.resources.food_report_reason_name
import opson.libraries.resources.generated.resources.food_report_reason_not_food
import opson.libraries.resources.generated.resources.food_report_reason_nutrients
import opson.libraries.resources.generated.resources.food_report_reason_other
import opson.libraries.resources.generated.resources.food_report_reason_serving
import opson.libraries.resources.generated.resources.food_report_send
import opson.libraries.resources.generated.resources.food_report_sending
import opson.libraries.resources.generated.resources.food_report_serving
import opson.libraries.resources.generated.resources.food_report_serving_hint
import opson.libraries.resources.generated.resources.food_report_title
import opson.libraries.resources.generated.resources.food_report_unit_g
import opson.libraries.resources.generated.resources.food_report_too_many
import opson.libraries.resources.generated.resources.food_report_from_photo
import opson.libraries.resources.generated.resources.food_report_values_for
import opson.libraries.resources.generated.resources.food_report_values_for_100
import opson.libraries.resources.generated.resources.food_report_values_title
import opson.libraries.resources.generated.resources.food_report_what
import opson.libraries.resources.generated.resources.logger_amount_kcal
import opson.libraries.resources.generated.resources.submit_choose
import opson.libraries.resources.generated.resources.submit_open_settings
import opson.libraries.resources.generated.resources.submit_photo_failed
import opson.libraries.resources.generated.resources.submit_retake
import opson.libraries.resources.generated.resources.submit_take
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
                    RadioRow(
                        title = stringResource(reason.label),
                        selected = reason == state.reason,
                        onClick = { onAction(FoodReportAction.ReasonPicked(reason)) },
                        showDivider = index < state.reasons.lastIndex,
                    )
                }
            }
        }
        state.values?.let { Values(it, onAction) }
        PhotoCard(state, onAction)
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
        if (values.fromPhoto) NoteBanner(stringResource(Res.string.food_report_from_photo))
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

/** The label photo: the thumbnail once there is one, and the ways to get or drop it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoCard(state: FoodReportState, onAction: (FoodReportAction) -> Unit) {
    FoodRowsCard {
        Row(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            verticalAlignment = Alignment.Top,
        ) {
            PhotoThumbnail(jpeg = state.photo?.jpeg, contentDescription = stringResource(Res.string.food_report_photo_cd))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                Text(stringResource(Res.string.food_report_photo_title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(stringResource(Res.string.food_report_photo_hint), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                    if (state.canTakePhoto && !state.cameraOff) {
                        ButtonPrimary(
                            onClick = { onAction(FoodReportAction.TakePhotoTapped) },
                            icon = AppIcons.PhotoCamera,
                            size = ButtonSize.Small,
                            style = if (state.photo == null) ButtonStyle.Filled else ButtonStyle.Tonal,
                        ) { Text(stringResource(if (state.photo == null) Res.string.submit_take else Res.string.submit_retake)) }
                    }
                    ButtonSecondary(
                        onClick = { onAction(FoodReportAction.ChooseFromLibraryTapped) },
                        icon = AppIcons.PhotoLibrary,
                        size = ButtonSize.Small,
                    ) { Text(stringResource(Res.string.submit_choose)) }
                    if (state.photo != null) {
                        ButtonGhost(onClick = { onAction(FoodReportAction.RemovePhotoTapped) }, size = ButtonSize.Small) {
                            Text(stringResource(Res.string.food_report_photo_remove))
                        }
                    }
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
    }
}

/** "1 cup (81 g)", or the amount alone when it already says its weight. */
@Composable
private fun ReportBasis.text(): String = grams?.let { stringResource(Res.string.food_report_basis_weight, amount, it) } ?: amount

private val FoodReportReason.label: StringResource
    get() = when (this) {
        FoodReportReason.WrongCalories -> Res.string.food_report_reason_calories
        FoodReportReason.WrongNutrients -> Res.string.food_report_reason_nutrients
        FoodReportReason.WrongServing -> Res.string.food_report_reason_serving
        FoodReportReason.WrongName -> Res.string.food_report_reason_name
        FoodReportReason.Duplicate -> Res.string.food_report_reason_duplicate
        FoodReportReason.NotFood -> Res.string.food_report_reason_not_food
        FoodReportReason.Other -> Res.string.food_report_reason_other
    }

private val ReportProblem.message: StringResource
    get() = when (this) {
        ReportProblem.Offline -> Res.string.food_report_offline
        ReportProblem.Failed -> Res.string.food_report_failed
        ReportProblem.TooMany -> Res.string.food_report_too_many
        ReportProblem.Gone -> Res.string.food_report_gone
    }
