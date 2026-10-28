package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.FoodRowsNote
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.CircularProgressIndicator
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.PhotoThumbnail
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.TapField
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.SearchField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.libraries.ui.system.clearFocusOnScroll
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_search_clear_cd
import opson.libraries.resources.generated.resources.photo_about
import opson.libraries.resources.generated.resources.photo_add_missed
import opson.libraries.resources.generated.resources.photo_add_title
import opson.libraries.resources.generated.resources.photo_allow_camera
import opson.libraries.resources.generated.resources.photo_amount_cd
import opson.libraries.resources.generated.resources.photo_another
import opson.libraries.resources.generated.resources.photo_back_to_search
import opson.libraries.resources.generated.resources.photo_banner
import opson.libraries.resources.generated.resources.photo_banner_note
import opson.libraries.resources.generated.resources.photo_camera_body
import opson.libraries.resources.generated.resources.photo_camera_body_ask
import opson.libraries.resources.generated.resources.photo_camera_title
import opson.libraries.resources.generated.resources.photo_choose
import opson.libraries.resources.generated.resources.photo_describe_instead
import opson.libraries.resources.generated.resources.photo_empty_body
import opson.libraries.resources.generated.resources.photo_empty_title
import opson.libraries.resources.generated.resources.photo_estimating
import opson.libraries.resources.generated.resources.photo_failed_body
import opson.libraries.resources.generated.resources.photo_failed_offline
import opson.libraries.resources.generated.resources.photo_failed_title
import opson.libraries.resources.generated.resources.photo_food_cd
import opson.libraries.resources.generated.resources.photo_footer_about
import opson.libraries.resources.generated.resources.photo_footer_count
import opson.libraries.resources.generated.resources.photo_list_empty
import opson.libraries.resources.generated.resources.photo_list_title
import opson.libraries.resources.generated.resources.photo_not_matched
import opson.libraries.resources.generated.resources.photo_note_label
import opson.libraries.resources.generated.resources.photo_note_placeholder
import opson.libraries.resources.generated.resources.photo_open_settings
import opson.libraries.resources.generated.resources.photo_overline
import opson.libraries.resources.generated.resources.photo_privacy
import opson.libraries.resources.generated.resources.photo_remove_cd
import opson.libraries.resources.generated.resources.photo_start_body
import opson.libraries.resources.generated.resources.photo_swap_none
import opson.libraries.resources.generated.resources.photo_swap_placeholder
import opson.libraries.resources.generated.resources.photo_swap_searching
import opson.libraries.resources.generated.resources.photo_swap_title
import opson.libraries.resources.generated.resources.photo_take
import opson.libraries.resources.generated.resources.photo_tap_to_edit
import opson.libraries.resources.generated.resources.photo_thumbnail_cd
import opson.libraries.resources.generated.resources.photo_title
import opson.libraries.resources.generated.resources.photo_too_many_body
import opson.libraries.resources.generated.resources.photo_too_many_title
import opson.libraries.resources.generated.resources.photo_try_again
import opson.libraries.resources.generated.resources.photo_unavailable_body
import opson.libraries.resources.generated.resources.photo_unavailable_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The photo sheet (3.7), stateless. The entry point wraps it in a full-height
 * `BottomSheet` over the logger; the camera and the picker are launched by
 * the entry point from this sheet's events, so nothing here touches a device.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PhotoEstimateSheetContent(
    state: PhotoEstimateState,
    onAction: (PhotoEstimateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.stage is PhotoStage.Swap) { onAction(PhotoEstimateAction.BackTapped) }
    Column(modifier = modifier.fillMaxSize()) {
        when (val stage = state.stage) {
            is PhotoStage.Swap -> SwapPane(stage, onAction)
            is PhotoStage.Review -> {
                Page(state, onAction, Modifier.weight(1f)) { ReviewBody(state, stage, onAction) }
                stage.footer?.let { Footer(it, state.slot, onAction) }
            }
            else -> Page(state, onAction, Modifier.weight(1f)) { StageBody(state, stage, onAction) }
        }
    }
}

