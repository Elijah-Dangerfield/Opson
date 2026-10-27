package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetDefaults
import com.opson.libraries.ui.components.dialog.bottomsheet.SheetFrame
import org.jetbrains.compose.ui.tooling.preview.Preview

// The + sheet's panes and the sheets over search. Every state comes from the real builders over
// LoggerFixtures, the same path the app and the Blind leak test take. The grid, search and the
// food detail are in AddSheetPreview.kt, SearchPreview.kt and FoodDetailPreview.kt, named for
// design sync 4's screens.

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.NoNumbers)

@Composable
private fun Pane(state: LoggerState, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame(heightFraction = BottomSheetDefaults.FullHeightFraction) { LoggerSheetContent(state, onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribePreview() = Pane(LoggerFixtures.describeState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribeBlindPreview() = Pane(LoggerFixtures.describeState(blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribeEmptyPreview() = Pane(LoggerFixtures.describeState(full, parsedLines = false))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribeEditedPreview() = Pane(LoggerFixtures.describeEditedState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribeEditedBlindDarkPreview() = Pane(LoggerFixtures.describeEditedState(blind), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerDescribeFindPreview() = Pane(LoggerFixtures.describeFindState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSavedPreview() = Pane(LoggerFixtures.savedState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSavedBlindDarkPreview() = Pane(LoggerFixtures.savedState(blind), dark = true)

// Search options, release.searchOptions on: the sheet search's options button opens.

@Composable
private fun SearchOptions(dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame(heightFraction = SEARCH_OPTIONS_PREVIEW_FRACTION) {
        SearchOptionsSheetContent(LoggerFixtures.searchOptionsSheetState(), onAction = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchOptionsPreview() = SearchOptions()

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerSearchOptionsDarkPreview() = SearchOptions(dark = true)

private const val SEARCH_OPTIONS_PREVIEW_FRACTION = 0.92f

/** 3.7, the photo estimate's review, with its photo and note compact on top. Its other states are in PhotoEstimatePreviews.kt. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoPreview() = PreviewContent {
    SheetFrame { PhotoEstimateSheetContent(PhotoFixtures.reviewState(full), onAction = {}) }
}
