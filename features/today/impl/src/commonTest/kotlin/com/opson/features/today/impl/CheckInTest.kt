package com.opson.features.today.impl

import com.opson.features.today.impl.checkin.CheckInAction
import com.opson.features.today.impl.checkin.CheckInEvent
import com.opson.features.today.impl.checkin.CheckInStateBuilder
import com.opson.features.today.impl.checkin.CheckInViewModel
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.MoodLevel
import com.opson.libraries.diary.Sleep
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The check-in sheet: sleep asked about last night only in the morning, the hunger scale's steps,
 * and a save that is its own write, on a closed day too.
 */
class CheckInTest : CoroutineTest() {

    private val today = TodayFixtures.today
    private val morning = LocalTime(7, 30)
    private val evening = LocalTime(21, 0)

    @Test
    fun theMorningAsksAboutLastNightFirst_andLaterSleepWaitsBehindItsLink() {
        val am = CheckInStateBuilder.build(today, today, morning, CheckIn(), sleepAsked = false)
        assertTrue(am.sleepFirst)
        assertTrue(am.sleepShown)

        val pm = CheckInStateBuilder.build(today, today, evening, CheckIn(), sleepAsked = false)
        assertFalse(pm.sleepFirst)
        assertFalse(pm.sleepShown, "nobody rates last night at 9 pm unless they ask to")

        assertTrue(CheckInStateBuilder.build(today, today, evening, CheckIn(), sleepAsked = true).sleepShown, "Add last night's sleep")
        assertTrue(CheckInStateBuilder.build(today, today, evening, CheckIn(sleep = Sleep.Good), sleepAsked = false).sleepShown, "the morning's answer stays editable")
        assertFalse(CheckInStateBuilder.build(today, today, CheckInStateBuilder.MORNING_ENDS, CheckIn(), sleepAsked = false).sleepFirst, "noon is afternoon")
    }

    @Test
    fun anotherDaysCheckIn_showsEveryRow() {
        val yesterday = CheckInStateBuilder.build(today.minus(DatePeriod(days = 1)), today, morning, CheckIn(), sleepAsked = false)
        assertFalse(yesterday.isToday)
        assertFalse(yesterday.sleepFirst)
        assertTrue(yesterday.sleepShown)
    }

    @Test
    fun theHungerScaleRunsOneToTen_withItsWords() {
        assertEquals(10, HungerLevel.all.size)
        assertNull(HungerLevel.of(0))
        assertNull(HungerLevel.of(11))
        assertEquals(1.0, HungerLevel.of(1)?.hungriness)
        assertEquals(0.0, HungerLevel.of(10)?.hungriness)
        assertEquals("Neutral", HungerLevel.of(HungerLevel.NEUTRAL)?.anchor?.name)
    }

    private val diary = FakeDiaryRepository(listOf(TodayFixtures.day(dinner = true).copy(checkIn = CheckIn(sleep = Sleep.Poor))))

    private fun vm(day: LocalDate? = null, at: LocalTime = evening) = CheckInViewModel(
        requestedDay = day,
        diary = diary,
        checkIns = diary,
        calendar = FakeCalendar(),
        clock = FakeClock(LocalDateTime(today, at).toInstant(TimeZone.UTC)),
    )

    @Test
    fun opensOnTheDaysCheckIn_andSavesTheWholeOfIt() = runUnitTest {
        val events = mutableListOf<CheckInEvent>()
        val vm = vm()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        assertEquals(CheckIn(sleep = Sleep.Poor), vm.state.checkIn)
        assertTrue(vm.state.sleepShown, "answered this morning, so still there")

        vm.takeAction(CheckInAction.HungerPicked(3))
        vm.takeAction(CheckInAction.HungerPicked(4))
        vm.takeAction(CheckInAction.EnergyTapped(Energy.Great))
        vm.takeAction(CheckInAction.MoodTapped(MoodLevel.Ok))
        vm.takeAction(CheckInAction.MoodTapped(MoodLevel.Ok))
        vm.takeAction(CheckInAction.MoodTagTapped(CheckInMood.Social))
        vm.takeAction(CheckInAction.SaveTapped)

        val expected = CheckIn(hunger = HungerLevel.of(4), energy = Energy.Great, sleep = Sleep.Poor, moods = setOf(CheckInMood.Social))
        assertEquals(listOf<Pair<LocalDate, CheckIn?>>(today to expected), diary.checkIns, "mood tapped twice is left blank")
        assertTrue(diary.closed.isEmpty(), "a check-in never closes the day")
        assertIs<CheckInEvent.ShowToast>(events.first())
        assertEquals<CheckInEvent>(CheckInEvent.Dismiss, events.last())
    }

    @Test
    fun clearingEverything_clearsTheStoredCheckIn_quietly() = runUnitTest {
        val events = mutableListOf<CheckInEvent>()
        val vm = vm()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(CheckInAction.SleepTapped(Sleep.Poor))
        vm.takeAction(CheckInAction.HungerPicked(6))
        vm.takeAction(CheckInAction.HungerCleared)
        vm.takeAction(CheckInAction.SaveTapped)

        assertEquals(listOf<Pair<LocalDate, CheckIn?>>(today to null), diary.checkIns)
        assertEquals(listOf<CheckInEvent>(CheckInEvent.Dismiss), events, "nothing saved, nothing to say")
    }

    @Test
    fun aClosedDayStillTakesACheckIn() = runUnitTest {
        diary.days.value = diary.days.value.mapValues { (_, d) -> d.copy(closedAt = TodayFixtures.evening) }
        val vm = vm()

        vm.takeAction(CheckInAction.EnergyTapped(Energy.Low))
        vm.takeAction(CheckInAction.SaveTapped)
        vm.takeAction(CheckInAction.SaveTapped)

        assertEquals(1, diary.checkIns.size, "a second tap while leaving writes nothing more")
        assertEquals(Energy.Low, diary.days.value.getValue(today).checkIn?.energy)
    }

    @Test
    fun closeLeavesTheStoredCheckInAsItWas() = runUnitTest {
        val vm = vm(at = morning)
        assertTrue(vm.state.sleepFirst)
        vm.takeAction(CheckInAction.SleepTapped(Sleep.Great))
        vm.takeAction(CheckInAction.CloseTapped)
        assertTrue(diary.checkIns.isEmpty())
    }
}
