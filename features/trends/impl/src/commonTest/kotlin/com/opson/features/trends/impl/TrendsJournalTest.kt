package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.RevealOn
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Hunger and Notes cards on Trends, over hand-built days in the fixture week (Oct 19 to 25,
 * seen on Sunday the 25th). The facts underneath are `MealJournalTest`.
 */
class TrendsJournalTest {

    private val monday = LocalDate(2026, 10, 19)
    private val tuesday = LocalDate(2026, 10, 20)
    private val thursday = LocalDate(2026, 10, 22)
    private val today = TrendsFixtures.today

    private fun build(
        days: List<Day>,
        settings: Settings = Settings(),
        notesLinked: Boolean = false,
        preset: PresetId = PresetId.Full,
    ) = TrendsStateBuilder.build(
        TrendsFixtures.trendsInputs(settings, days = days, weighIns = emptyList(), notesLinked = notesLinked),
        TrendsFixtures.policy(preset),
    )

    private fun chart(state: TrendsState) = assertIs<HungerTrend.Chart>(state.hunger)

    @Test
    fun week_aBarADay_asTallAsItsAverageHunger_withAGapWhereNothingWasNoted() {
        val days = listOf(
            day(monday, MealSlot.Breakfast to MealFeeling(hunger = Hunger.Neutral), MealSlot.Dinner to MealFeeling(hunger = Hunger.Starving)),
            day(tuesday, MealSlot.Lunch to MealFeeling(note = "No hunger picked")),
            day(today, MealSlot.Lunch to MealFeeling(hunger = Hunger.Stuffed)),
        )

        val hunger = chart(build(days))

        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), hunger.bars.map { it.label })
        // Neutral and starving average 3 of 4; a fifth of the height is kept for stuffed.
        assertEquals(listOf(0.8f, null, null, null, null, null, 0.2f), hunger.bars.map { it.fraction })
        assertEquals(listOf(false, false, false, false, false, false, true), hunger.bars.map { it.isCurrent })
        assertEquals(3, hunger.answers)
        assertEquals(false, hunger.perWeek)
    }

    @Test
    fun eachMeal_aDotAtItsAverage_overItsSpread_withTheAnswerGivenMost() {
        val days = listOf(
            day(monday, MealSlot.Dinner to MealFeeling(hunger = Hunger.Starving)),
            day(tuesday, MealSlot.Dinner to MealFeeling(hunger = Hunger.Hungry), MealSlot.Breakfast to MealFeeling(hunger = Hunger.Full)),
            day(today, MealSlot.Dinner to MealFeeling(hunger = Hunger.Starving)),
        )

        val meals = chart(build(days)).meals

        assertEquals(listOf(MealSlot.Breakfast, MealSlot.Dinner), meals.map { it.slot }, "meal order, only meals with an answer")
        assertEquals(MealHunger(MealSlot.Breakfast, 0.25f, 0.25f..0.25f, Hunger.Full), meals[0])
        assertEquals(MealHunger(MealSlot.Dinner, (11.0 / 12).toFloat(), 0.75f..1f, Hunger.Starving), meals[1])
    }

    @Test
    fun month_aBarAWeek_averagingEveryAnswerInIt() {
        val oct5 = LocalDate(2026, 10, 5)
        val days = listOf(
            day(oct5, MealSlot.Dinner to MealFeeling(hunger = Hunger.Starving)),
            day(LocalDate(2026, 10, 8), MealSlot.Lunch to MealFeeling(hunger = Hunger.Neutral), MealSlot.Dinner to MealFeeling(hunger = Hunger.Neutral)),
        )

        val hunger = chart(build(days, Settings(trendsRange = TrendsRange.FourWeeks)))

        assertEquals(listOf("9/28", "10/5", "10/12", "10/19"), hunger.bars.map { it.label })
        // Levels 4, 2 and 2 average 8/3; the bar is that plus one, over five.
        assertEquals(listOf(null, (11f / 15), null, null), hunger.bars.map { it.fraction })
        assertEquals(true, hunger.bars.last().isCurrent)
        assertEquals(true, hunger.perWeek)
    }

    @Test
    fun anUntrackedDaysHungerIsAGap() {
        val holiday = day(thursday, MealSlot.Dinner to MealFeeling(hunger = Hunger.Starving)).copy(untracked = true)
        val days = listOf(holiday, day(monday, MealSlot.Dinner to MealFeeling(hunger = Hunger.Hungry)))

        val hunger = chart(build(days))

        assertNull(hunger.bars[3].fraction)
        assertEquals(1, hunger.answers)
    }

    @Test
    fun nothingWritten_isOneSentence_andNoNotesCard() {
        val state = build(listOf(day(monday, MealSlot.Dinner to MealFeeling())))

        assertEquals(HungerTrend.Empty, state.hunger)
        assertNull(state.journal)
    }

    @Test
    fun notesButNoHunger_theHungerSentence_andTheNotesCount() {
        val days = listOf(
            day(monday, MealSlot.Lunch to MealFeeling(note = "With Sam"), MealSlot.Dinner to MealFeeling()),
            day(tuesday, MealSlot.Dinner to MealFeeling(note = "  ")),
        )

        val state = build(days)

        assertEquals(HungerTrend.Empty, state.hunger)
        assertEquals(JournalCard(noted = 1, meals = 3, fullness = emptyList(), moods = emptyList(), linksToNotes = false), state.journal)
    }

    @Test
    fun olderFullnessInTheScalesOrder_andMoodsMostOftenFirst() {
        val days = listOf(
            day(
                monday,
                MealSlot.Breakfast to MealFeeling(fullness = Fullness.Full, moods = setOf(Mood.Rushed)),
                MealSlot.Lunch to MealFeeling(fullness = Fullness.StillHungry, moods = setOf(Mood.Calm, Mood.Social)),
                MealSlot.Dinner to MealFeeling(fullness = Fullness.Full, moods = setOf(Mood.Calm)),
            ),
        )

        val journal = assertNotNull(build(days).journal)

        assertEquals(listOf(Tally(Fullness.StillHungry, 1), Tally(Fullness.Full, 2)), journal.fullness)
        assertEquals(listOf(Tally(Mood.Calm, 2), Tally(Mood.Social, 1), Tally(Mood.Rushed, 1)), journal.moods)
    }

    @Test
    fun theNotesLink_onlyWhenANotesListIsBound() {
        val days = listOf(day(monday, MealSlot.Dinner to MealFeeling(hunger = Hunger.Hungry, note = "Late")))

        assertEquals(false, assertNotNull(build(days).journal).linksToNotes)
        assertEquals(true, assertNotNull(build(days, notesLinked = true).journal).linksToNotes)
    }

    @Test
    fun switchedOff_neitherCardIsBuilt() {
        val state = build(TrendsFixtures.days(), Settings(trendsCards = TrendsCards(journal = false)))

        assertNull(state.hunger)
        assertNull(state.journal)
    }

    /** Hunger is not an amount: Blind keeps it, and so does Later, weekly, before its check opens. */
    @Test
    fun everyModeGetsHunger_evenBeforeTheWeeklyCheck() {
        val inputs = TrendsFixtures.trendsInputs()
        val blind = TrendsStateBuilder.build(inputs, TrendsFixtures.policy(PresetId.NoNumbers))
        val coast = TrendsStateBuilder.build(inputs, TrendsFixtures.policy(overrides = Presets.laterLevels, revealOn = RevealOn.Week))

        assertNotNull(coast.weeklyCheck, "the weekly check has not opened")
        listOf(blind, coast).forEach { state ->
            assertIs<HungerTrend.Chart>(state.hunger)
            assertNotNull(state.journal)
        }
    }

    @Test
    fun theFixtureWeek_hasHungerAndNotes_forThePreviews() {
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(), TrendsFixtures.policy())

        val hunger = chart(state)
        assertTrue(hunger.bars.count { it.fraction != null } >= 3)
        assertEquals(MealSlot.Dinner, hunger.meals.maxBy { it.average }.slot, "starving by dinner")
        val journal = assertNotNull(state.journal)
        assertTrue(journal.noted > 0 && journal.fullness.isNotEmpty() && journal.moods.isNotEmpty())
    }

    /** A day where each of [feelings]' meals has one item, so its feeling counts. */
    private fun day(date: LocalDate, vararg feelings: Pair<MealSlot, MealFeeling>): Day {
        val bySlot = feelings.toMap()
        return Day(
            date = date,
            itemsBySlot = MealSlot.entries.associateWith { slot -> if (slot in bySlot) listOf(item(date, slot)) else emptyList() },
            feelings = bySlot,
        )
    }

    private fun item(date: LocalDate, slot: MealSlot) = LogItem(
        id = "$date-$slot",
        day = date,
        slot = slot,
        loggedAt = TrendsFixtures.clock.now(),
        name = "Toast",
        precision = Precision.Estimate,
        method = LogMethod.Search,
        kcal = 300.0,
    )
}
