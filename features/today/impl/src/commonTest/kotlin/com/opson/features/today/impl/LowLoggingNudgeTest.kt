package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LoggingHabits
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The late-day card: when today got away, against the person's own usual day, never sooner. */
class LowLoggingNudgeTest {
    private val habits = TodayFixtures.habits
    private val evening = TodayFixtures.lateEvening

    private fun shows(
        day: Day = TodayFixtures.lightDay(),
        settings: Settings = Settings(),
        habits: LoggingHabits? = this.habits,
        now: LocalTime? = evening,
        dismissedOn: kotlinx.datetime.LocalDate? = null,
    ): Boolean = LowLoggingNudge.shows(TodayFixtures.inputs(day, settings, habits = habits, now = now, nudgeDismissedOn = dismissedOn))

    @Test
    fun aLightDay_lateInTheEvening_getsTheCard() {
        assertTrue(shows())
        assertTrue(shows(day = Day.empty(TodayFixtures.today)), "nothing logged at all")
    }

    @Test
    fun anOrdinaryDay_doesNot() {
        assertFalse(shows(day = TodayFixtures.day(dinner = true)))
    }

    @Test
    fun theEvening_startsAnHourAfterTheUsualLastMeal_withinSixToTen_elseAtEight() {
        assertEquals(LocalTime(20, 15), LowLoggingNudge.eveningFrom(habits))
        assertFalse(shows(now = LocalTime(20, 10)))
        assertTrue(shows(now = LocalTime(20, 15)))

        assertEquals(LocalTime(20, 0), LowLoggingNudge.eveningFrom(habits.copy(learned = emptySet())))
        val lateSnack = habits.copy(usual = MealTimes(snack = LocalTime(23, 0)), learned = setOf(MealSlot.Snack))
        assertEquals(LocalTime(22, 0), LowLoggingNudge.eveningFrom(lateSnack))
        val earlyDinner = habits.copy(usual = MealTimes(dinner = LocalTime(15, 0)), learned = setOf(MealSlot.Dinner))
        assertEquals(LocalTime(18, 0), LowLoggingNudge.eveningFrom(earlyDinner))
    }

    @Test
    fun notOnTheFirstDays_beforeThereIsAUsualDayToCompareWith() {
        assertFalse(shows(habits = habits.copy(loggedDays = LowLoggingNudge.MIN_LOGGED_DAYS - 1)))
        assertFalse(shows(habits = null))
    }

    @Test
    fun onceADay_andNeverOnAnUntrackedClosedOrOtherDay() {
        assertFalse(shows(dismissedOn = TodayFixtures.today))
        assertTrue(shows(dismissedOn = TodayFixtures.today.minus(DatePeriod(days = 1))), "yesterday's Not now was for yesterday")
        assertFalse(shows(day = TodayFixtures.lightDay().copy(untracked = true)))
        assertFalse(shows(day = TodayFixtures.lightDay().copy(closedAt = TodayFixtures.evening)))
        assertFalse(shows(day = Day.empty(TodayFixtures.today.minus(DatePeriod(days = 1)))), "a past day")
    }

    /** No numbers, Macros only, or numbers having been hard: the card counts entries, so an amount never decides it. */
    @Test
    fun whereCaloriesAreNotOnShow_entriesDecide_notEnergy() {
        // One big meal: plenty of energy, but one entry against a usual nine.
        val oneBigMeal = TodayFixtures.lightDay().let { day ->
            day.copy(itemsBySlot = mapOf(MealSlot.Lunch to day.items(MealSlot.Breakfast).map { it.copy(kcal = 2500.0) }))
        }
        assertFalse(shows(day = oneBigMeal), "All numbers reads energy: a usual day's worth")
        listOf(PresetId.NoNumbers, PresetId.MacrosOnly, PresetId.MacrosWithGrams).forEach { preset ->
            assertTrue(shows(day = oneBigMeal, settings = Settings(numbers = NumbersSettings(preset = preset))), "$preset")
        }
        assertTrue(shows(day = oneBigMeal, settings = Settings(onboarding = OnboardingAnswers(hard = true))))
    }

    @Test
    fun theBuilder_putsTheCardOnToday() {
        val state = TodayStateBuilder.build(
            TodayFixtures.inputs(TodayFixtures.lightDay(), habits = habits, now = evening),
            policy(PresetId.Full),
        )
        assertTrue(state.lowLogging)
    }
}
