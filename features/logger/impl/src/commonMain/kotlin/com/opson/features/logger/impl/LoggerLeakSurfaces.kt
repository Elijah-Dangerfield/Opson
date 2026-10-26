package com.opson.features.logger.impl

import com.opson.features.logger.LogTab
import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The logger's surfaces for `BlindModeLeakTest` (`docs/plans/screens/blind-leak-test.md`).
 * Each builds its state through the real builder from [LoggerFixtures], with the policy the
 * test hands it.
 */
object LoggerLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(id = "logger", label = "Quick-add sheet", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.loggerState(policy), onAction = {})
        },
        LeakSurface(id = "logger-picked", label = "Logger with picked foods", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(state = LoggerFixtures.loggerState(policy, basket = LoggerFixtures.pickedBasket()), onAction = {})
        },
        LeakSurface(id = "logger-results", label = "Food search", showsCaloriesInFull = true) { policy ->
            LoggerSheetContent(
                state = LoggerFixtures.loggerState(policy, query = "rice", search = LoggerFixtures.search()),
                onAction = {},
            )
        },
        LeakSurface(id = "amount-sheet", label = "Add-food sheet", showsCaloriesInFull = true) { policy ->
            AmountSheetContent(state = LoggerFixtures.amountState(policy), onAction = {})
        },
        LeakSurface(id = "log-describe-parsed", label = "Describe a meal", showsCaloriesInFull = true) { policy ->
            LogScreen(state = LoggerFixtures.logState(policy, LogTab.Describe), onAction = {})
        },
        LeakSurface(id = "log-saved", label = "Saved meals and copy yesterday", showsCaloriesInFull = true) { policy ->
            LogScreen(state = LoggerFixtures.logState(policy, LogTab.Saved), onAction = {})
        },
    )
}
