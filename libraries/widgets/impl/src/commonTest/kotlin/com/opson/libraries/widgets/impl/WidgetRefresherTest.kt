package com.opson.libraries.widgets.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.widgets.WidgetCopy
import com.opson.libraries.widgets.WidgetFixtures
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotBuilder
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What reaches the platform as `release.widgets` and the snapshot move. */
class WidgetRefresherTest : CoroutineTest() {

    private val flag = MutableStateFlow(true)
    private val source = FakeSnapshotSource()
    private val publisher = FakePublisher()
    private val settings = FakeSettingsRepository(Settings())
    private lateinit var appScope: AppCoroutineScope

    private fun refresher(): WidgetRefresher {
        appScope = AppCoroutineScope(dispatchers)
        return WidgetRefresher(
            config = configFlow(flag),
            settingsRepository = settings,
            source = source,
            publisher = publisher,
            appScope = appScope,
        )
    }

    private fun test(block: suspend TestScope.() -> Unit) = runUnitTest {
        try {
            block()
        } finally {
            appScope.cancel()
        }
    }

    private suspend fun snapshot(preset: PresetId): WidgetSnapshot = WidgetSnapshotBuilder.build(
        WidgetFixtures.inputs(NumbersSettings(preset = preset)),
        WidgetFixtures.policy(preset),
        WidgetCopy.resolve(KeyStrings),
    )

    @Test
    fun withTheFlagOff_nothingIsReadPublishedOrCleared() = test {
        flag.value = false
        refresher()
        source.snapshots.emit(snapshot(PresetId.Full))

        assertEquals(0, source.collections)
        assertEquals(emptyList(), publisher.published)
        assertEquals(0, publisher.clears)
    }

    @Test
    fun withTheFlagOn_eachSnapshotIsPublished() = test {
        refresher()
        val full = snapshot(PresetId.Full)
        val blind = snapshot(PresetId.NoNumbers)

        source.snapshots.emit(full)
        source.snapshots.emit(blind)

        assertEquals(listOf(full, blind), publisher.published)
    }

    @Test
    fun turningTheFlagOn_publishesWhatIsCurrent() = test {
        flag.value = false
        refresher()
        val full = snapshot(PresetId.Full)
        source.snapshots.emit(full)

        flag.value = true

        assertEquals(listOf(full), publisher.published)
    }

    @Test
    fun turningTheFlagOff_clearsWhatWasPublished_andStopsListening() = test {
        refresher()
        source.snapshots.emit(snapshot(PresetId.Full))

        flag.value = false
        source.snapshots.emit(snapshot(PresetId.NoNumbers))

        assertEquals(1, publisher.clears)
        assertEquals(1, publisher.published.size)
    }

    @Test
    fun aUserChange_clearsTheLastAccountsDay_untilTheNextAccountsSettingsArrive() = test {
        refresher()
        val full = snapshot(PresetId.Full)
        source.snapshots.emit(full)

        // Signed out and back in as someone else: their mode has not arrived yet.
        settings.known.value = false
        assertEquals(1, publisher.clears, "the last account's numbers leave the home screen")

        settings.known.value = true
        assertEquals(listOf(full, full), publisher.published, "and the snapshot comes back once settings are known")
    }

    @Test
    fun settingsNotKnownYetAtLaunch_publishNothing_andClearNothing() = test {
        settings.known.value = false
        refresher()
        source.snapshots.emit(snapshot(PresetId.Full))

        assertEquals(0, source.collections)
        assertEquals(0, publisher.clears)
    }

    @Test
    fun aFlagThatNeverPublished_hasNothingToClear() = test {
        refresher()
        flag.value = false

        assertEquals(0, publisher.clears)
        assertTrue(publisher.published.isEmpty())
    }
}
