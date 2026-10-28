package com.opson.features.logger.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

// The food detail: food-detail-preview__v4 pairs with food-detail-v4, and the rest are its states.

private val full = previewVisibilityPolicy(PresetId.Full)
private val macrosWithGrams = previewVisibilityPolicy(PresetId.MacrosWithGrams)
private val macrosOnly = previewVisibilityPolicy(PresetId.MacrosOnly)
private val noNumbers = previewVisibilityPolicy(PresetId.NoNumbers)

@Composable
private fun Detail(state: FoodDetailState, dark: Boolean = true, menu: DetailMenu = DetailMenu.None, atBottom: Boolean = false) =
    PreviewContent(darkTheme = dark) {
        // A scroll state past the end settles on the last card.
        val scroll = rememberScrollState(initial = if (atBottom) Int.MAX_VALUE else 0)
        FoodDetailContent(state, onAction = {}, menuShown = menu, scrollState = scroll)
    }

/** 32 typed in grams, so it is weighed; what is in it, and today with it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun V4Preview() = Detail(LoggerFixtures.detailState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun V4LightPreview() = Detail(LoggerFixtures.detailState(full), dark = false)

/** Macros with grams: no calories anywhere, the macros' grams and shares, and today against the macro targets. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MacrosWithGramsPreview() = Detail(LoggerFixtures.detailState(macrosWithGrams))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MacrosWithGramsLightPreview() = Detail(LoggerFixtures.detailState(macrosWithGrams), dark = false)

/** Macros only: a portion has no goal, so nothing is in it to show; today is words and bars. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsFirstPreview() = Detail(LoggerFixtures.detailState(macrosOnly))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MacrosOnlyLightPreview() = Detail(LoggerFixtures.detailState(macrosOnly), dark = false)

/** All nutrients open, scrolled to the end: the vitamins and minerals as a percent of a day. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsPreview() = Detail(LoggerFixtures.detailState(full, nutrientsOpen = true), atBottom = true)

/** The unit menu: each unit with its weight once, the person's own sizes, every weight, then "Custom size…". */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun UnitsPreview() = Detail(LoggerFixtures.detailUnitsState(full), menu = DetailMenu.Units)

/** ⋮: the favorite, Remove from recents, Report a problem and Edit a copy. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MoreMenuPreview() = Detail(LoggerFixtures.detailState(full), menu = DetailMenu.More)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MoreMenuLightPreview() = Detail(LoggerFixtures.detailState(full), dark = false, menu = DetailMenu.More)

/** Someone who weighs themselves in pounds: ounces come before grams in the menu. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PoundsUnitsPreview() = Detail(LoggerFixtures.detailPoundsState(full), dark = false, menu = DetailMenu.Units)

/** A carton that states its volume: 250 ml typed, and ml and fl oz in the menu. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LiquidPreview() = Detail(LoggerFixtures.liquidDetailState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LiquidLightPreview() = Detail(LoggerFixtures.liquidDetailState(full), dark = false)

/** A food of the person's own in tablespoons, never weighed: tablespoons are all it offers until Edit a copy weighs one. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CreamerPreview() = Detail(LoggerFixtures.creamerDetailState(full), dark = false)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CustomSizePreview() = Detail(LoggerFixtures.customSizeState(full))

/** No numbers: the amount in household measures, the meal, the time and the optional hunger check. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NoNumbersPreview() = Detail(LoggerFixtures.noNumbersDetailState(noNumbers))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NoNumbersLightPreview() = Detail(LoggerFixtures.noNumbersDetailState(noNumbers), dark = false)

/** A scan hit lands here: the label's serving, so how sure reads From label. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ScannedPreview() = Detail(LoggerFixtures.scannedDetailState(full))
