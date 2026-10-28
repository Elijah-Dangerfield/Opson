package com.opson.features.logger.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The logger's surfaces for `BlindModeLeakTest` (`docs/plans/screens/blind-leak-test.md`).
 * Each builds its state through the real builder from [LoggerFixtures], with the policy the
 * test hands it. The + sheet, search and the food detail use design sync 4's ids; the panes,
 * the scanner and the photo keep design v3's. Blind is a policy rather than a surface.
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
        LeakSurface(id = "food-detail-scan", label = "Food detail after a scan", showsCaloriesInFull = true) { policy ->
            FoodDetailContent(state = LoggerFixtures.scannedDetailState(policy), onAction = {})
        },
        // The scanner draws no food data in any stage; the miss is its fullest screen.
        LeakSurface(id = "logger-scan-miss", label = "Barcode not found", showsCaloriesInFull = false) { _ ->
            BarcodeScanScreen(state = LoggerFixtures.scanState(LoggerFixtures.missedNamed), onAction = {}, viewfinder = { _, _ -> })
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
