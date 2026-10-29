package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The running total's arithmetic: which days it adds, what it adds for each (burned less eaten),
 * the body weight that comes to by the 3,500 kcal a pound rule, and when the card is not drawn.
 */
class RunningTotalTest {

    private val today = TrendsFixtures.today
    private val yesterday = today.minus(1, DateTimeUnit.DAY)

    @Test
    fun theWindow_endsYesterday_untilTodayIsClosed() {
        assertEquals(today.minus(30, DateTimeUnit.DAY) to yesterday, RunningTotals.window(RunningSpan.Month, Day.empty(today), goalStart = null))
        assertEquals(today.minus(7, DateTimeUnit.DAY) to yesterday, RunningTotals.window(RunningSpan.Week, Day.empty(today), goalStart = null))
        val closed = Day.empty(today).copy(closedAt = Instant.parse("2026-10-25T21:00:00Z"))
        assertEquals(today.minus(6, DateTimeUnit.DAY) to today, RunningTotals.window(RunningSpan.Week, closed, goalStart = null))
        assertEquals(LocalDate(2026, 10, 1) to yesterday, RunningTotals.window(RunningSpan.SinceStart, Day.empty(today), LocalDate(2026, 10, 1)))
        assertNull(RunningTotals.window(RunningSpan.SinceStart, Day.empty(today), goalStart = null))
    }

    @Test
    fun theTotal_isEachDaysBurnedLessEaten_addedUp() {
        val balances = listOf(
            RunningTotals.DayBalance(yesterday, burnedKcal = 2300, eatenKcal = 1800.0),
            RunningTotals.DayBalance(yesterday.minus(1, DateTimeUnit.DAY), burnedKcal = 2200, eatenKcal = 2400.0),
            RunningTotals.DayBalance(yesterday.minus(2, DateTimeUnit.DAY), burnedKcal = 2100, eatenKcal = 1600.0),
        )
        assertEquals(500.0 - 200.0 + 500.0, RunningTotals.total(balances))
        assertEquals(Balance.Under, RunningTotals.side(800.0))
        assertEquals(Balance.Over, RunningTotals.side(-800.0))
        assertEquals(Balance.Even, RunningTotals.side(49.0))
        assertEquals(Balance.Over, RunningTotals.side(-50.0))
    }

    @Test
    fun weight_isTheRuleOfThumb_3500APound() {
        assertEquals(1.0, RunningTotals.weightKg(7700.0))
        assertEquals(1.0, RunningTotals.weightKg(-7700.0), "a surplus is the same size, the other way")
        // 3,500 kcal under, in pounds: the rule of thumb's one pound.
        val card = card(days = (1..7).map { logged(today.minus(it, DateTimeUnit.DAY), 1800.0) }, burnedKcal = 2300, unit = WeightUnit.Lb, span = RunningSpan.Week)
        assertEquals(Display.Shown("3,500 kcal"), card.balance)
        assertEquals(Balance.Under, card.side)
        assertEquals(Display.Shown("1.0 lb"), card.weight)
        assertEquals(7, card.days)
        assertEquals(7, card.of)
    }

    @Test
    fun onlyFullyLoggedDaysWithABurn_areCounted() {
        val days = listOf(
            logged(today.minus(1, DateTimeUnit.DAY), 1800.0),
            logged(today.minus(2, DateTimeUnit.DAY), 1800.0).copy(untracked = true),
            logged(today.minus(3, DateTimeUnit.DAY), 1800.0).copy(ateNoDetails = setOf(MealSlot.Dinner)),
            Day.empty(today.minus(4, DateTimeUnit.DAY)),
            logged(today.minus(5, DateTimeUnit.DAY), 2500.0),
            // In progress: today waits until it is closed.
            logged(today, 500.0),
        )
        val card = card(days, burnedKcal = 2300, span = RunningSpan.Week)
        assertEquals(2, card.days, "the untracked day, the meal without an amount and the empty day are left out")
        assertEquals(Display.Shown("300 kcal"), card.balance, "500 under, then 200 over")
        assertEquals(Balance.Under, card.side)
    }

    @Test
    fun aDayWithNoBurn_isLeftOut() {
        val days = (1..3).map { logged(today.minus(it, DateTimeUnit.DAY), 1800.0) }
        val burned = mapOf(today.minus(1, DateTimeUnit.DAY) to burned(2300))
        val card = assertNotNull(build(days, burned).runningTotal)
        assertEquals(1, card.days)
    }

    @Test
    fun theScale_sitsBesideTheEstimate() {
        val weighIns = listOf(30, 20, 10, 1).mapIndexed { index, back ->
            WeighIn("w$index", LocalDateTime(today.minus(back, DateTimeUnit.DAY), LocalTime(7, 0)).toInstant(TrendsFixtures.timeZone), 80.0 - index * 1.0)
        }
        val days = (1..30).map { logged(today.minus(it, DateTimeUnit.DAY), 1800.0) }
        val card = assertNotNull(build(days, burnedFor(days, 2300), weighIns = weighIns).runningTotal)
        assertEquals(ScaleDirection.Down, card.scale?.direction)
        assertEquals(30, card.of)
    }

