package com.opson.features.logger.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

// The food detail (design sync 4). food-detail-preview__v4 pairs with food-detail-v4 (13.4d).

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.Blind)
private val nutrient = previewVisibilityPolicy(PresetId.Nutrient)

@Composable
private fun Detail(state: FoodDetailState, dark: Boolean = true, unitMenu: Boolean = false, atBottom: Boolean = false) =
    PreviewContent(darkTheme = dark) {
        // A scroll state past the end settles on the last card.
        val scroll = rememberScrollState(initial = if (atBottom) Int.MAX_VALUE else 0)
        FoodDetailContent(state, onAction = {}, unitMenuShown = unitMenu, scrollState = scroll)
    }

/** 13.4d: 32 typed in grams, so the estimate became weighed; today with this under the tiles. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun V4Preview() = Detail(LoggerFixtures.detailState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun V4LightPreview() = Detail(LoggerFixtures.detailState(full), dark = false)

/** Blind: amount and how sure only; the energy card is gone. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BlindPreview() = Detail(LoggerFixtures.detailState(blind))

/** Nutrient focus: the macro tiles show, energy does not. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientFocusPreview() = Detail(LoggerFixtures.detailState(nutrient))

/** All nutrients open, scrolled to the end: the vitamins and minerals as a percent of a day. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsPreview() = Detail(LoggerFixtures.detailState(full, nutrientsOpen = true), atBottom = true)

/** The unit menu, each unit with its weight, the person's own sizes, then "Custom size…". */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun UnitsPreview() = Detail(LoggerFixtures.detailUnitsState(full), unitMenu = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CustomSizePreview() = Detail(LoggerFixtures.customSizeState(full))

/** A scan hit lands here: the label's serving, in the current meal. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ScannedPreview() = Detail(LoggerFixtures.scannedDetailState(full))
