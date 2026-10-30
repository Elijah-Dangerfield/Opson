package com.opson.features.foods.impl

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.foods.AddFoodRoute
import com.opson.features.foods.CreateMealRoute
import com.opson.features.foods.EditCopyRoute
import com.opson.features.foods.FoodsRoute
import com.opson.features.foods.RepeatRoute
import com.opson.features.logger.FoodSearchRoute
import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.SubmitEntry
import com.opson.features.logger.SubmitFoodRoute
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.OnTabReselected
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.nullableSerializableType
import com.opson.libraries.navigation.screen
import com.opson.libraries.ui.snackbar.showSnackBar
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_copy_mine
import opson.libraries.resources.generated.resources.foods_meal_prefill_name
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf
import com.opson.features.logger.CheckPackRoute

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class FoodsFeatureEntryPoint(
    private val foodsViewModelFactory: () -> FoodsViewModel,
    private val createMealViewModelFactory: (CreateMealRoute, String?) -> CreateMealViewModel,
    private val addFoodViewModelFactory: () -> AddFoodViewModel,
    private val editCopyViewModelFactory: (EditCopyRoute, CopyName) -> EditCopyViewModel,
    private val repeatViewModelFactory: (RepeatRoute) -> RepeatViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<FoodsRoute> {
            val viewModel: FoodsViewModel = viewModel { foodsViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                router.navigate(
                    when (event) {
                        is FoodsEvent.OpenLogger -> event.route
                        FoodsEvent.OpenCreateMeal -> CreateMealRoute()
                        FoodsEvent.OpenAddFood -> AddFoodRoute()
                        is FoodsEvent.OpenVerify -> CheckPackRoute(event.dayIso)
                        is FoodsEvent.OpenRepeat -> RepeatRoute(ruleId = event.ruleId)
                    },
                )
            }
            val listState = rememberLazyListState()
            router.OnTabReselected(FoodsRoute::class) { listState.animateScrollToItem(0) }
            FoodsScreen(state = state, onAction = viewModel::takeAction, listState = listState)
        }

        screen<CreateMealRoute>(
            typeMap = mapOf(typeOf<MealSlot?>() to nullableSerializableType<MealSlot>()),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<CreateMealRoute>()
            // "My breakfast" for a meal saved from Today; resolved here so the view model needs no resources.
            val prefillName = route.fromSlot?.let { stringResource(Res.string.foods_meal_prefill_name, stringResource(it.sentenceName)) }
            val viewModel: CreateMealViewModel = viewModel { createMealViewModelFactory(route, prefillName) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is CreateMealEvent.OpenPicker -> router.navigate(FoodSearchRoute(pickForMeal = event.requestId))
                    is CreateMealEvent.ShowToast -> show(event.toast)
                    CreateMealEvent.Close -> router.goBack()
                }
            }
            CreateMealScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<AddFoodRoute> {
            val viewModel: AddFoodViewModel = viewModel { addFoodViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is AddFoodEvent.ShowToast -> show(event.toast)
                    AddFoodEvent.Close -> router.goBack()
                    // Over this form, so backing out of the photos returns to typing; saving there closes both.
                    AddFoodEvent.OpenPack -> router.navigate(SubmitFoodRoute(entry = SubmitEntry.Foods))
                }
            }
            AddFoodScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<RepeatRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<RepeatRoute>()
            val viewModel: RepeatViewModel = viewModel { repeatViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is RepeatEvent.OpenPicker -> router.navigate(FoodSearchRoute(pickForMeal = event.requestId))
                    is RepeatEvent.ShowToast -> show(event.toast)
                    RepeatEvent.Close -> router.goBack()
                }
            }
            RepeatScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<EditCopyRoute>(
            typeMap = mapOf(typeOf<MealSlot?>() to nullableSerializableType<MealSlot>()),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<EditCopyRoute>()
            // "Peanut butter (mine)", worded here so the view model needs no resources.
            val viewModel: EditCopyViewModel = viewModel {
                editCopyViewModelFactory(route, CopyName { getString(Res.string.foods_copy_mine, it) })
            }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is EditCopyEvent.ShowToast -> show(event.toast)
                    EditCopyEvent.Close -> router.goBack()
                    // The food detail it came from goes too: the copy's detail takes its place, to log it.
                    is EditCopyEvent.OpenCopy -> router.batch {
                        goBack()
                        goBack()
                        navigate(LoggerRoute(dayIso = event.dayIso, slot = event.slot, prePickFoodId = event.foodId, prePickIsCustom = true))
                    }
                }
            }
            EditCopyScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}

private suspend fun show(toast: Toast) {
    val args = toast.args.map { if (it is StringResource) getString(it) else it }
    showSnackBar(message = getString(toast.message, *args.toTypedArray()))
}
