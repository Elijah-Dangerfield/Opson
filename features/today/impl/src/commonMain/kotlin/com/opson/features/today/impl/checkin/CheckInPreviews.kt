package com.opson.features.today.impl.checkin

import androidx.compose.runtime.Composable
import com.opson.features.today.impl.TodayFixtures
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.medication.Symptom
import com.opson.libraries.ui.PreviewContent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import org.jetbrains.compose.ui.tooling.preview.Preview

// The check-in sheet (release.checkIn), built through the same rules the sheet runs.

private val morning = LocalTime(7, 40)
private val evening = LocalTime(21, 10)

private fun state(now: LocalTime, checkIn: CheckIn = CheckIn(), sleepAsked: Boolean = false, past: Boolean = false): CheckInState {
    val day = if (past) TodayFixtures.today.minus(1, DateTimeUnit.DAY) else TodayFixtures.today
    return CheckInStateBuilder.build(day, TodayFixtures.today, now, checkIn, sleepAsked)
}

/** The morning check-in, nothing answered yet: last night's sleep first, the hunger slider waiting. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun CheckInMorningPreview() = PreviewContent { CheckInSheetContent(state(morning), onAction = {}) }

/** The evening: hunger and energy answered, sleep behind its link. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun CheckInEveningPreview() = PreviewContent {
    CheckInSheetContent(state(evening, CheckIn(hunger = HungerLevel.of(6), energy = Energy.Ok)), onAction = {})
}

/** Every row answered, sleep included from the morning. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun CheckInAnsweredDarkPreview() = PreviewContent(darkTheme = true) {
    CheckInSheetContent(state(evening, TodayFixtures.checkIn), onAction = {})
}

/** Yesterday's, from its Close the day: every row, sleep as the night before. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun CheckInPastDayPreview() = PreviewContent { CheckInSheetContent(state(evening, past = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun CheckInLargeTextPreview() = PreviewContent(largeText = true) {
    CheckInSheetContent(state(morning, TodayFixtures.checkIn), onAction = {})
}

/** On a tracked medication (`release.medication`): the Side effects row, with today's. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun CheckInSideEffectsPreview() = PreviewContent {
    val base = state(evening, CheckIn(energy = Energy.Ok))
    CheckInSheetContent(base.copy(sideEffects = listOf(Symptom.Nausea, Symptom.Fatigue)), onAction = {})
}
