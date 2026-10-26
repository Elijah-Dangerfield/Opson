package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_all_food
import opson.libraries.resources.generated.resources.today_banner_sprint
import opson.libraries.resources.generated.resources.today_banner_sprint_done
import opson.libraries.resources.generated.resources.today_locked_day_body_warm
import opson.libraries.resources.generated.resources.today_locked_day_title
import opson.libraries.resources.generated.resources.today_locked_week_title
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.MealReminders
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [TodayStateBuilder] against design v3: which hero card each layout and level gets, the meals
 * in each layout, water, planning, the menu, all through the real policy. What the view model
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
        remindersOn: Boolean = false,
    ): TodayState = TodayStateBuilder.build(
        TodayFixtures.inputs(day, settings, remindersOn = remindersOn),
        policy(preset, at, overrides),
        menuOpen,
        clearArmed,
    )

    @Test
    fun fullShowsEatenAgainstTheRangeAndEveryMeal() {
        val state = build(PresetId.Full)

        val energy = assertIs<TodaySummary.Energy>(state.summary)
        assertEquals("1,245", energy.eaten.text(), "breakfast, lunch and the snack")
        assertEquals("1,900 to 2,300", energy.range.text())
        assertEquals(1900 / 2760f, energy.rangeStart, 1e-4f)
        assertEquals(2300 / 2760f, energy.rangeEnd, 1e-4f)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), energy.macros.map { it.metric })
        assertEquals("51 / 120 g", energy.macros.first().display.text())
        assertEquals("750 of 2,000 ml", state.water?.display?.text())
        assertEquals(CloseDayCard(closed = false), state.closeDay)
        assertNull(state.mealTiles)
        assertEquals(MealSlot.meals, state.meals.map { it.slot })
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
    fun emptyMealsCollapseAndTheNextOneOffersYesterday() {
        val state = build(PresetId.Full, day = Day.empty(TodayFixtures.today))

        val breakfast = assertIs<MealEntry.Collapsed>(state.meals.first())
        assertTrue(breakfast.usual)
        assertEquals("Greek yogurt, plain, banana", breakfast.yesterday?.foods)
        assertEquals("205 kcal", breakfast.yesterday?.calories?.text())
        val lunch = assertIs<MealEntry.Collapsed>(state.meals[1])
        assertEquals(false, lunch.usual)
        assertNull(lunch.yesterday)
    }

    @Test
    fun delayedBeforeTheReveal_yesterdaysMealOfferShowsNoAmount() {
        // Yesterday is open in Delayed, but re-adding its dinner would tell part of today's total.
        val dinner = assertIs<MealEntry.Collapsed>(build(PresetId.Delayed).meals.last())
        val yesterday = assertNotNull(dinner.yesterday)
        assertEquals("Arroz con pollo, garlic fried rice (sinangag)", yesterday.foods, "the foods are still offered")
        assertIs<Display.Hidden>(yesterday.calories)

        val evening = assertIs<MealEntry.Collapsed>(build(PresetId.Delayed, at = Instant.parse("2026-10-25T21:00:00Z")).meals.last())
        assertEquals("813 kcal", evening.yesterday?.calories?.text(), "once today opens, so does the offer")
    }

    @Test
    fun withNoYesterdayTheNextMealOffersSavedMeals() {
        val dinner = assertIs<MealEntry.Collapsed>(build(PresetId.Full).meals.last())

        assertTrue(dinner.usual)
        assertNotNull(dinner.yesterday, "yesterday had a dinner")
        val noYesterday = TodayStateBuilder.build(
            TodayFixtures.inputs(dayBefore = Day.empty(TodayFixtures.today.plus(DatePeriod(days = -1)))),
            policy(PresetId.Full),
        ).meals.last()
        assertTrue(assertIs<MealEntry.Collapsed>(noYesterday).offerSaved)
    }

    @Test
    fun aFullMealUsesCompactMacrosAndBareItemNumbers() {
        val breakfast = assertIs<MealEntry.Full>(build(PresetId.Full).meals.first()).card

        assertEquals(MacroLineStyle.Compact, breakfast.macroStyle)
        assertEquals(listOf("13P", "80C", "8F"), breakfast.macros.map { it.text })
        assertEquals("445 kcal", breakfast.calories.text())
        assertEquals("300", breakfast.items.first().calories.text(), "the header carries the unit")
        assertEquals(3, breakfast.feel.size)
    }

    @Test
    fun delayedAtNoonLocksTotalsButKeepsCounts() {
        val state = build(PresetId.Delayed)

        val locked = assertIs<TodaySummary.Locked>(state.summary)
        assertEquals(Res.string.today_locked_day_title, locked.title)
        assertEquals(Counts(meals = 3, glasses = 3), state.counts)
        assertNull(state.water, "the glasses count stands in for the card")
        assertTrue(state.meals.filterIsInstance<MealEntry.Full>().all { it.card.calories is Display.Hidden && it.card.macros.isEmpty() })
    }

    @Test
    fun delayedOpensAtNineAsALinkToDayClose() {
        assertEquals(TodaySummary.Revealed, build(PresetId.Delayed, at = Instant.parse("2026-10-25T21:00:00Z")).summary)
        assertIs<TodaySummary.Energy>(build(PresetId.Delayed, day = TodayFixtures.pastDay()).summary, "a past day is simply open")
    }

    @Test
    fun coastStaysLockedForTheWeekAndShowsTiles() {
        val state = build(PresetId.Coast, at = Instant.parse("2026-10-25T21:30:00Z"))

        assertEquals(Res.string.today_locked_week_title, assertIs<TodaySummary.Locked>(state.summary).title)
        assertNotNull(state.mealTiles)
    }

    @Test
    fun warmToneSoftensTheLockedLine() {
        val locked = assertIs<TodaySummary.Locked>(build(PresetId.Delayed, settings = Settings(tone = Tone.Warm)).summary)
        assertEquals(Res.string.today_locked_day_body_warm, locked.body)
    }

    @Test
    fun blindCountsMealsAndListsWhatWasEaten() {
        val state = build(PresetId.Blind)

        assertEquals(TodaySummary.Counted(done = 3, next = MealSlot.Dinner, nextAround = MealTimes().dinner), state.summary)
        assertEquals(TileStatus.Items(3, kotlinx.datetime.LocalTime(8, 10)), state.mealTiles?.first()?.status)
        assertIs<TileStatus.Empty>(state.mealTiles?.last()?.status)
        val had = assertIs<WhatYouHad.PerMeal>(state.whatYouHad)
        assertEquals("Oat porridge with milk, banana, coffee with milk", had.rows.first().second)
        assertTrue(state.meals.isEmpty())
        assertNull(state.water, "no water amounts in Blind")
    }

    @Test
    fun nutrientFocusShowsSoftenedProteinAndFiber() {
        val focus = assertIs<TodaySummary.Focus>(build(PresetId.Nutrient).summary)

        assertEquals(listOf(Metric.Protein, Metric.Fiber), focus.rows.map { it.metric })
        assertTrue(focus.rows.all { it.display is Display.Soft })
    }

    @Test
    fun fuelLeadsWithMealsAndProteinAndSoftensEnergy() {
        val soft = assertIs<TodaySummary.Soft>(build(PresetId.Fuel).summary)

        assertEquals(MealCount(3, 4), soft.mealCount)
        assertEquals(Metric.Protein, soft.lead?.metric)
        assertEquals("51 of 120 g", soft.lead?.display?.text())
        assertEquals(listOf(Metric.Fiber), soft.rows.map { it.metric })
        assertEquals("Partway through", soft.energy.text())
        val breakfast = assertIs<MealEntry.Full>(build(PresetId.Fuel).meals.first()).card
        assertEquals(listOf("13 g protein"), breakfast.macros.map { it.text }, "only the macro on show, spelled out")
        assertEquals(Display.Hidden, breakfast.calories)
    }

    @Test
    fun rhythmIsAChecklistWithActionsOnTheNextMealOnly() {
        val state = build(PresetId.Rhythm)

        assertNull(state.summary)
        val rhythm = assertNotNull(state.rhythm)
        assertEquals(3, rhythm.done)
        assertIs<RhythmStatus.Logged>(rhythm.rows.first().status)
        assertEquals(listOf(false, false, false, true), rhythm.rows.map { it.showActions })
        assertIs<WhatYouHad.Together>(state.whatYouHad)
        assertTrue(state.meals.isEmpty())
    }

    @Test
    fun rhythmPlansAroundTheMealTimesInSettings() {
        val settings = Settings(mealTimes = MealTimes(dinner = LocalTime(18, 30)))
        val dinner = assertNotNull(build(PresetId.Rhythm, settings = settings).rhythm).rows.last()
        assertEquals(RhythmStatus.Pending(LocalTime(18, 30)), dinner.status)
    }

    @Test
    fun rhythmShowsTheNextReminder_onlyWithTheFlag() {
        assertNull(assertNotNull(build(PresetId.Rhythm, settings = TodayFixtures.remindersSet).rhythm).reminder)

        val on = build(PresetId.Rhythm, settings = TodayFixtures.remindersSet, remindersOn = true)
        assertEquals(RhythmReminder.Next(MealSlot.Dinner, LocalTime(19, 15)), assertNotNull(on.rhythm).reminder)
    }

    @Test
    fun rhythmOffersSetUpWhileMealRemindersAreOff_andNothingOnceEveryMealIsDone() {
        // Never chosen: the synced defaults are not reminders.
        assertEquals(RhythmReminder.Off, assertNotNull(build(PresetId.Rhythm, remindersOn = true).rhythm).reminder)

        val allDone = build(PresetId.Rhythm, day = TodayFixtures.day(dinner = true), settings = TodayFixtures.remindersSet, remindersOn = true)
        assertNull(assertNotNull(allDone.rhythm).reminder)

        val dinnerOff = TodayFixtures.remindersSet.copy(mealReminders = MealReminders(dinner = false))
        assertNull(assertNotNull(build(PresetId.Rhythm, settings = dinnerOff, remindersOn = true).rhythm).reminder)
    }

    @Test
    fun explorerShowsNewFoodsSafeFoodsAndCompactMeals() {
        val state = build(PresetId.Explorer)

        val newFoods = assertIs<TodaySummary.NewFoods>(state.summary)
        assertEquals(listOf("Yesterday", "Thu", "Tue"), newFoods.foods.map { it.seen })
        assertEquals("Rice", state.safeFoods?.first())
        val breakfast = assertIs<MealEntry.Compact>(state.meals.first())
        assertNull(breakfast.feel)
        assertEquals(false, breakfast.askFeel)
        assertNull(state.water)
    }

    @Test
    fun mindfulShowsHowMealsFeltAndAsksWhenUntagged() {
        val state = build(PresetId.Mindful)

        assertNull(state.summary)
        assertNull(state.banner, "v3 drops the explainer banner")
        val breakfast = assertIs<MealEntry.Compact>(state.meals.first())
        assertNotNull(breakfast.feel)
        val snack = assertIs<MealEntry.Compact>(state.meals[2])
        assertTrue(snack.askFeel)
    }

    @Test
    fun sprintCountsTheDayAndSaysWhenItBreaks() {
        val started = TodayFixtures.today.plus(DatePeriod(days = -8))
        val banner = assertNotNull(build(PresetId.Sprint, settings = Settings(sprintStartedOn = started)).banner)

        assertEquals(Res.string.today_banner_sprint, banner.text)
        assertEquals(listOf<Any>(9, "Oct 31"), banner.args)
        assertEquals(
            Res.string.today_banner_sprint_done,
            build(PresetId.Sprint, settings = Settings(sprintStartedOn = TodayFixtures.today.plus(DatePeriod(days = -20)))).banner?.text,
        )
        assertNull(build(PresetId.Coast).banner, "Coast's banner moved to Trends")
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
        assertEquals(CloseDayCard(closed = true), build(PresetId.Full, day = TodayFixtures.pastDay()).closeDay)
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
                listOf(DayMenuItem.CloseDay(isToday = true), DayMenuItem.Nutrients),
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
        listOf(PresetId.Full, PresetId.Delayed, PresetId.Blind, PresetId.Explorer).forEach { preset ->
            val state = build(preset, day = TodayFixtures.untrackedDay(), menuOpen = true)
            assertTrue(state.untracked, "$preset")
            assertNull(state.summary, "no hero card to measure it by in $preset")
            assertNull(state.counts)
            assertNull(state.closeDay)
            assertNull(state.bridge)
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
        val state = TodayFixtures.todayState(policy(PresetId.Blind), day = TodayFixtures.day(dinner = true), added = TodayFixtures.added())
        assertEquals(AddedNotice(MealSlot.Dinner, count = 2), state.added, "a count of foods, so Blind gets it too")
        assertNull(TodayFixtures.todayState(policy(PresetId.Full), day = TodayFixtures.pastDay(), added = TodayFixtures.added()).added)
    }

    @Test
    fun theFoodBridgeIsExplorersAndGoesWhenSkipped() {
        val inputs = TodayFixtures.inputs()
        val explorer = TodayStateBuilder.build(inputs, policy(PresetId.Explorer))
        assertEquals(FoodBridgeCard(from = "rice", to = "rice pudding", shared = "rice"), explorer.bridge)
        assertNull(TodayStateBuilder.build(inputs, policy(PresetId.Explorer), bridgeSkipped = true).bridge)
        assertNull(TodayStateBuilder.build(inputs, policy(PresetId.Full)).bridge)
        assertNull(TodayStateBuilder.build(inputs.copy(explorer = TodayFixtures.explorer().copy(bridge = null)), policy(PresetId.Explorer)).bridge)
    }

    @Test
    fun hiddenWaterHasNoCard() {
        assertNull(build(PresetId.Full, overrides = mapOf(Metric.Water to Visibility.Hide)).water)
        assertNull(build(PresetId.Delayed, overrides = mapOf(Metric.Water to Visibility.Hide)).counts?.glasses)
    }
}
