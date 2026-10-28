package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

// Full-screen search (design sync 4). search-preview__full pairs with search-full (13.4c). The
// design draws release.barcode and release.photo on (the barcode button, "Photograph it").

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.NoNumbers)

@Composable
private fun Search(state: FoodSearchState, dark: Boolean = true) = PreviewContent(darkTheme = dark) {
    FoodSearchContent(state, onAction = {})
}

/** 13.4c: "rice", jasmine rice in from its + (✓, your usual cup), "Not finding it?" under the rows. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FullPreview() = Search(LoggerFixtures.searchState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FullLightPreview() = Search(LoggerFixtures.searchState(full), dark = false)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FullBlindPreview() = Search(LoggerFixtures.searchState(blind))

/** Before typing: usual at dinner, then recent. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BrowsePreview() = Search(LoggerFixtures.searchState(full, query = "", added = false))

/** 3.6: a miss is never a dead end. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EmptyPreview() = Search(LoggerFixtures.searchEmptyState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OfflinePreview() = Search(LoggerFixtures.searchState(full, search = LoggerFixtures.search(offline = true), added = false))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun YourFoodsPreview() = Search(LoggerFixtures.searchState(full, query = "", scope = SearchScope.YourFoods, added = false))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SavedMealsPreview() = Search(LoggerFixtures.searchState(full, query = "", scope = SearchScope.SavedMeals, added = false))

/** Create a meal's "Add a food": + picks, and the footer hands the picks back. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PickForMealPreview() = Search(LoggerFixtures.searchState(full, pickingForMeal = true, flags = false))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FlagsOffPreview() = Search(LoggerFixtures.searchState(full, flags = false))

// release.searchOptions on: the options button, the category tabs and a badge on every row.

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TabsPreview() = Search(LoggerFixtures.optionsState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TabsBlindPreview() = Search(LoggerFixtures.optionsState(blind))

/** A to Z over USDA only: the options button lights up so the narrower list reads as chosen. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FilteredPreview() = Search(
    LoggerFixtures.optionsState(full, settings = Settings(searchSort = SearchSort.AToZ, searchDatabase = SearchDatabase.Usda)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FavoritesTabPreview() = Search(LoggerFixtures.optionsState(full, tab = SearchTab.Favorites, query = ""))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RestaurantHintPreview() = Search(LoggerFixtures.optionsState(full, tab = SearchTab.Restaurant, query = ""))

/** Search on a day closed with Close the day: the closed line in place of the lists. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ClosedDayPreview() = Search(LoggerFixtures.searchState(full, query = "", added = false, closed = true))
