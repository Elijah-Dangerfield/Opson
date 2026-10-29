package com.opson.libraries.diary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The facts Patterns states. Times are minutes after midnight; nothing here is
 * formatted, so the policy still owns every amount.
 */
class MealPatternsTest {

    private val zone = TimeZone.UTC

    /** A Monday. */
    private val start = LocalDate(2026, 9, 28)
    private val end = start.plus(27, DateTimeUnit.DAY)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private fun item(
        date: LocalDate,
        slot: MealSlot,
        hour: Int,
        minute: Int = 0,
        kcal: Double? = 400.0,
        name: String = "Toast",
        loggedOn: LocalDate = date,
        method: LogMethod = LogMethod.Search,
    ) = LogItem(
        id = "$date-$slot-$hour-$minute-$name",
        day = date,
        slot = slot,
        loggedAt = at(loggedOn, hour, minute),
        name = name,
        precision = Precision.Estimate,
        method = method,
        kcal = kcal,
    )

    private fun day(date: LocalDate, vararg items: LogItem, feelings: Map<MealSlot, MealFeeling> = emptyMap()) =
        Day(date = date, itemsBySlot = MealSlot.entries.associateWith { slot -> items.filter { it.slot == slot } }, feelings = feelings)

    private fun facts(days: List<Day>) = MealPatterns.of(days, start, end, zone)

    @Test
    fun theUsualWindowIsTheMiddleHalfAndTheTypicalTimeTheMedian() {
        val minutes = listOf(0, 5, 10, 15, 50)
        val days = minutes.mapIndexed { i, m -> day(start.plus(i, DateTimeUnit.DAY), item(start.plus(i, DateTimeUnit.DAY), MealSlot.Breakfast, 8, m)) }

        val facts = facts(days)

        assertEquals(listOf(480, 485, 490, 495, 530), facts.times.getValue(MealSlot.Breakfast))
        assertEquals(485..495, facts.usual.getValue(MealSlot.Breakfast))
        assertEquals(490, facts.typical.getValue(MealSlot.Breakfast))
    }

    @Test
    fun twoMealsAreNotEnoughForAUsualWindow() {
        val days = (0..1).map { day(start.plus(it, DateTimeUnit.DAY), item(start.plus(it, DateTimeUnit.DAY), MealSlot.Lunch, 12)) }

        val facts = facts(days)

        assertEquals(null, facts.usual[MealSlot.Lunch])
        assertEquals(720, facts.typical[MealSlot.Lunch])
    }

    @Test
    fun aMealsTimeIsItsFirstItem() {
        val date = start
        val facts = facts(listOf(day(date, item(date, MealSlot.Dinner, 19, 30, name = "b"), item(date, MealSlot.Dinner, 19, 5, name = "a"))))

        assertEquals(listOf(19 * 60 + 5), facts.times.getValue(MealSlot.Dinner))
    }

    @Test
    fun backFilledItemsCountForHowOftenButNotForWhen() {
        val date = start
        val facts = facts(listOf(day(date, item(date, MealSlot.Breakfast, 9, loggedOn = date.plus(1, DateTimeUnit.DAY)))))

        assertEquals(null, facts.times[MealSlot.Breakfast])
        assertEquals(1, facts.frequency.getValue(MealSlot.Breakfast))
    }

    @Test
    fun frequencyCountsAteNoDetailsAndIgnoresDaysOutsideTheSpan() {
        val inside = Day(date = start, ateNoDetails = setOf(MealSlot.Snack))
        val outside = day(end.plus(1, DateTimeUnit.DAY), item(end.plus(1, DateTimeUnit.DAY), MealSlot.Snack, 15))

        val facts = facts(listOf(inside, outside))

        assertEquals(1, facts.frequency.getValue(MealSlot.Snack))
        assertEquals(0, facts.frequency.getValue(MealSlot.Dinner))
        assertEquals(28, facts.dayCount)
    }

    @Test
    fun anUntrackedDayIsLeftOutOfEveryFactAndTheDayCount() {
        val tracked = day(start, item(start, MealSlot.Breakfast, 8))
        val holiday = start.plus(1, DateTimeUnit.DAY)
        val untracked = day(holiday, item(holiday, MealSlot.Breakfast, 11), item(holiday, MealSlot.Dinner, 21)).copy(untracked = true)

        val facts = facts(listOf(tracked, untracked))

        assertEquals(1, facts.frequency.getValue(MealSlot.Breakfast))
        assertEquals(0, facts.frequency.getValue(MealSlot.Dinner))
        assertEquals(listOf(8 * 60), facts.times.getValue(MealSlot.Breakfast))
        assertEquals(27, facts.dayCount, "a holiday is not a day with meals missing")
    }

