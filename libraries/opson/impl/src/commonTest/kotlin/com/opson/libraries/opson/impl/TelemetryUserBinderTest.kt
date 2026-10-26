package com.opson.libraries.opson.impl

import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.Telemetry
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryUserBinderTest : CoroutineTest() {

    @Test
    fun coldBoot_thenAuthenticatedProfile_forwardsToTelemetry() = runUnitTest {
        val profile = FakeProfile()
        val telemetry = RecordingTelemetry()
        val binder = build(profile, telemetry)

        binder.onColdBoot(AppEvent.ColdBoot)
        runCurrent()
        profile.emit(sample(id = "u1", name = "Alice"))
        runCurrent()

        assertEquals(listOf<String?>("u1"), telemetry.setUserCalls)
    }

    @Test
    fun emittedBeforeColdBoot_isPickedUp() = runUnitTest {
        val profile = FakeProfile()
        val telemetry = RecordingTelemetry()
        val binder = build(profile, telemetry)

        // Replay = 1 so the latest emission is available to a late
        // subscriber, mirroring SharedFlow(replay = 1) in the real impl.
        profile.emit(sample(id = "u1", name = "Alice"))
        binder.onColdBoot(AppEvent.ColdBoot)
        runCurrent()

        assertEquals("u1", telemetry.setUserCalls.single())
    }

    @Test
    fun multipleColdBoots_subscribeOnce() = runUnitTest {
        val profile = FakeProfile()
        val telemetry = RecordingTelemetry()
        val binder = build(profile, telemetry)

        binder.onColdBoot(AppEvent.ColdBoot)
        binder.onColdBoot(AppEvent.ColdBoot)
        runCurrent()
        profile.emit(sample(id = "u1", name = "Alice"))
        runCurrent()

        assertEquals(1, telemetry.setUserCalls.size, "second cold-boot should not start a second collector")
    }

    @Test
    fun unchangedProfile_doesNotReEmit() = runUnitTest {
        val profile = FakeProfile()
        val telemetry = RecordingTelemetry()
        val binder = build(profile, telemetry)

        profile.emit(sample(id = "u1", name = "Alice"))
        binder.onColdBoot(AppEvent.ColdBoot)
        runCurrent()
        profile.emit(sample(id = "u1", name = "Alice"))
        runCurrent()

        assertEquals(1, telemetry.setUserCalls.size)
    }

    @Test
    fun nameAndEmailChanges_doNotReachTelemetry() = runUnitTest {
        // Only the id is sent; a rename or a newly claimed email is the same
        // user and must not push personal data to crash reporting.
        val profile = FakeProfile()
        val telemetry = RecordingTelemetry()
        val binder = build(profile, telemetry)

        profile.emit(sample(id = "u1", name = "Alice"))
        binder.onColdBoot(AppEvent.ColdBoot)
        runCurrent()
        profile.emit(sample(id = "u1", name = "Renamed"))
        profile.emit(sample(id = "u1", name = "Renamed", email = "alice@example.com"))
        runCurrent()

        assertEquals(listOf<String?>("u1"), telemetry.setUserCalls)
    }

    @Test
    fun signedOut_clearsTelemetryUser() = runUnitTest {
        val profile = FakeProfile()
        val telemetry = RecordingTelemetry()
        val binder = build(profile, telemetry)

        profile.emit(sample(id = "u1", name = "Alice"))
        binder.onColdBoot(AppEvent.ColdBoot)
        runCurrent()
        // current == null → a true sign-out / delete clears the bound user.
        binder.onUserChanged(AppEvent.UserChanged(previous = "u1", current = null))

        assertEquals(2, telemetry.setUserCalls.size)
        assertNull(telemetry.setUserCalls.last())
    }

    @Test
    fun accountSwitch_doesNotClearTelemetryUser() = runUnitTest {
        // On a switch (current != null) the profile observer re-binds telemetry
        // to the incoming user; UserChanged itself must not null it out.
        val profile = FakeProfile()
        val telemetry = RecordingTelemetry()
        val binder = build(profile, telemetry)

        profile.emit(sample(id = "u1", name = "Alice"))
        binder.onColdBoot(AppEvent.ColdBoot)
        runCurrent()
        binder.onUserChanged(AppEvent.UserChanged(previous = "u1", current = "u2"))

        assertEquals(1, telemetry.setUserCalls.size, "switch must not push a clear call")
        assertEquals("u1", telemetry.setUserCalls.last())
    }

    @Test
    fun fallbackProfile_doesNotSet() = runUnitTest {
        // Fallback isn't a real user — no telemetry attribution to do.
        val profile = FakeProfile()
        val telemetry = RecordingTelemetry()
        val binder = build(profile, telemetry)

        profile.emit(Profile.Fallback(id = "local-uuid"))
        binder.onColdBoot(AppEvent.ColdBoot)
        runCurrent()

        assertTrue(telemetry.setUserCalls.isEmpty())
    }

    private fun build(profile: FakeProfile, telemetry: RecordingTelemetry) =
        TelemetryUserBinder(
            profileProvider = { profile },
            telemetry = telemetry,
            appScope = AppCoroutineScope(dispatchers),
        )

    private fun sample(id: String, name: String, email: String? = null) = Profile.Authenticated(
        id = id,
        displayName = name,
        isAnonymous = true,
        email = email,
        createdAt = kotlin.time.Instant.fromEpochMilliseconds(0),
    )

    private class RecordingTelemetry : Telemetry {
        val setUserCalls = mutableListOf<String?>()
        val setRouteCalls = mutableListOf<String>()

        override fun initialize() = Unit
        override fun setUser(id: String?) {
            setUserCalls += id
        }

        override fun setCurrentRoute(route: String) {
            setRouteCalls += route
        }

        override fun setSession(sessionId: String) = Unit

        override fun setInstallId(installId: String) = Unit

        override fun setContext(key: String, value: String?) = Unit

        override fun captureUserFeedback(
            message: String,
            isBugReport: Boolean,
            eventId: String?,
            errorCode: Int?,
            email: String?,
            screenshots: List<ByteArray>,
        ) = Unit
    }

    private class FakeProfile : ProfileRepository {
        private val flow = MutableSharedFlow<Profile>(replay = 1, extraBufferCapacity = 8)

        fun emit(profile: Profile) {
            flow.tryEmit(profile)
        }

        override suspend fun current(): Profile = error("not used in binder tests")
        override fun observe(): Flow<Profile> = flow
        override suspend fun update(
            displayName: String?,
        ): UpdateProfileOutcome = error("unused")
    }
}
