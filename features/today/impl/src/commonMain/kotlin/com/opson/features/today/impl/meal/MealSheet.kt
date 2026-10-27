package com.opson.features.today.impl.meal

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
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.SheetMenuGroup
import com.opson.libraries.ui.components.SheetMenuRow
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.system.AppTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_all_food
import opson.libraries.resources.generated.resources.today_meal_add_food
import opson.libraries.resources.generated.resources.today_meal_copy_day_before
import opson.libraries.resources.generated.resources.today_meal_copy_yesterday
import opson.libraries.resources.generated.resources.today_meal_note
import opson.libraries.resources.generated.resources.today_meal_save_as_meal
import opson.libraries.resources.generated.resources.today_toast_added_to
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sheet behind a meal's +. Scan a barcode, Photo and Voice log are not
 * built in 0.1, so they are not here at all.
 */
data class MealSheetState(
    val slot: MealSlot,
    /** The slot's name, or "All food" for Uncategorized on a day with meal groups off. */
    val title: StringResource,
    val items: List<MealSheetItem> = emptyList(),
)

sealed interface MealSheetItem {
    data object AddFood : MealSheetItem

    /** A note and how hungry, in one sheet. */
    data object Note : MealSheetItem

    /** Only when the day before has this meal. "Yesterday's" when the day is today. */
    data class CopyFromDayBefore(val dayIsToday: Boolean) : MealSheetItem

    /** Only when this meal has items. */
    data object SaveAsMeal : MealSheetItem
}

sealed interface MealSheetAction {
    data class DaysArrived(val today: LocalDate, val day: Day, val dayBefore: Day, val settings: Settings) : MealSheetAction
    data object AddFoodTapped : MealSheetAction
    data object NoteTapped : MealSheetAction
    data object CopyFromDayBeforeTapped : MealSheetAction
    data object SaveAsMealTapped : MealSheetAction
    data object CloseTapped : MealSheetAction
}

sealed interface MealSheetEvent {
    data class OpenLogger(val day: LocalDate, val slot: MealSlot) : MealSheetEvent
    data class OpenMealNote(val day: LocalDate, val slot: MealSlot) : MealSheetEvent
    data class OpenCreateMeal(val day: LocalDate, val slot: MealSlot) : MealSheetEvent
    data class ShowToast(val toast: Toast) : MealSheetEvent
    data object Dismiss : MealSheetEvent
}

object MealSheetStateBuilder {
    fun build(slot: MealSlot, today: LocalDate, day: Day, dayBefore: Day, settings: Settings): MealSheetState {
        val groupsOn = day.mealGroupsOn(settings)
        return MealSheetState(
            slot = slot,
            title = if (slot == MealSlot.Uncategorized && !groupsOn) Res.string.today_all_food else slot.label,
            items = buildList {
                add(MealSheetItem.AddFood)
                add(MealSheetItem.Note)
                if (dayBefore.items(slot).isNotEmpty()) add(MealSheetItem.CopyFromDayBefore(dayIsToday = day.date == today))
                if (day.items(slot).isNotEmpty()) add(MealSheetItem.SaveAsMeal)
            },
        )
    }
}

@Inject
class MealSheetViewModel(
    @Assisted private val day: LocalDate,
    @Assisted private val slot: MealSlot,
    private val diary: DiaryRepository,
    settingsRepository: SettingsRepository,
    calendar: LocalCalendar,
) : SEAViewModel<MealSheetState, MealSheetEvent, MealSheetAction>(
    initialStateArg = MealSheetState(slot = slot, title = slot.label),
) {
    /** Copied, and the sheet is closing: a second tap queued behind the first would copy the meal again. */
    private var written = false

    init {
        viewModelScope.launch {
            combine(
                diary.observeDay(day),
                diary.observeDay(day.minus(DatePeriod(days = 1))),
                settingsRepository.settings,
            ) { d, before, settings -> MealSheetAction.DaysArrived(calendar.today(), d, before, settings) }
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: MealSheetAction) {
        when (action) {
            is MealSheetAction.DaysArrived -> action.updateState {
                MealSheetStateBuilder.build(slot, action.today, action.day, action.dayBefore, action.settings)
            }
            MealSheetAction.AddFoodTapped -> sendEvent(MealSheetEvent.OpenLogger(day, slot))
            MealSheetAction.NoteTapped -> sendEvent(MealSheetEvent.OpenMealNote(day, slot))
            MealSheetAction.CopyFromDayBeforeTapped -> if (!written) {
                written = true
                diary.copySlot(fromDay = day.minus(DatePeriod(days = 1)), toDay = day, slot = slot)
                sendEvent(MealSheetEvent.ShowToast(Toast(Res.string.today_toast_added_to, listOf(slot.sentenceName))))
                sendEvent(MealSheetEvent.Dismiss)
            }
            MealSheetAction.SaveAsMealTapped -> sendEvent(MealSheetEvent.OpenCreateMeal(day, slot))
            MealSheetAction.CloseTapped -> sendEvent(MealSheetEvent.Dismiss)
        }
    }
}

@Composable
fun MealSheetContent(
    state: MealSheetState,
    onAction: (MealSheetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meal = stringResource(state.slot.sentenceName)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        SheetHeader(title = stringResource(state.title), onClose = { onAction(MealSheetAction.CloseTapped) })
        SheetMenuGroup {
            state.items.forEachIndexed { index, item ->
                val (label, icon, action) = when (item) {
                    MealSheetItem.AddFood -> Triple(stringResource(Res.string.today_meal_add_food), AppIcons.Restaurant, MealSheetAction.AddFoodTapped)
                    MealSheetItem.Note -> Triple(stringResource(Res.string.today_meal_note), AppIcons.EditNote, MealSheetAction.NoteTapped)
                    is MealSheetItem.CopyFromDayBefore -> Triple(
                        stringResource(if (item.dayIsToday) Res.string.today_meal_copy_yesterday else Res.string.today_meal_copy_day_before, meal),
                        AppIcons.History,
                        MealSheetAction.CopyFromDayBeforeTapped,
                    )
                    MealSheetItem.SaveAsMeal -> Triple(stringResource(Res.string.today_meal_save_as_meal), AppIcons.BookmarkAdd, MealSheetAction.SaveAsMealTapped)
                }
                SheetMenuRow(label = label, icon = icon, onClick = { onAction(action) }, showDivider = index < state.items.lastIndex)
            }
        }
    }
}