@Composable
private fun Page(
    state: PhotoEstimateState,
    onAction: (PhotoEstimateAction) -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        // The design's Plus badge sits on this header's right; Plus has no price yet, so it is not drawn.
        SheetHeader(
            title = stringResource(Res.string.photo_title),
            overline = stringResource(Res.string.photo_overline, stringResource(state.slot.sentenceName)),
            onBack = { onAction(PhotoEstimateAction.BackTapped) },
        )
        content()
    }
}

@Composable
private fun StageBody(state: PhotoEstimateState, stage: PhotoStage, onAction: (PhotoEstimateAction) -> Unit) {
    when (stage) {
        PhotoStage.Start -> StartBody(state, onAction)
        PhotoStage.Estimating -> {
            PhotoAndNote(state)
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(Res.string.photo_estimating), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
            }
        }
        PhotoStage.NothingFound -> Calm(
            state,
            stringResource(Res.string.photo_empty_title),
            stringResource(Res.string.photo_empty_body),
            primary = stringResource(Res.string.photo_another) to PhotoEstimateAction.AnotherPhotoTapped,
            secondary = stringResource(Res.string.photo_describe_instead) to PhotoEstimateAction.DescribeInsteadTapped,
            onAction = onAction,
        )
        PhotoStage.Unavailable -> Calm(
            state,
            stringResource(Res.string.photo_unavailable_title),
            stringResource(Res.string.photo_unavailable_body),
            primary = stringResource(Res.string.photo_describe_instead) to PhotoEstimateAction.DescribeInsteadTapped,
            secondary = stringResource(Res.string.photo_back_to_search) to PhotoEstimateAction.BackTapped,
            onAction = onAction,
        )
        PhotoStage.TooMany -> Calm(
            state,
            stringResource(Res.string.photo_too_many_title),
            stringResource(Res.string.photo_too_many_body),
            primary = stringResource(Res.string.photo_describe_instead) to PhotoEstimateAction.DescribeInsteadTapped,
            secondary = stringResource(Res.string.photo_back_to_search) to PhotoEstimateAction.BackTapped,
            onAction = onAction,
        )
        is PhotoStage.Failed -> Calm(
            state,
            stringResource(Res.string.photo_failed_title),
            stringResource(if (stage.offline) Res.string.photo_failed_offline else Res.string.photo_failed_body),
            primary = stringResource(Res.string.photo_try_again) to PhotoEstimateAction.TryAgainTapped,
            secondary = stringResource(Res.string.photo_describe_instead) to PhotoEstimateAction.DescribeInsteadTapped,
            onAction = onAction,
        )
        is PhotoStage.CameraOff -> Calm(
            state,
            stringResource(Res.string.photo_camera_title),
            stringResource(if (stage.canAskAgain) Res.string.photo_camera_body_ask else Res.string.photo_camera_body),
            primary = if (stage.canAskAgain) {
                stringResource(Res.string.photo_allow_camera) to PhotoEstimateAction.AllowCameraTapped
            } else {
                stringResource(Res.string.photo_open_settings) to PhotoEstimateAction.OpenSettingsTapped
            },
            secondary = stringResource(Res.string.photo_choose) to PhotoEstimateAction.ChooseFromLibraryTapped,
            onAction = onAction,
        )
        is PhotoStage.Review, is PhotoStage.Swap -> Unit
    }
}

