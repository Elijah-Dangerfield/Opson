package com.opson.features.today.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.foods.CreateMealRoute
import com.opson.features.logger.LogRoute
import com.opson.features.logger.LoggerRoute
import com.opson.features.settings.FastingRoute
import com.opson.features.settings.ModesRoute
import com.opson.features.settings.WaterSettingsRoute
import com.opson.features.settings.toRoute
import com.opson.features.today.DayCloseRoute
import com.opson.features.today.MealNoteRoute
import com.opson.features.today.TodayRoute
import com.opson.features.today.impl.calendar.CalendarEvent
import com.opson.features.today.impl.cards.TodayCardsEvent
import com.opson.features.today.impl.cards.TodayCardsSheetContent
import com.opson.features.today.impl.cards.TodayCardsViewModel
import com.opson.features.today.impl.calendar.CalendarSheetContent
import com.opson.features.today.impl.calendar.CalendarViewModel
import com.opson.features.today.impl.dayclose.DayCloseEvent
import com.opson.features.today.impl.dayclose.DayCloseScreen
import com.opson.features.today.impl.dayclose.DayCloseViewModel
import com.opson.features.today.impl.daynote.DayNoteEvent
import com.opson.features.today.impl.daynote.DayNoteSheetContent
import com.opson.features.today.impl.daynote.DayNoteViewModel
import com.opson.features.today.impl.item.ItemSheetContent
import com.opson.features.today.impl.item.ItemSheetEvent
import com.opson.features.today.impl.item.ItemSheetViewModel
import com.opson.features.today.impl.meal.MealFeelEvent
import com.opson.features.today.impl.meal.MealFeelSheetContent
import com.opson.features.today.impl.meal.MealFeelViewModel
import com.opson.features.today.impl.meal.MealNoteEvent
import com.opson.features.today.impl.meal.MealNoteSheetContent
import com.opson.features.today.impl.meal.MealNoteViewModel
import com.opson.features.today.impl.meal.MealSheetContent
import com.opson.features.today.impl.meal.MealSheetEvent
import com.opson.features.today.impl.meal.MealSheetViewModel
import com.opson.features.today.impl.water.WaterSheetContent
import com.opson.features.today.impl.water.WaterSheetEvent
import com.opson.features.today.impl.water.WaterSheetViewModel
import com.opson.features.trends.NutrientsRange
import com.opson.features.trends.NutrientsRoute
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.opson.libraries.ui.snackbar.showSnackBar
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf

