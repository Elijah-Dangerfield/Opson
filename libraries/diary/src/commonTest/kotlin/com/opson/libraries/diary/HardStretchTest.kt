package com.opson.libraries.diary

import com.opson.libraries.nutrition.BodySex
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The hard-stretch check: each signal on its own, what keeps an ordinary log from tripping it,
 * and when the note may show.
 */
class HardStretchTest {

    private val today = LocalDate(2026, 10, 30)
    private val zone = TimeZone.UTC

    private fun date(daysAgo: Int): LocalDate = today.minus(daysAgo, DateTimeUnit.DAY)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = date.atTime(hour, minute).toInstant(zone)

    private fun item(date: LocalDate, slot: MealSlot, kcal: Double?, loggedAt: Instant = at(date, 12)) = LogItem(
        id = "$date-$slot-${loggedAt.toEpochMilliseconds()}",
        day = date,
        slot = slot,
        loggedAt = loggedAt,
        name = "Food",
        precision = Precision.Estimate,
        method = LogMethod.QuickNumber,
        kcal = kcal,
    )

    /** A day with [kcal] spread over three meals, so it reads as complete. */
    private fun ate(daysAgo: Int, kcal: Double, closed: Boolean = false, lateAt: LocalTime? = null): Day {
        val date = date(daysAgo)
        val third = kcal / 3
        val slots = listOf(MealSlot.Breakfast, MealSlot.Lunch, MealSlot.Dinner)
        val items = slots.associateWith { slot ->
            val loggedAt = if (slot == MealSlot.Dinner && lateAt != null) LocalDateTime(date, lateAt).toInstant(zone) else at(date, 12)
            listOf(item(date, slot, third, loggedAt))
        }
        return Day(date = date, itemsBySlot = MealSlot.entries.associateWith { items[it].orEmpty() }, closedAt = if (closed) at(date, 21) else null)
    }

    /** A month of ordinary days around [kcal]. */
    private fun usual(kcal: Double = 2000.0, days: IntRange = 1..27): List<Day> = days.map { ate(it, kcal + (it % 3) * 50) }

    private fun check(
        days: List<Day>,
        weighIns: List<WeighIn> = emptyList(),
        looks: List<Instant> = emptyList(),
        sex: BodySex? = BodySex.Female,
    ) = HardStretch.check(StretchInput(today, zone, days, weighIns, looks, sex))

    private fun weighIn(daysAgo: Int, kg: Double, hour: Int = 7) = WeighIn("w$daysAgo-$hour", at(date(daysAgo), hour), kg)

    @Test
    fun anOrdinaryMonth_showsNothing() {
        val result = check(usual())
        assertTrue(result.signals.isEmpty())
        assertFalse(result.shows)
    }

    @Test
    fun anEmptyLog_showsNothing() {
        assertFalse(check(emptyList()).shows)
    }

    @Test
    fun severalVeryLowCompleteDays_areLowDays() {
        val days = usual(days = 15..27) + (1..14).map { if (it % 3 == 0) ate(it, 800.0) else ate(it, 1900.0) }
        val result = check(days)
        assertEquals(setOf(StretchSignal.LowDays), result.signals)
        assertTrue(result.shows)
    }

    @Test
    fun threeLowDays_isNotEnough() {
        val days = usual(days = 15..27) + (1..14).map { if (it in setOf(2, 6, 10)) ate(it, 800.0) else ate(it, 1900.0) }
        assertFalse(StretchSignal.LowDays in check(days).signals)
    }

    @Test
    fun theFloorFollowsSex() {
        val days = usual(days = 15..27) + (1..14).map { if (it % 3 == 0) ate(it, 1100.0) else ate(it, 2300.0) }
        assertFalse(StretchSignal.LowDays in check(days, sex = BodySex.Female).signals)
        assertTrue(StretchSignal.LowDays in check(days, sex = BodySex.Male).signals)
    }

    @Test
    fun aLowDayWithOnlyOneMealLogged_isAForgottenDay_notALowOne() {
        val forgotten = (1..14).filter { it % 3 == 0 }.map { daysAgo ->
            val date = date(daysAgo)
            Day(date = date, itemsBySlot = MealSlot.entries.associateWith { if (it == MealSlot.Breakfast) listOf(item(date, it, 400.0)) else emptyList() })
        }
        val days = usual(days = 15..27) + (1..14).filter { it % 3 != 0 }.map { ate(it, 1900.0) } + forgotten
        assertFalse(check(days).shows)
    }

    @Test
    fun aWrappedUpDay_isCompleteWithFewerMeals() {
        val wrapped = (1..14).filter { it % 3 == 0 }.map { daysAgo ->
            val date = date(daysAgo)
            Day(
                date = date,
                itemsBySlot = MealSlot.entries.associateWith { if (it == MealSlot.Lunch) listOf(item(date, it, 600.0)) else emptyList() },
                closedAt = at(date, 21),
            )
        }
        val days = usual(days = 15..27) + (1..14).filter { it % 3 != 0 }.map { ate(it, 1900.0) } + wrapped
        assertTrue(StretchSignal.LowDays in check(days).signals)
    }

