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
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.ui.components.CircularProgressIndicator
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.PhotoThumbnail
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.builder_check_confirmed
import opson.libraries.resources.generated.resources.builder_check_correct
import opson.libraries.resources.generated.resources.builder_check_corrected
import opson.libraries.resources.generated.resources.builder_check_differs_hidden
import opson.libraries.resources.generated.resources.builder_check_differs_title
import opson.libraries.resources.generated.resources.builder_check_gone
import opson.libraries.resources.generated.resources.builder_check_intro
import opson.libraries.resources.generated.resources.builder_check_matches_body
import opson.libraries.resources.generated.resources.builder_check_matches_title
import opson.libraries.resources.generated.resources.builder_check_not_builder
import opson.libraries.resources.generated.resources.builder_check_per_100
import opson.libraries.resources.generated.resources.builder_check_per_serving
import opson.libraries.resources.generated.resources.builder_check_reading
import opson.libraries.resources.generated.resources.builder_check_retake
import opson.libraries.resources.generated.resources.builder_check_row_opson
import opson.libraries.resources.generated.resources.builder_check_row_pack
import opson.libraries.resources.generated.resources.builder_check_send
import opson.libraries.resources.generated.resources.builder_check_title
import opson.libraries.resources.generated.resources.builder_check_too_many
import opson.libraries.resources.generated.resources.builder_check_unavailable
import opson.libraries.resources.generated.resources.builder_check_unconfirmed
import opson.libraries.resources.generated.resources.builder_check_unreadable
import opson.libraries.resources.generated.resources.builder_failed
import opson.libraries.resources.generated.resources.builder_offline
import opson.libraries.resources.generated.resources.food_report_calories
import opson.libraries.resources.generated.resources.food_report_camera_off
import opson.libraries.resources.generated.resources.food_report_carbs
import opson.libraries.resources.generated.resources.food_report_fat
import opson.libraries.resources.generated.resources.food_report_protein
import opson.libraries.resources.generated.resources.submit_choose
import opson.libraries.resources.generated.resources.submit_done
import opson.libraries.resources.generated.resources.submit_label_cd
import opson.libraries.resources.generated.resources.submit_open_settings
import opson.libraries.resources.generated.resources.submit_take
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Check against the pack, stateless: a photo of the label, then whether it matches, with Send the
 * check or Send a correction. Numbers show only where the mode shows them; otherwise a mismatch is
 * said in words. The entry point puts it in a `BottomSheet` with `scrollableContent`.
 */
