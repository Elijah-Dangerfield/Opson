package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.input.ImeAction
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.ui.components.CircularProgressIndicator
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.PhotoThumbnail
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.submit_add
import opson.libraries.resources.generated.resources.submit_after
import opson.libraries.resources.generated.resources.submit_after_save
import opson.libraries.resources.generated.resources.submit_allow_camera
import opson.libraries.resources.generated.resources.submit_camera_body
import opson.libraries.resources.generated.resources.submit_camera_body_ask
import opson.libraries.resources.generated.resources.submit_camera_title
import opson.libraries.resources.generated.resources.submit_check_energy
import opson.libraries.resources.generated.resources.submit_check_impossible
import opson.libraries.resources.generated.resources.submit_check_kj
import opson.libraries.resources.generated.resources.submit_check_serving_nutrient
import opson.libraries.resources.generated.resources.submit_check_serving_size
import opson.libraries.resources.generated.resources.submit_check_sodium
import opson.libraries.resources.generated.resources.submit_check_this
import opson.libraries.resources.generated.resources.submit_choose
import opson.libraries.resources.generated.resources.submit_done
import opson.libraries.resources.generated.resources.submit_failed_body
import opson.libraries.resources.generated.resources.submit_failed_offline
import opson.libraries.resources.generated.resources.submit_failed_title
import opson.libraries.resources.generated.resources.submit_field_barcode
import opson.libraries.resources.generated.resources.submit_field_barcode_optional
import opson.libraries.resources.generated.resources.submit_field_brand
import opson.libraries.resources.generated.resources.submit_field_name
import opson.libraries.resources.generated.resources.submit_field_per_container
import opson.libraries.resources.generated.resources.submit_field_serving
import opson.libraries.resources.generated.resources.submit_front_cd
import opson.libraries.resources.generated.resources.submit_front_hint
import opson.libraries.resources.generated.resources.submit_front_title
import opson.libraries.resources.generated.resources.submit_hidden_adds_up
import opson.libraries.resources.generated.resources.submit_hidden_incomplete
import opson.libraries.resources.generated.resources.submit_hidden_off
import opson.libraries.resources.generated.resources.submit_hidden_title
import opson.libraries.resources.generated.resources.submit_intro
import opson.libraries.resources.generated.resources.submit_intro_save
import opson.libraries.resources.generated.resources.submit_keep
import opson.libraries.resources.generated.resources.submit_kept
import opson.libraries.resources.generated.resources.submit_label_cd
import opson.libraries.resources.generated.resources.submit_label_hint
import opson.libraries.resources.generated.resources.submit_label_title
import opson.libraries.resources.generated.resources.submit_list_and
import opson.libraries.resources.generated.resources.submit_missing
import opson.libraries.resources.generated.resources.submit_missing_save
import opson.libraries.resources.generated.resources.submit_more
import opson.libraries.resources.generated.resources.submit_needed
import opson.libraries.resources.generated.resources.submit_no_label_body
import opson.libraries.resources.generated.resources.submit_no_label_title
import opson.libraries.resources.generated.resources.submit_notice_by_hand
import opson.libraries.resources.generated.resources.submit_notice_unavailable
import opson.libraries.resources.generated.resources.submit_open_settings
import opson.libraries.resources.generated.resources.submit_overline
import opson.libraries.resources.generated.resources.submit_overline_foods
import opson.libraries.resources.generated.resources.submit_per_serving
import opson.libraries.resources.generated.resources.submit_photo_failed
import opson.libraries.resources.generated.resources.submit_privacy
import opson.libraries.resources.generated.resources.submit_read
import opson.libraries.resources.generated.resources.submit_reading
import opson.libraries.resources.generated.resources.submit_reading_body
import opson.libraries.resources.generated.resources.submit_retake
import opson.libraries.resources.generated.resources.submit_retake_label
import opson.libraries.resources.generated.resources.submit_review_body
import opson.libraries.resources.generated.resources.submit_review_title
import opson.libraries.resources.generated.resources.submit_save
import opson.libraries.resources.generated.resources.submit_saving
import opson.libraries.resources.generated.resources.submit_saving_save
import opson.libraries.resources.generated.resources.submit_send_failed_body
import opson.libraries.resources.generated.resources.submit_send_failed_body_save
import opson.libraries.resources.generated.resources.submit_send_failed_offline
import opson.libraries.resources.generated.resources.submit_send_failed_offline_save
import opson.libraries.resources.generated.resources.submit_send_failed_title
import opson.libraries.resources.generated.resources.submit_send_failed_too_many
import opson.libraries.resources.generated.resources.submit_send_failed_too_many_save
import opson.libraries.resources.generated.resources.submit_take
import opson.libraries.resources.generated.resources.submit_title
import opson.libraries.resources.generated.resources.submit_too_many_body
import opson.libraries.resources.generated.resources.submit_too_many_title
import opson.libraries.resources.generated.resources.submit_try_again
import opson.libraries.resources.generated.resources.submit_type_it_in
import opson.libraries.resources.generated.resources.submit_use
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Add this food", stateless: the two photos, the reading, the form and the
 * calm ends. The camera and the picker are launched by the entry point from
 * the view model's events, so nothing here touches a device.
 *
 * The form's numbers only draw when [SubmitForm.numbers] is set, which the
 * builder decides from the mode; otherwise [SubmitForm.hidden] says in words
 * whether the label adds up.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SubmitFoodScreen(
    state: SubmitFoodState,
    onAction: (SubmitFoodAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.stage !is SubmitStage.Photos) { onAction(SubmitFoodAction.BackTapped) }
    Screen(
        modifier = modifier,
        containerColor = AppTheme.colors.bg.color,
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            SheetHeader(
                title = stringResource(Res.string.submit_title),
                overline = state.slot?.let { stringResource(Res.string.submit_overline, stringResource(it.sentenceName)) }
                    ?: stringResource(Res.string.submit_overline_foods),
                onBack = { onAction(SubmitFoodAction.BackTapped) },
            )
            when (val stage = state.stage) {
                is SubmitStage.Photos -> PhotosBody(state, stage, onAction)
                SubmitStage.Reading -> Waiting(state, Res.string.submit_reading, Res.string.submit_reading_body)
                is SubmitStage.Review -> ReviewBody(state, stage.form, onAction)
                SubmitStage.Saving -> Waiting(state, if (state.slot == null) Res.string.submit_saving_save else Res.string.submit_saving, null)
                SubmitStage.NoLabel -> Calm(
                    state, Res.string.submit_no_label_title, Res.string.submit_no_label_body,
                    primary = Res.string.submit_retake_label to SubmitFoodAction.RetakeLabelTapped,
                    secondary = Res.string.submit_type_it_in to SubmitFoodAction.TypeItInTapped,
                    onAction = onAction,
                )
                SubmitStage.TooMany -> Calm(
                    state, Res.string.submit_too_many_title, Res.string.submit_too_many_body,
                    primary = Res.string.submit_type_it_in to SubmitFoodAction.TypeItInTapped,
                    secondary = Res.string.submit_try_again to SubmitFoodAction.TryAgainTapped,
                    onAction = onAction,
                )
                is SubmitStage.ReadFailed -> Calm(
                    state, Res.string.submit_failed_title,
                    if (stage.offline) Res.string.submit_failed_offline else Res.string.submit_failed_body,
                    primary = Res.string.submit_try_again to SubmitFoodAction.TryAgainTapped,
                    secondary = Res.string.submit_type_it_in to SubmitFoodAction.TypeItInTapped,
                    onAction = onAction,
                )
                is SubmitStage.CameraOff -> Calm(
                    state, Res.string.submit_camera_title,
                    if (stage.canAskAgain) Res.string.submit_camera_body_ask else Res.string.submit_camera_body,
                    primary = if (stage.canAskAgain) {
                        Res.string.submit_allow_camera to SubmitFoodAction.AllowCameraTapped
                    } else {
                        Res.string.submit_open_settings to SubmitFoodAction.OpenSettingsTapped
                    },
                    secondary = Res.string.submit_choose to SubmitFoodAction.ChooseFromLibraryTapped(
                        if (state.front == null) PackSide.Front else PackSide.Label,
                    ),
                    onAction = onAction,
                )
                is SubmitStage.SendFailed -> Calm(
                    state, Res.string.submit_send_failed_title,
                    sendFailedBody(stage, logged = state.slot != null),
                    primary = Res.string.submit_try_again to SubmitFoodAction.TryAgainTapped,
                    secondary = Res.string.submit_done to SubmitFoodAction.DoneTapped,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PhotosBody(state: SubmitFoodState, stage: SubmitStage.Photos, onAction: (SubmitFoodAction) -> Unit) {
    Text(
        stringResource(if (state.slot == null) Res.string.submit_intro_save else Res.string.submit_intro),
        typography = AppTheme.type.body,
        color = AppTheme.colors.ink,
    )
    PhotoSlot(PackSide.Front, state, onAction)
    PhotoSlot(PackSide.Label, state, onAction)
    if (state.photoFailed != null) NoteBanner(stringResource(Res.string.submit_photo_failed))
    ButtonPrimary(
        onClick = { onAction(SubmitFoodAction.ReadTapped) },
        enabled = stage.canRead,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(Res.string.submit_read)) }
    Text(stringResource(Res.string.submit_privacy), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

/** One of the two photos: what it should show, the thumbnail once taken, and the ways to get it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoSlot(side: PackSide, state: SubmitFoodState, onAction: (SubmitFoodAction) -> Unit) {
    val photo = if (side == PackSide.Front) state.front else state.label
    FoodRowsCard {
        Row(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            verticalAlignment = Alignment.Top,
        ) {
            PhotoThumbnail(
                jpeg = photo?.jpeg,
                contentDescription = stringResource(if (side == PackSide.Front) Res.string.submit_front_cd else Res.string.submit_label_cd),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                Text(
                    stringResource(if (side == PackSide.Front) Res.string.submit_front_title else Res.string.submit_label_title),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                )
                Text(
                    stringResource(if (side == PackSide.Front) Res.string.submit_front_hint else Res.string.submit_label_hint),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                    if (state.canTakePhoto) {
                        ButtonPrimary(
                            onClick = { onAction(SubmitFoodAction.TakePhotoTapped(side)) },
                            icon = AppIcons.PhotoCamera,
                            size = ButtonSize.Small,
                            style = if (photo == null) ButtonStyle.Filled else ButtonStyle.Tonal,
                        ) { Text(stringResource(if (photo == null) Res.string.submit_take else Res.string.submit_retake)) }
                    }
                    ButtonSecondary(
                        onClick = { onAction(SubmitFoodAction.ChooseFromLibraryTapped(side)) },
                        icon = AppIcons.PhotoLibrary,
                        size = ButtonSize.Small,
                    ) { Text(stringResource(Res.string.submit_choose)) }
                }
            }
        }
    }
}

@Composable
private fun Photos(state: SubmitFoodState) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        PhotoThumbnail(jpeg = state.front?.jpeg, contentDescription = stringResource(Res.string.submit_front_cd))
        PhotoThumbnail(jpeg = state.label?.jpeg, contentDescription = stringResource(Res.string.submit_label_cd))
    }
}

@Composable
private fun Waiting(state: SubmitFoodState, title: StringResource, body: StringResource?) {
    Photos(state)
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Column {
            Text(stringResource(title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
            body?.let { Text(stringResource(it), typography = AppTheme.type.secondary, color = AppTheme.colors.muted) }
        }
    }
}

@Composable
private fun Calm(
    state: SubmitFoodState,
    title: StringResource,
    body: StringResource,
    primary: Pair<StringResource, SubmitFoodAction>,
    secondary: Pair<StringResource, SubmitFoodAction>,
    onAction: (SubmitFoodAction) -> Unit,
) {
    if (state.front != null || state.label != null) Photos(state)
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Text(stringResource(title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        Text(stringResource(body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
    }
    ButtonPrimary(onClick = { onAction(primary.second) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(primary.first)) }
    ButtonGhost(onClick = { onAction(secondary.second) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(secondary.first)) }
}

@Composable
private fun ReviewBody(state: SubmitFoodState, form: SubmitForm, onAction: (SubmitFoodAction) -> Unit) {
    Photos(state)
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Text(stringResource(Res.string.submit_review_title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        Text(stringResource(Res.string.submit_review_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
    }
    form.notice?.let { notice ->
        NoteBanner(
            stringResource(if (notice == ReviewNotice.Unavailable) Res.string.submit_notice_unavailable else Res.string.submit_notice_by_hand),
        )
    }
    FoodRowsCard {
        Column(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            TextEntry(form.name, Res.string.submit_field_name, onAction)
            TextEntry(form.brand, Res.string.submit_field_brand, onAction)
            if (form.barcodeLocked) {
                Labelled(form.barcode, stringResource(Res.string.submit_field_barcode)) {
                    Text(form.barcode.text, typography = AppTheme.type.lead, color = AppTheme.colors.ink)
                }
            } else {
                Labelled(form.barcode, stringResource(Res.string.submit_field_barcode_optional)) {
                    NumberField(
                        value = form.barcode.text,
                        onValueChange = { onAction(SubmitFoodAction.FieldTyped(form.barcode.key, it)) },
                        allowDecimal = false,
                        imeAction = ImeAction.Next,
                    )
                }
            }
            Labelled(form.servingSize, stringResource(Res.string.submit_field_serving)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(
                        value = form.servingSize.text,
                        onValueChange = { onAction(SubmitFoodAction.FieldTyped(form.servingSize.key, it)) },
                        imeAction = ImeAction.Next,
                        modifier = Modifier.weight(1f),
                    )
                    SegmentedControl(
                        options = ServingUnit.entries,
                        selected = form.servingUnit,
                        onSelect = { onAction(SubmitFoodAction.ServingUnitPicked(it)) },
                        label = { it.wire },
                        modifier = Modifier.weight(1f),
                    )
                }
                form.servingLabel?.let { Text(it, typography = AppTheme.type.secondary, color = AppTheme.colors.muted) }
            }
            Labelled(form.servingsPerContainer, stringResource(Res.string.submit_field_per_container)) {
                NumberField(
                    value = form.servingsPerContainer.text,
                    onValueChange = { onAction(SubmitFoodAction.FieldTyped(form.servingsPerContainer.key, it)) },
                    imeAction = ImeAction.Next,
                )
            }
        }
    }
    form.numbers?.let { Numbers(it, form.checks, onAction) }
    form.hidden?.let { Hidden(it) }
    val slot = state.slot
    ButtonPrimary(
        onClick = { onAction(SubmitFoodAction.SubmitTapped) },
        enabled = form.canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (slot == null) stringResource(Res.string.submit_save) else stringResource(Res.string.submit_add, stringResource(slot.sentenceName))) }
    if (form.missing.isNotEmpty()) {
        Text(
            stringResource(if (slot == null) Res.string.submit_missing_save else Res.string.submit_missing, fieldList(form.missing)),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
    Text(
        stringResource(if (slot == null) Res.string.submit_after_save else Res.string.submit_after),
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.muted,
    )
}

/** Why the review copy did not go, said for a food that was logged or only saved. */
private fun sendFailedBody(stage: SubmitStage.SendFailed, logged: Boolean): StringResource = when {
    stage.tooMany -> if (logged) Res.string.submit_send_failed_too_many else Res.string.submit_send_failed_too_many_save
    stage.offline -> if (logged) Res.string.submit_send_failed_offline else Res.string.submit_send_failed_offline_save
    else -> if (logged) Res.string.submit_send_failed_body else Res.string.submit_send_failed_body_save
}

@Composable
private fun Numbers(numbers: FormNumbers, checks: List<CheckRow>, onAction: (SubmitFoodAction) -> Unit) {
    FoodRowsCard {
        Column(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            SectionLabel(stringResource(Res.string.submit_per_serving))
            checks.forEach { CheckLine(it, onAction) }
            numbers.main.forEach { NutrientEntry(it, onAction) }
            if (numbers.more.isNotEmpty()) {
                SectionLabel(stringResource(Res.string.submit_more))
                numbers.more.forEach { NutrientEntry(it, onAction) }
            }
        }
    }
}

/** A failing check, and the two ways out of it: the figure the numbers point to, or the label's own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckLine(row: CheckRow, onAction: (SubmitFoodAction) -> Unit) {
    val field = row.fields.first()
    val text = when (row.code) {
        LabelCheckCode.EnergyMacrosMismatch -> stringResource(Res.string.submit_check_energy, row.difference.orEmpty())
        LabelCheckCode.EnergyLooksLikeKj -> stringResource(Res.string.submit_check_kj)
        LabelCheckCode.ServingMismatch -> if (field == SubmitFoodForm.SERVING_SIZE) {
            stringResource(Res.string.submit_check_serving_size)
        } else {
            stringResource(Res.string.submit_check_serving_nutrient, fieldName(field))
        }
        LabelCheckCode.SodiumUnit -> stringResource(Res.string.submit_check_sodium)
        LabelCheckCode.ImpossibleValue -> stringResource(Res.string.submit_check_impossible, fieldList(row.fields))
        LabelCheckCode.MissingRequired, LabelCheckCode.EnergyUnitsDisagree -> return
    }
    NoteBanner(text)
    if (row.kept) {
        Text(stringResource(Res.string.submit_kept), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    } else if (row.fix != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            ButtonPrimary(
                onClick = { onAction(SubmitFoodAction.FixTapped(row.code, field)) },
                size = ButtonSize.Small,
                style = ButtonStyle.Tonal,
            ) { Text(stringResource(Res.string.submit_use, row.fix)) }
            row.keep?.let { keep ->
                ButtonGhost(onClick = { onAction(SubmitFoodAction.KeepTapped(row.code, field)) }, size = ButtonSize.Small) {
                    Text(stringResource(Res.string.submit_keep, keep))
                }
            }
        }
    }
}

@Composable
private fun Hidden(hidden: HiddenNumbers) {
    FoodRowsCard {
        Column(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            SectionLabel(stringResource(Res.string.submit_hidden_title))
            val body = when {
                hidden.incomplete -> Res.string.submit_hidden_incomplete
                hidden.addsUp -> Res.string.submit_hidden_adds_up
                else -> Res.string.submit_hidden_off
            }
            Text(stringResource(body), typography = AppTheme.type.body, color = AppTheme.colors.ink)
        }
    }
}

@Composable
private fun TextEntry(field: FormField, label: StringResource, onAction: (SubmitFoodAction) -> Unit) {
    Labelled(field, stringResource(label)) {
        SingleLineField(
            value = field.text,
            onValueChange = { onAction(SubmitFoodAction.FieldTyped(field.key, it)) },
            imeAction = ImeAction.Next,
        )
    }
}

@Composable
private fun NutrientEntry(entry: NutrientField, onAction: (SubmitFoodAction) -> Unit) {
    Labelled(entry.field, fieldName(entry.nutrient.wire)) {
        NumberField(
            value = entry.field.text,
            onValueChange = { onAction(SubmitFoodAction.FieldTyped(entry.field.key, it)) },
            unit = entry.unit,
            imeAction = ImeAction.Next,
        )
    }
}

/** A field's label, with "Check this" when the model was unsure and "Needed" when it is required and empty. */
@Composable
private fun Labelled(field: FormField, label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            Text(
                label,
                typography = AppTheme.type.secondarySmallStrong,
                color = if (field.flagged || field.missing) AppTheme.colors.ink else AppTheme.colors.muted,
                modifier = Modifier.weight(1f),
            )
            when {
                field.missing -> Text(stringResource(Res.string.submit_needed), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.accent)
                field.needsLook || field.flagged ->
                    Text(stringResource(Res.string.submit_check_this), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.accent)
            }
        }
        content()
    }
}

@Composable
private fun fieldName(key: String): String = when (key) {
    SubmitFoodForm.NAME -> stringResource(Res.string.submit_field_name)
    SubmitFoodForm.BRAND -> stringResource(Res.string.submit_field_brand)
    SubmitFoodForm.BARCODE -> stringResource(Res.string.submit_field_barcode)
    SubmitFoodForm.SERVING_SIZE -> stringResource(Res.string.submit_field_serving)
    SubmitFoodForm.SERVINGS_PER_CONTAINER -> stringResource(Res.string.submit_field_per_container)
    else -> LabelNutrient.fromWire(key)?.let { stringResource(it.title) } ?: key
}

/** "name, serving size and calories", in the reading order of the form. */
@Composable
private fun fieldList(keys: List<String>): String {
    val names = keys.map { fieldName(it).lowercase() }
    return when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> stringResource(Res.string.submit_list_and, names.dropLast(1).joinToString(", "), names.last())
    }
}
