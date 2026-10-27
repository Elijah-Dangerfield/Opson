package com.opson.features.today.impl.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.CopyDay
import com.opson.features.today.impl.TodayFoodActionsEnabled
import com.opson.features.today.impl.Toast
import com.opson.features.today.impl.label
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryItemEditor
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.rowLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.foodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.NumberInput
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_food_amount_cd
import opson.libraries.resources.generated.resources.today_food_amount_save
import opson.libraries.resources.generated.resources.today_food_change_amount
import opson.libraries.resources.generated.resources.today_food_copy
import opson.libraries.resources.generated.resources.today_food_delete
import opson.libraries.resources.generated.resources.today_food_edit_copy
import opson.libraries.resources.generated.resources.today_food_edit_copy_body
import opson.libraries.resources.generated.resources.today_food_favorite
import opson.libraries.resources.generated.resources.today_food_move
import opson.libraries.resources.generated.resources.today_food_unfavorite
import opson.libraries.resources.generated.resources.today_select_back_cd
import opson.libraries.resources.generated.resources.today_select_copy_action
import opson.libraries.resources.generated.resources.today_select_copy_title
import opson.libraries.resources.generated.resources.today_select_move_title
import opson.libraries.resources.generated.resources.today_toast_amount_changed
import opson.libraries.resources.generated.resources.today_toast_copied_foods_one
import opson.libraries.resources.generated.resources.today_toast_favorited
import opson.libraries.resources.generated.resources.today_toast_moved_to
import opson.libraries.resources.generated.resources.today_toast_removed
import opson.libraries.resources.generated.resources.today_toast_unfavorited
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A food's actions (design sync 4, 13.3d), from its row or its ⋮ on Today: the food with its
 * amount, how it was logged, its meal and its energy through the policy, then what can be done
 * with it. With `release.foodActions` off only Delete is offered, as the item sheet always did.
 * Deleting is a tombstone, so it syncs.
 */
data class ItemSheetState(
    val loading: Boolean = true,
    val name: String = "",
    val amount: String = "",
    val precision: Precision = Precision.Estimate,
    /** Today's surface; hidden takes its separator with it. */
    val calories: Display = Display.Hidden,
    val slot: MealSlot = MealSlot.Uncategorized,
    /** The rows offered, in the design's order. */
    val actions: List<FoodAction> = emptyList(),
    val step: ItemStep = ItemStep.Actions,
)

enum class FoodAction { ChangeAmount, Move, CopyToDay, EditCopy, Favorite, Unfavorite, Delete }

sealed interface ItemStep {
    data object Actions : ItemStep

    /** [text] typed so far, counted in [unit] ("tbsp", "× 1 cup"). Save is on when it is a new amount above zero. */
    data class Amount(val text: String, val unit: String, val canSave: Boolean) : ItemStep

    /** The meals to move to, [current] marked. A pick moves at once. */
    data class MoveTo(val current: MealSlot) : ItemStep

    data class CopyTo(val day: CopyDay, val slot: MealSlot?) : ItemStep
}

sealed interface ItemSheetAction {
    data class EntryArrived(val day: Day, val today: LocalDate, val policy: VisibilityPolicy, val settings: Settings) : ItemSheetAction
    data object ChangeAmountTapped : ItemSheetAction
    data class AmountTyped(val text: String) : ItemSheetAction
    data object AmountSaved : ItemSheetAction
    data object MoveTapped : ItemSheetAction
    data class MoveSlotPicked(val slot: MealSlot) : ItemSheetAction
    data object CopyTapped : ItemSheetAction
    data class CopyDayPicked(val day: CopyDay) : ItemSheetAction
    data class CopySlotPicked(val slot: MealSlot) : ItemSheetAction
    data object CopyConfirmed : ItemSheetAction
    data object EditCopyTapped : ItemSheetAction
    data object FavoriteTapped : ItemSheetAction
    data object DeleteTapped : ItemSheetAction
    data object BackTapped : ItemSheetAction
    data object CloseTapped : ItemSheetAction
}

sealed interface ItemSheetEvent {
    data class ShowToast(val toast: Toast) : ItemSheetEvent

    /** Edit a copy of the entry; the sheet gives way to it. */
    data class OpenEditCopy(val day: LocalDate, val entryId: String) : ItemSheetEvent
    data object Dismiss : ItemSheetEvent
}

