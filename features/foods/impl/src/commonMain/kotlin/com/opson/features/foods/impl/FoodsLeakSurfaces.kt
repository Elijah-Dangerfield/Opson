package com.opson.features.foods.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The Foods screens for `BlindModeLeakTest` (`docs/plans/screens/blind-leak-test.md`). Each
 * builds its state through the real builder from [FoodsFixtures], with the policy the test hands
 * it. Ids are design v3's screen ids. `foods-add-food` renders in whatever mode the test picks,
 * so in Blind it is the same screen as `foods-add-food-blind`; the second id keeps v3's Blind
 * screen on the required list by name.
 */
object FoodsLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(id = "foods", label = "Foods tab", showsCaloriesInFull = true) { policy ->
            FoodsScreen(state = FoodsFixtures.foodsState(policy), onAction = {})
        },
        LeakSurface(id = "foods-create-meal", label = "Create a meal", showsCaloriesInFull = true) { policy ->
            CreateMealScreen(state = FoodsFixtures.createMealState(policy), onAction = {})
        },
        // Full has a Calories field holding 120 next to "kcal", which scans as "120 kcal".
        LeakSurface(id = "foods-add-food", label = "Custom food", showsCaloriesInFull = true) { policy ->
            AddFoodScreen(state = FoodsFixtures.addFoodState(policy), onAction = {})
        },
        LeakSurface(id = "foods-add-food-blind", label = "Custom food, nutrition out of view", showsCaloriesInFull = false) { policy ->
            AddFoodScreen(state = FoodsFixtures.addFoodState(policy), onAction = {})
        },
    )
}
