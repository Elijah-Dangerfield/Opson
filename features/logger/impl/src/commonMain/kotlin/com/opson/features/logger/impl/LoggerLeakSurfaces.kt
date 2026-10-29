package com.opson.features.logger.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The logger's surfaces for `CaloriesHiddenLeakTest` (`docs/plans/screens/blind-leak-test.md`).
 * Each builds its state through the real builder from [LoggerFixtures], with the policy the
 * test hands it. The + sheet, search and the food detail use design sync 4's ids; the panes,
 * the scanner and the photo keep design v3's. No numbers is a policy rather than a surface.
 */
object LoggerLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        // The grid names no number in any mode.
        LeakSurface(id = "add-sheet", label = "Add sheet", showsCaloriesInFull = false) { policy ->
            LoggerSheetContent(state = LoggerFixtures.sheetState(policy), onAction = {})
        },
        // Dragged up: Recent's rows carry energy and macros.
        LeakSurface(id = "add-sheet-expanded", label = "Add sheet, Recent", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.sheetState(policy, height = SheetHeight.Full, added = true), onAction = {})
        },
        // On a closed day: the closed line, a weigh-in and the fasting timer. No number in any mode.
        LeakSurface(id = "add-sheet-closed", label = "Add sheet, day closed", showsCaloriesInFull = false) { policy ->
            LoggerSheetContent(state = LoggerFixtures.sheetState(policy, closed = true), onAction = {})
        },
        LeakSurface(id = "search-closed", label = "Food search, day closed", showsCaloriesInFull = false) { policy ->
            FoodSearchContent(state = LoggerFixtures.searchState(policy, query = "", added = false, closed = true), onAction = {})
        },
        // The nav + at a time with no usual meal: Uncategorized.
        LeakSurface(id = "add-sheet-uncategorized", label = "Add sheet, no usual meal", showsCaloriesInFull = false) { policy ->
            LoggerSheetContent(state = LoggerFixtures.uncategorizedState(policy), onAction = {})
        },
        LeakSurface(id = "search-full", label = "Food search", showsCaloriesInFull = true) { policy ->
            FoodSearchContent(state = LoggerFixtures.searchState(policy), onAction = {})
        },
        LeakSurface(id = "search-browse", label = "Food search, before typing", showsCaloriesInFull = true) { policy ->
            FoodSearchContent(state = LoggerFixtures.searchState(policy, query = "", added = false), onAction = {})
        },
        LeakSurface(id = "search-saved-meals", label = "Food search, saved meals", showsCaloriesInFull = true) { policy ->
            FoodSearchContent(state = LoggerFixtures.searchState(policy, query = "", scope = SearchScope.SavedMeals, added = false), onAction = {})
        },
        LeakSurface(id = "search-pick-for-meal", label = "Food search, picking for a meal", showsCaloriesInFull = true) { policy ->
            FoodSearchContent(state = LoggerFixtures.searchState(policy, pickingForMeal = true), onAction = {})
        },
        // release.searchOptions on: tabs, the options button and a source badge on every row. Badges carry no number.
        LeakSurface(id = "search-tabs", label = "Food search with options", showsCaloriesInFull = true) { policy ->
            FoodSearchContent(state = LoggerFixtures.optionsState(policy), onAction = {})
        },
        // The options sheet: sort, database, tabs. Nothing in it is a number in any mode.
        LeakSurface(id = "search-options", label = "Search options", showsCaloriesInFull = false) { _ ->
            SearchOptionsSheetContent(state = LoggerFixtures.searchOptionsSheetState(), onAction = {})
        },
        LeakSurface(id = "search-empty", label = "Food search, no results", showsCaloriesInFull = false) { policy ->
            FoodSearchContent(state = LoggerFixtures.searchEmptyState(policy), onAction = {})
        },
        // Their own food waiting for review reads "Unverified", and "Can't find it?" offers the pack's label. Words, no numbers.
        LeakSurface(id = "search-unverified", label = "Food search, own food unverified", showsCaloriesInFull = true) { policy ->
            FoodSearchContent(state = LoggerFixtures.searchUnverifiedState(policy), onAction = {})
        },
        LeakSurface(id = "logger-describe", label = "Describe a meal", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.describeState(policy), onAction = {})
        },
        // Describe's lines after fixing them: amounts stepped, a swap, an added food, one not found, and the total.
        LeakSurface(id = "logger-describe-edited", label = "Describe a meal, lines corrected", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.describeEditedState(policy), onAction = {})
        },
        // Quick add with a label typed in: the fields each mode offers, and the energy check where calories show.
        LeakSurface(id = "logger-describe-quick-add", label = "Describe a meal, just the numbers", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.quickAddState(policy), onAction = {})
        },
        LeakSurface(id = "logger-describe-find", label = "Describe a meal, finding a food", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.describeFindState(policy), onAction = {})
        },
        // Voice log: words heard live over lines already read. The transcript is the person's; the lines are policy's.
        LeakSurface(id = "logger-describe-voice", label = "Describe a meal by voice", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = VoiceFixtures.listeningOverLines(policy), onAction = {})
        },
        LeakSurface(id = "logger-saved", label = "Saved meals", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.savedState(policy), onAction = {})
        },
        // kcal, each macro's share of it, and Today with this against the targets.
        LeakSurface(id = "food-detail", label = "Food detail", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.detailState(policy), onAction = {})
        },
        // The ⋮ menu drawn in place: words only, over the same detail.
        LeakSurface(id = "food-detail-more", label = "Food detail, more menu", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.detailState(policy), onAction = {}, menuShown = DetailMenu.More)
        },
        // ml typed, so "That is 258 g" under it: an amount of food, never a nutrient.
        LeakSurface(id = "food-detail-liquid", label = "Food detail, a liquid in ml", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.liquidDetailState(policy), onAction = {})
        },
        LeakSurface(id = "food-detail-creamer", label = "Food detail, own food in tablespoons", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.creamerDetailState(policy), onAction = {})
        },
        LeakSurface(id = "food-detail-pounds", label = "Food detail, ounces first", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.detailPoundsState(policy), onAction = {}, menuShown = DetailMenu.Units)
        },
        // All nutrients open: every vitamin and mineral the food carries, as a percent of a day.
        LeakSurface(id = "food-detail-nutrients", label = "Food detail, all nutrients", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.detailState(policy, nutrientsOpen = true), onAction = {})
        },
        // The unit menu's weights are amounts of food, not nutrients; the detail under it still follows the policy.
        LeakSurface(id = "food-detail-units", label = "Food detail, unit menu", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.detailUnitsState(policy), onAction = {}, menuShown = DetailMenu.Units)
        },
        // 13.5e: No numbers' own detail, the amount in measures and the hunger check.
        LeakSurface(id = "food-detail-no-numbers", label = "Food detail in No numbers", showsCaloriesInFull = false) { policy ->
            FoodDetailContent(state = LoggerFixtures.noNumbersDetailState(policy), onAction = {})
        },
        LeakSurface(id = "food-detail-in-review", label = "Food detail, own food in review", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.inReviewDetailState(policy), onAction = {})
        },
        LeakSurface(id = "food-detail-scan", label = "Food detail after a scan", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.scannedDetailState(policy), onAction = {})
        },
        // An entry opened from its row on Today: its own numbers, Today with this counting it once, Save changes and Remove from log.
        LeakSurface(id = "food-detail-entry", label = "Food detail, an entry on the log", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.entryDetailState(policy), onAction = {})
        },
        // The same entry changed to 3 tbsp: the numbers scale from the entry's own.
        LeakSurface(id = "food-detail-entry-changed", label = "Food detail, an entry with its amount changed", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.entryChangedState(policy), onAction = {})
        },
        // The entry's ⋮ in place: words only, Repeat this among them.
        LeakSurface(id = "food-detail-entry-more", label = "Food detail, an entry's more menu", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.entryDetailState(policy), onAction = {}, menuShown = DetailMenu.More)
        },
        // A quick number: no amount, its calories and protein as typed, the source tapped open.
        LeakSurface(id = "food-detail-entry-quick", label = "Food detail, a quick number on the log", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.quickNumberDetailState(policy), onAction = {})
        },
        // The scanner draws no food data in any other stage; the miss is its fullest screen.
        LeakSurface(id = "logger-scan-miss", label = "Barcode not found", showsCaloriesInFull = false) { _ ->
            BarcodeScanScreen(state = LoggerFixtures.scanState(LoggerFixtures.missedNamed.copy(canAddFood = true)), onAction = {}, viewfinder = { _, _ -> })
        },
        // A code in several databases: each row's serving and numbers as the mode shows them, and what is known about them.
        LeakSurface(id = "logger-scan-matches", label = "Barcode, pick a match", showsCaloriesInFull = true) { policy ->
            BarcodeScanScreen(state = LoggerFixtures.scanState(LoggerFixtures.matchedThree, policy), onAction = {}, viewfinder = { _, _ -> })
        },
        // Add this food (release.submitFood): the photos name no number in any mode.
        LeakSurface(id = "submit-food-photos", label = "Add this food, photos", showsCaloriesInFull = false) { policy ->
            SubmitFoodScreen(state = SubmitFoodFixtures.photosState(policy, photos = 2), onAction = {})
        },
        // The label as read: energy and the check that names its difference in Full; words only elsewhere.
        LeakSurface(id = "submit-food-review", label = "Add this food, check what we read", showsCaloriesInFull = true) { policy ->
            SubmitFoodScreen(state = SubmitFoodFixtures.reviewState(policy), onAction = {})
        },
        LeakSurface(id = "submit-food-by-hand", label = "Add this food, typed in", showsCaloriesInFull = false) { policy ->
            SubmitFoodScreen(state = SubmitFoodFixtures.byHandState(policy), onAction = {})
        },
        // From the Foods tab: saved, not logged. The same form; Save food in place of Add to a meal.
        LeakSurface(id = "submit-food-save-photos", label = "Add a packaged food from Foods, photos", showsCaloriesInFull = false) { policy ->
            SubmitFoodScreen(state = SubmitFoodFixtures.saveOnlyPhotosState(policy), onAction = {})
        },
        LeakSurface(id = "submit-food-save-review", label = "Add a packaged food from Foods, check what we read", showsCaloriesInFull = true) { policy ->
            SubmitFoodScreen(state = SubmitFoodFixtures.saveOnlyReviewState(policy), onAction = {})
        },
        LeakSurface(id = "submit-food-photo-failed", label = "Add a packaged food, a photo that would not open", showsCaloriesInFull = false) { policy ->
            SubmitFoodScreen(state = SubmitFoodFixtures.photoFailedState(policy), onAction = {})
        },
        // Report a problem (release.foodReports): the right numbers are asked only where calories and macros show.
        LeakSurface(id = "food-report-values", label = "Report a problem, the right numbers", showsCaloriesInFull = true) { policy ->
            FoodReportSheetContent(
                state = FoodReportStateBuilder.build(
                    FoodReportModel(
                        name = "Energy drink",
                        amountText = "8.0 fl oz (240 ml)",
                        amountGrams = 240.0,
                        canTakePhoto = true,
                        reason = com.opson.libraries.foods.FoodReportReason.WrongCalories,
                        fields = mapOf(FoodReportStateBuilder.KCAL to "5", FoodReportStateBuilder.CARBS to "2"),
                    ),
                    policy,
                ),
                onAction = {},
                onClose = {},
            )
        },
        // Builder mode (release.builders): the label read on the phone fills the same form.
        LeakSurface(id = "submit-food-builder-review", label = "Add a packaged food, Builder mode, read on the phone", showsCaloriesInFull = true) { policy ->
            SubmitFoodScreen(state = SubmitFoodFixtures.builderReviewState(policy), onAction = {})
        },
        // Check against the pack: pack and Opson side by side only where calories and macros show.
        LeakSurface(id = "check-food-differs", label = "Check against the pack, the label differs", showsCaloriesInFull = true) { policy ->
            CheckFoodSheetContent(
                state = CheckFoodStateBuilder.build(
                    CheckModel(
                        name = "Maple granola",
                        canTakePhoto = true,
                        step = CheckStep.Compared(
                            PackComparison(
                                pack = mapOf(com.opson.libraries.foods.LabelNutrient.Energy to 418.0, com.opson.libraries.foods.LabelNutrient.Fat to 14.5),
                                opson = mapOf(com.opson.libraries.foods.LabelNutrient.Energy to 545.0, com.opson.libraries.foods.LabelNutrient.Fat to 14.5),
                                differing = setOf(com.opson.libraries.foods.LabelNutrient.Energy),
                                servingGrams = 55.0,
                            ),
                        ),
                    ),
                    policy,
                ),
                onAction = {},
                onClose = {},
            )
        },
        LeakSurface(id = "logger-photo", label = "Photo estimate", showsCaloriesInFull = true) { policy ->
            PhotoEstimateSheetContent(state = PhotoFixtures.reviewState(policy, editing = true), onAction = {})
        },
        LeakSurface(id = "logger-photo-swap", label = "Photo estimate, swap a food", showsCaloriesInFull = true) { policy ->
            PhotoEstimateSheetContent(state = PhotoFixtures.swapState(policy), onAction = {})
        },
        // The start names no number in any mode; the note a person types is theirs.
        LeakSurface(id = "logger-photo-start", label = "Photo estimate, before the photo", showsCaloriesInFull = false) { policy ->
            PhotoEstimateSheetContent(state = PhotoFixtures.startState(policy, note = PhotoFixtures.NOTE), onAction = {})
        },
        // release.exercise: the estimate is energy out; a typed number is only offered where energy out is not hidden.
        LeakSurface(id = "exercise-sheet", label = "Exercise", showsCaloriesInFull = true) { policy ->
            ExerciseSheetContent(state = ExerciseFixtures.formState(policy), onAction = {})
        },
        // Other, with the number the person typed.
        LeakSurface(id = "exercise-sheet-other", label = "Exercise, other", showsCaloriesInFull = true) { policy ->
            ExerciseSheetContent(state = ExerciseFixtures.otherState(policy), onAction = {})
        },
        // The activity list names no number in any mode.
        LeakSurface(id = "exercise-sheet-pick", label = "Exercise, pick an activity", showsCaloriesInFull = false) { policy ->
            ExerciseSheetContent(state = ExerciseFixtures.pickState(policy), onAction = {})
        },
    )
}
