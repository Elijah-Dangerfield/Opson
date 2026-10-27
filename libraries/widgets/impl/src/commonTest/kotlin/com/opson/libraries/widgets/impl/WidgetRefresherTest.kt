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
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What reaches the platform as the snapshot and the settings move. Not gated on `release.widgets`. */
class WidgetRefresherTest : CoroutineTest() {

    private val source = FakeSnapshotSource()
    private val publisher = FakePublisher()
    private val settings = FakeSettingsRepository(Settings())
    private lateinit var appScope: AppCoroutineScope

    private fun refresher(): WidgetRefresher {
        appScope = AppCoroutineScope(dispatchers)
        return WidgetRefresher(
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
    fun eachSnapshotIsPublished() = test {
        refresher()
        val full = snapshot(PresetId.Full)
        val blind = snapshot(PresetId.NoNumbers)

        source.snapshots.emit(full)
        source.snapshots.emit(blind)

        assertEquals(listOf(full, blind), publisher.published)
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
    fun settingsGoingUnknownBeforeAnyPublish_hasNothingToClear() = test {
        refresher()
        settings.known.value = false

        assertEquals(0, publisher.clears)
        assertTrue(publisher.published.isEmpty())
    }
}
