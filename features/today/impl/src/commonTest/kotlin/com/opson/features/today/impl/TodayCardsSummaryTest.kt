package com.opson.features.today.impl

import com.opson.features.today.impl.cards.TodayCardsAction
import com.opson.features.today.impl.cards.TodayCardsEvent
import com.opson.features.today.impl.cards.TodayCardsStateBuilder
import com.opson.features.today.impl.cards.TodayCardsViewModel
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The summary card section of Edit Today's cards (design sync 2, 2.1e): the suggestion, ticking
 * and its limit, reordering, Reset, the macros page, and the lock on what the levels hide.
 */
class TodayCardsSummaryTest : CoroutineTest() {
    private val weightChanger = Settings(onboarding = OnboardingAnswers(why = listOf("changing_weight")))

    private fun vm(settings: FakeSettingsRepository, source: FakePolicySource = FakePolicySource(), on: Boolean = true) =
        TodayCardsViewModel(settings, source, FakeNumbersChanger(), FastingRelease { false }, summaryFlag(on))

    private fun Settings.stats(): List<String>? = todaySummaryStats

    @Test
    fun theFlagOff_hasNoSummarySection() {
        assertNull(TodayCardsStateBuilder.build(Settings(), policy(PresetId.Full)).summary)
    }

    @Test
    fun itOpensOnTheGoalsSuggestion_tickedFirst() {
        val section = assertNotNull(TodayCardsStateBuilder.build(weightChanger, policy(PresetId.Full), summaryOn = true).summary)

        assertNotNull(section.goal)
        assertEquals(
            listOf(SummaryStat.Expended, SummaryStat.DeficitSoFar, SummaryStat.Meals),
            section.stats.filter { it.ticked }.map { it.stat },
        )
        assertEquals(SummaryStat.Expended, section.stats.first().stat)
        assertEquals(3, section.ticked)
    }

    @Test
    fun aStatTheLevelsHideIsLocked_andDeficitIsNotListedWhereDeficitsAreNot() {
        val nutrient = assertNotNull(TodayCardsStateBuilder.build(weightChanger, policy(PresetId.MacrosOnly), summaryOn = true).summary)

        assertTrue(nutrient.stats.first { it.stat == SummaryStat.Expended }.locked)
        assertFalse(nutrient.stats.first { it.stat == SummaryStat.Expended }.ticked)
        assertFalse(nutrient.stats.any { it.stat == SummaryStat.DeficitSoFar })
        assertFalse(nutrient.stats.first { it.stat == SummaryStat.Meals }.locked, "a count is never locked")
    }

    @Test
    fun tickingAddsUpToThree_andUntickingRemoves() = runUnitTest {
        val settings = FakeSettingsRepository(Settings())
        val vm = vm(settings)

        vm.takeAction(TodayCardsAction.StatToggled(SummaryStat.Fiber))
        assertNull(settings.state.value.stats(), "three already: a fourth changes nothing")

        vm.takeAction(TodayCardsAction.StatToggled(SummaryStat.ToRange))
        vm.takeAction(TodayCardsAction.StatToggled(SummaryStat.Fiber))
        assertEquals(listOf("expended", "meals", "fiber"), settings.state.value.stats())
        assertEquals(3, vm.state.summary?.ticked)
    }

    @Test
    fun aLockedStatNeverTicks_andOpensModes() = runUnitTest {
        val settings = FakeSettingsRepository(Settings())
        val source = FakePolicySource(policy(PresetId.Full, overrides = mapOf(Metric.Fiber to Visibility.Hide)))
        val vm = vm(settings, source)
        val events = mutableListOf<TodayCardsEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(TodayCardsAction.StatToggled(SummaryStat.Meals))
        vm.takeAction(TodayCardsAction.StatToggled(SummaryStat.Fiber))
        assertEquals(listOf("expended", "to_range"), settings.state.value.stats())

        vm.takeAction(TodayCardsAction.LockedStatTapped)
        assertEquals(listOf<TodayCardsEvent>(TodayCardsEvent.OpenModes), events)
    }

    @Test
    fun movingARowReordersWhatIsTicked() = runUnitTest {
        val settings = FakeSettingsRepository(Settings())
        val vm = vm(settings)

        vm.takeAction(TodayCardsAction.StatMoved(from = 2, to = 0))

        assertEquals(listOf("meals", "expended", "to_range"), settings.state.value.stats())
        assertEquals(SummaryStat.Meals, vm.state.summary?.stats?.first()?.stat)
    }

    @Test
    fun resetGoesBackToTheSuggestion_andPageTwoSwitches() = runUnitTest {
        val settings = FakeSettingsRepository(weightChanger.copy(todaySummaryStats = listOf("fiber")))
        val vm = vm(settings)

        vm.takeAction(TodayCardsAction.StatsReset)
        assertNull(settings.state.value.stats())

        vm.takeAction(TodayCardsAction.MacrosPageToggled)
        assertFalse(settings.state.value.todayMacrosPage)
        assertEquals(false, vm.state.summary?.macrosPage)
    }
}
