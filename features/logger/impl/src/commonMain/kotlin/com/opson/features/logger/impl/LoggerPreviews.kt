package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.dialog.bottomsheet.SheetFrame
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every state here comes from the real builders over LoggerFixtures, the same path the app and
// the Blind leak test take. Function names follow design v3's screen ids (logger-search-blind is
// LoggerSearchBlindPreview), one theme per preview.

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.Blind)
private val nutrient = previewVisibilityPolicy(PresetId.Nutrient)

@Composable
private fun Logger(state: LoggerState, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame { LoggerSheetContent(state, onAction = {}) }
}

@Composable
private fun Amount(state: AmountState, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame(heightFraction = AMOUNT_HEIGHT_FRACTION) { AmountSheetContent(state, onAction = {}) }
}

@Composable
private fun HowItFelt(dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame(heightFraction = HOW_IT_FELT_HEIGHT_FRACTION) { HowItFeltSheetContent(LoggerFixtures.howItFeltState(), onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPreview() = Logger(LoggerFixtures.loggerState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDarkPreview() = Logger(LoggerFixtures.loggerState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerBlindPreview() = Logger(LoggerFixtures.loggerState(blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerNutrientFocusPreview() = Logger(LoggerFixtures.loggerState(nutrient))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerFirstRunPreview() = Logger(LoggerFixtures.loggerState(full, firstRun = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerGroupsOffPreview() = Logger(LoggerFixtures.loggerState(full, settings = Settings(mealGroups = false)))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerOtherDayPreview() =
    Logger(LoggerFixtures.loggerState(full, date = LoggerFixtures.today.minus(1, DateTimeUnit.DAY)))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPickedPreview() = Logger(LoggerFixtures.loggerState(full, basket = LoggerFixtures.pickedBasket()))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPickedDarkPreview() = Logger(LoggerFixtures.loggerState(full, basket = LoggerFixtures.pickedBasket()), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPickedBlindPreview() = Logger(LoggerFixtures.loggerState(blind, basket = LoggerFixtures.pickedBasket()))

/** Create a meal's "Add a food": the same sheet, picking for the meal instead of logging. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPickForMealPreview() =
    Logger(LoggerFixtures.loggerState(full, basket = LoggerFixtures.pickedBasket(), pickingForMeal = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchPreview() = Logger(LoggerFixtures.searchState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchBlindPreview() = Logger(LoggerFixtures.searchState(blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchOfflinePreview() =
    Logger(LoggerFixtures.loggerState(full, query = "rice", search = LoggerFixtures.search(offline = true)))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchEmptyPreview() = Logger(LoggerFixtures.searchEmptyState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchEmptyDarkPreview() = Logger(LoggerFixtures.searchEmptyState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribePreview() = Logger(LoggerFixtures.describeState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribeBlindPreview() = Logger(LoggerFixtures.describeState(blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribeEmptyPreview() = Logger(LoggerFixtures.describeState(full, parsedLines = false))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSavedPreview() = Logger(LoggerFixtures.savedState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSavedBlindDarkPreview() = Logger(LoggerFixtures.savedState(blind), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerAmountPreview() = Amount(LoggerFixtures.amountState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerAmountDarkPreview() = Amount(LoggerFixtures.amountState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerAmountBlindPreview() = Amount(LoggerFixtures.amountState(blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerAmountTagsOffPreview() = Amount(LoggerFixtures.amountState(full, settings = Settings(askTags = false)))

/** `release.barcode` on: v3's barcode button inside the empty search bar. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanButtonPreview() = Logger(LoggerFixtures.loggerState(full, scanAvailable = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanButtonDarkPreview() = Logger(LoggerFixtures.loggerState(full, scanAvailable = true), dark = true)

/** A scan hit lands here: the label's serving, in the current meal. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanAmountPreview() = Amount(LoggerFixtures.scannedAmountState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanAmountBlindPreview() = Amount(LoggerFixtures.scannedAmountState(blind))

// Search options, release.searchOptions on. With it off the Logger is LoggerSearchPreview above:
// no options button, no tabs, no badges.

@Composable
private fun SearchOptions(dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame(heightFraction = SEARCH_OPTIONS_PREVIEW_FRACTION) {
        SearchOptionsSheetContent(LoggerFixtures.searchOptionsSheetState(), onAction = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchTabsPreview() = Logger(LoggerFixtures.optionsState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchTabsDarkPreview() = Logger(LoggerFixtures.optionsState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchTabsBlindPreview() = Logger(LoggerFixtures.optionsState(blind))

/** A to Z over USDA only: the options button lights up so the narrower list reads as chosen. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchFilteredPreview() = Logger(
    LoggerFixtures.optionsState(full, settings = Settings(searchSort = SearchSort.AToZ, searchDatabase = SearchDatabase.Usda)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchFavoritesPreview() = Logger(LoggerFixtures.optionsState(full, tab = SearchTab.Favorites, query = ""))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchRestaurantHintPreview() = Logger(LoggerFixtures.optionsState(full, tab = SearchTab.Restaurant, query = ""))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchOptionsPreview() = SearchOptions()

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchOptionsDarkPreview() = SearchOptions(dark = true)

private const val SEARCH_OPTIONS_PREVIEW_FRACTION = 0.92f

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerHowItFeltPreview() = HowItFelt()

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerHowItFeltDarkPreview() = HowItFelt(dark = true)