/**
 * Today and everything that opens from it: the tab root, Day close, and the
 * calendar, meal, meal note, how it felt, water, item, day note and Today's cards sheets.
 * View models send events; only this class navigates.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class TodayFeatureEntryPoint(
    private val todayViewModelFactory: () -> TodayViewModel,
    private val dayCloseViewModelFactory: (LocalDate) -> DayCloseViewModel,
    private val calendarViewModelFactory: (LocalDate) -> CalendarViewModel,
    private val mealSheetViewModelFactory: (LocalDate, MealSlot) -> MealSheetViewModel,
    private val mealNoteViewModelFactory: (LocalDate, MealSlot) -> MealNoteViewModel,
    private val mealFeelViewModelFactory: (LocalDate, MealSlot) -> MealFeelViewModel,
    private val waterSheetViewModelFactory: (LocalDate) -> WaterSheetViewModel,
    private val itemSheetViewModelFactory: (LocalDate, String) -> ItemSheetViewModel,
    private val dayNoteViewModelFactory: (LocalDate) -> DayNoteViewModel,
    private val todayCardsViewModelFactory: () -> TodayCardsViewModel,
) : FeatureEntryPoint {

    @Suppress("LongMethod")
    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        val slotType = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>())

        screen<TodayRoute> {
            val viewModel: TodayViewModel = viewModel { todayViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event -> onTodayEvent(event, router) }
            TodayScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<DayCloseRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DayCloseRoute>()
            val viewModel: DayCloseViewModel = viewModel { dayCloseViewModelFactory(LocalDate.parse(route.dayIso)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    DayCloseEvent.Close -> router.goBack()
                }
            }
            DayCloseScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<CalendarSheetRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<CalendarSheetRoute>()
            val viewModel: CalendarViewModel = viewModel { calendarViewModelFactory(LocalDate.parse(route.selectedIso)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    CalendarEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { CalendarSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<MealSheetRoute>(typeMap = slotType) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<MealSheetRoute>()
            val viewModel: MealSheetViewModel = viewModel { mealSheetViewModelFactory(LocalDate.parse(route.dayIso), route.slot) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    // The meal sheet gives way to what it opens, so Back from there lands on Today.
                    is MealSheetEvent.OpenLogger -> router.batch {
                        goBack()
                        navigate(LoggerRoute(slot = event.slot, dayIso = event.day.toString()))
                    }
                    is MealSheetEvent.OpenMealNote -> router.batch {
                        goBack()
                        navigate(MealNoteRoute(event.day.toString(), event.slot))
                    }
                    is MealSheetEvent.OpenMealFeel -> router.batch {
                        goBack()
                        navigate(MealFeelRoute(event.day.toString(), event.slot))
                    }
                    is MealSheetEvent.OpenCreateMeal -> router.batch {
                        goBack()
                        navigate(CreateMealRoute(fromDayIso = event.day.toString(), fromSlot = event.slot))
                    }
                    is MealSheetEvent.ShowToast -> showToast(event.toast)
                    MealSheetEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { MealSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<MealNoteRoute>(typeMap = slotType) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<MealNoteRoute>()
            val viewModel: MealNoteViewModel = viewModel { mealNoteViewModelFactory(LocalDate.parse(route.dayIso), route.slot) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is MealNoteEvent.ShowToast -> showToast(event.toast)
                    MealNoteEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { MealNoteSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<MealFeelRoute>(typeMap = slotType) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<MealFeelRoute>()
            val viewModel: MealFeelViewModel = viewModel { mealFeelViewModelFactory(LocalDate.parse(route.dayIso), route.slot) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is MealFeelEvent.ShowToast -> showToast(event.toast)
                    MealFeelEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { MealFeelSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<WaterSheetRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<WaterSheetRoute>()
            val viewModel: WaterSheetViewModel = viewModel { waterSheetViewModelFactory(LocalDate.parse(route.dayIso)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    WaterSheetEvent.OpenWaterSettings -> router.batch {
                        goBack()
                        navigate(WaterSettingsRoute(fromMore = false))
                    }
                    is WaterSheetEvent.ShowToast -> showToast(event.toast)
                    WaterSheetEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { WaterSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<ItemSheetRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<ItemSheetRoute>()
            val viewModel: ItemSheetViewModel = viewModel { itemSheetViewModelFactory(LocalDate.parse(route.dayIso), route.entryId) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is ItemSheetEvent.ShowToast -> showToast(event.toast)
                    ItemSheetEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { ItemSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<TodayCardsRoute> { _, sheetState ->
            val viewModel: TodayCardsViewModel = viewModel { todayCardsViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is TodayCardsEvent.OpenPause -> router.navigate(event.change.toRoute())
                    TodayCardsEvent.OpenModes -> router.batch {
                        goBack()
                        navigate(ModesRoute())
                    }
                    is TodayCardsEvent.ShowToast -> showToast(event.toast)
                    TodayCardsEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { TodayCardsSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<DayNoteRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<DayNoteRoute>()
            val viewModel: DayNoteViewModel = viewModel { dayNoteViewModelFactory(LocalDate.parse(route.dayIso)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is DayNoteEvent.ShowToast -> showToast(event.toast)
                    DayNoteEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { DayNoteSheetContent(state = state, onAction = viewModel::takeAction) }
        }
    }

    private suspend fun onTodayEvent(event: TodayEvent, router: Router) {
        when (event) {
            is TodayEvent.OpenCalendar -> router.navigate(CalendarSheetRoute(event.day.toString()))
            is TodayEvent.OpenDayClose -> router.navigate(DayCloseRoute(event.day.toString()))
            is TodayEvent.OpenNutrients -> router.navigate(NutrientsRoute(event.day.toString(), NutrientsRange.Day))
            is TodayEvent.OpenLogger -> router.navigate(LoggerRoute(slot = event.slot, dayIso = event.day.toString()))
            is TodayEvent.OpenLoggerWithFood -> router.navigate(
                LoggerRoute(dayIso = event.day.toString(), prePickFoodId = event.foodId, prePickIsCustom = event.isCustom),
            )
            is TodayEvent.OpenLog -> router.navigate(LogRoute(event.day.toString(), event.slot, event.tab))
            is TodayEvent.OpenMealFeel -> router.navigate(MealFeelRoute(event.day.toString(), event.slot))
            TodayEvent.OpenTodayCards -> router.navigate(TodayCardsRoute())
            TodayEvent.OpenFasting -> router.navigate(FastingRoute())
            is TodayEvent.OpenDayNote -> router.navigate(DayNoteRoute(event.day.toString()))
            is TodayEvent.OpenMealSheet -> router.navigate(MealSheetRoute(event.day.toString(), event.slot))
            is TodayEvent.OpenItemSheet -> router.navigate(ItemSheetRoute(event.day.toString(), event.entryId))
            is TodayEvent.OpenWaterSheet -> router.navigate(WaterSheetRoute(event.day.toString()))
            is TodayEvent.OpenPause -> router.navigate(event.change.toRoute())
            is TodayEvent.ShowToast -> showToast(event.toast)
        }
    }
}

/** Every Today sheet: the page colour, a grab handle, its own scroll. */
@Composable
private fun TodaySheet(router: Router, sheetState: BottomSheetState, content: @Composable () -> Unit) {
    BottomSheet(
        onDismissRequest = { router.goBack() },
        state = sheetState,
        scrollableContent = true,
        showDragHandle = true,
        backgroundColor = AppTheme.colors.bg,
    ) {
        content()
    }
}

private suspend fun showToast(toast: Toast) {
    val args = toast.args.map { if (it is StringResource) getString(it) else it }
    showSnackBar(message = getString(toast.message, *args.toTypedArray()))
}