    @Test
    fun untrackedDays_andDaysWithoutAmounts_areLeftOut() {
        val untracked = (1..14).filter { it % 3 == 0 }.map { ate(it, 800.0).copy(untracked = true) }
        val unknown = (1..14).filter { it % 3 == 0 }.map { daysAgo ->
            val date = date(daysAgo)
            Day(date = date, itemsBySlot = MealSlot.entries.associateWith { listOf(item(date, it, if (it == MealSlot.Snack) null else 200.0)) })
        }
        val rest = usual(days = 15..27) + (1..14).filter { it % 3 != 0 }.map { ate(it, 1900.0) }
        assertFalse(check(rest + untracked).shows)
        assertFalse(check(rest + unknown).shows)
    }

    @Test
    fun todayStillGoing_neverCountsAsLow() {
        val days = usual(days = 15..27) + (1..14).map { if (it % 4 == 0) ate(it, 800.0) else ate(it, 1900.0) } + ate(0, 300.0)
        assertFalse(StretchSignal.LowDays in check(days).signals)
    }

    @Test
    fun bigDaysBesideLowOnes_twice_areSwings() {
        val days = usual(days = 1..27).associateBy { it.date }.toMutableMap()
        listOf(ate(10, 4000.0), ate(9, 700.0), ate(4, 3800.0), ate(5, 900.0)).forEach { days[it.date] = it }
        val result = check(days.values.toList())
        assertTrue(StretchSignal.Swings in result.signals)
        assertTrue(result.shows)
    }

    @Test
    fun oneBigDay_isJustABigDay() {
        val days = usual(days = 1..27).associateBy { it.date }.toMutableMap()
        listOf(ate(6, 4000.0), ate(5, 900.0)).forEach { days[it.date] = it }
        assertFalse(check(days.values.toList()).shows)
    }

    @Test
    fun bigDaysAlone_withNoLowDayNear_areNotSwings() {
        val days = usual(days = 1..27).associateBy { it.date }.toMutableMap()
        listOf(ate(12, 4000.0), ate(8, 3900.0), ate(3, 4100.0)).forEach { days[it.date] = it }
        assertFalse(StretchSignal.Swings in check(days.values.toList()).signals)
    }

    @Test
    fun weighingInManyTimesADay_onSeveralDays() {
        val many = listOf(1, 3, 5).flatMap { daysAgo -> listOf(7, 12, 18).map { weighIn(daysAgo, 60.0, it) } }
        assertEquals(setOf(StretchSignal.ManyWeighIns), check(usual(), weighIns = many).signals)
    }

    @Test
    fun aScaleSendingTwoReadings_isOneWeighIn() {
        val doubled = listOf(1, 3, 5).flatMap { daysAgo ->
            listOf(WeighIn("a$daysAgo", at(date(daysAgo), 7, 0), 60.0), WeighIn("b$daysAgo", at(date(daysAgo), 7, 1), 60.1), weighIn(daysAgo, 60.0, 19))
        }
        assertFalse(StretchSignal.ManyWeighIns in check(usual(), weighIns = doubled).signals)
    }

    @Test
    fun weightFallingFasterThanTheSafePace_forWeeks_isFastLoss() {
        // About 1.4 kg a week from 70 kg: 2% of body weight.
        val falling = (0..24 step 3).map { weighIn(it, 70.0 - (24 - it) * 0.2) }
        assertEquals(setOf(StretchSignal.FastLoss), check(usual(), weighIns = falling).signals)
    }

    @Test
    fun aGentlePace_orTooShortASpan_isNotFastLoss() {
        val gentle = (0..24 step 3).map { weighIn(it, 70.0 - (24 - it) * 0.05) }
        val firstWeeks = (0..12 step 3).map { weighIn(it, 70.0 - (12 - it) * 0.3) }
        assertFalse(StretchSignal.FastLoss in check(usual(), weighIns = gentle).signals)
        assertFalse(StretchSignal.FastLoss in check(usual(), weighIns = firstWeeks).signals)
    }

    @Test
    fun supportingSignals_needASecond() {
        val looks = (1..3).map { at(date(it), 21) }
        val hardDays = (1..4).map { ate(it, 2000.0).copy(feeling = DayFeeling.Hard) }
        val rest = usual(days = 5..27)
        val onlyLooks = check(rest + (1..4).map { ate(it, 2000.0) }, looks = looks)
        assertEquals(setOf(StretchSignal.CutBackLooks), onlyLooks.signals)
        assertFalse(onlyLooks.shows)
        val both = check(rest + hardDays, looks = looks)
        assertEquals(setOf(StretchSignal.CutBackLooks, StretchSignal.HardDays), both.signals)
        assertTrue(both.shows)
    }

    @Test
    fun lateLoggingOnBigDays_isSupporting() {
        val days = usual(days = 1..27).associateBy { it.date }.toMutableMap()
        listOf(ate(8, 4000.0, lateAt = LocalTime(23, 30)), ate(3, 4200.0, lateAt = LocalTime(23, 10))).forEach { days[it.date] = it }
        assertEquals(setOf(StretchSignal.LateAfterBigDays), check(days.values.toList()).signals)
    }

    @Test
    fun oldSignals_fallOutOfTheWindow() {
        val looks = (20..22).map { at(date(it), 21) }
        val days = usual(days = 1..14) + (15..27).map { if (it % 3 == 0) ate(it, 800.0) else ate(it, 1900.0) }
        assertTrue(check(days, looks = looks).signals.isEmpty())
    }
}
