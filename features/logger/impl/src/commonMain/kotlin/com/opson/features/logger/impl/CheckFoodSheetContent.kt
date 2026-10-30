package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.VerifyField
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.CircularProgressIndicator
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.VerifiedMark
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipSize
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.builder_check_differs_hidden
import opson.libraries.resources.generated.resources.builder_check_differs_title
import opson.libraries.resources.generated.resources.builder_check_gone
import opson.libraries.resources.generated.resources.builder_check_not_builder
import opson.libraries.resources.generated.resources.builder_check_per_100
import opson.libraries.resources.generated.resources.builder_check_per_serving
import opson.libraries.resources.generated.resources.builder_check_reading
import opson.libraries.resources.generated.resources.builder_check_row_opson
import opson.libraries.resources.generated.resources.builder_check_row_pack
import opson.libraries.resources.generated.resources.builder_check_too_many
import opson.libraries.resources.generated.resources.builder_check_unavailable
import opson.libraries.resources.generated.resources.builder_check_unconfirmed
import opson.libraries.resources.generated.resources.builder_check_corrected
import opson.libraries.resources.generated.resources.builder_check_unreadable
import opson.libraries.resources.generated.resources.builder_failed
import opson.libraries.resources.generated.resources.builder_offline
import opson.libraries.resources.generated.resources.builder_verify_again
import opson.libraries.resources.generated.resources.builder_verify_barcode
import opson.libraries.resources.generated.resources.builder_verify_brand
import opson.libraries.resources.generated.resources.builder_verify_confirmed
import opson.libraries.resources.generated.resources.builder_verify_field_barcode
import opson.libraries.resources.generated.resources.builder_verify_field_calories
import opson.libraries.resources.generated.resources.builder_verify_field_ingredients
import opson.libraries.resources.generated.resources.builder_verify_field_macros
import opson.libraries.resources.generated.resources.builder_verify_field_name
import opson.libraries.resources.generated.resources.builder_verify_field_serving
import opson.libraries.resources.generated.resources.builder_verify_front_hint
import opson.libraries.resources.generated.resources.builder_verify_front_title
import opson.libraries.resources.generated.resources.builder_verify_in_review
import opson.libraries.resources.generated.resources.builder_verify_intro
import opson.libraries.resources.generated.resources.builder_verify_label_hint
import opson.libraries.resources.generated.resources.builder_verify_label_title
import opson.libraries.resources.generated.resources.builder_verify_matches_body
import opson.libraries.resources.generated.resources.builder_verify_matches_title
import opson.libraries.resources.generated.resources.builder_verify_missing
import opson.libraries.resources.generated.resources.builder_verify_name
import opson.libraries.resources.generated.resources.builder_verify_note
import opson.libraries.resources.generated.resources.builder_verify_note_hint
import opson.libraries.resources.generated.resources.builder_verify_photos_kept
import opson.libraries.resources.generated.resources.builder_verify_send
import opson.libraries.resources.generated.resources.builder_verify_send_review
import opson.libraries.resources.generated.resources.builder_verify_serving_grams
import opson.libraries.resources.generated.resources.builder_verify_skip_front
import opson.libraries.resources.generated.resources.builder_verify_something_wrong
import opson.libraries.resources.generated.resources.builder_verify_something_wrong_sub
import opson.libraries.resources.generated.resources.builder_verify_title
import opson.libraries.resources.generated.resources.builder_verify_values_title
import opson.libraries.resources.generated.resources.builder_verify_what
import opson.libraries.resources.generated.resources.builder_verify_wrong
import opson.libraries.resources.generated.resources.food_check_verified
import opson.libraries.resources.generated.resources.food_report_basis_weight
import opson.libraries.resources.generated.resources.food_report_calories
import opson.libraries.resources.generated.resources.food_report_camera_off
import opson.libraries.resources.generated.resources.food_report_carbs
import opson.libraries.resources.generated.resources.food_report_fat
import opson.libraries.resources.generated.resources.food_report_protein
import opson.libraries.resources.generated.resources.food_report_serving
import opson.libraries.resources.generated.resources.food_report_unit_g
import opson.libraries.resources.generated.resources.food_report_values_for
import opson.libraries.resources.generated.resources.food_report_values_for_100
import opson.libraries.resources.generated.resources.logger_amount_kcal
import opson.libraries.resources.generated.resources.submit_done
import opson.libraries.resources.generated.resources.submit_label_cd
import opson.libraries.resources.generated.resources.submit_open_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Verify this food, stateless: the front of the pack and its label, then the pack against what
 * Opson has, with "Something was wrong" to say which parts were wrong or missing and what the pack
 * says instead. Numbers show only where the mode shows them; otherwise a difference is said in
 * words. The entry point puts it in a `BottomSheet` with `scrollableContent`.
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
        SheetHeader(title = stringResource(Res.string.builder_verify_title), overline = state.name, onClose = onClose)
        when (val stage = state.stage) {
            is CheckStage.Photos -> Photos(state, stage.next, onAction)
            CheckStage.Reading -> Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Body(stringResource(Res.string.builder_check_reading))
            }
            CheckStage.Unreadable -> {
                Body(stringResource(Res.string.builder_check_unreadable))
                FoodRowsCard { LabelSlot(state, active = true, onAction) }
            }
            is CheckStage.Review -> Review(state, stage, onAction)
            is CheckStage.Done -> {
                if (stage.outcome == CheckDone.Confirmed) VerifiedMark(text = stringResource(Res.string.food_check_verified), iconSize = 18.dp)
                Body(stringResource(stage.outcome.message))
                ButtonPrimary(onClick = { onAction(CheckFoodAction.DoneTapped) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.submit_done))
                }
            }
        }
        state.problem?.let { NoteBanner(stringResource(it.message)) }
    }
}

