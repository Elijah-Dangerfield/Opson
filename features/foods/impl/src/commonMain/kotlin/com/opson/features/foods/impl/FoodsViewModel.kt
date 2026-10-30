package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.FoodSearchRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.diary.RecipesRelease
import com.opson.libraries.diary.RepeatRepository
import com.opson.libraries.diary.RepeatRule
import com.opson.libraries.diary.RepeatsRelease
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import com.opson.features.builder.VerifyFoodAvailable
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.diary.LocalCalendar
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The Foods tab: favorites, saved meals, recipes (`release.recipes`), repeats (`release.repeats`), the user's own foods and
 * recent ones, each a way into the logger with that food already picked or into its editor, and
 * for an active Builder, Verify a food. Everything it reads is on the device.
 */
@Inject
class FoodsViewModel(
    customFoods: CustomFoodRepository,
    savedMeals: SavedMealRepository,
    diary: DiaryRepository,
    policySource: VisibilityPolicySource,
    submittedFoods: SubmittedFoods,
    repeats: RepeatRepository,
    repeatsRelease: RepeatsRelease,
    settingsRepository: SettingsRepository,
    foodCatalog: FoodCatalog,
    private val calendar: LocalCalendar,
    recipes: RecipeRepository,
    recipesRelease: RecipesRelease,
    verifyFood: VerifyFoodAvailable = VerifyFoodAvailable { false },
) : SEAViewModel<FoodsState, FoodsEvent, FoodsAction>(initialStateArg = FoodsState()) {

    private var inputs: FoodsInputs? = null
    private var policy: VisibilityPolicy = policySource.policy.value

    init {
        viewModelScope.launch {
            val rules: Flow<List<RepeatRule>?> = if (repeatsRelease.isOn()) repeats.observeAll() else flowOf(null)
            val verifyOn = verifyFood()
            val base = combine(customFoods.observeAll(), savedMeals.observeAll(), diary.recentFoods(limit = RECENT_LIMIT), submittedFoods.reviews, rules, ::FoodsInputs)
            val starred = settingsRepository.settings.map { it.favoriteFoods }.distinctUntilChanged()
            val known = combine(foodCatalog.observeFavorites(), foodCatalog.observeRecent(limit = KNOWN_LIMIT)) { favorites, recent ->
                (recent + favorites).associateBy { it.id }
            }
            val recipeList: Flow<List<Recipe>?> = if (recipesRelease.isOn()) recipes.observeAll() else flowOf(null)
            combine(base, starred, known, diary.recentFoods(limit = HISTORY_LIMIT), recipeList) { inputs, keys, catalog, history, list ->
                inputs.copy(favoriteKeys = keys, catalog = catalog, history = history, verifyOn = verifyOn, recipes = list)
            }.collect { takeAction(FoodsAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(FoodsAction.PolicyArrived(it)) }
        }
    }

    override suspend fun handleAction(action: FoodsAction) {
        when (action) {
            is FoodsAction.InputsArrived -> {
                inputs = action.inputs
                action.rebuild()
            }
            is FoodsAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            FoodsAction.SearchTapped -> sendEvent(FoodsEvent.OpenLogger(FoodSearchRoute()))
            FoodsAction.CreateMealTapped -> sendEvent(FoodsEvent.OpenCreateMeal)
            FoodsAction.AddFoodTapped -> sendEvent(FoodsEvent.OpenAddFood)
            FoodsAction.VerifyTapped -> if (inputs?.verifyOn == true) {
                sendEvent(FoodsEvent.OpenVerify(calendar.today().toString()))
            }
            is FoodsAction.RowTapped -> sendEvent(FoodsEvent.OpenLogger(action.target.toLoggerRoute()))
            FoodsAction.NewRepeatTapped -> sendEvent(FoodsEvent.OpenRepeat(ruleId = null))
            is FoodsAction.RepeatTapped -> sendEvent(FoodsEvent.OpenRepeat(action.id))
            is FoodsAction.RecipeTapped -> sendEvent(FoodsEvent.OpenRecipe(action.id))
            FoodsAction.NewRecipeTapped -> sendEvent(FoodsEvent.OpenNewRecipe)
            FoodsAction.ScanRecipeTapped -> sendEvent(FoodsEvent.OpenScanRecipe)
        }
    }

    private suspend fun FoodsAction.rebuild() {
        val current = inputs ?: return
        val currentPolicy = policy
        updateState { FoodsStateBuilder.build(current, currentPolicy) }
    }

    companion object {
        const val RECENT_LIMIT = 6

        /** How far back a favorite's last amount and the catalog foods the device knows reach. */
        const val HISTORY_LIMIT = 100
        const val KNOWN_LIMIT = 100
    }
}
