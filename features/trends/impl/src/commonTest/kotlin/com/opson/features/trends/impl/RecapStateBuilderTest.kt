package com.opson.features.trends.impl

import com.opson.features.trends.impl.recap.BurnedPart
import com.opson.features.trends.impl.recap.RecapBanners
import com.opson.features.trends.impl.recap.RecapDayStatus
import com.opson.features.trends.impl.recap.RecapFixtures
import com.opson.features.trends.impl.recap.RecapInputs
import com.opson.features.trends.impl.recap.RecapStateBuilder
import com.opson.features.trends.impl.recap.RecapViewModel
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MoodLevel
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.RecapKind
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A recap's arithmetic: how skipped and empty days count (totals over logged days only, averages
 * per logged day), what Burned is made of, what each mode keeps, and the period math the banner,
 * Trends and Wrap up the day agree on.
 */
class RecapStateBuilderTest {

    private val full = TrendsFixtures.policy()
    private val week = RecapPeriod(RecapKind.Week, LocalDate(2026, 10, 12))
    private val today = LocalDate(2026, 10, 20)

    private fun day(offset: Int, kcal: Double? = null, untracked: Boolean = false, food: String = "Toast", checkIn: CheckIn? = null): Day {
        val date = week.start.plus(offset, DateTimeUnit.DAY)
        val items = kcal?.let {
            listOf(
                LogItem(
                    id = "$date",
                    day = date,
                    slot = MealSlot.Lunch,
                    loggedAt = Instant.parse("${date}T12:00:00Z"),
                    name = food,
                    precision = Precision.Estimate,
                    method = LogMethod.Search,
                    foodId = food.lowercase(),
                    kcal = it,
                ),
            )
        }.orEmpty()
        return Day(date = date, itemsBySlot = MealSlot.entries.associateWith { slot -> if (slot == MealSlot.Lunch) items else emptyList() }, untracked = untracked, checkIn = checkIn)
    }

    private fun inputs(
        days: List<Day>,
        settings: Settings = Settings(),
        burned: Map<LocalDate, Burned>? = null,
        checkInOn: Boolean = false,
    ) = RecapInputs(
        today = today,
        period = week,
        settings = settings,
        days = days,
        weighIns = emptyList(),
        timeZone = TrendsFixtures.timeZone,
        burnedByDay = burned,
        checkInOn = checkInOn,
    )

    @Test
    fun skippedAndEmptyDays_areLeftOutOfTotals_andAveragesArePerLoggedDay() {
        val days = listOf(
            day(0, kcal = 1000.0),
            day(1, kcal = 2000.0),
            day(2, kcal = 3000.0),
            // Skipped with food in it: still left out, as Trends leaves it out.
            day(3, kcal = 9000.0, untracked = true),
        )
        val state = RecapStateBuilder.build(inputs(days), full)

        val showingUp = assertNotNull(state.showingUp)
        assertEquals(3, showingUp.logged)
        assertEquals(1, showingUp.skipped)
        assertEquals(3, showingUp.notLogged)
        assertEquals(0, showingUp.ahead)
        assertTrue(showingUp.countingNote, "the note says what the totals count when a day was left out")
        assertEquals(
            listOf(RecapDayStatus.Logged, RecapDayStatus.Logged, RecapDayStatus.Logged, RecapDayStatus.Skipped) + List(3) { RecapDayStatus.NotLogged },
            showingUp.days.map { it.status },
        )

        val eaten = assertNotNull(state.eaten)
        assertEquals(Display.Shown("6,000 kcal"), eaten.total)
        assertEquals(Display.Shown("2,000 kcal"), eaten.average, "per logged day, never per day of the week")
        assertEquals(3, eaten.days)
        assertNull(eaten.bars[3].fraction, "a skipped day is a gap, never a low bar")
        assertNull(eaten.bars[4].fraction)
    }

    @Test
    fun aWeekStillGoing_countsWhatsLeftAsStillToCome() {
        val going = RecapStateBuilder.build(inputs(listOf(day(0, kcal = 1500.0))).copy(period = week.next(), today = week.next().start.plus(2, DateTimeUnit.DAY)), full)
        assertTrue(going.inProgress)
        assertFalse(going.canGoForward)
        val showingUp = assertNotNull(going.showingUp)
        assertEquals(5, showingUp.ahead, "today, empty, is still to come, and so are the four after it")
        assertEquals(2, showingUp.notLogged)
    }

