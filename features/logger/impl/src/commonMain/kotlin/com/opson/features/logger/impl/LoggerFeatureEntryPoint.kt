package com.opson.features.logger.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.logger.HowItFeltRoute
import com.opson.features.logger.LogRoute
import com.opson.features.logger.LogTab
import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.features.today.MealNoteRoute
import com.opson.libraries.diary.MealSlot
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
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetDefaults
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.opson.libraries.ui.components.dialog.bottomsheet.expandOnDragUp
import com.opson.libraries.ui.components.dialog.bottomsheet.sheetHeightFraction
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.snackbar.showSnackBar
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class LoggerFeatureEntryPoint(
    private val loggerViewModelFactory: (LoggerRoute) -> LoggerViewModel,
    private val amountViewModelFactory: (AmountSheetRoute) -> AmountViewModel,
    private val howItFeltViewModelFactory: (HowItFeltRoute) -> HowItFeltViewModel,
    private val barcodeScanViewModelFactory: (BarcodeScanRoute) -> BarcodeScanViewModel,
    private val barcodeCamera: BarcodeCamera,
    private val photoEstimateViewModelFactory: (PhotoEstimateRoute) -> PhotoEstimateViewModel,
    // A provider, so nothing about photos is built at launch; the sheet asks for it when it opens.
    private val mealPhotoPicker: () -> MealPhotoPicker,
    private val micPermissionPrompt: MicPermissionPrompt,
    private val searchOptionsViewModelFactory: (SearchOptionsRoute) -> SearchOptionsViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        bottomSheet<LoggerRoute>(
            typeMap = mapOf(
                typeOf<MealSlot?>() to nullableSerializableType<MealSlot>(),
                typeOf<LoggerStart>() to serializableType<LoggerStart>(),
            ),
        ) { backStackEntry, sheetState ->
            LoggerSheet(backStackEntry.toRoute<LoggerRoute>(), sheetState, router)
        }

        // The Log screen's old route, kept as an alias: the same sheet on the matching pane.
        bottomSheet<LogRoute>(
            typeMap = mapOf(
                typeOf<MealSlot>() to serializableType<MealSlot>(),
                typeOf<LogTab>() to serializableType<LogTab>(),
            ),
        ) { backStackEntry, sheetState ->
            LoggerSheet(backStackEntry.toRoute<LogRoute>().toLoggerRoute(), sheetState, router)
        }

        bottomSheet<AmountSheetRoute>(
            typeMap = mapOf(
                typeOf<PickSource>() to serializableType<PickSource>(),
                typeOf<MealSlot>() to serializableType<MealSlot>(),
            ),
        ) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<AmountSheetRoute>()
            val viewModel: AmountViewModel = viewModel { amountViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    AmountEvent.Dismiss -> sheetState.dismiss()
                    // Both sheets go: this one and the logger under it. Today takes it from RecentLogEvents.
                    is AmountEvent.Logged -> router.batch {
                        goBack()
                        goBack()
                    }
                }
            }

            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                heightFraction = AMOUNT_HEIGHT_FRACTION,
                showDragHandle = true,
                backgroundColor = AppTheme.colors.bg,
            ) {
                AmountSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        // Only the logger's barcode button opens it, and that button is behind release.barcode.
        screen<BarcodeScanRoute>(
            typeMap = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>()),
        ) { backStackEntry ->
            BarcodeScanner(backStackEntry.toRoute<BarcodeScanRoute>(), router)
        }

        // Only the logger's Photo chip opens it, and that chip is behind release.photo.
        bottomSheet<PhotoEstimateRoute>(
            typeMap = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>()),
        ) { backStackEntry, sheetState ->
            PhotoEstimateSheet(backStackEntry.toRoute<PhotoEstimateRoute>(), sheetState, router)
        }

        // Only the logger's options button opens it, and that button is behind release.searchOptions.
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

        bottomSheet<HowItFeltRoute>(
            typeMap = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>()),
        ) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<HowItFeltRoute>()
            val viewModel: HowItFeltViewModel = viewModel { howItFeltViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    HowItFeltEvent.Dismiss -> sheetState.dismiss()
                }
            }

            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                heightFraction = HOW_IT_FELT_HEIGHT_FRACTION,
                showDragHandle = true,
                backgroundColor = AppTheme.colors.bg,
            ) {
                HowItFeltSheetContent(state = state, onAction = viewModel::takeAction)
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
                is LoggerEvent.OpenAmount -> router.navigate(
                    AmountSheetRoute(
                        foodId = event.ref.id,
                        isCustom = event.ref is FoodRef.Custom,
                        source = event.source,
                        dayIso = event.day.toString(),
                        slot = event.slot,
                    ),
                )
                // The logger gives way to what it opens, so Back from there lands where it was opened.
                is LoggerEvent.OpenMealNote -> router.batch {
                    goBack()
                    navigate(MealNoteRoute(event.day.toString(), event.slot))
                }
                // Over the sheet, so Back from the scanner lands on the logger and its basket.
                is LoggerEvent.OpenScanner -> router.navigate(BarcodeScanRoute(event.day.toString(), event.slot))
                is LoggerEvent.OpenPhoto -> router.navigate(PhotoEstimateRoute(event.day.toString(), event.slot))
                // Over the logger: what is picked there applies to the search underneath as it is chosen.
                is LoggerEvent.OpenSearchOptions -> router.navigate(SearchOptionsRoute(event.openFoodFacts))
                is LoggerEvent.ShowToast -> showToast(event.toast)
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
            // Back on Describe or Saved goes to search; the content handles that one.
            shouldDismissOnBackPress = state.pane == LoggerPane.Search,
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
    private fun PhotoEstimateSheet(route: PhotoEstimateRoute, sheetState: BottomSheetState, router: Router) {
        val viewModel: PhotoEstimateViewModel = viewModel { photoEstimateViewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val picker = remember { mealPhotoPicker() }
        val launchCamera = picker.rememberCamera { viewModel.takeAction(PhotoEstimateAction.PhotoAnswered(it, fromCamera = true)) }
        val launchLibrary = picker.rememberLibrary { viewModel.takeAction(PhotoEstimateAction.PhotoAnswered(it, fromCamera = false)) }
        val requestPermission = barcodeCamera.rememberPermissionRequest { answer ->
            viewModel.takeAction(PhotoEstimateAction.CameraPermissionAnswered(answer))
        }

        viewModel.ObserveEvents { event ->
            when (event) {
                PhotoEstimateEvent.LaunchCamera -> launchCamera()
                PhotoEstimateEvent.LaunchLibrary -> launchLibrary()
                PhotoEstimateEvent.RequestCameraPermission -> requestPermission()
                // This sheet and the logger go; the logger comes back on Describe it, its basket intact.
                is PhotoEstimateEvent.OpenDescribe -> router.batch {
                    goBack()
                    goBack()
                    navigate(LoggerRoute(slot = event.slot, dayIso = event.day.toString(), start = LoggerStart.Describe))
                }
                // Both sheets go, as after the amount sheet's Add. Today takes it from RecentLogEvents.
                is PhotoEstimateEvent.Logged -> router.batch {
                    goBack()
                    goBack()
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

        viewModel.ObserveEvents { event ->
            when (event) {
                BarcodeScanEvent.RequestPermission -> requestPermission()
                // The amount sheet takes this screen's place over the logger, as it does for a tapped search row.
                is BarcodeScanEvent.OpenAmount -> router.batch {
                    goBack()
                    navigate(
                        AmountSheetRoute(
                            foodId = event.foodId,
                            isCustom = false,
                            source = PickSource.Barcode,
                            dayIso = event.day.toString(),
                            slot = event.slot,
                        ),
                    )
                }
                BarcodeScanEvent.Close -> router.goBack()
            }
        }

        BarcodeScanScreen(
            state = state,
            onAction = viewModel::takeAction,
            viewfinder = { modifier ->
                barcodeCamera.Viewfinder(
                    onCode = { viewModel.takeAction(BarcodeScanAction.CodeRead(it)) },
                    onUnavailable = { viewModel.takeAction(BarcodeScanAction.CameraUnavailable) },
                    modifier = modifier,
                )
            },
        )
    }
}

private suspend fun showToast(toast: Toast) {
    val args = toast.args.map { if (it is StringResource) getString(it) else it }
    showSnackBar(message = getString(toast.message, *args.toTypedArray()))
}