object ItemSheetStateBuilder {
    /** Null when the entry is gone (removed here or on another device). */
    @Suppress("LongParameterList")
    fun build(
        entryId: String,
        day: Day,
        today: LocalDate,
        policy: VisibilityPolicy,
        settings: Settings = Settings(),
        actionsOn: Boolean = false,
        step: ItemStep = ItemStep.Actions,
    ): ItemSheetState? {
        val item = day.allItems.firstOrNull { it.id == entryId } ?: return null
        return ItemSheetState(
            loading = false,
            name = item.name,
            amount = item.amountLabel,
            precision = item.precision,
            calories = policy.resolve(Metric.Calories, item.kcal, Surface.Today, day.formatContext(today)),
            slot = item.slot,
            actions = if (actionsOn) actions(item, settings) else listOf(FoodAction.Delete),
            step = step,
        )
    }

    /** Change amount needs an amount to scale; favorites need a food to star, not just a name. */
    private fun actions(item: LogItem, settings: Settings): List<FoodAction> {
        val key = item.foodRef()?.key
        return listOfNotNull(
            FoodAction.ChangeAmount.takeIf { item.quantity != null && item.unit != null },
            FoodAction.Move,
            FoodAction.CopyToDay,
            FoodAction.EditCopy,
            key?.let { if (settings.isFavorite(it)) FoodAction.Unfavorite else FoodAction.Favorite },
            FoodAction.Delete,
        )
    }

    /** "2" of "tbsp", or "1" of "× 1 cup" where the unit is a whole portion. */
    fun amountStep(item: LogItem, text: String = HouseholdAmount.number(item.quantity ?: 1.0)): ItemStep.Amount {
        val unit = item.unit.orEmpty()
        val shown = if (HouseholdAmount.amountOf(unit) == 1.0 && unit.firstOrNull()?.isDigit() != true) unit else "$TIMES $unit"
        val typed = text.toDoubleOrNull()
        return ItemStep.Amount(text = text, unit = shown, canSave = typed != null && typed > 0.0 && typed != item.quantity)
    }

    private const val TIMES = "×"
}

