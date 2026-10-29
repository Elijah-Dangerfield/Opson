package com.opson.features.today.impl

import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.LoggingHabits
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import com.opson.features.today.NotesOrigin
import com.opson.features.today.impl.dayclose.DayClosedSheetContent
import com.opson.features.today.impl.dayclose.DayClosedState
import com.opson.features.today.impl.item.ItemSheetStateBuilder
import com.opson.features.today.impl.item.ItemStep
import com.opson.features.today.impl.dayclose.DayCloseScreen
import com.opson.features.today.impl.daynote.DayNoteSheetContent
import com.opson.features.today.impl.item.ItemSheetContent
import com.opson.features.today.impl.meal.MealNoteSheetContent
import com.opson.features.today.impl.meal.MealSheetContent
import com.opson.features.today.impl.notes.NotesScreen
import com.opson.features.today.impl.notes.NotesStateBuilder
import com.opson.features.today.impl.water.WaterSheetContent
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ModeTimerEnded
import com.opson.libraries.diary.Reading
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.ui.FastingStatusFixtures
import com.opson.libraries.health.EnergyExpended
import com.opson.features.today.impl.activity.TodayActivityFixtures
import com.opson.features.today.impl.activity.TodayActivitySection
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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.Instant

// Every state here comes from the real builders over TodayFixtures, the same path the app and
// the leak test take. One theme per preview at the phone's 390 width, so each is also a
// design-sync screenshot; the names follow the v3 design's screen ids (today-full, day-close-blind).

private fun policy(preset: PresetId, at: Instant = TodayFixtures.noon): VisibilityPolicy = policy(NumbersSettings(preset = preset), at)

private fun policy(numbers: NumbersSettings, at: Instant = TodayFixtures.noon): VisibilityPolicy =
    VisibilityPolicy(numbers, Clock.fixed(at), TodayFixtures.timeZone)

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
    activity: (@Composable () -> Unit)? = null,
    habits: LoggingHabits? = null,
    now: LocalTime? = null,
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
            habits = habits,
            now = now,
        ),
        onAction = {},
        drag = drag,
        listState = rememberLazyListState(initialFirstVisibleItemIndex = firstRow),
        activity = activity,
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

/**
 * Every figure flipped (owner feedback, 2026-10-29): the main number on Eaten, the macro row on
 * grams left with the bars still on what was eaten.
 */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryFlippedPreview() = Phone(dark = true) { TodayV4(TodayFixtures.flipped()) }

