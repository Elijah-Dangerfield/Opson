package com.opson.features.logger.impl

import com.opson.features.foods.EditCopyRoute
import com.opson.libraries.navigation.BugReportRoute
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.logger.ExerciseRoute
import com.opson.features.logger.FoodSearchRoute
import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.features.logger.SubmitEntry
import com.opson.features.logger.SubmitFoodRoute
import com.opson.features.settings.FastingRoute
import com.opson.features.today.DayClosedRoute
import com.opson.features.today.MealNoteRoute
import com.opson.features.trends.WeightRoute
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.RecentLogEvents
import com.opson.libraries.diary.ui.label
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.nullableSerializableType
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.speech.MicPermissionPrompt
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetDefaults
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.opson.libraries.ui.components.dialog.bottomsheet.expandOnDragUp
import com.opson.libraries.ui.components.dialog.bottomsheet.sheetHeightFraction
import com.opson.libraries.ui.snackbar.showSnackBar
import com.opson.libraries.ui.system.confirm
import com.opson.libraries.ui.system.toggle
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.adding_added
import opson.libraries.resources.generated.resources.adding_undo
import opson.libraries.resources.generated.resources.adding_water_added
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
@Suppress("LongParameterList")
class LoggerFeatureEntryPoint(
    private val loggerViewModelFactory: (LoggerRoute) -> LoggerViewModel,
    private val exerciseViewModelFactory: (ExerciseRoute) -> ExerciseSheetViewModel,
    private val searchViewModelFactory: (FoodSearchRoute) -> FoodSearchViewModel,
    private val detailViewModelFactory: (FoodDetailRoute) -> FoodDetailViewModel,
    private val barcodeScanViewModelFactory: (BarcodeScanRoute) -> BarcodeScanViewModel,
    private val barcodeCamera: BarcodeCamera,
    private val photoEstimateViewModelFactory: (PhotoEstimateRoute) -> PhotoEstimateViewModel,
    // A provider, so nothing about photos is built at launch; the sheet asks for it when it opens.
    private val mealPhotoPicker: () -> MealPhotoPicker,
    private val micPermissionPrompt: MicPermissionPrompt,
    private val searchOptionsViewModelFactory: (SearchOptionsRoute) -> SearchOptionsViewModel,
    // Providers, so the snackbars' Undo runs after the screen that offered it has gone.
    private val appScope: () -> AppCoroutineScope,
    private val recentLogEvents: () -> RecentLogEvents,
    private val diary: () -> DiaryRepository,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        bottomSheet<LoggerRoute>(
            typeMap = mapOf(
                typeOf<MealSlot?>() to nullableSerializableType<MealSlot>(),
                typeOf<LoggerStart>() to serializableType<LoggerStart>(),
            ),
        ) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<LoggerRoute>()
            val foodId = route.prePickFoodId
            if (foodId != null) {
                // A food already chosen goes straight to its detail; the sheet never shows.
                LaunchedEffect(Unit) {
                    router.batch {
                        goBack()
                        navigate(FoodDetailRoute(foodId, route.prePickIsCustom, PickSource.Foods, route.dayIso, route.slot))
                    }
                }
            } else {
                LoggerSheet(route, sheetState, router)
            }
        }

        screen<FoodSearchRoute>(
            typeMap = mapOf(typeOf<MealSlot?>() to nullableSerializableType<MealSlot>()),
        ) { backStackEntry ->
            FoodSearch(backStackEntry.toRoute<FoodSearchRoute>(), router)
        }

        screen<FoodDetailRoute>(
            typeMap = mapOf(
                typeOf<PickSource>() to serializableType<PickSource>(),
                typeOf<MealSlot?>() to nullableSerializableType<MealSlot>(),
            ),
        ) { backStackEntry ->
            FoodDetail(backStackEntry.toRoute<FoodDetailRoute>(), router)
        }

        // Only the + sheet's and search's barcode buttons open it, and both are behind release.barcode.
        screen<BarcodeScanRoute>(
            typeMap = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>()),
        ) { backStackEntry ->
            BarcodeScanner(backStackEntry.toRoute<BarcodeScanRoute>(), router)
        }

        // Only Photo on the + sheet opens it, behind release.photo. Packaged foods go through "Add this food" instead.
        bottomSheet<PhotoEstimateRoute>(
            typeMap = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>()),
        ) { backStackEntry, sheetState ->
            PhotoEstimateSheet(backStackEntry.toRoute<PhotoEstimateRoute>(), sheetState, router)
        }

        // Only the add sheet's Exercise and Today's Burned section open it, both behind release.exercise.
        bottomSheet<ExerciseRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<ExerciseRoute>()
            val viewModel: ExerciseSheetViewModel = viewModel { exerciseViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    is ExerciseSheetEvent.ShowToast -> showToast(event.toast)
                    // The sheet gives way to the closed one, so Back lands where it was opened.
                    is ExerciseSheetEvent.DayClosed -> router.batch {
                        goBack()
                        navigate(DayClosedRoute(event.day.toString()))
                    }
                    ExerciseSheetEvent.Dismiss -> sheetState.dismiss()
                }
            }

            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                showDragHandle = true,
                scrollableContent = true,
                backgroundColor = AppTheme.colors.bg,
            ) {
                ExerciseSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        // Only search's options button opens it, and that button is behind release.searchOptions.
        bottomSheet<SearchOptionsRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<SearchOptionsRoute>()
            val viewModel: SearchOptionsViewModel = viewModel { searchOptionsViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    SearchOptionsEvent.Dismiss -> sheetState.dismiss()
                }
            }

            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                showDragHandle = true,
                scrollableContent = true,
                backgroundColor = AppTheme.colors.bg,
            ) {
                SearchOptionsSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }
    }

    @Composable
    private fun LoggerSheet(route: LoggerRoute, sheetState: BottomSheetState, router: Router) {
        val viewModel: LoggerViewModel = viewModel { loggerViewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        // Only with release.voice on: off, nothing about the microphone is even composed.
        val requestMic: (() -> Unit)? = if (state.describe.voice != null) {
            LifecycleResumeEffect(viewModel) {
                onPauseOrDispose { viewModel.takeAction(VoiceAction.ScreenPaused) }
            }
            micPermissionPrompt.rememberPermissionRequest { answer ->
                viewModel.takeAction(VoiceAction.MicPermissionAnswered(answer))
            }
        } else {
            null
        }
        // The event collector starts with the first composition, before the flag has been read into state.
        val latestRequestMic by rememberUpdatedState(requestMic)

        viewModel.ObserveEvents { event ->
            when (event) {
                // Everything the grid opens takes the sheet's place, so Back from there lands where the sheet was opened.
                is LoggerEvent.OpenSearch -> router.batch {
                    goBack()
                    navigate(FoodSearchRoute(slot = event.slot, dayIso = event.day.toString()))
                }
                is LoggerEvent.OpenFoodDetail -> router.batch {
                    goBack()
                    navigate(FoodDetailRoute(event.ref.id, event.ref is FoodRef.Custom, event.source, event.day.toString(), event.slot))
                }
                is LoggerEvent.OpenMealNote -> router.batch {
                    goBack()
                    navigate(MealNoteRoute(event.day.toString(), event.slot))
                }
                is LoggerEvent.OpenScanner -> router.batch {
                    goBack()
                    navigate(BarcodeScanRoute(event.day.toString(), event.slot))
                }
                is LoggerEvent.OpenPhoto -> router.batch {
                    goBack()
                    navigate(PhotoEstimateRoute(event.day.toString(), event.slot))
                }
                is LoggerEvent.OpenPackagedFood -> router.batch {
                    goBack()
                    navigate(SubmitFoodRoute(event.day.toString(), event.slot, entry = SubmitEntry.AddSheet))
                }
                LoggerEvent.OpenWeighIn -> router.batch {
                    goBack()
                    navigate(WeightRoute())
                }
                LoggerEvent.OpenFasting -> router.batch {
                    goBack()
                    navigate(FastingRoute())
                }
                is LoggerEvent.OpenExercise -> router.batch {
                    goBack()
                    navigate(ExerciseRoute(dayIso = event.day.toString()))
                }
                is LoggerEvent.WaterAdded -> showWaterAdded(event.day, event.ml)
                // Animate the sheet down first; its dismiss callback pops the route.
                LoggerEvent.Dismiss -> sheetState.dismiss()
                RequestMicPermission -> latestRequestMic?.invoke()
            }
        }

        // Opens at 60% from a + so Today stays in view; grows to full height and stays there.
        val partial = state.height == SheetHeight.Partial
        val height = animateFloatAsState(
            targetValue = if (partial) BottomSheetDefaults.PartialHeightFraction else BottomSheetDefaults.FullHeightFraction,
            label = "LoggerHeight",
        )
        val expand = { viewModel.takeAction(LoggerAction.SheetExpanded) }
        BottomSheet(
            onDismissRequest = { router.goBack() },
            state = sheetState,
            // Read in layout, so growing does not recompose the sheet's content every frame.
            modifier = Modifier.sheetHeightFraction { height.value },
            showDragHandle = true,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expandOnDragUp(enabled = partial, onExpand = expand)
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { SheetHandle() }
            },
            // Back on Describe or Saved goes to the grid; the content handles that one.
            shouldDismissOnBackPress = state.pane == LoggerPane.Actions,
            backgroundColor = AppTheme.colors.bg,
        ) {
            LoggerSheetContent(
                state = state,
                onAction = viewModel::takeAction,
                modifier = Modifier.expandOnDragUp(enabled = partial, onExpand = expand),
            )
        }
    }

    @Composable
    private fun FoodSearch(route: FoodSearchRoute, router: Router) {
        val viewModel: FoodSearchViewModel = viewModel { searchViewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val haptics = LocalHapticFeedback.current

        viewModel.ObserveEvents { event ->
            when (event) {
                is SearchEvent.OpenFoodDetail -> router.navigate(
                    FoodDetailRoute(event.ref.id, event.ref is FoodRef.Custom, event.source, event.day.toString(), event.slot),
                )
                // Over search, so Back from these lands here.
                is SearchEvent.OpenScanner -> router.navigate(BarcodeScanRoute(event.day.toString(), event.slot, overSearch = true))
                is SearchEvent.OpenSubmitFood -> router.navigate(SubmitFoodRoute(event.day.toString(), event.slot, entry = event.entry))
                is SearchEvent.OpenDescribe -> router.navigate(
                    LoggerRoute(slot = event.slot, dayIso = event.day.toString(), start = LoggerStart.Describe, describeText = event.text),
                )
                // Over search: what is picked there applies to the results underneath as it is chosen.
                is SearchEvent.OpenSearchOptions -> router.navigate(SearchOptionsRoute(event.openFoodFacts))
                is SearchEvent.Added -> showAdded(event.name, event.event)
                is SearchEvent.Picked -> haptics.toggle(on = event.on)
                SearchEvent.Close -> router.goBack()
            }
        }

        FoodSearchScreen(state = state, onAction = viewModel::takeAction)
    }

    @Composable
    private fun FoodDetail(route: FoodDetailRoute, router: Router) {
        val viewModel: FoodDetailViewModel = viewModel { detailViewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val haptics = LocalHapticFeedback.current

        viewModel.ObserveEvents { event ->
            when (event) {
                FoodDetailEvent.Dismiss -> router.goBack()
                // Over the detail, so after Reopen the food is still there to add.
                is FoodDetailEvent.DayClosed -> router.navigate(DayClosedRoute(event.day.toString()))
                // Back to where it was opened: search draws ✓, Today takes it from RecentLogEvents.
                is FoodDetailEvent.Logged -> {
                    haptics.confirm()
                    router.goBack()
                }
                // TODO(review-queue): the review queue takes new packs (SubmitFoodRoute) but has no kind for a correction to a food it
                // already holds; once it does, file this there. Until then the bug report carries the food's ref.
                is FoodDetailEvent.OpenReport -> router.navigate(BugReportRoute(contextMessage = event.context))
                is FoodDetailEvent.OpenEditCopy -> router.navigate(
                    EditCopyRoute(
                        dayIso = event.day.toString(),
                        foodId = event.ref.id,
                        isCustom = event.ref is FoodRef.Custom,
                        slot = event.slot,
                    ),
                )
            }
        }

        Screen(containerColor = AppTheme.colors.bg.color) { padding ->
            FoodDetailContent(state = state, onAction = viewModel::takeAction, modifier = Modifier.padding(padding))
        }
    }

    @Composable
    private fun PhotoEstimateSheet(route: PhotoEstimateRoute, sheetState: BottomSheetState, router: Router) {
        val viewModel: PhotoEstimateViewModel = viewModel { photoEstimateViewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val picker = remember { mealPhotoPicker() }
        val launchCamera = picker.rememberCamera { viewModel.takeAction(PhotoEstimateAction.PhotoAnswered(it, fromCamera = true)) }
        val launchLibrary = picker.rememberLibrary { viewModel.takeAction(PhotoEstimateAction.PhotoAnswered(it, fromCamera = false)) }
        val requestPermission = barcodeCamera.rememberPermissionRequest { answer ->
            viewModel.takeAction(PhotoEstimateAction.CameraPermissionAnswered(answer))
        }
        val haptics = LocalHapticFeedback.current

        viewModel.ObserveEvents { event ->
            when (event) {
                PhotoEstimateEvent.LaunchCamera -> launchCamera()
                PhotoEstimateEvent.LaunchLibrary -> launchLibrary()
                PhotoEstimateEvent.RequestCameraPermission -> requestPermission()
                // This sheet gives way to the + sheet on Describe it.
                is PhotoEstimateEvent.OpenDescribe -> router.batch {
                    goBack()
                    navigate(LoggerRoute(slot = event.slot, dayIso = event.day.toString(), start = LoggerStart.Describe))
                }
                // Back to where it was opened. Today takes it from RecentLogEvents; search draws ✓.
                is PhotoEstimateEvent.Logged -> {
                    haptics.confirm()
                    router.goBack()
                }
                is PhotoEstimateEvent.DayClosed -> router.batch {
                    goBack()
                    navigate(DayClosedRoute(event.day.toString()))
                }
                PhotoEstimateEvent.Dismiss -> sheetState.dismiss()
            }
        }

        BottomSheet(
            onDismissRequest = { router.goBack() },
            state = sheetState,
            heightFraction = BottomSheetDefaults.FullHeightFraction,
            showDragHandle = true,
            // Back while swapping a food returns to the list; the content handles that one.
            shouldDismissOnBackPress = state.stage !is PhotoStage.Swap,
            backgroundColor = AppTheme.colors.bg,
        ) {
            PhotoEstimateSheetContent(state = state, onAction = viewModel::takeAction)
        }
    }

    @Composable
    private fun BarcodeScanner(route: BarcodeScanRoute, router: Router) {
        val viewModel: BarcodeScanViewModel = viewModel { barcodeScanViewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val requestPermission = barcodeCamera.rememberPermissionRequest { answer ->
            viewModel.takeAction(BarcodeScanAction.PermissionAnswered(answer))
        }
        LifecycleResumeEffect(viewModel) {
            viewModel.takeAction(BarcodeScanAction.Resumed)
            onPauseOrDispose { }
        }
        val haptics = LocalHapticFeedback.current

        viewModel.ObserveEvents { event ->
            when (event) {
                BarcodeScanEvent.RequestPermission -> requestPermission()
                BarcodeScanEvent.Scanned -> haptics.confirm()
                // The food detail takes this screen's place.
                is BarcodeScanEvent.OpenFoodDetail -> router.batch {
                    goBack()
                    navigate(FoodDetailRoute(event.foodId, isCustom = false, source = PickSource.Barcode, dayIso = event.day.toString(), slot = event.slot))
                }
                // The pack's photos take the scanner's place, for the code that missed.
                is BarcodeScanEvent.OpenSubmitFood -> router.batch {
                    goBack()
                    navigate(SubmitFoodRoute(event.day.toString(), event.slot, barcode = event.code, entry = SubmitEntry.BarcodeMiss))
                }
                is BarcodeScanEvent.OpenSearch -> router.batch {
                    goBack()
                    navigate(FoodSearchRoute(slot = event.slot, dayIso = event.day.toString()))
                }
                BarcodeScanEvent.Close -> router.goBack()
                is BarcodeScanEvent.DayClosed -> router.navigate(DayClosedRoute(event.day.toString()))
            }
        }

        BarcodeScanScreen(
            state = state,
            onAction = viewModel::takeAction,
            viewfinder = { request, modifier ->
                barcodeCamera.Viewfinder(
                    onCode = { viewModel.takeAction(BarcodeScanAction.CodeRead(it)) },
                    onUnavailable = { viewModel.takeAction(BarcodeScanAction.CameraUnavailable) },
                    scanArea = request.scanArea,
                    torchOn = request.torchOn,
                    onTorchAvailable = { viewModel.takeAction(BarcodeScanAction.CameraStarted(hasTorch = it)) },
                    focusPoint = request.focusPoint,
                    modifier = modifier,
                )
            },
        )
    }

    /** "Jasmine rice added to Dinner", with Undo for exactly those items, even once search has closed. */
    private suspend fun showAdded(name: String, event: LoggedToMeal) {
        showSnackBar(
            message = getString(Res.string.adding_added, name, getString(event.slot.label)),
            actionLabel = getString(Res.string.adding_undo),
            onAction = { appScope().launch { recentLogEvents().undo(event) } },
        )
    }

    private suspend fun showWaterAdded(day: LocalDate, ml: Int) {
        showSnackBar(
            message = getString(Res.string.adding_water_added),
            actionLabel = getString(Res.string.adding_undo),
            onAction = { appScope().launch { diary().removeWater(day, ml) } },
        )
    }
}

private suspend fun showToast(toast: Toast) {
    val args = toast.args.map { if (it is StringResource) getString(it) else it }
    showSnackBar(message = getString(toast.message, *args.toTypedArray()))
}
