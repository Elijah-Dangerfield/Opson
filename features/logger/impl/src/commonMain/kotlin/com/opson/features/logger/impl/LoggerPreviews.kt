package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.features.logger.LogTab
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every state here comes from the real builders over LoggerFixtures, the same path the app and
// the Blind leak test take.

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.Blind)
private val fuel = previewVisibilityPolicy(PresetId.Fuel)
private val nutrient = previewVisibilityPolicy(PresetId.Nutrient)

@Composable
private fun Phone(dark: Boolean = false, content: @Composable () -> Unit) {
    PreviewContent(darkTheme = dark, content = content)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerFirstRunPreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(full, firstRun = true), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerFirstRunDarkPreview() = Phone(dark = true) {
    LoggerSheetContent(LoggerFixtures.loggerState(full, firstRun = true), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPickedPreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(full, basket = LoggerFixtures.pickedBasket()), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPickedDarkPreview() = Phone(dark = true) {
    LoggerSheetContent(LoggerFixtures.loggerState(full, basket = LoggerFixtures.pickedBasket()), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPickedBlindPreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(blind, basket = LoggerFixtures.pickedBasket()), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerBlindDarkPreview() = Phone(dark = true) {
    LoggerSheetContent(LoggerFixtures.loggerState(blind), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerNutrientFocusPreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(nutrient), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerFuelPreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(fuel), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerResultsPreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(full, query = "rice", search = LoggerFixtures.search()), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerNoResultsPreview() = Phone {
    LoggerSheetContent(
        LoggerFixtures.loggerState(full, query = "pancit", search = LoggerFixtures.search("pancit").copy(foods = emptyList())),
        onAction = {},
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerOfflinePreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(full, query = "rice", search = LoggerFixtures.search(offline = true)), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerGroupsOffPreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(full, settings = Settings(mealGroups = false)), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerOtherDayPreview() = Phone {
    LoggerSheetContent(LoggerFixtures.loggerState(full, date = LoggerFixtures.today.minus(1, DateTimeUnit.DAY)), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AmountSheetPreview() = Phone {
    AmountSheetContent(LoggerFixtures.amountState(full), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AmountSheetDarkPreview() = Phone(dark = true) {
    AmountSheetContent(LoggerFixtures.amountState(full), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AmountSheetBlindPreview() = Phone {
    AmountSheetContent(LoggerFixtures.amountState(blind), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AmountSheetTagsOffPreview() = Phone {
    AmountSheetContent(LoggerFixtures.amountState(full, settings = Settings(askTags = false)), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LogDescribeIdlePreview() = Phone {
    LogScreen(LoggerFixtures.logState(full, LogTab.Describe, parsed = false), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LogDescribeParsedPreview() = Phone {
    LogScreen(LoggerFixtures.logState(full, LogTab.Describe), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LogDescribeParsedDarkPreview() = Phone(dark = true) {
    LogScreen(LoggerFixtures.logState(full, LogTab.Describe), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LogDescribeBlindPreview() = Phone {
    LogScreen(LoggerFixtures.logState(blind, LogTab.Describe), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LogSavedPreview() = Phone {
    LogScreen(LoggerFixtures.logState(full, LogTab.Saved), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LogSavedBlindDarkPreview() = Phone(dark = true) {
    LogScreen(LoggerFixtures.logState(blind, LogTab.Saved), onAction = {})
}