/** The deficit as the main number and the macros as a percent of each goal. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryFlippedPercentPreview() =
    Phone { TodayV4(TodayFixtures.flipped(main = MainNumberChoice.Balance, macros = Reading.Percent)) }

/** Watch energy hidden: no Burned, so the main number flips between the gap and eaten alone. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryFlipWatchHiddenPreview() = Phone(dark = true) {
    Today(
        policy(NumbersSettings(overrides = mapOf(Metric.EnergyOut to Visibility.Hide))),
        settings = TodayFixtures.flipped(main = MainNumberChoice.Goal),
        expended = TodayFixtures.expended,
        burned = TodayFixtures.burned,
    )
}

/** Every macro softened: their words stay and the row has no ⇄, whatever was chosen. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodaySummaryFlipMacrosSoftPreview() = Phone(dark = true) {
    Today(
        policy(NumbersSettings(overrides = listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber).associateWith { Visibility.Soften })),
        settings = TodayFixtures.flipped(),
        expended = TodayFixtures.expended,
        burned = TodayFixtures.burned,
    )
}

/** The older card's stats flipped: Burned so far, and glasses to go. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayStatsFlippedPreview() = Phone { TodayFull(settings = TodayFixtures.flipped()) }

/** Scrolled to lunch with the deficit as the main number: the sticky bar leads with it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayStickyFlippedPreview() = Phone(dark = true) {
    Today(
        full,
        settings = TodayFixtures.flipped(main = MainNumberChoice.Balance),
        expended = TodayFixtures.expended,
        burned = TodayFixtures.burned,
        firstRow = LUNCH_ROW,
    )
}

/** Both release flags off: the refined v3 card, macros as columns, no dots, no chevron, no swipe. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayFullFlagsOffPreview() = Phone { Today(full, expended = TodayFixtures.expended, flagsOn = false) }

/** 2.1b: swiped to the macros page, which is remembered. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayMacrosPreview() = Phone { TodayFull(settings = Settings(todaySummaryPage = 1)) }

/** Every meal's Note, newest first, from Today's ⋮ menu or Trends' Notes card. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNotesPreview() = Phone { NotesScreen(state = TodayFixtures.notesState(), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNotesDarkPreview() = Phone(dark = true) { NotesScreen(state = TodayFixtures.notesState(), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNotesEmptyPreview() = Phone {
    NotesScreen(state = NotesStateBuilder.build(emptyList(), TodayFixtures.today, NotesStateBuilder.PAGE_DAYS, NotesOrigin.Trends), onAction = {})
}

/** The last page: key minerals and See all, which opens the Nutrients screen. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNutrientsPagePreview() = Phone(dark = true) { TodayV4(Settings(todaySummaryPage = 2)) }

/** Scrolled to dinner: the Activity card is the last thing on Today, under the meals. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayActivityAtTheBottomPreview() = Phone {
    Today(
        full,
        expended = TodayFixtures.expended,
        weighIns = TodayFixtures.weighIns,
        firstRow = DINNER_ROW,
        activity = { TodayActivityFixtures.withHealth(full)?.let { TodayActivitySection(it, onAction = {}) } },
    )
}

/** 2.1c: scrolled to lunch, the card pinned under the header as one line. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayStickyPreview() = Phone { TodayFull(firstRow = LUNCH_ROW) }

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

/** 9:30 pm with one food logged against a usual day of nine: the late-day card offers to skip the day. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayLowLoggingPreview() = Phone {
    Today(full, day = TodayFixtures.lightDay(), habits = TodayFixtures.habits, now = TodayFixtures.lateEvening)
}

/** The same evening in No numbers, where entries decide rather than energy. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayLowLoggingBlindDarkPreview() = Phone(dark = true) {
    Today(policy(PresetId.NoNumbers), day = TodayFixtures.lightDay(), settings = Settings(numbers = NumbersSettings(PresetId.NoNumbers)), habits = TodayFixtures.habits, now = TodayFixtures.lateEvening)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayUntrackedBlindPreview() = Phone { Today(policy(PresetId.NoNumbers), day = TodayFixtures.untrackedDay()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayBigDayPreview() = Phone { Today(full, day = TodayFixtures.day(bigDay = true)) }

/** Every number on Later, before the reveal: when totals open and what is logged, the water glasses, the meals. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayLaterPreview() = Phone(dark = true) { Today(policy(NumbersSettings.later())) }

/** Every number on Later, after 9:00 pm: the All numbers card for the rest of the day. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayLaterRevealedPreview() = Phone { Today(policy(NumbersSettings.later(), at = TodayFixtures.evening)) }

/** Later, weekly (Coast's people): saved for the weekly check, even in the evening. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayLaterWeeklyPreview() = Phone {
    val weekly = VisibilityPolicy(
        NumbersSettings.later(RevealOn.Week),
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

/** Macros only: the macros as words and bars with no grams, no calories, the water glasses. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNutrientsFirstPreview() = Phone(dark = true) { Today(policy(PresetId.MacrosOnly)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayNutrientsFirstLightPreview() = Phone { Today(policy(PresetId.MacrosOnly)) }

/** Macros with grams: the macros with their grams against each target, no calories anywhere. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayMacrosWithGramsPreview() = Phone { Today(policy(PresetId.MacrosWithGrams)) }

/** End after a set time ran out today: All numbers gave way to No numbers, said once. */
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
        endAfter = ModeTiming(startedOn = TodayFixtures.today.minus(DatePeriod(days = 14)), weeks = 2, then = PresetId.MacrosWithGrams),
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
private fun DayCloseRevealedPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(policy(NumbersSettings.later(), at = TodayFixtures.evening)), onAction = {}) }

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
private fun DayCloseFuelPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.MacrosOnly), bigDay = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayClosePastDayPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(full, date = TodayFixtures.pastDay().date), onAction = {})
}

