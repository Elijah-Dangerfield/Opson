package com.opson.features.today.impl

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import com.opson.features.today.impl.item.ItemSheetStateBuilder
import com.opson.features.today.impl.item.ItemStep
import com.opson.features.today.impl.cards.TodayCardsSheetContent
import com.opson.features.today.impl.dayclose.DayCloseScreen
import com.opson.features.today.impl.daynote.DayNoteSheetContent
import com.opson.features.today.impl.item.ItemSheetContent
import com.opson.features.today.impl.meal.MealNoteSheetContent
import com.opson.features.today.impl.meal.MealSheetContent
import com.opson.features.today.impl.water.WaterSheetContent
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ModeTimerEnded
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.Tone
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.ui.FastingStatusFixtures
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.Instant

// Every state here comes from the real builders over TodayFixtures, the same path the app and
// the leak test take. One theme per preview at the phone's 390 width, so each is also a
// design-sync screenshot; the names follow the v3 design's screen ids (today-full, day-close-blind).

private fun policy(preset: PresetId, at: Instant = TodayFixtures.noon): VisibilityPolicy =
    VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(at), TodayFixtures.timeZone)

private val full = policy(PresetId.Full)

@Composable
private fun Phone(dark: Boolean = false, largeText: Boolean = false, content: @Composable () -> Unit) {
    PreviewContent(darkTheme = dark, largeText = largeText, content = content)
}

/**
 * Today as the design sync 2 and 4 screens draw it: the release flags on, unless a preview says
 * otherwise. [firstRow] scrolls the list, so the sticky bar and the rows below the fold show.
 */
@Composable
private fun Today(
    policy: VisibilityPolicy,
    day: Day = TodayFixtures.day(),
    settings: Settings = Settings(),
    highlight: MealSlot? = null,
    added: LoggedToMeal? = null,
    expended: EnergyExpended? = null,
    weighIns: List<WeighIn> = emptyList(),
    flagsOn: Boolean = true,
    ui: TodayUi = TodayUi(),
    firstRow: Int = 0,
    drag: FoodDrag = rememberFoodDrag(),
    burned: Burned? = null,
) {
    TodayScreen(
        state = TodayFixtures.todayState(
            policy,
            day = day,
            settings = settings,
            highlight = highlight,
            added = added,
            expended = expended,
            summaryOn = flagsOn,
            selectOn = flagsOn,
            weighIns = weighIns,
            ui = ui,
            foodActionsOn = flagsOn,
            goalsAndEnergyOn = burned != null,
            burned = burned,
        ),
        onAction = {},
        drag = drag,
        listState = rememberLazyListState(initialFirstVisibleItemIndex = firstRow),
    )
}

/** v3 `today-full`: the watch connected and a weigh-in this morning. */
@Composable
private fun TodayFull(settings: Settings = Settings(), ui: TodayUi = TodayUi(), firstRow: Int = 0) =
    Today(full, settings = settings, expended = TodayFixtures.expended, weighIns = TodayFixtures.weighIns, ui = ui, firstRow = firstRow)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFullPreview() = Phone { TodayFull() }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFullDarkPreview() = Phone(dark = true) { TodayFull() }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFullLargeTextPreview() = Phone(largeText = true) { TodayFull() }

/** Design sync 4's card in each preset over Burned (`release.goalsAndEnergy`), dark as the design draws it. */
@Composable
private fun TodayV4(settings: Settings = Settings()) =
    Today(full, settings = settings, expended = TodayFixtures.expended, burned = TodayFixtures.burned)

/** 13.1 1d, the default: In and out, the main number To your range. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryInOutPreview() = Phone(dark = true) { TodayV4() }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryInOutLightPreview() = Phone { TodayV4() }

/** 13.1 1c: one bar, Burned as a tick. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryRangeBarPreview() = Phone(dark = true) { TodayV4(Settings(todayCard = SummaryCardPreset.RangeBar)) }

/** 13.1 1a with a single target, as the app allows it (13.6c is the one-number version). */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryRingsPreview() = Phone(dark = true) { TodayV4(TodayFixtures.oneNumber.copy(todayCard = SummaryCardPreset.Rings)) }

