package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.ModeTimerEnded
import kotlin.time.Clock
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.DatePeriod
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.diary_hunger_hungry
import opson.libraries.resources.generated.resources.today_all_food
import com.opson.libraries.diary.MealTimes
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.minus

/**
 * [TodayStateBuilder] against design v3 and design sync 4's mode layouts (13.5b to 13.5d): which
 * hero card each layout and level gets, the meals in each layout, water, planning, the menu and
 * the End after a set time banner, all through the real policy. What the view model
 * does with taps is [TodayViewModelTest]'s; whether a hidden number reaches the screen is
 * `BlindModeLeakTest`'s, over these same builders.
 */
class TodayStateBuilderTest {

    private fun build(
        preset: PresetId,
        day: Day = TodayFixtures.day(),
        settings: Settings = Settings(),
        at: Instant = TodayFixtures.noon,
        overrides: Map<Metric, Visibility> = emptyMap(),
        menuOpen: Boolean = false,
        clearArmed: Boolean = false,
    ): TodayState = build(NumbersSettings(preset), day, settings, at, overrides, menuOpen, clearArmed)

    private fun build(
        numbers: NumbersSettings,
        day: Day = TodayFixtures.day(),
        settings: Settings = Settings(),
        at: Instant = TodayFixtures.noon,
        overrides: Map<Metric, Visibility> = emptyMap(),
        menuOpen: Boolean = false,
        clearArmed: Boolean = false,
    ): TodayState = TodayStateBuilder.build(
        TodayFixtures.inputs(day, settings),
        policy(numbers, at, overrides),
        menuOpen,
        clearArmed,
    )

    /** Every number on Later, what the Later mode was. */
    private val later = NumbersSettings.later()