    @Test
    fun burned_isAddedUpOverLoggedDays_inItsParts() {
        val days = listOf(day(0, kcal = 2000.0), day(1, kcal = 1800.0), day(2))
        val burn = Burned(RestingEnergy(1600, RestingFrom.Health), movementKcal = 400, workoutKcal = 300, exerciseKcal = 100, foodKcal = 200)
        val burned = week.days.associateWith { burn }
        val section = assertNotNull(RecapStateBuilder.build(inputs(days, burned = burned), full).burned)

        assertEquals(2, section.days, "a day with nothing logged is left out, as eaten is")
        assertEquals(Display.Shown("5,200 kcal"), section.total)
        assertEquals(Display.Shown("2,600 kcal"), section.average)
        assertEquals(
            mapOf(
                BurnedPart.Resting to Display.Shown("3,200 kcal"),
                BurnedPart.Activity to Display.Shown("800 kcal"),
                BurnedPart.Exercise to Display.Shown("800 kcal"),
                BurnedPart.Digesting to Display.Shown("400 kcal"),
            ),
            section.parts.associate { it.part to it.amount },
        )
        assertEquals(1f, section.parts.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun theBalance_followsTheRunningTotalsRules() {
        val days = listOf(day(0, kcal = 1800.0), day(1, kcal = 2000.0))
        val burned = week.days.associateWith { Burned(RestingEnergy(2300, RestingFrom.Health)) }
        val balance = assertNotNull(RecapStateBuilder.build(inputs(days, burned = burned), full).balance)
        assertEquals(Display.Shown("800 kcal"), balance.amount)
        assertEquals(Balance.Under, balance.side)
        assertEquals(2, balance.days)
    }

    @Test
    fun afterNumbersHaveBeenHard_aRecapIsAboutShowingUp_neverAnAmount() {
        val state = RecapFixtures.state(full, hard = true)
        assertTrue(state.gentle)
        assertNull(state.eaten)
        assertNull(state.burned)
        assertNull(state.balance)
        assertNull(state.weight)
        assertNull(state.plan)
        assertNotNull(state.showingUp)
        assertNotNull(state.checkIns)
        assertTrue(state.topFoods.all { it.days == null }, "foods by name only")
        assertFalse(state.milestones?.counts == true, "no counts in the run, if there is one")
    }

    @Test
    fun eachModeKeepsOnlyWhatItShows() {
        val noNumbers = RecapFixtures.state(TrendsFixtures.policy(PresetId.NoNumbers))
        assertNull(noNumbers.eaten)
        assertNull(noNumbers.burned)
        assertNull(noNumbers.balance)
        assertNull(noNumbers.plan)
        assertNotNull(noNumbers.showingUp)

        val allNumbers = RecapFixtures.state(full, deficit = true)
        assertNotNull(allNumbers.eaten)
        assertNotNull(allNumbers.burned)
        assertNotNull(allNumbers.balance)
        assertNotNull(allNumbers.weight)
        assertNotNull(allNumbers.plan)
    }

    @Test
    fun checkIns_sayTheStepNearestTheirAverage() {
        val section = assertNotNull(
            RecapStateBuilder.checkIns(
                listOf(
                    CheckIn(hunger = HungerLevel.of(4), energy = Energy.Low, mood = MoodLevel.Good),
                    CheckIn(hunger = HungerLevel.of(6), energy = Energy.Good, mood = MoodLevel.Good),
                ),
            ),
        )
        assertEquals(2, section.days)
        assertEquals(HungerLevel.of(5)?.label, section.hunger)
        assertEquals(Energy.Ok.label, section.energy)
        assertEquals(MoodLevel.Good.label, section.mood)
        assertNull(section.sleep)
        assertNull(RecapStateBuilder.checkIns(emptyList()))
    }

    @Test
    fun topFoods_areTheOnesOnTheMostDays_neverOnlyOnce() {
        val days = listOf(day(0, 300.0, food = "Oats"), day(1, 300.0, food = "Oats"), day(2, 300.0, food = "Soup"), day(3, 300.0, food = "Oats"), day(4, 300.0, food = "Soup"), day(5, 300.0, food = "Cake"))
        val foods = RecapStateBuilder.topFoods(days)
        assertEquals(listOf("Oats", "Soup"), foods.map { it.name })
        assertEquals(listOf(3, 2), foods.map { it.days })
    }

    @Test
    fun aWeekStartsOnThePersonsOwnDay_andAMonthOnItsFirst() {
        val sunday = LocalDate(2026, 10, 25)
        assertEquals(LocalDate(2026, 10, 19), RecapPeriod.weekOf(sunday, DayOfWeek.MONDAY).start)
        assertEquals(sunday, RecapPeriod.weekOf(sunday, DayOfWeek.SUNDAY).start)
        assertEquals(LocalDate(2026, 10, 12), RecapPeriod.lastWeek(sunday, DayOfWeek.MONDAY).start)
        assertEquals(LocalDate(2026, 9, 30), RecapPeriod.lastMonth(sunday).end)
        assertEquals(30, RecapPeriod.lastMonth(sunday).dayCount)
        assertEquals(LocalDate(2026, 2, 28), RecapPeriod.monthOf(LocalDate(2026, 2, 10)).end)
    }

    @Test
    fun aRoute_opensItsPeriod_orTheLastOneOver() {
        val sunday = LocalDate(2026, 10, 25)
        assertEquals(RecapPeriod(RecapKind.Week, LocalDate(2026, 10, 12)), RecapViewModel.initialPeriod(RecapKind.Week, "", sunday, DayOfWeek.MONDAY))
        assertEquals(RecapPeriod(RecapKind.Week, LocalDate(2026, 10, 19)), RecapViewModel.initialPeriod(RecapKind.Week, "2026-10-21", sunday, DayOfWeek.MONDAY))
        assertEquals(RecapPeriod(RecapKind.Month, LocalDate(2026, 9, 1)), RecapViewModel.initialPeriod(RecapKind.Month, "2026-11-01", sunday, DayOfWeek.MONDAY), "a month not begun")
        assertEquals(
            RecapPeriod(RecapKind.Month, LocalDate(2026, 9, 1)),
            RecapViewModel.switched(RecapPeriod(RecapKind.Week, LocalDate(2026, 9, 21)), RecapKind.Month, sunday, DayOfWeek.MONDAY),
        )
        assertEquals(
            RecapPeriod(RecapKind.Week, LocalDate(2026, 10, 12)),
            RecapViewModel.switched(RecapPeriod(RecapKind.Month, LocalDate(2026, 10, 1)), RecapKind.Week, sunday, DayOfWeek.MONDAY),
            "this month is still going, so its last week over",
        )
    }

    @Test
    fun theBanner_offersAWeekForThreeDaysAfter_once_andOnlyWithAFewDaysLogged() {
        val days = listOf(day(0, 500.0), day(1, 500.0))
        val monday = week.end.plus(1, DateTimeUnit.DAY)
        assertEquals(week, RecapBanners.pending(monday, DayOfWeek.MONDAY, emptySet(), days))
        assertEquals(week, RecapBanners.pending(week.end.plus(RecapBanners.SHOWN_DAYS, DateTimeUnit.DAY), DayOfWeek.MONDAY, emptySet(), days))
        assertNull(RecapBanners.pending(week.end.plus(RecapBanners.SHOWN_DAYS + 1, DateTimeUnit.DAY), DayOfWeek.MONDAY, emptySet(), days), "it waits in Trends after")
        assertNull(RecapBanners.pending(monday, DayOfWeek.MONDAY, setOf(week.key), days), "opened or dismissed")
        assertNull(RecapBanners.pending(monday, DayOfWeek.MONDAY, emptySet(), days.take(1)), "one logged day is no recap to announce")
    }

    @Test
    fun theBanner_offersAMonthFirst_thenItsWeek() {
        // Sunday Nov 1 ends a week; October ended the day before.
        val october = RecapPeriod(RecapKind.Month, LocalDate(2026, 10, 1))
        val lastWeek = RecapPeriod(RecapKind.Week, LocalDate(2026, 10, 26))
        val days = (26..31).map { Day(date = LocalDate(2026, 10, it), itemsBySlot = day(0, 500.0).itemsBySlot) }
        val monday = LocalDate(2026, 11, 2)
        assertEquals(october, RecapBanners.pending(monday, DayOfWeek.MONDAY, emptySet(), days))
        assertEquals(lastWeek, RecapBanners.pending(monday, DayOfWeek.MONDAY, setOf(october.key), days))
    }
}
