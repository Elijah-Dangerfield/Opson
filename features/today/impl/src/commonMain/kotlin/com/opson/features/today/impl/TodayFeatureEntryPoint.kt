package com.opson.features.today.impl

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.foods.CreateMealRoute
import com.opson.features.foods.EditCopyRoute
import com.opson.features.foods.RepeatRoute
import com.opson.features.logger.ExerciseRoute
import com.opson.features.logger.LoggedFoodRoute
import com.opson.features.logger.LoggerRoute
import com.opson.features.settings.EnergyExplainerRoute
import com.opson.features.settings.EnergyTodayRoute
import com.opson.features.settings.FastingRoute
import com.opson.features.settings.FindSupportEnabled
import com.opson.features.settings.FindSupportRoute
import com.opson.features.settings.SupportRoute
import com.opson.features.settings.TodayScreenSettingsRoute
import com.opson.features.settings.RestingEnergyRoute
import com.opson.features.settings.WaterSettingsRoute
import com.opson.features.settings.toRoute
import com.opson.features.medication.MedicationRoute
import com.opson.features.today.CelebrationRoute
import com.opson.features.today.CheckInRoute
import com.opson.features.today.ProgressRoute
import com.opson.features.today.impl.celebration.CelebrationEvent
import com.opson.features.today.impl.celebration.CelebrationScreen
import com.opson.features.today.impl.celebration.CelebrationViewModel
import com.opson.features.today.impl.celebration.ProgressEvent
import com.opson.features.today.impl.celebration.ProgressSheetContent
import com.opson.features.today.impl.celebration.ProgressViewModel
import com.opson.features.today.impl.celebration.StreakIndicator
import com.opson.features.today.impl.celebration.StreakIndicatorAction
import com.opson.features.today.impl.celebration.StreakIndicatorEvent
import com.opson.features.today.impl.celebration.StreakIndicatorViewModel
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.celebrate_turned_off
import com.opson.features.today.DayCloseRoute
import com.opson.features.today.DayClosedRoute
import com.opson.features.today.MealNoteRoute
import com.opson.features.today.TodayCardContribution
import com.opson.features.today.NotesOrigin
import com.opson.features.today.NotesRoute
import com.opson.features.today.TodayRoute
import com.opson.features.today.impl.activity.TodayActivityEvent
import com.opson.features.today.impl.activity.TodayActivitySection
import com.opson.features.today.impl.activity.TodayActivityViewModel
import com.opson.features.today.impl.refresh.TodayRefreshAction
import com.opson.features.today.impl.refresh.TodayRefreshEvent
import com.opson.features.today.impl.refresh.TodayRefreshViewModel
import com.opson.features.today.impl.calendar.CalendarEvent
import com.opson.features.today.impl.checkin.CheckInEvent
import com.opson.features.today.impl.checkin.CheckInSheetContent
import com.opson.features.today.impl.checkin.CheckInViewModel
import com.opson.features.today.impl.calendar.CalendarDropdownContent
import com.opson.features.today.impl.calendar.CalendarViewModel
import com.opson.features.today.impl.dayclose.DayCloseEvent
import com.opson.features.today.impl.dayclose.DayCloseScreen
import com.opson.features.today.impl.dayclose.DayCloseViewModel
import com.opson.features.today.impl.dayclose.DayClosedEvent
import com.opson.features.today.impl.dayclose.DayClosedSheetContent
import com.opson.features.today.impl.dayclose.DayClosedViewModel
import com.opson.features.today.impl.dayclose.WhyNotCutBackSheetContent
import com.opson.features.today.impl.daynote.DayNoteEvent
import com.opson.features.today.impl.daynote.DayNoteSheetContent
import com.opson.features.today.impl.daynote.DayNoteViewModel
import com.opson.features.today.impl.item.ItemSheetContent
import com.opson.features.today.impl.item.ItemSheetEvent
import com.opson.features.today.impl.item.ItemSheetViewModel
import com.opson.features.today.impl.meal.MealNoteEvent
import com.opson.features.today.impl.meal.MealNoteSheetContent
import com.opson.features.today.impl.meal.MealNoteViewModel
import com.opson.features.today.impl.notes.NotesEvent
import com.opson.features.today.impl.notes.NotesScreen
import com.opson.features.today.impl.notes.NotesViewModel
import com.opson.features.today.impl.water.WaterSheetContent
import com.opson.features.today.impl.water.WaterSheetEvent
import com.opson.features.today.impl.water.WaterSheetViewModel
import com.opson.features.trends.NutrientsRange
import com.opson.features.trends.NutrientsRoute
import com.opson.features.trends.WeightRoute
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.OnTabReselected
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.TabBar
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.dialog
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.ui.components.dialog.DropdownDialog
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
 * Today and everything that opens from it: the tab root, Day close, a milestone's celebration and
 * Your progress, the calendar that drops from
 * the title, the Notes list, and the meal, meal Note (hunger and words), water, food actions, day
 * note, check-in and day closed sheets. Arranging Today is More › Today screen's, which the ⋮ menu opens.
 * View models send events; only this class navigates.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class TodayFeatureEntryPoint(
    private val todayViewModelFactory: () -> TodayViewModel,
    private val dayCloseViewModelFactory: (LocalDate) -> DayCloseViewModel,
    private val dayClosedViewModelFactory: (LocalDate) -> DayClosedViewModel,
    private val calendarViewModelFactory: (LocalDate) -> CalendarViewModel,
    private val mealNoteViewModelFactory: (LocalDate, MealSlot) -> MealNoteViewModel,
    private val waterSheetViewModelFactory: (LocalDate) -> WaterSheetViewModel,
    private val itemSheetViewModelFactory: (LocalDate, String) -> ItemSheetViewModel,
    private val dayNoteViewModelFactory: (LocalDate) -> DayNoteViewModel,
    private val notesViewModelFactory: (NotesOrigin) -> NotesViewModel,
    private val todayActivityViewModelFactory: () -> TodayActivityViewModel,
    private val checkInViewModelFactory: (LocalDate?) -> CheckInViewModel,
    private val refreshViewModelFactory: () -> TodayRefreshViewModel,
    private val celebrationViewModelFactory: (String, Boolean) -> CelebrationViewModel,
    private val progressViewModelFactory: () -> ProgressViewModel,
    private val streakViewModelFactory: () -> StreakIndicatorViewModel,
    private val tabBar: TabBar,
    private val findSupportEnabled: FindSupportEnabled,
    /** Cards other modules add to the list, such as Medication's. */
    private val cardContributions: Set<TodayCardContribution> = emptySet(),
) : FeatureEntryPoint {

    @Suppress("LongMethod")
    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        val slotType = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>())

        screen<TodayRoute> {
            val viewModel: TodayViewModel = viewModel { todayViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event -> onTodayEvent(event, router) }
            // The select panel takes the bottom bar's place while foods are being picked (2.12).
            val selecting = state.selection != null
            DisposableEffect(selecting) {
                tabBar.hide(TAB_BAR_OWNER, selecting)
                onDispose { tabBar.hide(TAB_BAR_OWNER, false) }
            }
            val listState = rememberLazyListState()
            router.OnTabReselected(TodayRoute::class) { listState.animateScrollToItem(0) }
            // Pull to refresh: the diary sync and a health read together, with its own view model.
            val refresh: TodayRefreshViewModel = viewModel { refreshViewModelFactory() }
            val refreshState by refresh.stateFlow.collectAsStateWithLifecycle()
            refresh.ObserveEvents { event ->
                when (event) {
                    is TodayRefreshEvent.ShowToast -> showToast(event.toast)
                }
            }
            TodayScreen(
                state = state,
                onAction = viewModel::takeAction,
                listState = listState,
                activity = activitySection(router),
                contributed = contributedCards(state.day, router),
                refreshing = refreshState.refreshing,
                onRefresh = { refresh.takeAction(TodayRefreshAction.Pulled) },
                streak = streakIndicator(router),
            )
        }

        // A milestone's celebration (release.celebrations). Only the prompt host opens it.
        screen<CelebrationRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<CelebrationRoute>()
            val viewModel: CelebrationViewModel = viewModel { celebrationViewModelFactory(route.milestone, route.offerTurnOff) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    CelebrationEvent.Close -> router.goBack()
                    CelebrationEvent.TurnedOff -> {
                        router.goBack()
                        showToast(Toast(Res.string.celebrate_turned_off))
                    }
                }
            }
            CelebrationScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<ProgressRoute> { _, sheetState ->
            val viewModel: ProgressViewModel = viewModel { progressViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    ProgressEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { ProgressSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        screen<NotesRoute>(typeMap = mapOf(typeOf<NotesOrigin>() to serializableType<NotesOrigin>())) { backStackEntry ->
            val route = backStackEntry.toRoute<NotesRoute>()
            val viewModel: NotesViewModel = viewModel { notesViewModelFactory(route.from) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is NotesEvent.OpenNote -> router.navigate(MealNoteRoute(event.day.toString(), event.slot))
                    is NotesEvent.OpenDayNote -> router.navigate(DayNoteRoute(event.day.toString()))
                    NotesEvent.Close -> router.goBack()
                }
            }
            NotesScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<DayCloseRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DayCloseRoute>()
            val viewModel: DayCloseViewModel = viewModel { dayCloseViewModelFactory(LocalDate.parse(route.dayIso)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    DayCloseEvent.Close -> router.goBack()
                    DayCloseEvent.OpenWhyNotCutBack -> router.navigate(WhyNotCutBackRoute())
                    // Find support once it is released, the Support page's helpline until then. Never logged.
                    DayCloseEvent.OpenSupport -> router.navigate(if (findSupportEnabled()) FindSupportRoute() else SupportRoute())
                    is DayCloseEvent.OpenCheckIn -> router.navigate(CheckInRoute(event.day.toString()))
                    is DayCloseEvent.OpenNutrients -> router.navigate(NutrientsRoute(event.day.toString(), NutrientsRange.Day))
                    is DayCloseEvent.ShowToast -> showToast(event.toast)
                }
            }
            DayCloseScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<DayClosedRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<DayClosedRoute>()
            val viewModel: DayClosedViewModel = viewModel { dayClosedViewModelFactory(LocalDate.parse(route.dayIso)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    DayClosedEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { DayClosedSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        // The check-in, from the + sheet and Close the day (release.checkIn); a closed day still takes one.
        bottomSheet<CheckInRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<CheckInRoute>()
            val viewModel: CheckInViewModel = viewModel { checkInViewModelFactory(route.dayIso?.let(LocalDate::parse)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is CheckInEvent.ShowToast -> showToast(event.toast)
                    CheckInEvent.Dismiss -> sheetState.dismiss()
                    // The sheet gives way to the page, so Back from there lands where the check-in was opened.
                    CheckInEvent.OpenSideEffects -> router.batch {
                        goBack()
                        navigate(MedicationRoute())
                    }
                }
            }
            TodaySheet(router, sheetState) { CheckInSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        dialog<CalendarRoute> { backStackEntry, dialogState ->
            val route = backStackEntry.toRoute<CalendarRoute>()
            val viewModel: CalendarViewModel = viewModel { calendarViewModelFactory(LocalDate.parse(route.selectedIso)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    CalendarEvent.Dismiss -> dialogState.dismiss()
                }
            }
            DropdownDialog(onDismissRequest = { router.goBack() }, state = dialogState) {
                CalendarDropdownContent(state = state, onAction = viewModel::takeAction)
            }
        }

        bottomSheet<MealNoteRoute>(typeMap = slotType) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<MealNoteRoute>()
            val viewModel: MealNoteViewModel = viewModel { mealNoteViewModelFactory(LocalDate.parse(route.dayIso), route.slot) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is MealNoteEvent.ShowToast -> showToast(event.toast)
                    is MealNoteEvent.DayClosed -> router.showDayClosed(event.day)
                    MealNoteEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { MealNoteSheetContent(state = state, onAction = viewModel::takeAction) }
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
                    is WaterSheetEvent.DayClosed -> router.showDayClosed(event.day)
                    WaterSheetEvent.Dismiss -> sheetState.dismiss()
                }
            }
            // Half height: a few rows, and Today's water card stays in view above it.
            TodaySheet(router, sheetState, heightFraction = WATER_SHEET_HEIGHT) { WaterSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<ItemSheetRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<ItemSheetRoute>()
            val viewModel: ItemSheetViewModel = viewModel { itemSheetViewModelFactory(LocalDate.parse(route.dayIso), route.entryId) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is ItemSheetEvent.ShowToast -> showToast(event.toast)
                    is ItemSheetEvent.OpenEditCopy -> router.batch {
                        goBack()
                        navigate(EditCopyRoute(dayIso = event.day.toString(), entryId = event.entryId))
                    }
                    is ItemSheetEvent.OpenRepeat -> router.batch {
                        goBack()
                        navigate(
                            if (event.ruleId != null) {
                                RepeatRoute(ruleId = event.ruleId)
                            } else {
                                RepeatRoute(fromDayIso = event.day.toString(), fromEntryId = event.entryId)
                            },
                        )
                    }
                    is ItemSheetEvent.DayClosed -> router.showDayClosed(event.day)
                    ItemSheetEvent.Dismiss -> sheetState.dismiss()
                }
            }
            TodaySheet(router, sheetState) { ItemSheetContent(state = state, onAction = viewModel::takeAction) }
        }

        bottomSheet<WhyNotCutBackRoute> { _, sheetState ->
            TodaySheet(router, sheetState) { WhyNotCutBackSheetContent(onClose = { sheetState.dismiss() }) }
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

    /** The streak in Today's header (`release.celebrations`), or null while there is no run to show. */
    @Composable
    private fun streakIndicator(router: Router): (@Composable () -> Unit)? {
        val viewModel: StreakIndicatorViewModel = viewModel { streakViewModelFactory() }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        viewModel.ObserveEvents { event ->
            when (event) {
                StreakIndicatorEvent.OpenProgress -> router.navigate(ProgressRoute())
            }
        }
        val badge = state.badge ?: return null
        return remember(badge) { { StreakIndicator(badge, onClick = { viewModel.takeAction(StreakIndicatorAction.Tapped) }) } }
    }

    /** Today's Activity card (`release.exercise`), or null while it has nothing to show. */
    @Composable
    private fun activitySection(router: Router): (@Composable () -> Unit)? {
        val viewModel: TodayActivityViewModel = viewModel { todayActivityViewModelFactory() }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        viewModel.ObserveEvents { event ->
            when (event) {
                is TodayActivityEvent.OpenExercise -> router.navigate(
                    ExerciseRoute(entryId = event.entryId, dayIso = event.day.toString(), notOnWatch = event.notOnWatch),
                )
                TodayActivityEvent.OpenRestingEnergy -> router.navigate(RestingEnergyRoute())
                TodayActivityEvent.OpenBurnedExplainer -> router.navigate(EnergyExplainerRoute(EnergyExplainerRoute.TOPIC_BURNED))
            }
        }
        val card = state.card ?: return null
        return remember(card) { { TodayActivitySection(card, viewModel::takeAction) } }
    }

    /** Each contribution's card for [day], in a fixed order so their composition slots stay put. */
    @Composable
    private fun contributedCards(day: LocalDate, router: Router): List<ContributedCard> {
        val ordered = remember { cardContributions.sortedBy { it.key } }
        val cards = ordered.map { contribution -> contribution.key to contribution.card(day, router) }
        return cards.mapNotNull { (key, content) -> content?.let { ContributedCard(key, it) } }
    }

    private suspend fun onTodayEvent(event: TodayEvent, router: Router) {
        when (event) {
            is TodayEvent.OpenCalendar -> router.navigate(CalendarRoute(event.day.toString()))
            is TodayEvent.OpenDayClose -> router.navigate(DayCloseRoute(event.day.toString()))
            is TodayEvent.OpenNutrients -> router.navigate(NutrientsRoute(event.day.toString(), NutrientsRange.Day))
            TodayEvent.OpenNotes -> router.navigate(NotesRoute())
            TodayEvent.OpenEnergyToday -> router.navigate(EnergyTodayRoute())
            is TodayEvent.OpenLogger -> router.navigate(LoggerRoute(slot = event.slot, dayIso = event.day.toString()))
            TodayEvent.OpenWeight -> router.navigate(WeightRoute())
            is TodayEvent.OpenCreateMeal -> router.navigate(
                CreateMealRoute(fromDayIso = event.day.toString(), fromSlot = event.slot, entryIds = event.entryIds),
            )
            TodayEvent.OpenTodayScreenSettings -> router.navigate(TodayScreenSettingsRoute(fromToday = true))
            TodayEvent.OpenFasting -> router.navigate(FastingRoute())
            is TodayEvent.OpenDayNote -> router.navigate(DayNoteRoute(event.day.toString()))
            is TodayEvent.OpenItemSheet -> router.navigate(ItemSheetRoute(event.day.toString(), event.entryId))
            is TodayEvent.OpenLoggedFood -> router.navigate(LoggedFoodRoute(event.day.toString(), event.entryId))
            is TodayEvent.OpenWaterSheet -> router.navigate(WaterSheetRoute(event.day.toString()))
            is TodayEvent.OpenDayClosed -> router.navigate(DayClosedRoute(event.day.toString()))
            is TodayEvent.OpenPause -> router.navigate(event.change.toRoute())
            is TodayEvent.ShowToast -> showToast(event.toast)
        }
    }
}

private const val TAB_BAR_OWNER = "today-select"

/** The water sheet's height, a share of the screen's. */
private const val WATER_SHEET_HEIGHT = 0.5f

/** A Today sheet whose write the closed day refused gives way to the closed sheet, so Back lands on Today. */
private fun Router.showDayClosed(day: LocalDate) = batch {
    goBack()
    navigate(DayClosedRoute(day.toString()))
}

/**
 * Every Today sheet: the page colour, a grab handle, its own scroll. The sheet owns the scroll, so
 * it is pinned to the full height unless [heightFraction] pins it lower (BottomSheet's KDoc).
 */
@Composable
private fun TodaySheet(router: Router, sheetState: BottomSheetState, heightFraction: Float? = null, content: @Composable () -> Unit) {
    BottomSheet(
        onDismissRequest = { router.goBack() },
        state = sheetState,
        scrollableContent = true,
        heightFraction = heightFraction,
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