// Close the day's recap: eaten against burned, the macros and the vitamins and minerals. Tall, so
// every card is in the capture.

/** A set 500 kcal deficit on a busy day: about 500 under what was burned, right around the plan. */
@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun DayCloseRecapDeficitPreview() = Phone {
    DayCloseScreen(
        TodayFixtures.dayCloseState(full, settings = TodayFixtures.deficit500, burned = TodayFixtures.activeBurned, checkInOn = true, checkIn = TodayFixtures.checkIn),
        onAction = {},
    )
}

/** A set surplus on a quiet day: about 300 over what was burned, around the surplus the goal aims for. */
@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun DayCloseRecapSurplusPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(full, settings = TodayFixtures.surplus300, burned = TodayFixtures.quietBurned, checkInOn = true), onAction = {})
}

/** A deficit goal and a day over what was burned: the big-day card, then the same facts, said kindly. */
@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun DayCloseRecapDeficitMissedPreview() = Phone(dark = true) {
    DayCloseScreen(TodayFixtures.dayCloseState(full, settings = TodayFixtures.deficit500, burned = TodayFixtures.quietBurned, checkInOn = true), onAction = {})
}

/** Macros with grams hides energy: no eaten and burned card, the macros against their targets stay. */
@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun DayCloseRecapMacrosWithGramsPreview() = Phone {
    DayCloseScreen(
        TodayFixtures.dayCloseState(policy(PresetId.MacrosWithGrams), settings = TodayFixtures.deficit500, burned = TodayFixtures.activeBurned, checkInOn = true),
        onAction = {},
    )
}

/** No numbers with the same day and Burned: the meals, the check-in and the note, and not one amount. */
@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun DayCloseRecapNoNumbersPreview() = Phone {
    DayCloseScreen(
        TodayFixtures.dayCloseState(policy(PresetId.NoNumbers), settings = TodayFixtures.deficit500, burned = TodayFixtures.activeBurned, checkInOn = true, checkIn = TodayFixtures.checkIn),
        onAction = {},
    )
}

// A day closed with Close the day: no + anywhere, the closed line with Reopen

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayClosedPreview() = Phone { Today(full, day = TodayFixtures.closedDay(), expended = TodayFixtures.expended) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayClosedDarkPreview() = Phone(dark = true) { Today(full, day = TodayFixtures.closedDay()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayClosedBlindPreview() = Phone { Today(policy(PresetId.NoNumbers), day = TodayFixtures.closedDay()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseClosedPreview() = Phone { DayCloseScreen(TodayFixtures.dayCloseState(full, note = "Dinner at Lola's", closed = true), onAction = {}) }

/** Wrapped up with celebrations on (`release.celebrations`): the run so far and the next milestone. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseClosedStreakPreview() = Phone {
    DayCloseScreen(TodayFixtures.dayCloseState(full, closed = true, celebrating = TodayFixtures.celebrating()), onAction = {})
}

/** After numbers have been hard: a kind line, no count. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseClosedStreakGentlePreview() = Phone {
    DayCloseScreen(
        TodayFixtures.dayCloseState(policy(PresetId.NoNumbers), closed = true, celebrating = TodayFixtures.celebrating(CelebrationStyle.Gentle)),
        onAction = {},
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseClosedBlindPreview() = Phone(dark = true) {
    DayCloseScreen(TodayFixtures.dayCloseState(policy(PresetId.NoNumbers), feeling = DayFeeling.Steady, closed = true), onAction = {})
}

@Preview(widthDp = 390)
@Composable
private fun DayClosedSheetPreview() = Phone { DayClosedSheetContent(DayClosedState(isToday = true), onAction = {}) }

@Preview(widthDp = 390)
@Composable
private fun DayClosedSheetOtherDayDarkPreview() = Phone(dark = true) { DayClosedSheetContent(DayClosedState(isToday = false), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodActionsClosedPreview() = Phone(dark = true) { ItemSheetContent(TodayFixtures.foodActionsState(full, closed = true), onAction = {}) }

/** Rows of [TodayFull]'s list: the card, water, weight, then breakfast and lunch. */
private const val BREAKFAST_ROW = 3
private const val LUNCH_ROW = 4
private const val DINNER_ROW = 6
