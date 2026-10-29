package com.opson.features.trends.impl

import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The check-ins' 1 to 10 hunger on Trends' Hunger card: a dot a day on Week, a week's average and
 * range on the longer ranges, hungrier higher, with the scale's own words down the side.
 */
class CheckInHungerTest {

    private val monday = LocalDate(2026, 10, 19)
    private val wednesday = LocalDate(2026, 10, 21)
    private val thursday = LocalDate(2026, 10, 22)

    private fun build(days: List<Day>, settings: Settings = Settings(), checkInOn: Boolean = true, preset: PresetId = PresetId.Full) =
        TrendsStateBuilder.build(
            TrendsFixtures.trendsInputs(settings, days = days, weighIns = emptyList(), checkInOn = checkInOn),
            TrendsFixtures.policy(preset),
        )

    /** Level [level]'s height: 1 for ravenous down to 0 for stuffed. */
    private fun h(level: Int): Float = HungerLevel.of(level)!!.hungriness.toFloat()

    private fun checkedIn(date: LocalDate, level: Int) = Day(date, checkIn = CheckIn(hunger = HungerLevel.of(level)))

    @Test
    fun week_aDotADay_hungrierHigher_withGapsWhereNoOneAnswered() {
        val hunger = assertNotNull(build(listOf(checkedIn(monday, 1), checkedIn(wednesday, 10), checkedIn(thursday, 5))).checkInHunger)
        assertEquals(3, hunger.answers)
        assertEquals(false, hunger.perWeek)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), hunger.dots.map { it.label })
        assertEquals(listOf(h(1), null, h(10), h(5), null, null, null), hunger.dots.map { it.value })
        assertEquals(true, hunger.dots.last().isCurrent)
        assertEquals(3, hunger.readouts.count { it != null })
    }

    @Test
    fun theSide_carriesTheScalesWords_notNumbers() {
        val hunger = assertNotNull(build(listOf(checkedIn(monday, 4))).checkInHunger)
        assertEquals(listOf(1, 3, 5, 8, 10), hunger.axis.map { it.second.value })
        assertEquals(1f, hunger.axis.first().first, "ravenous at the top")
        assertEquals(0f, hunger.axis.last().first, "stuffed at the bottom")
    }

    @Test
    fun longerRanges_aDotAWeek_itsAverageAndRange() {
        val days = listOf(checkedIn(monday, 2), checkedIn(wednesday, 6), checkedIn(thursday, 4))
        val hunger = assertNotNull(build(days, Settings(trendsRange = TrendsRange.FourWeeks)).checkInHunger)
        assertEquals(true, hunger.perWeek)
        assertEquals(4, hunger.dots.size)
        val week = hunger.dots.last()
        assertEquals(h(4), week.value, "the average, 4, as a height")
        assertEquals(h(6), week.low, "the fullest answer, 6")
        assertEquals(h(2), week.high, "the hungriest answer, 2")
        assertNull(hunger.dots.first().value, "a week with no answer is a gap")
    }

    @Test
    fun untrackedDays_areGaps() {
        val hunger = assertNotNull(build(listOf(checkedIn(monday, 2), checkedIn(wednesday, 6).copy(untracked = true))).checkInHunger)
        assertEquals(1, hunger.answers)
        assertNull(hunger.dots[2].value)
    }

    @Test
    fun notDrawn_withoutTheFlag_withoutAnswers_orWithHungerAndNotesOff() {
        val days = listOf(checkedIn(monday, 3))
        assertNull(build(days, checkInOn = false).checkInHunger)
        assertNull(build(emptyList()).checkInHunger)
        assertNull(build(days, Settings(trendsCards = TrendsCards(journal = false))).checkInHunger)
    }

    @Test
    fun everyMode_getsIt_sinceHungerHasNoAmount() {
        val days = listOf(checkedIn(monday, 3))
        PresetId.entries.forEach { assertNotNull(build(days, preset = it).checkInHunger, "$it") }
    }
}
