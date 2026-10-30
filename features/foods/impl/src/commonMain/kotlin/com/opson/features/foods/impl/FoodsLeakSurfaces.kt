package com.opson.features.foods.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The Foods screens for `CaloriesHiddenLeakTest` (`docs/plans/screens/blind-leak-test.md`). Each
 * builds its state through the real builder from [FoodsFixtures], with the policy the test hands
 * it. Ids are design v3's screen ids. `foods-add-food` renders in whatever mode the test picks,
 * so in Blind it is the same screen as `foods-add-food-blind`; the second id keeps v3's Blind
 * screen on the required list by name.
 */
object FoodsLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        // With favorites and a Builder's Verify a food: a favorite's calories follow the mode like every row's.
        LeakSurface(id = "foods", label = "Foods tab", showsCaloriesInFull = true) { policy ->
            FoodsScreen(state = FoodsFixtures.foodsState(policy, favorites = true, builder = true), onAction = {})
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
        // release.submitFood: "Packaged food?" above the form. The card is words; the form below is the same.
        LeakSurface(id = "foods-add-food-pack", label = "Custom food, from the pack first", showsCaloriesInFull = true) { policy ->
            AddFoodScreen(state = FoodsFixtures.addFoodState(policy, canUsePack = true), onAction = {})
        },
        // A food of theirs waiting for review: "Unverified" under it, which is a word, not a number.
        LeakSurface(id = "foods-unverified", label = "Foods tab, own food unverified", showsCaloriesInFull = true) { policy ->
            FoodsScreen(state = FoodsFixtures.foodsState(policy, inReview = true), onAction = {})
        },
        // release.repeats: each repeat's row carries its foods' calories, as a saved meal's does.
        LeakSurface(id = "foods-repeating", label = "Foods tab, Repeating", showsCaloriesInFull = true) { policy ->
            FoodsScreen(state = FoodsFixtures.foodsState(policy, withRecent = false, repeatsOn = true), onAction = {})
        },
        LeakSurface(id = "foods-repeat", label = "New repeat", showsCaloriesInFull = true) { policy ->
            RepeatScreen(state = FoodsFixtures.repeatState(policy), onAction = {})
        },
        LeakSurface(id = "foods-repeat-edit", label = "Edit repeat", showsCaloriesInFull = true) { policy ->
            RepeatScreen(state = FoodsFixtures.repeatState(policy, edit = true), onAction = {})
        },
        // Design sync 4, 13.3e, from a food's ⋮ on Today (release.foodActions). The Calories field
        // and its "Was" are tagged as calories, so Full's control sees them and no other mode may.
        LeakSurface(id = "edit-a-copy", label = "Edit a copy", showsCaloriesInFull = true) { policy ->
            EditCopyScreen(state = FoodsFixtures.editCopyState(policy), onAction = {})
        },
        // From the food detail: the whole label per 100 g, the vitamins' shares of a day, and the 4/4/9 check's kcal.
        LeakSurface(id = "edit-a-copy-label", label = "Edit a copy, the whole label", showsCaloriesInFull = true) { policy ->
            EditCopyScreen(state = FoodsFixtures.catalogCopyState(policy), onAction = {})
        },
        // The serving sizes' grams are amounts of food; the numbers below them follow the mode.
        LeakSurface(id = "edit-a-copy-sizes", label = "Edit a copy, serving sizes", showsCaloriesInFull = true) { policy ->
            EditCopyScreen(state = FoodsFixtures.creamerCopyState(policy), onAction = {})
        },
    )
}