@Composable
fun CheckFoodSheetContent(
    state: CheckFoodState,
    onAction: (CheckFoodAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.builder_check_title), overline = state.name, onClose = onClose)
        state.photo?.let { PhotoThumbnail(jpeg = it.jpeg, contentDescription = stringResource(Res.string.submit_label_cd)) }
        when (val stage = state.stage) {
            CheckStage.Intro -> Intro(state, onAction)
            CheckStage.Reading -> Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Body(stringResource(Res.string.builder_check_reading))
            }
            CheckStage.Unreadable -> {
                Body(stringResource(Res.string.builder_check_unreadable))
                ButtonPrimary(onClick = { onAction(CheckFoodAction.RetakeTapped) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.builder_check_retake))
                }
            }
            CheckStage.Matches -> {
                Title(stringResource(Res.string.builder_check_matches_title))
                Body(stringResource(Res.string.builder_check_matches_body))
                ButtonPrimary(onClick = { onAction(CheckFoodAction.SendCheckTapped) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.builder_check_send))
                }
                Retake(onAction)
            }
            is CheckStage.Differs -> {
                Title(stringResource(Res.string.builder_check_differs_title))
                if (stage.rows.isEmpty()) {
                    Body(stringResource(Res.string.builder_check_differs_hidden))
                } else {
                    Rows(stage)
                }
                ButtonPrimary(onClick = { onAction(CheckFoodAction.SendCorrectionTapped) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.builder_check_correct))
                }
                Retake(onAction)
            }
            is CheckStage.Done -> {
                Body(
                    stringResource(
                        when (stage.outcome) {
                            CheckDone.Confirmed -> Res.string.builder_check_confirmed
                            CheckDone.Unconfirmed -> Res.string.builder_check_unconfirmed
                            CheckDone.Corrected -> Res.string.builder_check_corrected
                        },
                    ),
                )
                ButtonPrimary(onClick = { onAction(CheckFoodAction.DoneTapped) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.submit_done))
                }
            }
        }
        state.problem?.let { NoteBanner(stringResource(it.message)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(state: CheckFoodState, onAction: (CheckFoodAction) -> Unit) {
    Body(stringResource(Res.string.builder_check_intro))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        if (state.canTakePhoto && !state.cameraOff) {
            ButtonPrimary(onClick = { onAction(CheckFoodAction.TakePhotoTapped) }, icon = AppIcons.PhotoCamera, size = ButtonSize.Small) {
                Text(stringResource(Res.string.submit_take))
            }
        }
        ButtonSecondary(onClick = { onAction(CheckFoodAction.ChooseFromLibraryTapped) }, icon = AppIcons.PhotoLibrary, size = ButtonSize.Small) {
            Text(stringResource(Res.string.submit_choose))
        }
    }
    if (state.cameraOff) {
        Body(stringResource(Res.string.food_report_camera_off))
        ButtonGhost(onClick = { onAction(CheckFoodAction.OpenSettingsTapped) }, size = ButtonSize.Small) {
            Text(stringResource(Res.string.submit_open_settings))
        }
    }
}

@Composable
private fun Rows(stage: CheckStage.Differs) {
    FoodRowsCard {
        Column(modifier = Modifier.padding(vertical = AppTheme.spacing.section), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            Text(
                stringResource(if (stage.perServing) Res.string.builder_check_per_serving else Res.string.builder_check_per_100),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
            )
            RowLine(label = "", pack = stringResource(Res.string.builder_check_row_pack), opson = stringResource(Res.string.builder_check_row_opson), strong = false)
            stage.rows.forEach { row ->
                RowLine(label = stringResource(row.nutrient.label), pack = row.pack.shown(), opson = row.opson.shown(), strong = row.differs)
            }
        }
    }
}

@Composable
private fun RowLine(label: String, pack: String, opson: String, strong: Boolean) {
    val type = if (strong) AppTheme.type.bodyStrong else AppTheme.type.body
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Text(label, typography = type, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        Text(pack, typography = type, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        Text(opson, typography = type, color = AppTheme.colors.muted, modifier = Modifier.weight(1f))
    }
}

/** A side with no figure reads as a dash. */
@Composable
private fun UnitAmount?.shown(): String = this?.text() ?: NO_FIGURE

@Composable
private fun Retake(onAction: (CheckFoodAction) -> Unit) {
    ButtonGhost(onClick = { onAction(CheckFoodAction.RetakeTapped) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.builder_check_retake))
    }
}

@Composable
private fun Title(text: String) = Text(text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)

@Composable
private fun Body(text: String) = Text(text, typography = AppTheme.type.body, color = AppTheme.colors.muted)

private val LabelNutrient.label: StringResource
    get() = when (this) {
        LabelNutrient.Energy -> Res.string.food_report_calories
        LabelNutrient.Protein -> Res.string.food_report_protein
        LabelNutrient.Carbs -> Res.string.food_report_carbs
        else -> Res.string.food_report_fat
    }

private val CheckProblem.message: StringResource
    get() = when (this) {
        CheckProblem.Offline -> Res.string.builder_offline
        CheckProblem.Failed -> Res.string.builder_failed
        CheckProblem.TooMany -> Res.string.builder_check_too_many
        CheckProblem.Gone -> Res.string.builder_check_gone
        CheckProblem.NotBuilder -> Res.string.builder_check_not_builder
        CheckProblem.Unavailable -> Res.string.builder_check_unavailable
    }

private const val NO_FIGURE = "–"
