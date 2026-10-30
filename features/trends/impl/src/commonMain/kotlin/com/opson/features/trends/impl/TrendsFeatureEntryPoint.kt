package com.opson.features.trends.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.settings.EnergyExplainerRoute
import com.opson.features.settings.IntegrationsRoute
import com.opson.features.settings.ModesRoute
import com.opson.features.trends.NotesListLink
import com.opson.features.trends.NutrientFoodsRoute
import com.opson.features.trends.NutrientsRange
import com.opson.features.trends.NutrientsRoute
import com.opson.features.trends.PatternsRoute
import com.opson.features.trends.ProgressPhotoCaptureRoute
import com.opson.features.trends.ProgressPhotoRoute
import com.opson.features.trends.ProgressPhotosCompareRoute
import com.opson.features.trends.ProgressPhotosRoute
import com.opson.features.today.TodayRoute
import com.opson.features.trends.TrendsDayRoute
import com.opson.features.trends.TrendsRoute
import com.opson.features.trends.TrendsSettingsRoute
import com.opson.features.trends.WeightRoute
import com.opson.features.trends.impl.photos.CaptureAction
import com.opson.features.trends.impl.photos.CaptureEvent
import com.opson.features.trends.impl.photos.CaptureScreen
import com.opson.features.trends.impl.photos.CaptureViewModel
import com.opson.features.trends.impl.photos.CompareAction
import com.opson.features.trends.impl.photos.CompareEvent
import com.opson.features.trends.impl.photos.CompareScreen
import com.opson.features.trends.impl.photos.CompareViewModel
import com.opson.features.trends.impl.photos.PhotoAction
import com.opson.features.trends.impl.photos.PhotoEvent
import com.opson.features.trends.impl.photos.PhotoScreen
import com.opson.features.trends.impl.photos.PhotoViewModel
import com.opson.features.trends.impl.photos.ProgressPhotosAction
import com.opson.features.trends.impl.photos.ProgressPhotosEvent
import com.opson.features.trends.impl.photos.ProgressPhotosScreen
import com.opson.features.trends.impl.photos.ProgressPhotosViewModel
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.OnTabReselected
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.StillCamera
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.system.AppTheme
import com.opson.libraries.nutrition.Nutrient
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class TrendsFeatureEntryPoint(
    private val trendsViewModelFactory: () -> TrendsViewModel,
    private val trendsSettingsViewModelFactory: () -> TrendsSettingsViewModel,
    private val trendsDayViewModelFactory: (day: LocalDate) -> TrendsDayViewModel,
    private val nutrientsViewModelFactory: (day: LocalDate, range: NutrientsRange, fromTrends: Boolean) -> NutrientsViewModel,
    private val nutrientFoodsViewModelFactory: (day: LocalDate, nutrient: Nutrient, range: NutrientsRange) -> NutrientFoodsViewModel,
    private val weightViewModelFactory: () -> WeightViewModel,
    private val patternsViewModelFactory: () -> PatternsViewModel,
    private val progressPhotosViewModelFactory: () -> ProgressPhotosViewModel,
    private val compareViewModelFactory: (leftId: String?, rightId: String?) -> CompareViewModel,
    private val photoViewModelFactory: (id: String) -> PhotoViewModel,
    private val captureViewModelFactory: () -> CaptureViewModel,
    private val stillCamera: StillCamera,
    private val cameraPrompt: BarcodeCamera,
    /** The Notes list's route, which Today binds (see [NotesListLink]). */
    private val notesListLink: NotesListLink? = null,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<TrendsRoute> {
            val viewModel: TrendsViewModel = viewModel { trendsViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    TrendsEvent.OpenTrendsSettings -> router.navigate(TrendsSettingsRoute())
                    is TrendsEvent.OpenNutrients ->
                        router.navigate(NutrientsRoute(event.dayIso, NutrientsRange.WeekAverage, fromTrends = true))
                    TrendsEvent.OpenWeight -> router.navigate(WeightRoute())
                    TrendsEvent.OpenPatterns -> router.navigate(PatternsRoute())
                    TrendsEvent.OpenNotes -> notesListLink?.let { router.navigate(it.route()) }
                    is TrendsEvent.OpenDay -> router.navigate(TrendsDayRoute(event.dayIso))
                    is TrendsEvent.OpenEnergyExplainer -> router.navigate(EnergyExplainerRoute(event.topic))
                    TrendsEvent.OpenIntegrations -> router.navigate(IntegrationsRoute())
                }
            }
            val scrollState = rememberScrollState()
            router.OnTabReselected(TrendsRoute::class) { scrollState.animateScrollTo(0) }
            TrendsScreen(state = state, onAction = viewModel::takeAction, scrollState = scrollState)
        }

        bottomSheet<TrendsSettingsRoute> { _, sheetState ->
            val viewModel: TrendsSettingsViewModel = viewModel { trendsSettingsViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    // Animate the sheet down first; its dismiss callback pops the route.
                    TrendsSettingsEvent.Dismiss -> sheetState.dismiss()
                }
            }
            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                backgroundColor = AppTheme.colors.bg,
            ) {
                TrendsSettingsSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        bottomSheet<TrendsDayRoute> { entry, sheetState ->
            val route = entry.toRoute<TrendsDayRoute>()
            val viewModel: TrendsDayViewModel = viewModel { trendsDayViewModelFactory(LocalDate.parse(route.dayIso)) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    TrendsDayEvent.Dismiss -> sheetState.dismiss()
                    // Today already shows the day: SelectedDay moved before this event.
                    TrendsDayEvent.OpenToday -> router.batch {
                        goBack()
                        switchTab(TodayRoute())
                    }
                }
            }
            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                backgroundColor = AppTheme.colors.bg,
            ) {
                TrendsDaySheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        screen<NutrientsRoute>(
            typeMap = mapOf(typeOf<NutrientsRange>() to serializableType<NutrientsRange>()),
        ) { entry ->
            val route = entry.toRoute<NutrientsRoute>()
            val viewModel: NutrientsViewModel = viewModel {
                nutrientsViewModelFactory(LocalDate.parse(route.dayIso), route.range, route.fromTrends)
            }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    NutrientsEvent.Close -> router.goBack()
                    NutrientsEvent.OpenModes -> router.navigate(ModesRoute())
                    is NutrientsEvent.OpenFoods -> router.navigate(NutrientFoodsRoute(route.dayIso, event.nutrient.key, event.range))
                }
            }
            NutrientsScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<NutrientFoodsRoute>(
            typeMap = mapOf(typeOf<NutrientsRange>() to serializableType<NutrientsRange>()),
        ) { entry, sheetState ->
            val route = entry.toRoute<NutrientFoodsRoute>()
            // A key this build does not know (a link from a newer one) has nothing to list.
            val nutrient = Nutrient.fromKey(route.nutrient)
            if (nutrient == null) {
                LaunchedEffect(route) { router.goBack() }
                return@bottomSheet
            }
            val viewModel: NutrientFoodsViewModel = viewModel {
                nutrientFoodsViewModelFactory(LocalDate.parse(route.dayIso), nutrient, route.range)
            }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    NutrientFoodsEvent.Dismiss -> sheetState.dismiss()
                }
            }
            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                backgroundColor = AppTheme.colors.bg,
                scrollableContent = true,
            ) {
                NutrientFoodsSheetContent(state = state, nutrient = nutrient, onAction = viewModel::takeAction)
            }
        }

        screen<WeightRoute> { backStackEntry ->
            val focusInput = remember(backStackEntry) { backStackEntry.toRoute<WeightRoute>().focusInput }
            val viewModel: WeightViewModel = viewModel { weightViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    WeightEvent.Close -> router.goBack()
                    WeightEvent.OpenProgressPhotos -> router.navigate(ProgressPhotosRoute())
                }
            }
            WeightScreen(state = state, onAction = viewModel::takeAction, focusInput = focusInput)
        }

        screen<PatternsRoute> {
            val viewModel: PatternsViewModel = viewModel { patternsViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    PatternsEvent.Close -> router.goBack()
                }
            }
            PatternsScreen(state = state, onAction = viewModel::takeAction)
        }

        progressPhotos(router)
    }

    /**
     * Progress photos. Only Weight's row opens the grid, and that row is behind
     * `release.progressPhotos`. Every screen asks for the phone's lock again when
     * it comes back to the front locked (the lock itself locks on background).
     */
    private fun NavGraphBuilder.progressPhotos(router: Router) {
        screen<ProgressPhotosRoute> {
            val viewModel: ProgressPhotosViewModel = viewModel { progressPhotosViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.takeAction(ProgressPhotosAction.Foregrounded) }
            viewModel.ObserveEvents { event ->
                when (event) {
                    is ProgressPhotosEvent.OpenPhoto -> router.navigate(ProgressPhotoRoute(event.id))
                    is ProgressPhotosEvent.OpenCompare -> router.navigate(ProgressPhotosCompareRoute(event.leftId, event.rightId))
                    ProgressPhotosEvent.OpenCapture -> router.navigate(ProgressPhotoCaptureRoute())
                    ProgressPhotosEvent.Close -> router.goBack()
                }
            }
            ProgressPhotosScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<ProgressPhotosCompareRoute> { entry ->
            val route = entry.toRoute<ProgressPhotosCompareRoute>()
            val viewModel: CompareViewModel = viewModel { compareViewModelFactory(route.leftId, route.rightId) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.takeAction(CompareAction.Foregrounded) }
            viewModel.ObserveEvents { event ->
                when (event) {
                    CompareEvent.Close -> router.goBack()
                }
            }
            CompareScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<ProgressPhotoRoute> { entry ->
            val route = entry.toRoute<ProgressPhotoRoute>()
            val viewModel: PhotoViewModel = viewModel { photoViewModelFactory(route.id) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.takeAction(PhotoAction.Foregrounded) }
            viewModel.ObserveEvents { event ->
                when (event) {
                    PhotoEvent.Close -> router.goBack()
                }
            }
            PhotoScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<ProgressPhotoCaptureRoute> {
            val viewModel: CaptureViewModel = viewModel { captureViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            val requestPermission = cameraPrompt.rememberPermissionRequest { answer ->
                viewModel.takeAction(CaptureAction.PermissionAnswered(answer))
            }
            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.takeAction(CaptureAction.Foregrounded) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.takeAction(CaptureAction.Resumed) }
            viewModel.ObserveEvents { event ->
                when (event) {
                    CaptureEvent.RequestPermission -> requestPermission()
                    CaptureEvent.Close -> router.goBack()
                }
            }
            CaptureScreen(
                state = state,
                onAction = viewModel::takeAction,
                viewfinder = { modifier ->
                    if (state.cameraOn) {
                        stillCamera.Viewfinder(
                            facing = state.facing,
                            shutter = state.shutter,
                            onResult = { viewModel.takeAction(CaptureAction.Captured(it)) },
                            onUnavailable = { viewModel.takeAction(CaptureAction.CameraUnavailable) },
                            modifier = modifier,
                        )
                    }
                },
            )
        }
    }
}
