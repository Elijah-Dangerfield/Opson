package com.opson.features.settings.impl.widgets

import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.widgets.WidgetCopy
import com.opson.libraries.widgets.WidgetFixtures
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotBuilder
import com.opson.libraries.widgets.WidgetSnapshotSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Widgets page shows the snapshot the widget gets, and goes back to More. */
class WidgetsViewModelTest : CoroutineTest() {

    private val snapshots = MutableSharedFlow<WidgetSnapshot>(replay = 1)
    private val source = object : WidgetSnapshotSource {
        override fun observe(): Flow<WidgetSnapshot> = snapshots
    }

    private val settings = FakeSettingsRepository()

    private fun snapshot(preset: PresetId): WidgetSnapshot = WidgetSnapshotBuilder.build(
        WidgetFixtures.inputs(NumbersSettings(preset = preset)),
        WidgetFixtures.policy(preset),
        COPY,
    )

    @Test
    fun beforeTheFirstSnapshot_thePageHasNoWidget() = runUnitTest {
        val vm = WidgetsViewModel(source, settings)
        assertNull(vm.state.snapshot)
    }

    @Test
    fun aSnapshotArrives_andThePageShowsItForItsOwnDay() = runUnitTest {
        val vm = WidgetsViewModel(source, settings)

        snapshots.emit(snapshot(PresetId.Full))

        assertEquals(snapshot(PresetId.Full), vm.state.snapshot)
        assertEquals(WidgetFixtures.today, vm.state.today)
        assertEquals(PresetId.Full, vm.state.preset)
    }

    @Test
    fun aModeChange_reachesThePage() = runUnitTest {
        val vm = WidgetsViewModel(source, settings)
        snapshots.emit(snapshot(PresetId.Full))

        snapshots.emit(snapshot(PresetId.Blind))
        settings.state.value = settings.state.value.copy(numbers = NumbersSettings(preset = PresetId.Blind))

        assertNull(vm.state.snapshot?.days?.first()?.energy)
        assertEquals(PresetId.Blind, vm.state.preset)
    }

    @Test
    fun back_closes() = runUnitTest {
        val vm = WidgetsViewModel(source, settings)
        val events = eventsOf(vm)

        vm.takeAction(WidgetsAction.BackTapped)

        assertEquals(listOf<WidgetsEvent>(WidgetsEvent.Close), events)
    }

    private companion object {
        val COPY = WidgetCopy(
            today = "widgets_today",
            log = "widgets_log",
            noteLog = "widgets_note_log",
            noteUntracked = "widgets_note_untracked",
            meals = MealSlot.meals.associateWith { WidgetCopy.MealCopy(it.wire, it.wire, it.wire) },
        )
    }
}