@Inject
class ItemSheetViewModel(
    @Assisted private val day: LocalDate,
    @Assisted private val entryId: String,
    private val diary: DiaryRepository,
    private val editor: DiaryItemEditor,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val foodActions: TodayFoodActionsEnabled,
) : SEAViewModel<ItemSheetState, ItemSheetEvent, ItemSheetAction>(initialStateArg = ItemSheetState()) {

    private var dismissed = false
    private var arrived: ItemSheetAction.EntryArrived? = null
    private var step: ItemStep = ItemStep.Actions

    init {
        viewModelScope.launch {
            combine(diary.observeDay(day), policySource.policy, settingsRepository.settings) { d, policy, settings ->
                ItemSheetAction.EntryArrived(d, calendar.today(), policy, settings)
            }.collect(::takeAction)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: ItemSheetAction) {
        when (action) {
            is ItemSheetAction.EntryArrived -> {
                arrived = action
                action.rebuild()
            }
            ItemSheetAction.DeleteTapped -> {
                val slot = state.slot
                dismissed = true
                diary.removeItem(entryId)
                sendEvent(ItemSheetEvent.ShowToast(Toast(Res.string.today_toast_removed, listOf(slot.sentenceName))))
                sendEvent(ItemSheetEvent.Dismiss)
            }
            ItemSheetAction.CloseTapped -> dismissOnce()
            ItemSheetAction.BackTapped -> action.go(ItemStep.Actions)
            else -> if (foodActions()) foodAction(action)
        }
    }

    /** Everything but Delete, behind `release.foodActions`. */
    @Suppress("CyclomaticComplexMethod")
    private suspend fun foodAction(action: ItemSheetAction) {
        val item = item() ?: return
        when (action) {
            ItemSheetAction.ChangeAmountTapped -> action.go(ItemSheetStateBuilder.amountStep(item))
            is ItemSheetAction.AmountTyped -> action.go(ItemSheetStateBuilder.amountStep(item, NumberInput.sanitize(action.text, allowDecimal = true)))
            ItemSheetAction.AmountSaved -> {
                val count = (step as? ItemStep.Amount)?.takeIf { it.canSave }?.text?.toDoubleOrNull() ?: return
                val changed = item.withQuantity(count) ?: return
                done(Toast(Res.string.today_toast_amount_changed)) { diary.updateItem(changed) }
            }
            ItemSheetAction.MoveTapped -> action.go(ItemStep.MoveTo(item.slot))
            is ItemSheetAction.MoveSlotPicked -> if (action.slot == item.slot) {
                action.go(ItemStep.Actions)
            } else {
                done(Toast(Res.string.today_toast_moved_to, listOf(action.slot.sentenceName))) { editor.moveItems(listOf(entryId), action.slot) }
            }
            ItemSheetAction.CopyTapped -> {
                // From today the likeliest copy is tomorrow's; from any other day, today.
                val toDay = if (day == calendar.today()) CopyDay.Tomorrow else CopyDay.Today
                action.go(ItemStep.CopyTo(toDay, item.slot.takeIf { it.isMeal }))
            }
            is ItemSheetAction.CopyDayPicked -> (step as? ItemStep.CopyTo)?.let { action.go(it.copy(day = action.day)) }
            is ItemSheetAction.CopySlotPicked -> (step as? ItemStep.CopyTo)?.let { action.go(it.copy(slot = action.slot)) }
            ItemSheetAction.CopyConfirmed -> {
                val copy = step as? ItemStep.CopyTo ?: return
                val slot = copy.slot ?: return
                val to = calendar.today().plus(DatePeriod(days = copy.day.offset))
                done(Toast(Res.string.today_toast_copied_foods_one)) { editor.copyItems(listOf(entryId), to, slot) }
            }
            ItemSheetAction.EditCopyTapped -> {
                dismissed = true
                sendEvent(ItemSheetEvent.OpenEditCopy(day, entryId))
            }
            ItemSheetAction.FavoriteTapped -> {
                val key = item.foodRef()?.key ?: return
                val starred = settingsRepository.update { it.toggleFavorite(key) }.isFavorite(key)
                done(Toast(if (starred) Res.string.today_toast_favorited else Res.string.today_toast_unfavorited)) {}
            }
            else -> Unit
        }
    }

    /** Writes, says so, and closes; the entry changing under the sheet must not close it twice. */
    private suspend fun done(toast: Toast, write: suspend () -> Unit) {
        dismissed = true
        write()
        sendEvent(ItemSheetEvent.ShowToast(toast))
        sendEvent(ItemSheetEvent.Dismiss)
    }

    private fun item(): LogItem? = arrived?.day?.allItems?.firstOrNull { it.id == entryId }

    private suspend fun ItemSheetAction.go(next: ItemStep) {
        step = next
        rebuild()
    }

    private suspend fun ItemSheetAction.rebuild() {
        val input = arrived ?: return
        val next = ItemSheetStateBuilder.build(entryId, input.day, input.today, input.policy, input.settings, foodActions(), step)
        if (next != null) updateState { next } else dismissOnce()
    }

    private fun dismissOnce() {
        if (dismissed) return
        dismissed = true
        sendEvent(ItemSheetEvent.Dismiss)
    }
}

@Composable
fun ItemSheetContent(
    state: ItemSheetState,
    onAction: (ItemSheetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        if (state.loading) return@Column
        when (val step = state.step) {
            ItemStep.Actions -> Actions(state, onAction)
            is ItemStep.Amount -> AmountStep(state, step, onAction)
            is ItemStep.MoveTo -> MoveStep(step, onAction)
            is ItemStep.CopyTo -> CopyStep(step, onAction)
        }
    }
}

@Composable
private fun Actions(state: ItemSheetState, onAction: (ItemSheetAction) -> Unit) {
    Header(state)
    HorizontalDivider(color = AppTheme.colors.line)
    Column {
        state.actions.forEach { action ->
            when (action) {
                FoodAction.ChangeAmount -> ActionRow(AppIcons.EditOutline, Res.string.today_food_change_amount) { onAction(ItemSheetAction.ChangeAmountTapped) }
                FoodAction.Move -> ActionRow(AppIcons.DriveFileMove, Res.string.today_food_move) { onAction(ItemSheetAction.MoveTapped) }
                FoodAction.CopyToDay -> ActionRow(AppIcons.ContentCopy, Res.string.today_food_copy) { onAction(ItemSheetAction.CopyTapped) }
                FoodAction.EditCopy -> ActionRow(AppIcons.EditNote, Res.string.today_food_edit_copy, Res.string.today_food_edit_copy_body) {
                    onAction(ItemSheetAction.EditCopyTapped)
                }
                FoodAction.Favorite -> ActionRow(AppIcons.StarOutline, Res.string.today_food_favorite) { onAction(ItemSheetAction.FavoriteTapped) }
                FoodAction.Unfavorite -> ActionRow(AppIcons.Star, Res.string.today_food_unfavorite) { onAction(ItemSheetAction.FavoriteTapped) }
                FoodAction.Delete -> ActionRow(AppIcons.DeleteOutline, Res.string.today_food_delete) { onAction(ItemSheetAction.DeleteTapped) }
            }
        }
    }
}

/** "Peanut butter" over "2 tbsp · Weighed · Snack · 190 kcal". The energy goes through the policy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: ItemSheetState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = state.name, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
        val precision = stringResource(state.precision.rowLabel)
        val line = listOfNotNull(state.amount.ifBlank { null }, precision, stringResource(state.slot.label)).joinToString(SEPARATOR)
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            Text(text = line, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted)
            NutrientVisible(state.calories) {
                Text(text = SEPARATOR, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                NutrientText(Metric.Calories, state.calories, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ActionRow(icon: IconResource, label: StringResource, sub: StringResource? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ActionRowHeight)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Icon(icon = icon, size = 26.dp, color = AppTheme.colors.muted)
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(label), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            if (sub != null) Text(text = stringResource(sub), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
}

@Composable
private fun StepTitle(title: String, onAction: (ItemSheetAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        RoundIconButton(
            icon = AppIcons.ArrowBack.described(stringResource(Res.string.today_select_back_cd)),
            onClick = { onAction(ItemSheetAction.BackTapped) },
            size = MinTouchTarget,
            containerColor = AppTheme.colors.soft,
            contentColor = AppTheme.colors.ink,
        )
        Text(text = title, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
    }
}

/** A number of the entry's own unit; the energy is never shown here, only the amount. */
@Composable
private fun AmountStep(state: ItemSheetState, step: ItemStep.Amount, onAction: (ItemSheetAction) -> Unit) {
    StepTitle(stringResource(Res.string.today_food_change_amount), onAction)
    Text(text = state.name, typography = AppTheme.type.body, color = AppTheme.colors.muted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        val description = stringResource(Res.string.today_food_amount_cd, step.unit)
        NumberField(
            value = step.text,
            onValueChange = { onAction(ItemSheetAction.AmountTyped(it)) },
            textAlign = TextAlign.Center,
            modifier = Modifier.width(96.dp).semantics { contentDescription = description },
        )
        Text(text = step.unit, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
    }
    FlatButton(
        text = stringResource(Res.string.today_food_amount_save),
        onClick = { onAction(ItemSheetAction.AmountSaved) },
        tone = FlatButtonTone.Accent,
        centered = true,
        enabled = step.canSave,
        minHeight = FlatButtonDefaults.TallMinHeight,
        typography = AppTheme.type.button,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveStep(step: ItemStep.MoveTo, onAction: (ItemSheetAction) -> Unit) {
    StepTitle(stringResource(Res.string.today_select_move_title), onAction)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        (MealSlot.meals + MealSlot.Uncategorized).forEach { slot ->
            ChoiceChip(
                label = stringResource(slot.label),
                selected = slot == step.current,
                onClick = { onAction(ItemSheetAction.MoveSlotPicked(slot)) },
                unselectedColor = AppTheme.colors.surface,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CopyStep(step: ItemStep.CopyTo, onAction: (ItemSheetAction) -> Unit) {
    StepTitle(stringResource(Res.string.today_select_copy_title), onAction)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        CopyDay.entries.forEach { day ->
            ChoiceChip(
                label = stringResource(day.label),
                selected = step.day == day,
                onClick = { onAction(ItemSheetAction.CopyDayPicked(day)) },
                unselectedColor = AppTheme.colors.surface,
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        MealSlot.meals.forEach { slot ->
            ChoiceChip(
                label = stringResource(slot.label),
                selected = step.slot == slot,
                onClick = { onAction(ItemSheetAction.CopySlotPicked(slot)) },
                unselectedColor = AppTheme.colors.surface,
            )
        }
    }
    FlatButton(
        text = stringResource(Res.string.today_select_copy_action),
        onClick = { onAction(ItemSheetAction.CopyConfirmed) },
        tone = FlatButtonTone.Accent,
        centered = true,
        enabled = step.slot != null,
        minHeight = FlatButtonDefaults.TallMinHeight,
        typography = AppTheme.type.button,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val SEPARATOR = " · "
private val ActionRowHeight = 60.dp
