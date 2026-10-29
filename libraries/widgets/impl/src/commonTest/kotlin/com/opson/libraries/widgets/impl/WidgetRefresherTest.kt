package com.opson.libraries.widgets.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.opson.AppData
import com.opson.libraries.widgets.WidgetCopy
import com.opson.libraries.widgets.WidgetFixtures
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotBuilder
import com.opson.libraries.widgets.WidgetSnapshotSource
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What reaches the platform as the snapshot, the settings and the signed-in user move. Not gated on `release.widgets`. */
class WidgetRefresherTest : CoroutineTest() {

    private val source = FakeSnapshotSource()
    private val publisher = FakePublisher()
    private val settings = FakeSettingsRepository(Settings())
    private val auth = FakeAuth()
    private val appCache = FakeAppCache(AppData(hasUserOnboarded = true))
    private lateinit var appScope: AppCoroutineScope

    private fun refresher(): WidgetRefresher {
        appScope = AppCoroutineScope(dispatchers)
        return WidgetRefresher(
            settingsRepository = settings,
            source = source,
            publisher = publisher,
            appCache = appCache,
            authRepositoryProvider = { auth },
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
        assertEquals(listOf(full), publisher.published, "the first goes out at once")

        source.snapshots.emit(blind)
        advanceTimeBy(SETTLE_MS + 1)

        assertEquals(listOf(full, blind), publisher.published)
    }

    /** A meal of several foods, or a sync pull, lands as a burst. The widget reloads once for it. */
    @Test
    fun aBurstOfSnapshots_isPublishedOnce_whenItSettles() = test {
        refresher()
        val full = snapshot(PresetId.Full)
        val blind = snapshot(PresetId.NoNumbers)
        val macros = snapshot(PresetId.MacrosOnly)
        source.snapshots.emit(full)

        source.snapshots.emit(blind)
        advanceTimeBy(SETTLE_MS / 2)
        source.snapshots.emit(macros)
        advanceTimeBy(SETTLE_MS / 2)
        assertEquals(listOf(full), publisher.published, "still settling")

        advanceTimeBy(SETTLE_MS)
        assertEquals(listOf(full, macros), publisher.published)
    }

    /** A throw while building used to leave the widget on its last snapshot until the next launch. */
    @Test
    fun aSourceThatThrows_isReadAgain() = test {
        val full = snapshot(PresetId.Full)
        var calls = 0
        val flaky = object : WidgetSnapshotSource {
            override fun observe(): Flow<WidgetSnapshot> = flow {
                calls++
                if (calls == 1) error("the policy flow broke")
                emit(full)
            }
        }
        appScope = AppCoroutineScope(dispatchers)
        WidgetRefresher(settings, flaky, publisher, appCache, { auth }, appScope)
        assertTrue(publisher.published.isEmpty())

        advanceTimeBy(RETRY_MS + 1)

        assertEquals(listOf(full), publisher.published)
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

    /**
     * Signed out, the settings store holds the defaults (All numbers) and calls them known. A
     * No numbers widget must not turn into a calorie card for someone who chose never to see one.
     */
    @Test
    fun aSignOut_clearsTheWidget_andDoesNotDrawTheDefaultSettings() = test {
        refresher()
        val blind = snapshot(PresetId.NoNumbers)
        source.snapshots.emit(blind)

        auth.state.value = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SignedOut)
        source.snapshots.emit(snapshot(PresetId.Full))

        assertEquals(listOf(blind), publisher.published, "nothing is drawn from the defaults")
        assertEquals(1, publisher.clears)
    }

    @Test
    fun anExpiredSession_clearsWhatAnEarlierRunPublished() = test {
        auth.state.value = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SessionExpired)
        refresher()
        source.snapshots.emit(snapshot(PresetId.Full))

        assertEquals(1, publisher.clears, "this run published nothing, but the home screen may still hold a day")
        assertEquals(0, source.collections)
    }

    @Test
    fun noSessionAndNotOnboarded_leavesTheWidgetAsItIs() = test {
        // A fresh install, or a relaunch after a sign-out: the settings are only the defaults.
        appCache.state.value = AppData(hasUserOnboarded = false)
        auth.state.value = AuthState.Unauthenticated()
        refresher()
        source.snapshots.emit(snapshot(PresetId.Full))

        assertEquals(0, source.collections)
        assertEquals(0, publisher.clears)
    }

    @Test
    fun signingInAgain_publishesOnceTheirSettingsAreKnown() = test {
        auth.state.value = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SignedOut)
        refresher()
        val blind = snapshot(PresetId.NoNumbers)
        source.snapshots.emit(blind)
        assertTrue(publisher.published.isEmpty())

        auth.state.value = AuthState.Authenticated(userId = "u2", isAnonymous = false, email = null)

        assertEquals(listOf(blind), publisher.published)
    }

    /**
     * Signed out, then in as someone new: [SettingsRepository.isKnown] still says true for the
     * signed-out stretch when the refresher hears the sign-in, but nothing stored is the new
     * account's yet, so nothing may be drawn from it.
     */
    @Test
    fun aSignInBeforeIsKnownCatchesUp_publishesNothing() = test {
        auth.state.value = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SignedOut)
        settings.knownFor = { it != "u2" }
        refresher()
        source.snapshots.emit(snapshot(PresetId.Full))

        auth.state.value = AuthState.Authenticated(userId = "u2", isAnonymous = false, email = null)

        assertTrue(publisher.published.isEmpty(), "the defaults are not the new account's")
    }

    /** An hour-old token can't refresh offline. The stored settings are still this person's, and so is the day. */
    @Test
    fun aSessionThatCannotRefreshOffline_stillPublishesForSomeoneOnboarded() = test {
        auth.state.value = AuthState.Unauthenticated()
        refresher()
        val blind = snapshot(PresetId.NoNumbers)
        source.snapshots.emit(blind)

        assertEquals(listOf(blind), publisher.published)
    }

    private companion object {
        /** WidgetRefresher's settle and first retry pause. */
        const val SETTLE_MS = 250L
        const val RETRY_MS = 5_000L
    }
}
