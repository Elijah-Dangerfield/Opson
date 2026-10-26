package com.opson.features.trends.impl

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.settings.toRoute
import com.opson.features.trends.NutrientsRange
import com.opson.features.trends.NutrientsRoute
import com.opson.features.trends.TrendsRoute
import com.opson.features.trends.TrendsSettingsRoute
import com.opson.features.trends.WeightRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.system.AppTheme
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
    private val nutrientsViewModelFactory: (day: LocalDate, range: NutrientsRange, fromTrends: Boolean) -> NutrientsViewModel,
    private val weightViewModelFactory: () -> WeightViewModel,
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
                }
            }
            TrendsScreen(state = state, onAction = viewModel::takeAction)
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
                    is NutrientsEvent.OpenPause -> router.navigate(event.change.toRoute())
                }
            }
            NutrientsScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<WeightRoute> {
            val viewModel: WeightViewModel = viewModel { weightViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    WeightEvent.Close -> router.goBack()
                    is WeightEvent.OpenPause -> router.navigate(event.change.toRoute())
                }
            }
            WeightScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
