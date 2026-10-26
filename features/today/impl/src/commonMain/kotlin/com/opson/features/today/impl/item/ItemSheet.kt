package com.opson.features.today.impl.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.ui.DiaryItemRow
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.rowLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.SheetMenuGroup
import com.opson.libraries.ui.components.SheetMenuRow
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.system.AppTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_item_remove
import opson.libraries.resources.generated.resources.today_item_sub
import opson.libraries.resources.generated.resources.today_toast_removed
import org.jetbrains.compose.resources.stringResource

/**
 * One logged item, opened from its row on Today. Not in the handoff: it is how
 * someone takes back a wrong entry. Removing is a tombstone, so it syncs.
 */
data class ItemSheetState(
    val loading: Boolean = true,
    val name: String = "",
    val amount: String = "",
    val precision: Precision = Precision.Estimate,
    val calories: Display = Display.Hidden,
    val slot: MealSlot = MealSlot.Uncategorized,
)

sealed interface ItemSheetAction {
    data class EntryArrived(val day: Day, val today: LocalDate, val policy: VisibilityPolicy) : ItemSheetAction
    data object RemoveTapped : ItemSheetAction
    data object CloseTapped : ItemSheetAction
}

sealed interface ItemSheetEvent {
    data class ShowToast(val toast: Toast) : ItemSheetEvent
    data object Dismiss : ItemSheetEvent
}

object ItemSheetStateBuilder {
    /** Null when the entry is gone (removed here or on another device). */
    fun build(entryId: String, day: Day, today: LocalDate, policy: VisibilityPolicy): ItemSheetState? {
        val item = day.allItems.firstOrNull { it.id == entryId } ?: return null
        return ItemSheetState(
            loading = false,
            name = item.name,
            amount = item.amountLabel,
            precision = item.precision,
            calories = policy.resolve(Metric.Calories, item.kcal, Surface.Today, day.formatContext(today)),
            slot = item.slot,
        )
    }
}

@Inject
class ItemSheetViewModel(
    @Assisted private val day: LocalDate,
    @Assisted private val entryId: String,
    private val diary: DiaryRepository,
    policySource: VisibilityPolicySource,
    calendar: LocalCalendar,
) : SEAViewModel<ItemSheetState, ItemSheetEvent, ItemSheetAction>(initialStateArg = ItemSheetState()) {

    private var dismissed = false

    init {
        viewModelScope.launch {
            combine(diary.observeDay(day), policySource.policy) { d, policy -> ItemSheetAction.EntryArrived(d, calendar.today(), policy) }
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: ItemSheetAction) {
        when (action) {
            is ItemSheetAction.EntryArrived -> {
                val next = ItemSheetStateBuilder.build(entryId, action.day, action.today, action.policy)
                if (next != null) action.updateState { next } else dismissOnce()
            }
            ItemSheetAction.RemoveTapped -> {
                val slot = state.slot
                dismissed = true
                diary.removeItem(entryId)
                sendEvent(ItemSheetEvent.ShowToast(Toast(Res.string.today_toast_removed, listOf(slot.sentenceName))))
                sendEvent(ItemSheetEvent.Dismiss)
            }
            ItemSheetAction.CloseTapped -> dismissOnce()
        }
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
        SheetHeader(title = state.name, onClose = { onAction(ItemSheetAction.CloseTapped) })
        if (state.loading) return@Column
        val precision = stringResource(state.precision.rowLabel)
        FoodRowsCard {
            DiaryItemRow(
                name = state.name,
                sub = if (state.amount.isBlank()) precision else stringResource(Res.string.today_item_sub, state.amount, precision),
                calories = state.calories,
                onClick = null,
                showDivider = false,
            )
        }
        SheetMenuGroup {
            SheetMenuRow(
                label = stringResource(Res.string.today_item_remove, stringResource(state.slot.sentenceName)),
                icon = AppIcons.Delete,
                onClick = { onAction(ItemSheetAction.RemoveTapped) },
                showDivider = false,
            )
        }
    }
}