/** The front first (it can be skipped), then the label; the one up next has the buttons. */
@Composable
private fun Photos(state: CheckFoodState, next: PackSide, onAction: (CheckFoodAction) -> Unit) {
    Body(stringResource(Res.string.builder_verify_intro))
    FoodRowsCard {
        PackPhotoSlot(
            photo = state.front,
            title = stringResource(Res.string.builder_verify_front_title),
            hint = stringResource(Res.string.builder_verify_front_hint),
            description = stringResource(Res.string.builder_verify_front_title),
            onTake = takeIf(state, next == PackSide.Front, onAction),
            onChoose = { onAction(CheckFoodAction.ChooseFromLibraryTapped) }.takeIf { next == PackSide.Front },
            onRemove = null,
        )
        HorizontalDivider()
        LabelSlot(state, active = next == PackSide.Label, onAction)
    }
    if (next == PackSide.Front) {
        ButtonGhost(onClick = { onAction(CheckFoodAction.SkipFrontTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.builder_verify_skip_front))
        }
    }
    if (state.cameraOff) {
        Body(stringResource(Res.string.food_report_camera_off))
        ButtonGhost(onClick = { onAction(CheckFoodAction.OpenSettingsTapped) }) { Text(stringResource(Res.string.submit_open_settings)) }
    }
    Text(stringResource(Res.string.builder_verify_photos_kept), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
}

@Composable
private fun LabelSlot(state: CheckFoodState, active: Boolean, onAction: (CheckFoodAction) -> Unit) {
    PackPhotoSlot(
        photo = state.label,
        title = stringResource(Res.string.builder_verify_label_title),
        hint = stringResource(Res.string.builder_verify_label_hint),
        description = stringResource(Res.string.submit_label_cd),
        onTake = takeIf(state, active, onAction),
        onChoose = { onAction(CheckFoodAction.ChooseFromLibraryTapped) }.takeIf { active },
        onRemove = null,
    )
}

private fun takeIf(state: CheckFoodState, active: Boolean, onAction: (CheckFoodAction) -> Unit): (() -> Unit)? =
    { onAction(CheckFoodAction.TakePhotoTapped) }.takeIf { active && state.canTakePhoto && !state.cameraOff }

@Composable
private fun Review(state: CheckFoodState, stage: CheckStage.Review, onAction: (CheckFoodAction) -> Unit) {
    if (stage.matches) {
        Title(stringResource(Res.string.builder_verify_matches_title))
        Body(stringResource(Res.string.builder_verify_matches_body))
    } else {
        Title(stringResource(Res.string.builder_check_differs_title))
        if (stage.rows.isEmpty()) Body(stringResource(Res.string.builder_check_differs_hidden)) else Rows(stage)
    }
    FoodRowsCard {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.builder_verify_something_wrong), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                Text(stringResource(Res.string.builder_verify_something_wrong_sub), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            AppSwitch(checked = stage.form != null, onCheckedChange = { onAction(CheckFoodAction.SomethingWrongChanged(it)) }, enabled = !state.busy)
        }
    }
    stage.form?.let { Form(it, onAction) }
    ButtonPrimary(onClick = { onAction(CheckFoodAction.SendTapped) }, enabled = stage.canSend, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (stage.form != null) Res.string.builder_verify_send_review else Res.string.builder_verify_send))
    }
    ButtonGhost(onClick = { onAction(CheckFoodAction.RetakeTapped) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.builder_verify_again))
    }
}