/** 13.1 1b: Eaten − Burned = Deficit. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryEquationPreview() =
    Phone(dark = true) { TodayV4(Settings(todayCard = SummaryCardPreset.Equation, todayMainNumber = MainNumberChoice.Balance)) }

/** 13.2a: a set deficit of 500, the main number Deficit, the target 1,680. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayDeficitGoalPreview() = Phone(dark = true) { TodayV4(TodayFixtures.deficit500) }

/** 13.2b: a set surplus of 300, the main number To your surplus, the target 2,480. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySurplusGoalPreview() = Phone(dark = true) { TodayV4(TodayFixtures.surplus300) }

/** 13.6c: one number, 2,100, as rings; the third ring Left. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayRingsPreview() = Phone(dark = true) { TodayV4(TodayFixtures.oneNumber.copy(todayCard = SummaryCardPreset.Rings, todayWater = false)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryInOutLargeTextPreview() = Phone(largeText = true) { TodayV4() }

/** Both release flags off: the refined v3 card, macros as columns, no dots, no chevron, no swipe. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFullFlagsOffPreview() = Phone { Today(full, expended = TodayFixtures.expended, flagsOn = false) }

/** 2.1b: swiped to the macros page, which is remembered. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayMacrosPreview() = Phone { TodayFull(settings = Settings(todaySummaryPage = 1)) }

/** 2.1c: scrolled to lunch, the card pinned under the header as one line. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayStickyPreview() = Phone { TodayFull(firstRow = LUNCH_ROW) }

/** 2.1d: collapsed by choice, at the top of the page. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayCollapsedPreview() = Phone { TodayFull(settings = Settings(todaySummaryCollapsed = true)) }

/** 2.12: three foods picked, the bottom bar's actions. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySelectPreview() = Phone { TodayFull(ui = TodayUi(selected = TodayFixtures.picked), firstRow = BREAKFAST_ROW) }

/** 2.12 in Blind: the rows without amounts, and the same actions. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySelectBlindPreview() = Phone {
    Today(policy(PresetId.NoNumbers), ui = TodayUi(selected = TodayFixtures.picked))
}

/** 2.13: the banana swiped open to Delete. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySwipeDeletePreview() = Phone {
    TodayFull(ui = TodayUi(swipe = SwipeState(open = TodayFixtures.banana)), firstRow = BREAKFAST_ROW)
}

/** 2.14: the banana's row turned into its confirmation. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayDeleteConfirmPreview() = Phone {
    TodayFull(ui = TodayUi(swipe = SwipeState(confirming = TodayFixtures.banana)), firstRow = BREAKFAST_ROW)
}

/** Foods logged outside a usual meal wait in Uncategorized with Move to…. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayUncategorizedPreview() = Phone { Today(full, day = TodayFixtures.day(loose = true), firstRow = BREAKFAST_ROW + 2) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayEmptyPreview() = Phone { Today(full, day = Day.empty(TodayFixtures.today), expended = TodayFixtures.morningExpended) }

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
private fun TodayUntrackedBlindPreview() = Phone { Today(policy(PresetId.NoNumbers), day = TodayFixtures.untrackedDay()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayBigDayPreview() = Phone { Today(full, day = TodayFixtures.day(bigDay = true)) }

/** 13.5c, Later before the reveal: when totals open and what is logged, the water glasses, the meals. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayLaterPreview() = Phone(dark = true) { Today(policy(PresetId.Later)) }

/** Later after 9:00 pm: the Full numbers card for the rest of the day. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayLaterRevealedPreview() = Phone { Today(policy(PresetId.Later, at = TodayFixtures.evening)) }

/** Later, weekly (Coast's people): saved for the weekly check, even in the evening. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayLaterWeeklyPreview() = Phone {
    val weekly = VisibilityPolicy(
        NumbersSettings(preset = PresetId.Later, revealOn = RevealOn.Week),
        Clock.fixed(TodayFixtures.evening),
        TodayFixtures.timeZone,
    )
    Today(weekly)
}

/** 13.5d, No numbers: the meals as a timeline, foods by name and amount, how breakfast felt. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNoNumbersPreview() = Phone(dark = true) { Today(policy(PresetId.NoNumbers)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNoNumbersLightPreview() = Phone { Today(policy(PresetId.NoNumbers)) }

/** 13.5b, Nutrients first: words and bars, the meal count, the water glasses. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNutrientsFirstPreview() = Phone(dark = true) { Today(policy(PresetId.NutrientsFirst)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNutrientsFirstLightPreview() = Phone { Today(policy(PresetId.NutrientsFirst)) }

/** End after a set time ran out today: Full numbers gave way to No numbers, said once. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayTimerEndedPreview() = Phone {
    Today(policy(PresetId.NoNumbers), settings = Settings(modeTimerEnded = ModeTimerEnded(on = TodayFixtures.today, from = PresetId.Full)))
}

/** The timer ran out on a switch to more numbers with the pause on: offered, not made. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayTimerWaitingPreview() = Phone {
    val numbers = NumbersSettings(
        preset = PresetId.NoNumbers,
        pauseBeforeMore = true,
        endAfter = ModeTiming(startedOn = TodayFixtures.today.minus(DatePeriod(days = 14)), weeks = 2, then = PresetId.Later),
    )
    Today(policy(PresetId.NoNumbers), settings = Settings(numbers = numbers))
}

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
private fun TodayCardsSheetPreview() = Phone {
    // v3 draws fiber hidden in Modes and numbers, so its row shows the lock.
    val fiberHidden = VisibilityPolicy(
        NumbersSettings(preset = PresetId.Full, overrides = mapOf(Metric.Fiber to Visibility.Hide)),
        Clock.fixed(TodayFixtures.noon),
        TodayFixtures.timeZone,
    )
    TodayCardsSheetContent(TodayFixtures.todayCardsState(fiberHidden, TodayFixtures.changingWeight, summaryOn = true), onAction = {})
}

/** With the summary flag off: the cards alone, as before. */
@Preview(widthDp = 390)
@Composable
private fun TodayCardsSheetFlagOffPreview() = Phone { TodayCardsSheetContent(TodayFixtures.todayCardsState(full), onAction = {}) }

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

