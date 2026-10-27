package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetDefaults
import com.opson.libraries.ui.components.dialog.bottomsheet.SheetFrame
import org.jetbrains.compose.ui.tooling.preview.Preview

// The + sheet (design sync 4). Ids follow the design's: add-sheet-preview__half pairs with
// add-sheet-half (13.4a), add-sheet-preview__expanded with add-sheet-expanded (13.4b). The design
// draws every flag on (barcode, photo, voice, fasting), so these do too; FlagsOffPreview is the
// sheet as it ships with them off.

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.NoNumbers)

/** The sheet at the height the state asks for: 60% from a +, full once dragged up. */
@Composable
private fun Sheet(state: LoggerState, dark: Boolean = true) = PreviewContent(darkTheme = dark) {
    val fraction = when (state.height) {
        SheetHeight.Partial -> BottomSheetDefaults.PartialHeightFraction
        SheetHeight.Full -> BottomSheetDefaults.FullHeightFraction
    }
    SheetFrame(heightFraction = fraction) { LoggerSheetContent(state, onAction = {}) }
}

/** 13.4a: about 60% tall, twelve actions, the edge fading over the last row. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HalfPreview() = Sheet(LoggerFixtures.sheetState(full))

/** 13.4b: dragged up, Recent under the grid, jasmine rice already in from its +. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ExpandedPreview() = Sheet(LoggerFixtures.sheetState(full, height = SheetHeight.Full, added = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HalfLightPreview() = Sheet(LoggerFixtures.sheetState(full), dark = false)

/** No barcode, photo, voice or fasting: what ships with the 0.2 flags off. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FlagsOffPreview() = Sheet(LoggerFixtures.sheetState(full, flags = false, settings = Settings()))

/** Blind: Recent shows no energy. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BlindExpandedPreview() = Sheet(LoggerFixtures.sheetState(blind, height = SheetHeight.Full))

/** The nav + at 10:40, when this person has no usual meal: Uncategorized, so no "Ate, no details". */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun UncategorizedPreview() = Sheet(LoggerFixtures.uncategorizedState(full))

/** Nothing logged before: no Copy a meal, no Recent. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FirstRunExpandedPreview() = Sheet(LoggerFixtures.sheetState(full, height = SheetHeight.Full, firstRun = true, slot = MealSlot.Lunch))