/** Which parts were off, the right values for them, and a note. */
@Composable
private fun Form(form: VerifyFormUi, onAction: (CheckFoodAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(stringResource(Res.string.builder_verify_what))
        FoodRowsCard {
            form.fields.forEachIndexed { index, row ->
                IssueRow(row, onAction)
                if (index < form.fields.lastIndex) HorizontalDivider()
            }
        }
    }
    if (form.values.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            SectionLabel(stringResource(Res.string.builder_verify_values_title))
            if (form.numbers && form.values.any { it.key in PER_BASIS }) {
                Text(
                    form.basis?.let { stringResource(Res.string.food_report_values_for, stringResource(Res.string.food_report_basis_weight, it.amount, it.grams ?: 0L)) }
                        ?: stringResource(Res.string.food_report_values_for_100),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
            form.values.forEach { input -> Input(input, onAction) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(stringResource(Res.string.builder_verify_note))
        TextArea(value = form.note, onValueChange = { onAction(CheckFoodAction.NoteTyped(it)) }, placeholder = stringResource(Res.string.builder_verify_note_hint))
    }
}

/** "Calories  [Wrong] [Missing]": one of the two, or neither. */
@Composable
private fun IssueRow(row: FieldIssueUi, onAction: (CheckFoodAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Text(stringResource(row.field.label), typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        PillChip(
            label = stringResource(Res.string.builder_verify_wrong),
            selected = row.issue == FieldIssue.Wrong,
            onClick = { onAction(CheckFoodAction.IssuePicked(row.field, FieldIssue.Wrong)) },
            size = PillChipSize.Compact,
            role = Role.Checkbox,
        )
        PillChip(
            label = stringResource(Res.string.builder_verify_missing),
            selected = row.issue == FieldIssue.Missing,
            onClick = { onAction(CheckFoodAction.IssuePicked(row.field, FieldIssue.Missing)) },
            size = PillChipSize.Compact,
            role = Role.Checkbox,
        )
    }
}

@Composable
private fun Input(input: VerifyInputUi, onAction: (CheckFoodAction) -> Unit) {
    val label = stringResource(inputLabel(input.key))
    val typed: (String) -> Unit = { onAction(CheckFoodAction.ValueTyped(input.key, it)) }
    if (input.key in VerifyForm.NUMBERS) {
        val unit = stringResource(if (input.key == VerifyForm.KCAL) Res.string.logger_amount_kcal else Res.string.food_report_unit_g)
        NumberField(value = input.text, onValueChange = typed, label = label, unit = unit, imeAction = ImeAction.Next)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            Text(label, typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
            SingleLineField(value = input.text, onValueChange = typed, imeAction = ImeAction.Next)
        }
    }
}

@Composable
private fun Rows(stage: CheckStage.Review) {
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
private fun Title(text: String) = Text(text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)

@Composable
private fun Body(text: String) = Text(text, typography = AppTheme.type.body, color = AppTheme.colors.muted)

/** The inputs whose numbers are for the label's serving: the line above says which. */
private val PER_BASIS = setOf(VerifyForm.KCAL, VerifyForm.PROTEIN, VerifyForm.CARBS, VerifyForm.FAT)

private fun inputLabel(key: String): StringResource = when (key) {
    VerifyForm.NAME -> Res.string.builder_verify_name
    VerifyForm.BRAND -> Res.string.builder_verify_brand
    VerifyForm.BARCODE -> Res.string.builder_verify_barcode
    VerifyForm.SERVING -> Res.string.food_report_serving
    VerifyForm.SERVING_GRAMS -> Res.string.builder_verify_serving_grams
    VerifyForm.KCAL -> Res.string.food_report_calories
    VerifyForm.PROTEIN -> Res.string.food_report_protein
    VerifyForm.CARBS -> Res.string.food_report_carbs
    else -> Res.string.food_report_fat
}

private val VerifyField.label: StringResource
    get() = when (this) {
        VerifyField.Calories -> Res.string.builder_verify_field_calories
        VerifyField.Macros -> Res.string.builder_verify_field_macros
        VerifyField.Serving -> Res.string.builder_verify_field_serving
        VerifyField.NameBrand -> Res.string.builder_verify_field_name
        VerifyField.Ingredients -> Res.string.builder_verify_field_ingredients
        VerifyField.Barcode -> Res.string.builder_verify_field_barcode
    }

private val LabelNutrient.label: StringResource
    get() = when (this) {
        LabelNutrient.Energy -> Res.string.food_report_calories
        LabelNutrient.Protein -> Res.string.food_report_protein
        LabelNutrient.Carbs -> Res.string.food_report_carbs
        else -> Res.string.food_report_fat
    }

private val CheckDone.message: StringResource
    get() = when (this) {
        CheckDone.Confirmed -> Res.string.builder_verify_confirmed
        CheckDone.Unconfirmed -> Res.string.builder_check_unconfirmed
        CheckDone.InReview -> Res.string.builder_verify_in_review
        CheckDone.Corrected -> Res.string.builder_check_corrected
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
