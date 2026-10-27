package com.opson.features.foods.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every state here comes from the real builders over FoodsFixtures, the same path the app and
// the Blind leak test take. Function names follow design v3's screen ids (foods-add-food-blind is
// FoodsAddFoodBlindPreview), one theme per preview.

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.NoNumbers)
private val nutrient = previewVisibilityPolicy(PresetId.NutrientsFirst)

@Composable
private fun Foods(state: FoodsState, dark: Boolean = false) = FoodsPreviewFrame(dark) { FoodsScreen(state, onAction = {}) }

@Composable
private fun CreateMeal(state: CreateMealState, dark: Boolean = false) = FoodsPreviewFrame(dark) { CreateMealScreen(state, onAction = {}) }

@Composable
private fun AddFood(state: AddFoodState, dark: Boolean = false) = FoodsPreviewFrame(dark) { AddFoodScreen(state, onAction = {}) }

/** 11.1 as drawn: nothing recent yet, so the list ends at Your foods. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsPreview() = Foods(FoodsFixtures.foodsState(full, withRecent = false))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsDarkPreview() = Foods(FoodsFixtures.foodsState(full, withRecent = false), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsBlindPreview() = Foods(FoodsFixtures.foodsState(blind, withRecent = false))

/** The whole list, Recent and the data credit included. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRecentPreview() = Foods(FoodsFixtures.foodsState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsEmptyPreview() = Foods(FoodsFixtures.foodsState(full, empty = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsCreateMealPreview() = CreateMeal(FoodsFixtures.createMealState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsCreateMealDarkPreview() = CreateMeal(FoodsFixtures.createMealState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsCreateMealBlindPreview() = CreateMeal(FoodsFixtures.createMealState(blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsCreateMealEmptyPreview() = CreateMeal(FoodsFixtures.createMealState(full, empty = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsAddFoodPreview() = AddFood(FoodsFixtures.addFoodState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsAddFoodDarkPreview() = AddFood(FoodsFixtures.addFoodState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsAddFoodNutrientFocusPreview() = AddFood(FoodsFixtures.addFoodState(nutrient))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsAddFoodBlindPreview() = AddFood(FoodsFixtures.addFoodState(blind))