// Design sync 4, Today actions (13.3a to 13.3d). Dark, as the Round 4 screens are drawn.

/** 13.3a: the title tapped, the month dropped over Today. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayCalendarPreview() = Phone(dark = true) { TodayWithCalendar(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayCalendarLightPreview() = Phone { TodayWithCalendar(full) }

/** 13.3b: the apple and the peanut butter picked, the bottom bar in the tab bar's place. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySelectV4Preview() = Phone(dark = true) {
    TodayFull(ui = TodayUi(selected = TodayFixtures.snackPicked), firstRow = LUNCH_ROW)
}

/** 13.3b in Blind: the same, since select mode shows no amounts beyond the rows'. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySelectV4BlindPreview() = Phone(dark = true) {
    Today(policy(PresetId.NoNumbers), ui = TodayUi(selected = TodayFixtures.snackPicked), firstRow = LUNCH_ROW)
}

/** 13.3c: both carried over dinner, which outlines and says where they will land. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayDragMovePreview() = Phone(dark = true) { TodayDragging(full) }

/** Move from the bottom bar, the way to move without dragging. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySelectMovePreview() = Phone(dark = true) {
    TodayFull(ui = TodayUi(selected = TodayFixtures.snackPicked, step = SelectStep.MoveTo(MealSlot.Dinner)), firstRow = LUNCH_ROW)
}

/** After a drop: "Moved 2 foods to dinner" with Undo. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayMovedPreview() = Phone(dark = true) {
    TodayFull(ui = TodayUi(moved = MovedNotice(count = 2, name = null, slot = MealSlot.Dinner)), firstRow = LUNCH_ROW)
}

/** 13.3d: the peanut butter's ⋮ over Today. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodActionsPreview() = Phone(dark = true) { TodayWithFoodActions(full) }

/** 13.3d in Blind: the same rows, and no energy in the header. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodActionsBlindPreview() = Phone(dark = true) { TodayWithFoodActions(policy(PresetId.NoNumbers)) }

/** Change amount, from the ⋮. */
@Preview(widthDp = 390)
@Composable
private fun FoodActionsAmountPreview() = Phone(dark = true) {
    val item = TodayFixtures.day().allItems.first { it.id == TodayFixtures.peanutButter }
    ItemSheetContent(TodayFixtures.foodActionsState(full, ItemSheetStateBuilder.amountStep(item, "3")), onAction = {})
}

/** Copy to another day, from the ⋮. */
@Preview(widthDp = 390)
@Composable
private fun FoodActionsCopyPreview() = Phone(dark = true) {
    ItemSheetContent(TodayFixtures.foodActionsState(full, ItemStep.CopyTo(CopyDay.Tomorrow, MealSlot.Snack)), onAction = {})
}

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
private fun MealNoteSheetEmptyPreview() = Phone { MealNoteSheetContent(TodayFixtures.mealNoteEmptyState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun MealNoteSheetDarkPreview() = Phone(dark = true) { MealNoteSheetContent(TodayFixtures.mealNoteState(), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun WaterSheetPreview() = Phone { WaterSheetContent(TodayFixtures.waterSheetState(), onAction = {}) }

/** `release.foodActions` off: the food and Delete. */
@Preview(widthDp = 390)
@Composable
private fun ItemSheetPreview() = Phone { ItemSheetContent(TodayFixtures.itemSheetState(full), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun ItemSheetBlindPreview() = Phone { ItemSheetContent(TodayFixtures.itemSheetState(policy(PresetId.NoNumbers)), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun DayNoteSheetPreview() = Phone { DayNoteSheetContent(TodayFixtures.dayNoteState(), onAction = {}) }

// Day close

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayClosePreview() = Phone {
    DayCloseScreen(
        TodayFixtures.dayCloseState(full, note = "", expended = TodayFixtures.expended, checkInOn = true, checkIn = TodayFixtures.checkIn),
        onAction = {},
    )
}

/** With `release.checkIn` off: no check-in, as before. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseCheckInOffPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(full), onAction = {}) }

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
private fun DayCloseRevealedPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.Later, at = TodayFixtures.evening)), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseBlindPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.NoNumbers), checkInOn = true, checkIn = TodayFixtures.checkIn), onAction = {})
}

/** Blind with `release.checkIn` off: the one-tap feeling, as before. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseBlindCheckInOffPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.NoNumbers), feeling = DayFeeling.Steady), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseBlindDarkPreview() = Phone(dark = true) {
    DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.NoNumbers), feeling = DayFeeling.Steady), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseFuelPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.NutrientsFirst), bigDay = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayClosePastDayPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(full, date = TodayFixtures.pastDay().date), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseWarmPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(full, settings = Settings(tone = Tone.Warm)), onAction = {}) }

/** Rows of [TodayFull]'s list: the card, water, weight, then breakfast and lunch. */
private const val BREAKFAST_ROW = 3
private const val LUNCH_ROW = 4