    @Test
    fun notDrawn_withoutBurned_inHiddenModes_switchedOff_orAfterNumbersHaveBeenHard() {
        val days = (1..7).map { logged(today.minus(it, DateTimeUnit.DAY), 1800.0) }
        val burned = burnedFor(days, 2300)
        assertNull(build(days, burned = null).runningTotal, "release.goalsAndEnergy off")
        assertNull(build(days, burned, policy = TrendsFixtures.policy(PresetId.MacrosWithGrams)).runningTotal)
        assertNull(build(days, burned, policy = TrendsFixtures.policy(PresetId.NoNumbers)).runningTotal)
        assertNull(build(days, burned, policy = TrendsFixtures.policy(overrides = mapOf(Metric.EnergyOut to Visibility.Hide))).runningTotal)
        assertNull(build(days, burned, settings = Settings(trendsCards = TrendsCards(runningTotal = false))).runningTotal)
        assertNull(build(days, burned, settings = Settings(onboarding = OnboardingAnswers(hard = true))).runningTotal)
        assertNotNull(build(days, burned).runningTotal)
    }

    @Test
    fun weightHidden_leavesThePoundsAndTheScaleOut_butKeepsTheTotal() {
        val days = (1..7).map { logged(today.minus(it, DateTimeUnit.DAY), 1800.0) }
        val card = assertNotNull(build(days, burnedFor(days, 2300), policy = TrendsFixtures.policy(overrides = mapOf(Metric.Weight to Visibility.Hide))).runningTotal)
        assertNull(card.weight)
        assertNull(card.scale)
        assertEquals(Display.Shown("3,500 kcal"), card.balance)
    }

    @Test
    fun sinceYouStarted_isOfferedOnceTheGoalHasAStart_andIsThenTheDefault() {
        assertEquals(listOf(RunningSpan.Week, RunningSpan.Month), RunningTotals.spans(goalStart = null))
        val days = (1..10).map { logged(today.minus(it, DateTimeUnit.DAY), 1800.0) }
        val inputs = TrendsFixtures.trendsInputs(days = days, weighIns = emptyList(), burnedByDay = burnedFor(days, 2300)).copy(goalStart = today.minus(5, DateTimeUnit.DAY))
        val card = assertNotNull(TrendsStateBuilder.build(inputs, TrendsFixtures.policy()).runningTotal)
        assertEquals(RunningSpan.SinceStart, card.span)
        assertEquals(5, card.days)
    }

    @Test
    fun nothingCounted_isTheEmptyCard() {
        val card = assertNotNull(build(emptyList(), burned = emptyMap()).runningTotal)
        assertEquals(true, card.empty)
        assertEquals(Display.Hidden, card.balance)
    }

    private fun card(
        days: List<Day>,
        burnedKcal: Int,
        unit: WeightUnit = WeightUnit.Kg,
        span: RunningSpan = RunningSpan.Month,
    ): RunningTotalCard = assertNotNull(
        build(days, burnedFor(days, burnedKcal), settings = Settings(weightUnit = unit), ui = TrendsUi(runningSpan = span)).runningTotal,
    )

    private fun build(
        days: List<Day>,
        burned: Map<LocalDate, Burned>?,
        settings: Settings = Settings(),
        policy: com.opson.libraries.nutrition.VisibilityPolicy = TrendsFixtures.policy(),
        weighIns: List<WeighIn> = emptyList(),
        ui: TrendsUi = TrendsUi(),
    ): TrendsState = TrendsStateBuilder.build(
        TrendsFixtures.trendsInputs(settings = settings, days = days, weighIns = weighIns, burnedByDay = burned),
        policy,
        ui,
    )

    private fun burnedFor(days: List<Day>, kcal: Int): Map<LocalDate, Burned> = (days.map { it.date } + today).associateWith { burned(kcal) }

    /** Resting only, so the total is exactly [kcal]. */
    private fun burned(kcal: Int) = Burned(RestingEnergy(kcal, RestingFrom.Own), activityCounted = false)

    private fun logged(date: LocalDate, kcal: Double) = Day(
        date = date,
        itemsBySlot = MealSlot.entries.associateWith { slot ->
            if (slot != MealSlot.Lunch) {
                emptyList()
            } else {
                listOf(
                    LogItem(
                        id = "lunch-$date",
                        day = date,
                        slot = MealSlot.Lunch,
                        loggedAt = Instant.parse("2026-10-20T12:00:00Z"),
                        name = "Lunch",
                        precision = Precision.Estimate,
                        method = LogMethod.Search,
                        kcal = kcal,
                    ),
                )
            }
        },
    )
}