@Composable
private fun StartBody(state: PhotoEstimateState, onAction: (PhotoEstimateAction) -> Unit) {
    Text(stringResource(Res.string.photo_start_body), typography = AppTheme.type.body, color = AppTheme.colors.ink)
    FoodRowsCard {
        Column(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            SectionLabel(stringResource(Res.string.photo_note_label))
            TextArea(
                value = state.note,
                onValueChange = { onAction(PhotoEstimateAction.NoteTyped(it)) },
                placeholder = stringResource(Res.string.photo_note_placeholder),
                minLines = 2,
            )
        }
    }
    if (state.canTakePhoto) {
        ButtonPrimary(
            onClick = { onAction(PhotoEstimateAction.TakePhotoTapped) },
            icon = AppIcons.PhotoCamera,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.photo_take)) }
    }
    ButtonSecondary(
        onClick = { onAction(PhotoEstimateAction.ChooseFromLibraryTapped) },
        icon = AppIcons.PhotoLibrary,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(Res.string.photo_choose)) }
    Text(stringResource(Res.string.photo_privacy), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

/** The photo and the note, compact up top so the lines are the focus (3.7). */
@Composable
private fun PhotoAndNote(state: PhotoEstimateState) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.Top) {
        PhotoThumbnail(jpeg = state.photo?.jpeg, contentDescription = stringResource(Res.string.photo_thumbnail_cd))
        if (state.note.isNotBlank()) {
            FoodRowsCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(vertical = AppTheme.spacing.row),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
                ) {
                    SectionLabel(stringResource(Res.string.photo_note_label))
                    Text(state.note, typography = AppTheme.type.body, color = AppTheme.colors.ink)
                }
            }
        }
    }
}

@Composable
private fun Calm(
    state: PhotoEstimateState,
    title: String,
    body: String,
    primary: Pair<String, PhotoEstimateAction>,
    secondary: Pair<String, PhotoEstimateAction>,
    onAction: (PhotoEstimateAction) -> Unit,
) {
    if (state.photo != null) PhotoAndNote(state)
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Text(title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        Text(body, typography = AppTheme.type.body, color = AppTheme.colors.muted)
    }
    ButtonPrimary(onClick = { onAction(primary.second) }, modifier = Modifier.fillMaxWidth()) { Text(primary.first) }
    ButtonGhost(onClick = { onAction(secondary.second) }, modifier = Modifier.fillMaxWidth()) { Text(secondary.first) }
}

@Composable
private fun ColumnScope.ReviewBody(state: PhotoEstimateState, review: PhotoStage.Review, onAction: (PhotoEstimateAction) -> Unit) {
    PhotoAndNote(state)
    FoodRowsCard {
        Column(
            modifier = Modifier.padding(vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.photo_list_title),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (review.rows.isNotEmpty()) {
                    Text(stringResource(Res.string.photo_tap_to_edit), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                }
            }
            if (review.rows.isEmpty()) FoodRowsNote(stringResource(Res.string.photo_list_empty))
            review.rows.forEach { row ->
                EstimateRow(row, editing = review.editor?.key == row.key, onAction)
                if (review.editor?.key == row.key) AmountEditor(review.editor, onAction)
            }
            ButtonGhost(onClick = { onAction(PhotoEstimateAction.AddMissedTapped) }, icon = AppIcons.Add) {
                Text(stringResource(Res.string.photo_add_missed))
            }
        }
    }
    NoteBanner(stringResource(if (review.usedNote) Res.string.photo_banner_note else Res.string.photo_banner))
}

