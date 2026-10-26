package com.opson.features.today.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.core.fixed
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock

// Today with a health app connected (0.2, release.health): v3 today-full's "Expended so far"
// line beside Nutrients. Kept apart from TodayPreviews so the 0.2 work merges cleanly.

private fun policy(preset: PresetId): VisibilityPolicy =
    VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(TodayFixtures.noon), TodayFixtures.timeZone)

@Composable
private fun TodayExpended(preset: PresetId, dark: Boolean = false) {
    PreviewContent(darkTheme = dark) {
        TodayScreen(state = TodayFixtures.todayState(policy(preset), expended = TodayFixtures.expended), onAction = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayExpendedPreview() = TodayExpended(PresetId.Full)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayExpendedDarkPreview() = TodayExpended(PresetId.Full, dark = true)

/** Connected, and in Blind: nothing about energy out at all. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayExpendedBlindPreview() = TodayExpended(PresetId.Blind)
