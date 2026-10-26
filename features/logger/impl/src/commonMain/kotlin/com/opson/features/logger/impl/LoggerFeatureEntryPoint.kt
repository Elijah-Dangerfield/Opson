package com.opson.features.logger.impl

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.logger.LogRoute
import com.opson.features.logger.LogTab
import com.opson.features.logger.LoggerRoute
import com.opson.features.today.MealNoteRoute
import com.opson.features.trends.WeightRoute
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.nullableSerializableType
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetDefaults
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
    private val logViewModelFactory: (LogRoute) -> LogViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        bottomSheet<LoggerRoute>(
            typeMap = mapOf(typeOf<MealSlot?>() to nullableSerializableType<MealSlot>()),
        ) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<LoggerRoute>()
            val viewModel: LoggerViewModel = viewModel { loggerViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    is LoggerEvent.OpenAmount -> router.navigate(
                        AmountSheetRoute(
                            foodId = event.ref.id,
                            isCustom = event.ref is FoodRef.Custom,
                            source = event.source,
                        ),
                    )
                    // The logger gives way to what it opens, so Back from there lands on Today.
                    is LoggerEvent.OpenLog -> router.batch {
                        goBack()
                        navigate(LogRoute(event.day.toString(), event.slot, event.tab, event.focusNumber))
                    }
                    LoggerEvent.OpenWeight -> router.batch {
                        goBack()
                        navigate(WeightRoute())
                    }
                    is LoggerEvent.OpenMealNote -> router.batch {
                        goBack()
                        navigate(MealNoteRoute(event.day.toString(), event.slot))
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
                backgroundColor = AppTheme.colors.bg,
            ) {
                LoggerSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        bottomSheet<AmountSheetRoute>(
            typeMap = mapOf(typeOf<PickSource>() to serializableType<PickSource>()),
        ) { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<AmountSheetRoute>()
            val viewModel: AmountViewModel = viewModel { amountViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    is AmountEvent.ShowToast -> showToast(event.toast)
                    AmountEvent.Dismiss -> sheetState.dismiss()
                }
            }

            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                scrollableContent = true,
                showDragHandle = true,
                backgroundColor = AppTheme.colors.bg,
            ) {
                AmountSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        screen<LogRoute>(
            typeMap = mapOf(
                typeOf<MealSlot>() to serializableType<MealSlot>(),
                typeOf<LogTab>() to serializableType<LogTab>(),
            ),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<LogRoute>()
            val viewModel: LogViewModel = viewModel { logViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    is LogEvent.ShowToast -> showToast(event.toast)
                    LogEvent.Close -> router.goBack()
                }
            }

            LogScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}

private suspend fun showToast(toast: Toast) {
    val args = toast.args.map { if (it is StringResource) getString(it) else it }
    showSnackBar(message = getString(toast.message, *args.toTypedArray()))
}