    @Test
    fun theLongestGapIsTheBackToBackPairWithTheLongestUsualGap() {
        val days = (0 until 5).map { i ->
            val date = start.plus(i, DateTimeUnit.DAY)
            day(date, item(date, MealSlot.Breakfast, 8), item(date, MealSlot.Lunch, 12, 30), item(date, MealSlot.Dinner, 18, 30))
        }

        assertEquals(MealGap(MealSlot.Lunch, MealSlot.Dinner, 360), facts(days).longestGap)
    }

    @Test
    fun aGapSeenOnFewerThanThreeDaysIsNotAFact() {
        val days = (0 until 2).map { i ->
            val date = start.plus(i, DateTimeUnit.DAY)
            day(date, item(date, MealSlot.Lunch, 12), item(date, MealSlot.Dinner, 19))
        }

        assertNull(facts(days).longestGap)
    }

    @Test
    fun aMealThatRunsLaterOnAFewWeekdaysIsNamed() {
        // Four weeks of dinner at 6:30 pm, an hour later on Fridays and Saturdays.
        val days = (0 until 28).map { i ->
            val date = start.plus(i, DateTimeUnit.DAY)
            val late = date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY
            day(date, item(date, MealSlot.Dinner, if (late) 19 else 18, 30))
        }

        assertEquals(LaterOn(MealSlot.Dinner, listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), 60), facts(days).laterOn)
    }

    @Test
    fun anEvenWeekHasNoLaterDays() {
        val days = (0 until 14).map { i -> start.plus(i, DateTimeUnit.DAY).let { day(it, item(it, MealSlot.Dinner, 18, 30)) } }

        assertNull(facts(days).laterOn)
    }

    @Test
    fun hungerIsTheMostCommonTagPerMealAndTheTieGoesToTheHungrierOne() {
        fun tagged(i: Int, hunger: Hunger) = start.plus(i, DateTimeUnit.DAY).let { date ->
            day(date, item(date, MealSlot.Dinner, 19), feelings = mapOf(MealSlot.Dinner to MealFeeling(hunger = hunger)))
        }
        val days = listOf(tagged(0, Hunger.Hungry), tagged(1, Hunger.Starving), tagged(2, Hunger.Starving), tagged(3, Hunger.Hungry))

        val facts = facts(days)

        assertEquals(Hunger.Starving, facts.hunger.getValue(MealSlot.Dinner))
        assertEquals(4, facts.taggedMeals)
    }

    /** Lunch tagged [hunger] on day [i], with breakfast eaten or skipped. */
    private fun hungerDay(i: Int, hunger: Hunger, breakfast: Boolean) = start.plus(i, DateTimeUnit.DAY).let { date ->
        val items = listOfNotNull(
            item(date, MealSlot.Breakfast, 8).takeIf { breakfast },
            item(date, MealSlot.Lunch, 13),
        )
        day(date, *items.toTypedArray(), feelings = mapOf(MealSlot.Lunch to MealFeeling(hunger = hunger)))
    }

    @Test
    fun hungerByWeekdayAveragesThatWeekdaysTaggedMeals() {
        // start is a Monday: day 0 and day 7 are Mondays, day 1 a Tuesday.
        val days = listOf(
            hungerDay(0, Hunger.Starving, breakfast = true),
            hungerDay(7, Hunger.Neutral, breakfast = true),
            hungerDay(1, Hunger.Stuffed, breakfast = true),
        )

        val byWeekday = facts(days).hungerByWeekday

        // Starving is 4 and Neutral 2 on the five-step scale.
        assertEquals(3.0, byWeekday.getValue(DayOfWeek.MONDAY))
        assertEquals(0.0, byWeekday.getValue(DayOfWeek.TUESDAY))
        assertNull(byWeekday[DayOfWeek.WEDNESDAY])
    }

    @Test
    fun hungrierWithoutBreakfastNamesThoseWeekdays() {
        val days = listOf(
            hungerDay(1, Hunger.Starving, breakfast = false),
            hungerDay(3, Hunger.Starving, breakfast = false),
            hungerDay(8, Hunger.Hungry, breakfast = false),
            hungerDay(0, Hunger.Neutral, breakfast = true),
            hungerDay(2, Hunger.Full, breakfast = true),
        )

        assertEquals(listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), facts(days).hungrierWithoutBreakfast)
    }

    private fun checkInDay(offset: Int, hunger: Int?, breakfast: Boolean): Day {
        val date = start.plus(offset, DateTimeUnit.DAY)
        val items = listOfNotNull(item(date, MealSlot.Breakfast, 8).takeIf { breakfast }, item(date, MealSlot.Lunch, 13))
        return day(date, *items.toTypedArray()).copy(checkIn = CheckIn(hunger = HungerLevel.of(hunger), energy = Energy.Ok))
    }

    @Test
    fun checkInHungerByWeekday_averagesOneAnswerADay_fromZeroToOne() {
        val days = listOf(
            checkInDay(0, 1, breakfast = true),
            checkInDay(7, 10, breakfast = true),
            checkInDay(1, 10, breakfast = true),
            // Answered, but not the hunger row: nothing for Wednesday.
            checkInDay(2, hunger = null, breakfast = true),
        )

        val facts = facts(days)

        assertEquals(0.5, facts.checkInHungerByWeekday.getValue(DayOfWeek.MONDAY), "ravenous and stuffed average to the middle")
        assertEquals(0.0, facts.checkInHungerByWeekday.getValue(DayOfWeek.TUESDAY))
        assertNull(facts.checkInHungerByWeekday[DayOfWeek.WEDNESDAY])
        assertEquals(emptyMap(), facts.hungerByWeekday, "the meal tags are a separate fact")
    }

    @Test
    fun checkInHungrierWithoutBreakfast_namesThoseWeekdays_onlyWhenTheCheckInsSaySo() {
        val hungrier = listOf(
            checkInDay(1, 2, breakfast = false),
            checkInDay(3, 3, breakfast = false),
            checkInDay(0, 5, breakfast = true),
            checkInDay(2, 6, breakfast = true),
        )
        val even = listOf(
            checkInDay(1, 4, breakfast = false),
            checkInDay(3, 4, breakfast = false),
            checkInDay(0, 4, breakfast = true),
            checkInDay(2, 4, breakfast = true),
        )

        assertEquals(listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), facts(hungrier).checkInHungrierWithoutBreakfast)
        assertNull(facts(even).checkInHungrierWithoutBreakfast)
        assertNull(facts(hungrier).hungrierWithoutBreakfast, "no meal tags, so no tag fact")
    }

    @Test
    fun aCheckInOnAnUntrackedDayIsLeftOut() {
        val untracked = checkInDay(0, 1, breakfast = true).copy(untracked = true)

        assertEquals(emptyMap(), facts(listOf(untracked)).checkInHungerByWeekday)
    }

    @Test
    fun noSkippedBreakfastFactWhenTheTagsDontSaySo() {
        val even = listOf(
            hungerDay(1, Hunger.Neutral, breakfast = false),
            hungerDay(3, Hunger.Neutral, breakfast = false),
            hungerDay(0, Hunger.Neutral, breakfast = true),
            hungerDay(2, Hunger.Neutral, breakfast = true),
        )
        val tooFew = listOf(hungerDay(1, Hunger.Starving, breakfast = false), hungerDay(0, Hunger.Full, breakfast = true))

        assertNull(facts(even).hungrierWithoutBreakfast)
        assertNull(facts(tooFew).hungrierWithoutBreakfast)
    }

    @Test
    fun aTagOnAMealNotEatenIsNotCounted() {
        val facts = facts(listOf(Day(date = start, feelings = mapOf(MealSlot.Lunch to MealFeeling(hunger = Hunger.Hungry)))))

        assertEquals(0, facts.taggedMeals)
        assertEquals(emptyMap(), facts.hunger)
    }

    @Test
    fun energyPerMealAveragesTheMealsWithKnownEnergy() {
        val days = listOf(
            day(start, item(start, MealSlot.Lunch, 12, kcal = 500.0), item(start, MealSlot.Lunch, 12, 5, kcal = 100.0, name = "Apple")),
            start.plus(1, DateTimeUnit.DAY).let { day(it, item(it, MealSlot.Lunch, 12, kcal = 400.0)) },
            start.plus(2, DateTimeUnit.DAY).let { day(it, item(it, MealSlot.Lunch, 12, kcal = null)) },
        )

        assertEquals(500.0, facts(days).energyPerMeal.getValue(MealSlot.Lunch), 1e-9)
    }

    @Test
    fun newFoodsAreTheOnesNotSeenInTheLookbackInTheOrderFirstLogged() {
        val before = start.plus(-10, DateTimeUnit.DAY)
        val days = listOf(
            day(before, item(before, MealSlot.Lunch, 12, name = "Rice")),
            day(start, item(start, MealSlot.Lunch, 12, name = "Poutine"), item(start, MealSlot.Breakfast, 8, name = "rice ")),
            start.plus(1, DateTimeUnit.DAY).let { day(it, item(it, MealSlot.Dinner, 19, name = "Kabsa"), item(it, MealSlot.Snack, 15, name = "poutine")) },
            start.plus(2, DateTimeUnit.DAY).let { day(it, item(it, MealSlot.Snack, 15, name = "1,200", method = LogMethod.QuickNumber)) },
        )

        assertEquals(listOf("Poutine", "Kabsa"), NewFoods.between(days, start, end))
    }
}
