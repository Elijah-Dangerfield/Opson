package com.opson.features.logger.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The logger's surfaces for `BlindModeLeakTest` (`docs/plans/screens/blind-leak-test.md`).
 * Each builds its state through the real builder from [LoggerFixtures], with the policy the
 * test hands it. Ids follow design v3's screen ids; Blind is a policy rather than a surface,
 * so v3's `logger-search-blind` is `logger-search` rendered in Blind.
 */
object LoggerLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(id = "logger", label = "Quick-add sheet", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.loggerState(policy), onAction = {})
        },
        // The nav + at a time with no usual meal: "Usual around" lists foods from any meal.
        LeakSurface(id = "logger-uncategorized", label = "Quick-add sheet, no usual meal", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.uncategorizedState(policy), onAction = {})
        },
        LeakSurface(id = "logger-picked", label = "Logger with picked foods", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.loggerState(policy, basket = LoggerFixtures.pickedBasket()), onAction = {})
        },
        LeakSurface(id = "logger-search", label = "Food search", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.searchState(policy), onAction = {})
        },
        // release.searchOptions on: tabs, the options button and a source badge on every row. Badges carry no number.
        LeakSurface(id = "logger-search-tabs", label = "Food search with options", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.optionsState(policy), onAction = {})
        },
        // The options sheet: sort, database, tabs. Nothing in it is a number in any mode.
        LeakSurface(id = "logger-search-options", label = "Search options", showsCaloriesInFull = false) { _ ->
            SearchOptionsSheetContent(state = LoggerFixtures.searchOptionsSheetState(), onAction = {})
        },
        LeakSurface(id = "logger-search-empty", label = "Food search, no results", showsCaloriesInFull = false) { policy ->
            LoggerSheetContent(state = LoggerFixtures.searchEmptyState(policy), onAction = {})
        },
        LeakSurface(id = "logger-describe", label = "Describe a meal", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.describeState(policy), onAction = {})
        },
        // Voice log: words heard live over lines already read. The transcript is the person's; the lines are policy's.
        LeakSurface(id = "logger-describe-voice", label = "Describe a meal by voice", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = VoiceFixtures.listeningOverLines(policy), onAction = {})
        },
        LeakSurface(id = "logger-saved", label = "Saved meals", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.savedState(policy), onAction = {})
        },
        // This portion, Your day with this and All nutrients: Blind removes the cards.
        LeakSurface(id = "logger-amount", label = "Add-food sheet", showsCaloriesInFull = true) { policy ->
            AmountSheetContent(state = LoggerFixtures.amountState(policy), onAction = {})
        },
        // All nutrients open: every vitamin and mineral the plate carries, as a percent of a day.
        LeakSurface(id = "logger-amount-nutrients", label = "Add-food sheet, all nutrients", showsCaloriesInFull = true) { policy ->
            AmountSheetContent(state = LoggerFixtures.amountState(policy, nutrientsOpen = true), onAction = {})
        },
        // The unit menu's weights are amounts of food, not nutrients; the sheet under it still follows the policy.
        LeakSurface(id = "logger-amount-units", label = "Add-food sheet, unit menu", showsCaloriesInFull = true) { policy ->
            AmountSheetContent(state = LoggerFixtures.amountState(policy), onAction = {}, unitMenuShown = true)
        },
        LeakSurface(id = "logger-scan-amount", label = "Add-food sheet after a scan", showsCaloriesInFull = true) { policy ->
            AmountSheetContent(state = LoggerFixtures.scannedAmountState(policy), onAction = {})
        },
        // The scanner draws no food data in any stage; the miss is its fullest screen.
        LeakSurface(id = "logger-scan-miss", label = "Barcode not found", showsCaloriesInFull = false) { _ ->
            BarcodeScanScreen(state = LoggerFixtures.scanState(LoggerFixtures.missedNamed), onAction = {}, viewfinder = {})
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
    )
}
