package com.opson.features.logger.impl

import com.opson.features.logger.HowItFeltRoute
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [HowItFeltViewModel]: prefill, toggles, what Save writes, and that Skip writes nothing. */
class HowItFeltTest : CoroutineTest() {
    private val today = LoggerFixtures.today
    private val diary = FakeDiaryRepository(
        listOf(Day(date = today, feelings = mapOf(MealSlot.Lunch to MealFeeling(hunger = Hunger.Peckish)))),
    )

    private fun vm(slot: MealSlot = MealSlot.Lunch) = HowItFeltViewModel(HowItFeltRoute(today.toString(), slot), diary)

    private fun TestScope.events(vm: HowItFeltViewModel): List<HowItFeltEvent> {
        val events = mutableListOf<HowItFeltEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun itStartsFromWhatTheMealAlreadyHas() = runUnitTest {
        val state = vm().state

        assertFalse(state.loading)
        assertEquals(MealSlot.Lunch, state.slot)
        assertEquals(Hunger.Peckish, state.hunger, "hunger picked on the amount sheet shows here")
        assertNull(vm(MealSlot.Dinner).state.hunger)
    }

    @Test
    fun choicesSelectAndClearAndMoodsToggle() = runUnitTest {
        val vm = vm()

        vm.takeAction(HowItFeltAction.HungerTapped(Hunger.Peckish))
        assertNull(vm.state.hunger)
        vm.takeAction(HowItFeltAction.FullnessTapped(Fullness.Full))
        assertEquals(Fullness.Full, vm.state.fullness)
        vm.takeAction(HowItFeltAction.MoodTapped(Mood.Rushed))
        vm.takeAction(HowItFeltAction.MoodTapped(Mood.Calm))
        vm.takeAction(HowItFeltAction.MoodTapped(Mood.Rushed))
        assertEquals(setOf(Mood.Calm), vm.state.moods)
    }

    @Test
    fun saveWritesTheFeelingAndCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(HowItFeltAction.FullnessTapped(Fullness.Satisfied))
        vm.takeAction(HowItFeltAction.MoodTapped(Mood.Social))
        vm.takeAction(HowItFeltAction.NoteTyped("  with Sam  "))

        vm.takeAction(HowItFeltAction.SaveTapped)

        val expected = MealFeeling(hunger = Hunger.Peckish, fullness = Fullness.Satisfied, moods = setOf(Mood.Social), note = "with Sam")
        assertEquals(Triple(today, MealSlot.Lunch, expected), diary.feelings.single())
        assertEquals(listOf<HowItFeltEvent>(HowItFeltEvent.Dismiss), events)
    }

    @Test
    fun savingNothingClearsTheFeeling() = runUnitTest {
        val vm = vm()
        vm.takeAction(HowItFeltAction.HungerTapped(Hunger.Peckish))

        vm.takeAction(HowItFeltAction.SaveTapped)

        assertNull(diary.feelings.single().third)
    }

    @Test
    fun skipAndCloseWriteNothing() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(HowItFeltAction.SkipTapped)
        vm.takeAction(HowItFeltAction.CloseTapped)

        assertTrue(diary.feelings.isEmpty())
        assertEquals(listOf<HowItFeltEvent>(HowItFeltEvent.Dismiss, HowItFeltEvent.Dismiss), events)
    }
}