@Composable
private fun EstimateRow(row: PhotoRowUi, editing: Boolean, onAction: (PhotoEstimateAction) -> Unit) {
    val amount = if (row.isWeight) stringResource(Res.string.photo_about, row.amount) else row.amount
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapField(
                text = row.name,
                onClick = { onAction(PhotoEstimateAction.RowNameTapped(row.key)) },
                // The name gets the room: the lines are the focus (3.7).
                modifier = Modifier.weight(NAME_WEIGHT),
                description = stringResource(Res.string.photo_food_cd, row.name),
            )
            TapField(
                text = amount,
                // A line the catalog did not know needs a food before it can have an amount.
                onClick = {
                    onAction(if (row.matched) PhotoEstimateAction.RowAmountTapped(row.key) else PhotoEstimateAction.RowNameTapped(row.key))
                },
                modifier = Modifier.weight(1f),
                active = editing,
                description = stringResource(Res.string.photo_amount_cd, row.name),
            )
            NutrientText(
                metric = Metric.Calories,
                display = row.calories,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
            RoundIconButton(
                icon = AppIcons.Close.described(stringResource(Res.string.photo_remove_cd, row.name)),
                onClick = { onAction(PhotoEstimateAction.RowRemoveTapped(row.key)) },
                size = AppTheme.spacing.touchTarget,
                containerColor = AppTheme.colors.surface,
                contentColor = AppTheme.colors.muted,
            )
        }
        if (!row.matched) {
            Text(stringResource(Res.string.photo_not_matched), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountEditor(editor: PhotoAmountEditor, onAction: (PhotoEstimateAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Stepper(
            amount = editor.amount,
            onDecrease = { onAction(PhotoEstimateAction.LessTapped(editor.key)) },
            onIncrease = { onAction(PhotoEstimateAction.MoreTapped(editor.key)) },
            canDecrease = editor.canLess,
        )
        if (editor.units.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                editor.units.forEachIndexed { index, unit ->
                    ChoiceChip(
                        label = unit,
                        selected = index == editor.unitIndex,
                        onClick = { onAction(PhotoEstimateAction.UnitPicked(editor.key, index)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Footer(footer: PhotoFooter, slot: MealSlot, onAction: (PhotoEstimateAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row, bottom = AppTheme.spacing.page),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(Res.plurals.photo_footer_count, footer.count, footer.count),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                    maxLines = 1,
                )
                // "About" belongs to the number, so it goes when the number does.
                if (footer.calories !is Display.Hidden) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                        Text(stringResource(Res.string.photo_footer_about), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                        NutrientText(
                            metric = Metric.Calories,
                            display = footer.calories,
                            typography = AppTheme.type.secondary,
                            color = AppTheme.colors.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
            ButtonPrimary(onClick = { onAction(PhotoEstimateAction.AddToMealTapped) }) {
                Text(stringResource(Res.string.logger_add_to, stringResource(slot.sentenceName)))
            }
        }
    }
}

@Composable
private fun SwapPane(swap: PhotoStage.Swap, onAction: (PhotoEstimateAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SheetHeader(
            title = stringResource(if (swap.replacing) Res.string.photo_swap_title else Res.string.photo_add_title),
            onBack = { onAction(PhotoEstimateAction.BackTapped) },
            modifier = Modifier.padding(horizontal = AppTheme.spacing.page),
        )
        SearchField(
            value = swap.query,
            onValueChange = { onAction(PhotoEstimateAction.SwapTyped(it)) },
            placeholder = stringResource(Res.string.photo_swap_placeholder),
            focusOnOpen = swap.query.isEmpty(),
            onClear = { onAction(PhotoEstimateAction.SwapTyped("")) },
            clearDescription = stringResource(Res.string.logger_search_clear_cd),
            modifier = Modifier.padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().clearFocusOnScroll(),
            contentPadding = PaddingValues(
                start = AppTheme.spacing.page,
                end = AppTheme.spacing.page,
                top = AppTheme.spacing.section,
                bottom = AppTheme.spacing.page,
            ),
        ) {
            val results = swap.results
            when {
                results == null -> item { FoodRowsNote(stringResource(Res.string.photo_swap_searching)) }
                results.isEmpty() && swap.query.isNotBlank() -> item { FoodRowsNote(stringResource(Res.string.photo_swap_none)) }
                else -> itemsIndexed(results, key = { _, result -> result.id }) { index, result ->
                    ListRow(
                        title = result.name,
                        subtitle = listOfNotNull(result.subtitle, result.amount.ifEmpty { null }).joinToString(" · "),
                        showDivider = index < results.lastIndex,
                        onClick = { onAction(PhotoEstimateAction.SwapPicked(result.id)) },
                    ) {
                        NutrientText(
                            metric = Metric.Calories,
                            display = result.calories,
                            typography = AppTheme.type.secondary.Tabular,
                            color = AppTheme.colors.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private const val NAME_WEIGHT = 1.4f
