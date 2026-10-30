package com.opson.features.today.impl

import com.opson.features.settings.FindSupportEnabled
import com.opson.features.today.impl.kindnote.CutBackLooks
import com.opson.features.today.impl.kindnote.KindNoteAction
import com.opson.features.today.impl.kindnote.KindNoteEvent
import com.opson.features.today.impl.kindnote.KindNotePrompt
import com.opson.features.today.impl.kindnote.KindNoteViewModel
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.prompts.PromptPriority
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.kind_note_stopped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The kind note: when the prompt offers it, what it never sends, and its sheet's buttons. */
class KindNoteTest : CoroutineTest() {

    private val today = TodayFixtures.today

    private fun day(daysAgo: Int, kcal: Double): Day {
        val date = today.minus(DatePeriod(days = daysAgo))
        val third = kcal / 3
        val items = listOf(MealSlot.Breakfast, MealSlot.Lunch, MealSlot.Dinner).associateWith { slot ->
            listOf(
                LogItem(
                    id = "$date-$slot",
                    day = date,
                    slot = slot,
                    loggedAt = date.atTime(12, 0).toInstant(TimeZone.UTC),
                    name = "Food",
                    precision = Precision.Estimate,
                    method = LogMethod.QuickNumber,
                    kcal = third,
                ),
            )
        }
        return Day(date = date, itemsBySlot = MealSlot.entries.associateWith { items[it].orEmpty() })
    }

    /** Four very low days in the last two weeks, ordinary ones around them. */
    private val hardStretch = (1..27).map { if (it in setOf(2, 5, 8, 11)) day(it, 700.0) else day(it, 2000.0) }
    private val ordinary = (1..27).map { day(it, 2000.0) }

    private fun flag(on: Boolean) = FindSupportEnabled(
        object : AppConfigMap() {
            override val map: Map<String, *> = mapOf("release" to mapOf("findSupport" to on))
        },
    )

    private fun prompt(
        days: List<Day> = hardStretch,
        settings: Settings = Settings(bodySex = BodySex.Female),
        findSupport: Boolean = true,
    ) = KindNotePrompt(
        findSupport = flag(findSupport),
        settings = FakeSettingsRepository(settings),
        diary = FakeDiaryRepository(days),
        weights = FakeWeightRepository(),
        looks = FakeCutBackLooks(),
        calendar = FakeCalendar(),
    )

    @Test
    fun aHardStretch_isEligible() = runUnitTest {
        assertTrue(prompt().isEligible())
    }

    @Test
    fun anOrdinaryLog_isNot() = runUnitTest {
        assertFalse(prompt(days = ordinary).isEligible())
    }

    @Test
    fun theFlagAndThePersonsOwnSwitch_bothTurnItOff() = runUnitTest {
        assertFalse(prompt(findSupport = false).isEligible())
        assertFalse(prompt(settings = Settings(hardStretchNotes = false)).isEligible())
    }

    @Test
    fun itSitsAboveEverything_isSentNowhere_andIsOfferedAfterNumbersHaveBeenHard() {
        val prompt = prompt()
        assertEquals(PromptPriority.CARE, prompt.priority)
        assertTrue(prompt.sensitive)
        assertTrue(prompt.offerWhenNumbersHard)
    }

    @Test
    fun stopTurnsTheNotesOff_andSaysWhereToTurnThemBackOn() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = KindNoteViewModel(settings)
        val events = mutableListOf<KindNoteEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(KindNoteAction.StopTapped)

        assertFalse(settings.state.value.hardStretchNotes)
        assertEquals(listOf(KindNoteEvent.ShowToast(Toast(Res.string.kind_note_stopped)), KindNoteEvent.Dismiss), events)
    }

    @Test
    fun fewerNumbers_isOnlyOfferedWhereThereAreNumbersToHide() = runUnitTest {
        val noNumbers = KindNoteViewModel(FakeSettingsRepository(Settings(numbers = NumbersSettings(preset = PresetId.NoNumbers))))
        val events = mutableListOf<KindNoteEvent>()
        backgroundScope.launch { noNumbers.eventFlow.toList(events) }

        assertFalse(noNumbers.state.offersFewerNumbers)
        noNumbers.takeAction(KindNoteAction.FewerNumbersTapped)
        assertTrue(events.isEmpty())

        val full = KindNoteViewModel(FakeSettingsRepository())
        backgroundScope.launch { full.eventFlow.toList(events) }
        full.takeAction(KindNoteAction.FewerNumbersTapped)
        full.takeAction(KindNoteAction.FindSupportTapped)
        assertEquals(listOf(KindNoteEvent.OpenModes, KindNoteEvent.OpenFindSupport), events)
    }
}

class FakeCutBackLooks(private val looks: List<Instant> = emptyList()) : CutBackLooks {
    var recorded = 0
        private set

    override suspend fun record() {
        recorded++
    }

    override suspend fun recent(): List<Instant> = looks
}