    @Test
    fun fullShowsEatenAgainstTheRangeAndEveryMeal() {
        val state = build(PresetId.Full)

        val energy = assertIs<TodaySummary.Energy>(state.summary)
        assertEquals("1,245", energy.eaten.text(), "breakfast, lunch and the snack")
        assertEquals("1,900 to 2,300", energy.range.text())
        assertEquals(1900 / 2760f, energy.rangeStart, 1e-4f)
        assertEquals(2300 / 2760f, energy.rangeEnd, 1e-4f)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), energy.macros.map { it.metric })
        assertEquals("51 g", energy.macros.first().display.text())
        assertEquals("750 of 2,000 ml", state.water?.display?.text())
        assertEquals(CloseDayButton(closed = false), state.closeDay)
        assertNull(state.mealTiles)
        assertEquals(MealSlot.meals, state.meals.map { it.slot })
    }

    /** A blank list under the header read as an empty day while the diary (or the health store) was still answering. */
    @Test
    fun beforeTheDayIsReadTheListIsOnlyTheSpinner() {
        assertEquals(listOf<TodayRow>(TodayRow.Loading), todayRows(TodayState(day = TodayFixtures.today)))
        assertTrue(TodayRow.Loading !in todayRows(build(PresetId.Full)))
    }

    /** "Different by day": a training day's range is the one Today draws, not the base. */
    @Test
    fun aTrainingDayShowsItsOwnRange() {
        val today = TodayFixtures.today.dayOfWeek
        val training = DayGroup(setOf(today), minKcal = 2300, maxKcal = 2700)
        val rest = DayGroup(DayOfWeek.entries.toSet() - today, minKcal = 1800, maxKcal = 2200)
        val byDay = Settings(targets = Targets.Default.copy(style = TargetStyle.ByDay(listOf(rest, training))))

        assertEquals("2,300 to 2,700", assertIs<TodaySummary.Energy>(build(PresetId.Full, settings = byDay).summary).range.text())
        val restDay = TodayFixtures.day().copy(date = TodayFixtures.today.minus(DatePeriod(days = 1)))
        assertEquals(
            "1,800 to 2,200",
            assertIs<TodaySummary.Energy>(build(PresetId.Full, day = restDay, settings = byDay).summary).range.text(),
        )
    }

    @Test
    fun theEatenBarStopsAtFullHowEverBigTheDay() {
        val energy = assertIs<TodaySummary.Energy>(build(PresetId.Full, day = TodayFixtures.day(bigDay = true)).summary)

        assertTrue((energy.eaten as Display.Shown).fraction!! > 1f, "the fraction is honest")
        // RangeBar clamps it: going over draws nothing new. The bar component owns the clamp.
    }

    @Test
    fun anEmptyDaySaysNothingYetAndHidesTheMacros() {
        val energy = assertIs<TodaySummary.Energy>(build(PresetId.Full, day = Day.empty(TodayFixtures.today)).summary)

        assertTrue(energy.empty)
        assertTrue(energy.macros.isEmpty())
    }

    @Test
    fun emptyMealsCollapse_theNextOneSaysUsually_andNothingOffersYesterday() {
        val state = build(PresetId.Full, day = Day.empty(TodayFixtures.today))

        val breakfast = assertIs<MealEntry.Collapsed>(state.meals.first())
        assertTrue(breakfast.usual)
        val lunch = assertIs<MealEntry.Collapsed>(state.meals[1])
        assertEquals(false, lunch.usual)
        // Design sync 2, 2.2: copying yesterday's meal happens in the add sheet now.
        assertNull(state.closeDay, "nothing logged, nothing to close")
    }

    @Test
    fun aFullMealUsesCompactMacrosAndBareItemNumbers() {
        val breakfast = assertIs<MealEntry.Full>(build(PresetId.Full).meals.first()).card

        assertEquals(MacroLineStyle.Compact, breakfast.macroStyle)
        assertEquals(listOf("13P", "80C", "8F"), breakfast.macros.map { it.text })
        assertEquals("445 kcal", breakfast.calories.text())
        assertEquals("300", breakfast.items.first().calories.text(), "the header carries the unit")
    }

    @Test
    fun laterAtNoonLocksTotals_countsWhatIsLogged_andKeepsTheWaterGlasses() {
        val state = build(later)

        val foods = TodayFixtures.day().allItems.size
        assertEquals(TodaySummary.Locked(opensAt = LocalTime(21, 0), weekly = false, meals = 3, foods = foods), state.summary)
        val water = assertNotNull(state.water, "13.5c keeps the glasses")
        assertIs<Display.Soft>(water.display)
        assertEquals(3, water.filled)
        val breakfast = assertIs<MealEntry.Full>(state.meals.first()).card
        assertEquals(Display.Hidden, breakfast.calories)
        assertTrue(breakfast.macros.isEmpty())
        assertEquals(Precision.Weighed, breakfast.items.first().precision, "rows keep how sure")
    }

    @Test
    fun laterOpensAtTheRevealTime_asTheFullNumbersCard() {
        assertIs<TodaySummary.Energy>(build(later, at = Instant.parse("2026-10-25T21:00:00Z")).summary)
        assertIs<TodaySummary.Energy>(build(later, day = TodayFixtures.pastDay()).summary, "a past day is simply open")
        val sevenThirty = Settings(numbers = NumbersSettings.later().copy(revealAt = LocalTime(19, 30)))
        val locked = TodayStateBuilder.build(TodayFixtures.inputs(settings = sevenThirty), VisibilityPolicy(sevenThirty.numbers, Clock.fixed(TodayFixtures.noon), TodayFixtures.timeZone))
        assertEquals(LocalTime(19, 30), assertIs<TodaySummary.Locked>(locked.summary).opensAt, "the person's own reveal time")
    }

    @Test
    fun laterWeekly_staysLockedForTheWeek_evenInTheEvening() {
        val weekly = NumbersSettings.later().copy(revealOn = RevealOn.Week)
        val evening = VisibilityPolicy(weekly, Clock.fixed(Instant.parse("2026-10-25T21:30:00Z")), TodayFixtures.timeZone)

        val locked = assertIs<TodaySummary.Locked>(TodayStateBuilder.build(TodayFixtures.inputs(), evening).summary)
        assertTrue(locked.weekly)
    }

    @Test
    fun noNumbers_isAMealTimeline_withFoodsByNameAndHowHungry() {
        val state = build(PresetId.NoNumbers)

        assertNull(state.summary, "the timeline says which meals are done")
        val tiles = assertNotNull(state.mealTiles)
        assertEquals(TileStatus.Logged(LocalTime(8, 10)), tiles.first().status)
        assertEquals(TileStatus.Empty(MealTimes().dinner), tiles.last().status)
        val breakfast = assertIs<MealEntry.Full>(state.meals.first()).card
        assertEquals("Oat porridge with milk", breakfast.items.first().name)
        assertNull(breakfast.items.first().precision, "names and household amounts only")
        assertEquals(Display.Hidden, breakfast.calories)
        assertEquals(Res.string.diary_hunger_hungry, breakfast.note?.hunger, "breakfast's Note says how hungry")
        val lunch = assertIs<MealEntry.Full>(state.meals[1]).card
        assertEquals(MealNoteUi(hunger = null, text = "Ate outside with Sam. Felt relaxed."), lunch.note, "lunch's Note is words only")
        val water = assertNotNull(state.water)
        assertIs<Display.Soft>(water.display)
    }

    @Test
    fun noNumbers_aMealEatenWithNoDetailsIsDone_atItsUsualTime() {
        val day = TodayFixtures.day().copy(ateNoDetails = setOf(MealSlot.Dinner))
        assertEquals(TileStatus.AteNoDetails(MealTimes().dinner), assertNotNull(build(PresetId.NoNumbers, day = day).mealTiles).last().status)
    }

    /** The Note is on every meal's + in every mode, so its hunger shows in every mode too. */
    @Test
    fun fullNumbers_showsTheNotesHungerOnTheCard_withNoTimeline() {
        val state = build(PresetId.Full)
        assertNull(state.mealTiles)
        assertEquals(Res.string.diary_hunger_hungry, assertIs<MealEntry.Full>(state.meals.first()).card.note?.hunger)
    }

    /** A Note written before anything is logged still shows, under the meal's row. */
    @Test
    fun aNoteOnAnEmptyMeal_showsUnderItsRow() {
        val day = TodayFixtures.day().let { it.copy(feelings = it.feelings + (MealSlot.Dinner to MealFeeling(hunger = Hunger.Hungry, note = "  Late shift  "))) }
        val dinner = assertIs<MealEntry.Collapsed>(build(PresetId.Full, day = day).meals.first { it.slot == MealSlot.Dinner })

        assertEquals(MealNoteUi(Res.string.diary_hunger_hungry, "Late shift"), dinner.note)
        assertNull(assertIs<MealEntry.Collapsed>(build(PresetId.Full).meals.first { it.slot == MealSlot.Dinner }).note)
    }

    @Test
    fun softCalories_countMeals_andShowTheMacrosAndEnergyAsWords() {
        val softCalories = mapOf(Metric.Calories to Visibility.Soften)
        val soft = assertIs<TodaySummary.Soft>(build(PresetId.MacrosOnly, overrides = softCalories).summary)

        assertEquals(MealCount(3, 4), soft.mealCount)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), soft.rows.map { it.metric })
        assertTrue(soft.rows.all { it.display is Display.Soft })
        assertEquals("Partway through", soft.energy.text())
    }

    @Test
    fun softCalories_withAProteinShownByChoice_showsItsAmount() {
        val overrides = mapOf(Metric.Calories to Visibility.Soften, Metric.Protein to Visibility.Show)
        val soft = assertIs<TodaySummary.Soft>(build(PresetId.MacrosOnly, overrides = overrides).summary)
        assertEquals("51 of 120 g", soft.rows.first().display.text())
    }

    @Test
    fun macrosOnly_isTheFocusCardInWords_withNoCaloriesAndNoGrams() {
        val state = build(PresetId.MacrosOnly)
        val focus = assertIs<TodaySummary.Focus>(state.summary)

        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), focus.rows.map { it.metric })
        assertTrue(focus.rows.all { it.display is Display.Soft })
        val breakfast = assertIs<MealEntry.Full>(state.meals.first()).card
        assertTrue(breakfast.macros.isEmpty(), "words never join a macro line")
        assertEquals(Display.Hidden, breakfast.calories)
        assertEquals(Precision.Weighed, breakfast.items.first().precision, "rows keep how sure")
    }

    @Test
    fun macrosWithGrams_isTheFocusCardInGrams_withNoCaloriesAnywhere() {
        val state = build(PresetId.MacrosWithGrams)
        val focus = assertIs<TodaySummary.Focus>(state.summary)

        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), focus.rows.map { it.metric })
        assertTrue(focus.rows.all { it.display is Display.Shown })
        assertEquals("51 of 120 g", focus.rows.first().display.text())
        val breakfast = assertIs<MealEntry.Full>(state.meals.first()).card
        assertEquals(Display.Hidden, breakfast.calories)
        assertTrue(breakfast.macros.isNotEmpty(), "grams join the macro line")
        assertTrue(breakfast.items.all { it.calories == Display.Hidden }, "no food row spells its energy")
    }

    @Test
    fun theTimerBanner_saysSoOnTheDayItSwitched_andNoOtherDay() {
        val ended = Settings(modeTimerEnded = ModeTimerEnded(on = TodayFixtures.today, from = PresetId.Full))
        assertEquals(TodayBanner(from = PresetId.Full, to = PresetId.NoNumbers, waiting = false), build(PresetId.NoNumbers, settings = ended).banner)

        val yesterday = Settings(modeTimerEnded = ModeTimerEnded(on = TodayFixtures.today.minus(DatePeriod(days = 1)), from = PresetId.Full))
        assertNull(build(PresetId.NoNumbers, settings = yesterday).banner)
        assertNull(build(PresetId.Full).banner)
    }

    @Test
    fun theTimerBanner_offersASwitchThePauseHolds() {
        val waiting = Settings(
            numbers = NumbersSettings(
                preset = PresetId.NoNumbers,
                pauseBeforeMore = true,
                endAfter = ModeTiming(startedOn = TodayFixtures.today.minus(DatePeriod(days = 14)), weeks = 2, then = PresetId.MacrosWithGrams),
            ),
        )
        assertEquals(TodayBanner(from = PresetId.NoNumbers, to = PresetId.MacrosWithGrams, waiting = true), build(PresetId.NoNumbers, settings = waiting).banner)
    }

    @Test
    fun groupsOffPutsEveryItemInAllFood() {
        val state = build(PresetId.Full, day = TodayFixtures.day(loose = true), settings = Settings(mealGroups = false))

        val all = assertIs<MealEntry.Full>(state.meals.single()).card
        assertEquals(Res.string.today_all_food, all.title)
        assertEquals(TodayFixtures.day(loose = true).allItems.size, all.items.size)
    }

    @Test
    fun planningGoesOneDayAheadWithPlannedMeals() {
        val state = build(PresetId.Full, day = TodayFixtures.plannedDay())

        assertTrue(state.planning)
        assertEquals(RelativeDay.Tomorrow, state.dayLabel)
        assertEquals(false, state.canGoForward)
        assertNull(state.summary)
        assertNull(state.closeDay)
        assertEquals("445 kcal", assertIs<MealEntry.Planned>(state.meals.first()).calories.text())
        assertIs<MealEntry.NotPlanned>(state.meals.last())
    }

    @Test
    fun aPastDayIsClosed() {
        assertEquals(CloseDayButton(closed = true), build(PresetId.Full, day = TodayFixtures.pastDay()).closeDay)
    }

    @Test
    fun aMealOfNameOnlyItemsHasNoTotal() {
        val named = LogItem(
            id = "pancit",
            day = TodayFixtures.today,
            slot = MealSlot.Dinner,
            loggedAt = TodayFixtures.evening,
            name = "Lola's pancit",
            precision = Precision.Named,
            method = LogMethod.Search,
        )
        val day = TodayFixtures.day().let { it.copy(itemsBySlot = it.itemsBySlot + (MealSlot.Dinner to listOf(named))) }
        val dinner = assertIs<MealEntry.Full>(build(PresetId.Full, day = day).meals.last()).card

        assertEquals(Display.Hidden, dinner.calories)
        assertEquals(Display.Hidden, dinner.items.single().calories)
        assertTrue(dinner.macros.isEmpty(), "no macro line of zeros for a name")
    }

    @Test
    fun ateNoDetailsKeepsTheMealCardWithNoItems() {
        val day = TodayFixtures.day().copy(ateNoDetails = setOf(MealSlot.Dinner))
        val dinner = assertIs<MealEntry.Full>(build(PresetId.Full, day = day).meals.last()).card

        assertTrue(dinner.ateNoDetails)
        assertTrue(dinner.items.isEmpty())
    }

    @Test
    fun theDayMenuIsGroupedAndFollowsTheDay() {
        val today = assertNotNull(build(PresetId.Full, menuOpen = true, clearArmed = true).menu)
        assertEquals(
            listOf(
                listOf(DayMenuItem.CloseDay(isToday = true), DayMenuItem.Nutrients, DayMenuItem.Notes),
                listOf(DayMenuItem.CopyYesterday, DayMenuItem.CopyToTomorrow, DayMenuItem.AddNote, DayMenuItem.Untracked(marked = false)),
                listOf(DayMenuItem.EditCards, DayMenuItem.ClearDay),
            ),
            today.groups,
        )
        assertTrue(today.clearArmed)

        val tomorrow = assertNotNull(build(PresetId.Full, day = TodayFixtures.plannedDay(), menuOpen = true).menu)
        assertTrue(tomorrow.groups.flatten().none { it is DayMenuItem.CloseDay || it == DayMenuItem.CopyToTomorrow })
        assertNull(build(PresetId.Full).menu)
    }

    @Test
    fun anUntrackedDayIsCalm_itsMealsStayButNothingReadsItAsADay() {
        (PresetId.entries.map(::NumbersSettings) + later).forEach { preset ->
            val state = build(preset, day = TodayFixtures.untrackedDay(), menuOpen = true)
            assertTrue(state.untracked, "$preset")
            assertNull(state.summary, "no hero card to measure it by in $preset")
            assertNull(state.closeDay)
            val menu = assertNotNull(state.menu).groups.flatten()
            assertTrue(DayMenuItem.Untracked(marked = true) in menu, "the menu offers to track it again")
            assertTrue(menu.none { it is DayMenuItem.CloseDay })
        }
        val full = build(PresetId.Full, day = TodayFixtures.untrackedDay())
        assertEquals(MealSlot.meals, full.meals.map { it.slot }, "logging still works")
        val planned = build(PresetId.Full, day = TodayFixtures.plannedDay().copy(untracked = true))
        assertTrue(planned.untracked)
        assertEquals(false, planned.planning)
    }

    @Test
    fun anAddShowsItsCountOnItsOwnDayOnly() {
        val state = TodayFixtures.todayState(policy(PresetId.NoNumbers), day = TodayFixtures.day(dinner = true), added = TodayFixtures.added())
        assertEquals(AddedNotice(MealSlot.Dinner, count = 2), state.added, "a count of foods, so No numbers gets it too")
        assertNull(TodayFixtures.todayState(policy(PresetId.Full), day = TodayFixtures.pastDay(), added = TodayFixtures.added()).added)
    }

    @Test
    fun hiddenWaterHasNoCard() {
        assertNull(build(PresetId.Full, overrides = mapOf(Metric.Water to Visibility.Hide)).water)
        assertNull(build(PresetId.NoNumbers, overrides = mapOf(Metric.Water to Visibility.Hide)).water)
    }

    /**
     * Meal cards skip by equality when the rest of Today moves (an Undo bar, the menu, the fasting
     * minute): the cards and what their rows read come out equal from two such states.
     */
    @Test
    fun theMenuOrAnUndoBarLeavesTheMealsAndWhatTheirRowsReadEqual() {
        val inputs = TodayFixtures.inputs(TodayFixtures.day(), Settings())
        val quiet = TodayStateBuilder.build(inputs, policy(PresetId.Full))
        val busy = TodayStateBuilder.build(inputs, policy(PresetId.Full), menuOpen = true, ui = TodayUi(removed = RemovedNotice(1, "Banana", MealSlot.Breakfast)))

        assertEquals(quiet.meals, busy.meals)
        assertEquals(FoodRowContext.of(quiet), FoodRowContext.of(busy))
        assertNotEquals(FoodRowContext.of(quiet), FoodRowContext.of(quiet.copy(highlight = MealSlot.Lunch)))
    }
}
