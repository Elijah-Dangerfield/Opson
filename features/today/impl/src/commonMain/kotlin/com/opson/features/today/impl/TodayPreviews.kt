package com.opson.features.today.impl

import androidx.compose.runtime.Composable
import com.opson.features.today.impl.calendar.CalendarSheetContent
import com.opson.features.today.impl.cards.TodayCardsSheetContent
import com.opson.features.today.impl.dayclose.DayCloseScreen
import com.opson.features.today.impl.daynote.DayNoteSheetContent
import com.opson.features.today.impl.item.ItemSheetContent
import com.opson.features.today.impl.meal.MealFeelSheetContent
import com.opson.features.today.impl.meal.MealNoteSheetContent
import com.opson.features.today.impl.meal.MealSheetContent
import com.opson.features.today.impl.water.WaterSheetContent
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.diary.ui.FastingStatusFixtures
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.Instant

// Every state here comes from the real builders over TodayFixtures, the same path the app and
// the Blind leak test take. One theme per preview at the phone's 390 width, so each is also a
// design-sync screenshot; the names follow the v3 design's screen ids (today-full, day-close-blind).

private fun policy(preset: PresetId, at: Instant = TodayFixtures.noon): VisibilityPolicy =
    VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(at), TodayFixtures.timeZone)

private val full = policy(PresetId.Full)

@Composable
private fun Phone(dark: Boolean = false, largeText: Boolean = false, content: @Composable () -> Unit) {
    PreviewContent(darkTheme = dark, largeText = largeText, content = content)
}

@Composable
private fun Today(
    policy: VisibilityPolicy,
    day: Day = TodayFixtures.day(),
    settings: Settings = Settings(),
    highlight: MealSlot? = null,
    added: LoggedToMeal? = null,
) {
    TodayScreen(state = TodayFixtures.todayState(policy, day = day, settings = settings, highlight = highlight, added = added), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFullPreview() = Phone { Today(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFullDarkPreview() = Phone(dark = true) { Today(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFullLargeTextPreview() = Phone(largeText = true) { Today(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayEmptyPreview() = Phone { Today(full, day = Day.empty(TodayFixtures.today)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayAddedPreview() = Phone {
    Today(full, day = TodayFixtures.day(dinner = true), highlight = MealSlot.Dinner, added = TodayFixtures.added())
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayAddedDarkPreview() = Phone(dark = true) {
    Today(full, day = TodayFixtures.day(dinner = true), highlight = MealSlot.Dinner, added = TodayFixtures.added())
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayUntrackedPreview() = Phone { Today(full, day = TodayFixtures.untrackedDay()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayUntrackedBlindPreview() = Phone { Today(policy(PresetId.Blind), day = TodayFixtures.untrackedDay()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayBigDayPreview() = Phone { Today(full, day = TodayFixtures.day(bigDay = true)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayDelayedLockedPreview() = Phone { Today(policy(PresetId.Delayed)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayDelayedRevealedPreview() = Phone { Today(policy(PresetId.Delayed, at = TodayFixtures.evening)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayBlindPreview() = Phone { Today(policy(PresetId.Blind)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayBlindDarkPreview() = Phone(dark = true) { Today(policy(PresetId.Blind)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNutrientFocusPreview() = Phone { Today(policy(PresetId.Nutrient)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFuelPreview() = Phone { Today(policy(PresetId.Fuel)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayRhythmPreview() = Phone { Today(policy(PresetId.Rhythm)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayExplorerPreview() = Phone { Today(policy(PresetId.Explorer)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayExplorerDarkPreview() = Phone(dark = true) { Today(policy(PresetId.Explorer)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayMindfulPreview() = Phone { Today(policy(PresetId.Mindful)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySprintPreview() = Phone {
    Today(policy(PresetId.Sprint), settings = Settings(sprintStartedOn = TodayFixtures.today.minus(DatePeriod(days = 8))))
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayCoastPreview() = Phone { Today(policy(PresetId.Coast)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayGroupsOffPreview() = Phone { Today(full, day = TodayFixtures.day(loose = true), settings = Settings(mealGroups = false)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayPastDayPreview() = Phone { Today(full, day = TodayFixtures.pastDay()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayPlanTomorrowPreview() = Phone { Today(full, day = TodayFixtures.plannedDay()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayDayMenuPreview() = Phone { TodayWithMenu(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayDayMenuArmedDarkPreview() = Phone(dark = true) { TodayWithMenu(full, clearArmed = true) }

// Sheets

@Preview(widthDp = 390)
@Composable
private fun TodayCardsSheetPreview() = Phone { TodayCardsSheetContent(TodayFixtures.todayCardsState(full), onAction = {}) }

/** 0.2: with fasting offered and its timer on, the Fasting card switch joins the sheet. */
@Preview(widthDp = 390)
@Composable
private fun TodayCardsSheetFastingPreview() = Phone {
    TodayCardsSheetContent(TodayFixtures.todayCardsState(full, Settings(fasting = true), fastingReleased = true), onAction = {})
}

/** 0.2: the fasting card on an empty morning, 13h 40m into last night's fast. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFastingPreview() = Phone {
    val state = TodayFixtures.todayState(full, day = Day.empty(TodayFixtures.today))
    TodayScreen(state = state.copy(fasting = FastingStatusFixtures.running), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFastingDarkPreview() = Phone(dark = true) {
    val state = TodayFixtures.todayState(full, day = Day.empty(TodayFixtures.today))
    TodayScreen(state = state.copy(fasting = FastingStatusFixtures.windowOpen), onAction = {})
}

@Preview(widthDp = 390)
@Composable
private fun CalendarSheetPreview() = Phone { CalendarSheetContent(TodayFixtures.calendarState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun CalendarSheetDarkPreview() = Phone(dark = true) { CalendarSheetContent(TodayFixtures.calendarState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun MealSheetPreview() = Phone { MealSheetContent(TodayFixtures.mealSheetState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun MealSheetDarkPreview() = Phone(dark = true) { MealSheetContent(TodayFixtures.mealSheetState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun MealNoteSheetPreview() = Phone { MealNoteSheetContent(TodayFixtures.mealNoteState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun MealFeelSheetPreview() = Phone { MealFeelSheetContent(TodayFixtures.mealFeelState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun MealFeelSheetDarkPreview() = Phone(dark = true) { MealFeelSheetContent(TodayFixtures.mealFeelState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun WaterSheetPreview() = Phone { WaterSheetContent(TodayFixtures.waterSheetState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun ItemSheetPreview() = Phone { ItemSheetContent(TodayFixtures.itemSheetState(full), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun ItemSheetBlindPreview() = Phone { ItemSheetContent(TodayFixtures.itemSheetState(policy(PresetId.Blind)), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun DayNoteSheetPreview() = Phone { DayNoteSheetContent(TodayFixtures.dayNoteState(), onAction = {}) }

// Day close

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayClosePreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(full), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseDarkPreview() = Phone(dark = true) { DayCloseScreen(TodayFixtures.dayCloseState(full), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseBigDayPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(full, bigDay = true, note = "Lola's birthday dinner"), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseBigDayCommittedPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(full, bigDay = true, committed = true, note = "Lola's birthday dinner"), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseRevealedPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.Delayed, at = TodayFixtures.evening)), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseBlindPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.Blind), feeling = DayFeeling.Steady), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseBlindDarkPreview() = Phone(dark = true) {
    DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.Blind), feeling = DayFeeling.Steady), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseFuelPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.Fuel), bigDay = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayClosePastDayPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(full, date = TodayFixtures.pastDay().date), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseWarmPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(full, settings = Settings(tone = Tone.Warm)), onAction = {}) }
