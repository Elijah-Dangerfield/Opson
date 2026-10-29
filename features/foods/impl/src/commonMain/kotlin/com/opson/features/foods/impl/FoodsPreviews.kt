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
private val nutrient = previewVisibilityPolicy(PresetId.MacrosOnly)
private val macrosWithGrams = previewVisibilityPolicy(PresetId.MacrosWithGrams)

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

/** release.submitFood: the pack's photos first, typing it in under them. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsAddFoodPackPreview() = AddFood(FoodsFixtures.addFoodState(full, canUsePack = true))

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsAddFoodPackDarkPreview() = AddFood(FoodsFixtures.addFoodState(full, canUsePack = true), dark = true)

/** The pandesal sent to Opson's food database and not checked yet. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsUnverifiedPreview() = Foods(FoodsFixtures.foodsState(full, withRecent = false, inReview = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsAddFoodDarkPreview() = AddFood(FoodsFixtures.addFoodState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsAddFoodNutrientFocusPreview() = AddFood(FoodsFixtures.addFoodState(nutrient))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsAddFoodBlindPreview() = AddFood(FoodsFixtures.addFoodState(blind))

@Composable
private fun EditCopy(state: EditCopyState, dark: Boolean = false) = FoodsPreviewFrame(dark) { EditCopyScreen(state, onAction = {}) }

/** 13.3e: calories and protein changed from the peanut butter's own, Use for this entry on. Dark, as Round 4 draws it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyPreview() = EditCopy(FoodsFixtures.editCopyState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyLightPreview() = EditCopy(FoodsFixtures.editCopyState(full))

/** As it opens: nothing changed yet, so no "Was" hints. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyUnchangedPreview() = EditCopy(FoodsFixtures.editCopyState(full, changed = false), dark = true)

/** No numbers: the name, brand and serving sizes, and no numbers at all. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyBlindPreview() = EditCopy(FoodsFixtures.editCopyState(blind), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyBlindLightPreview() = EditCopy(FoodsFixtures.editCopyState(blind))

/** Macros with grams: every number but energy, the vitamins and minerals with their share of a day. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyMacrosWithGramsPreview() = EditCopy(FoodsFixtures.catalogCopyState(macrosWithGrams))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyMacrosWithGramsDarkPreview() = EditCopy(FoodsFixtures.catalogCopyState(macrosWithGrams), dark = true)

/** Macros only: the serving sizes and nothing to type, since the macros are words. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyMacrosOnlyPreview() = EditCopy(FoodsFixtures.creamerCopyState(nutrient))

/** A catalog food per 100 g: the whole label, and energy that disagrees with the macros, with Use to fix it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyFullLabelPreview() = EditCopy(FoodsFixtures.catalogCopyState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyFullLabelDarkPreview() = EditCopy(FoodsFixtures.catalogCopyState(full), dark = true)

/** The creamer as it opens from its detail: a tablespoon nobody has weighed, and what to do about it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyUnweighedPreview() = EditCopy(FoodsFixtures.creamerCopyState(full, weighed = false))

/** The creamer weighed: a tablespoon is 15 g and a cup 240 g, so it logs by weight from now on. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyWeighedPreview() = EditCopy(FoodsFixtures.creamerCopyState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditACopyWeighedDarkPreview() = EditCopy(FoodsFixtures.creamerCopyState(full), dark = true)

@Composable
private fun Repeat(state: RepeatState, dark: Boolean = false) = FoodsPreviewFrame(dark) { RepeatScreen(state, onAction = {}) }

/** release.repeats: the Repeating card under Saved meals, one of its three paused. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRepeatingPreview() = Foods(FoodsFixtures.foodsState(full, withRecent = false, repeatsOn = true))

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRepeatingDarkPreview() = Foods(FoodsFixtures.foodsState(full, withRecent = false, repeatsOn = true), dark = true)

/** No repeats yet: the card says what one is and offers New repeat. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodsRepeatingEmptyPreview() = Foods(FoodsFixtures.foodsState(full, withRecent = false, repeatsOn = true, withRepeats = false))

/** A new repeat of the usual breakfast on weekdays, from today. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRepeatPreview() = Repeat(FoodsFixtures.repeatState(full))

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRepeatDarkPreview() = Repeat(FoodsFixtures.repeatState(full), dark = true)

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRepeatBlindPreview() = Repeat(FoodsFixtures.repeatState(blind))

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRepeatEmptyPreview() = Repeat(FoodsFixtures.repeatState(full, empty = true))

/** A saved one, paused and ending on the 31st: Pause and Delete, and the end kept as it was set. */
@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun FoodsRepeatEditPreview() = Repeat(FoodsFixtures.repeatState(full, edit = true))
