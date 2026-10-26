package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetDefaults
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
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
                    is AmountEvent.ShowToast -> showToast(event.toast)
                    AmountEvent.Dismiss -> sheetState.dismiss()
                    // Both sheets go: this one and the logger under it. Today takes it from RecentLogEvents.
                    is AmountEvent.Logged -> router.batch {
                        goBack()
                        goBack()
                        if (event.committed.askHowItFelt) navigate(HowItFeltRoute(event.day.toString(), event.committed.slot))
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
                is LoggerEvent.OpenHowItFelt -> router.batch {
                    goBack()
                    navigate(HowItFeltRoute(event.day.toString(), event.slot))
                }
                is LoggerEvent.ShowToast -> showToast(event.toast)
                // Animate the sheet down first; its dismiss callback pops the route.
                LoggerEvent.Dismiss -> sheetState.dismiss()
            }
        }

        BottomSheet(
            onDismissRequest = { router.goBack() },
            state = sheetState,
            heightFraction = BottomSheetDefaults.FullHeightFraction,
            showDragHandle = true,
            // Back on Describe or Saved goes to search; the content handles that one.
            shouldDismissOnBackPress = state.pane == LoggerPane.Search,
            backgroundColor = AppTheme.colors.bg,
        ) {
            LoggerSheetContent(state = state, onAction = viewModel::takeAction)
        }
    }
}

private suspend fun showToast(toast: Toast) {
    val args = toast.args.map { if (it is StringResource) getString(it) else it }
    showSnackBar(message = getString(toast.message, *args.toTypedArray()))
}
