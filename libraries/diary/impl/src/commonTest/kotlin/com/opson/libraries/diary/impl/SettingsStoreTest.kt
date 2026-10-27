package com.opson.libraries.diary.impl

import com.opson.libraries.diary.PaletteChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsJson
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.Tone
import com.opson.libraries.diary.impl.wire.SettingsRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [SettingsStore]: when `changedAt` moves, what goes into the outbox, and
 * whose settings are on screen across a user change (the `isKnown` rule that
 * keeps a new phone from pushing default settings over the user's own).
 *
 * The pull path that feeds [SettingsStore.applyPulled] and
 * [SettingsStore.markPulled] is covered from the syncer's side in
 * [DiarySyncerTest].
 */
class SettingsStoreTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val clock = FakeClock(Instant.parse("2026-10-25T08:00:00Z"))
    private val user = FakeActiveUser(ActiveUser.SignedOut)
    private val appScope by lazy { AppCoroutineScope(dispatchers) }
    private val store by lazy { SettingsStore(FakeCacheFactory(), db.outboxDao, db.transactor, clock, user, appScope) }

    @AfterTest
    fun tearDown() = appScope.cancel()

    @Test
    fun changedAtMovesOnlyWhenTheNumbersChange() = runUnitTest {
        store.update(stampNumbersChange = true) { it.copy(palette = PaletteChoice.Clay) }
        assertNull(store.settings.value.numbers.changedAt, "a palette is not a numbers change")

        val pickedAt = Instant.parse("2026-10-25T09:00:00Z")
        clock.now = pickedAt
        store.update(stampNumbersChange = true) {
            it.withNumbers(it.numbers.pickPreset(PresetId.Blind, at = Instant.parse("2001-01-01T00:00:00Z")))
        }
        assertEquals(pickedAt, store.settings.value.numbers.changedAt, "stamped with the store's clock")

        clock.now = Instant.parse("2026-10-25T10:00:00Z")
        store.update(stampNumbersChange = true) { it.copy(tone = Tone.Warm) }
        store.update(stampNumbersChange = true) { it.withNumbers(it.numbers.copy(changedAt = clock.now)) }
        assertEquals(pickedAt, store.settings.value.numbers.changedAt, "nothing but a numbers change moves it")
    }

    @Test
    fun onboardingSetsThePresetWithoutStampingIt() = runUnitTest {
        store.update(stampNumbersChange = false) { it.withNumbers(NumbersSettings(preset = PresetId.Rhythm)) }

        assertEquals(PresetId.Rhythm, store.settings.value.numbers.preset)
        assertNull(store.settings.value.numbers.changedAt)
    }

    @Test
    fun aChangeIsQueuedWithThePresetAtTheTopLevel() = runUnitTest {
        store.update(stampNumbersChange = true) { it.withNumbers(it.numbers.pickPreset(PresetId.Blind, clock.now)) }

        val row = db.outbox.rows.single()
        assertEquals(SyncEntity.Settings.wire, row.entity)
        val wire = WireJson.decodeFromString(SettingsRowDto.serializer(), row.payload)
        assertEquals(JsonPrimitive("blind"), wire.settings["preset"], "the one key the server reads")
        assertEquals(JsonPrimitive("blind"), wire.settings.getValue("numbers").jsonObject["preset"])
        assertEquals(clock.now.toEpochMilliseconds(), wire.changedAtEpochMs)
    }

    @Test
    fun anUnchangedUpdateQueuesNothing() = runUnitTest {
        store.update(stampNumbersChange = true) { it }

        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun whileUnknownAChangeStaysOnTheDeviceUntilThePullLands() = runUnitTest {
        user.user.value = ActiveUser.SignedIn("user-a")
        assertFalse(store.isKnown.value)

        store.update(stampNumbersChange = true) { it.copy(tone = Tone.Warm) }
        assertEquals(Tone.Warm, store.settings.value.tone)
        assertTrue(db.outbox.rows.isEmpty(), "defaults must not go up over the user's real settings")

        val wantsSync = store.markPulled("user-a", pulledSettings = false)

        assertTrue(wantsSync)
        assertEquals(1, db.outbox.rows.size, "a new account has nothing on the server, so the local copy goes up")
        assertTrue(store.isKnown.value)
    }

    @Test
    fun whileUnknownTheUsersOwnSettingsWinOverLocalChanges() = runUnitTest {
        user.user.value = ActiveUser.SignedIn("user-a")
        store.update(stampNumbersChange = true) { it.copy(tone = Tone.Warm) }

        store.applyPulled(row(Settings(numbers = NumbersSettings(preset = PresetId.Blind))))
        val wantsSync = store.markPulled("user-a", pulledSettings = true)

        assertEquals(PresetId.Blind, store.settings.value.numbers.preset)
        assertEquals(Tone.Plain, store.settings.value.tone)
        assertFalse(wantsSync)
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun aPulledCopyNeverOverwritesAChangeStillWaiting() = runUnitTest {
        store.update(stampNumbersChange = true) { it.copy(palette = PaletteChoice.Clay) }

        store.applyPulled(row(Settings(palette = PaletteChoice.Plum)))

        assertEquals(PaletteChoice.Clay, store.settings.value.palette)
    }

    @Test
    fun aUserChangeResetsToTheDefaultsAndUnknown() = runUnitTest {
        user.user.value = ActiveUser.SignedIn("user-a")
        store.markPulled("user-a", pulledSettings = false)
        store.update(stampNumbersChange = true) { it.withNumbers(it.numbers.pickPreset(PresetId.Blind, clock.now)) }
        assertTrue(store.isKnown.value)

        store.clear("user-a")
        user.user.value = ActiveUser.SignedIn("user-b")

        assertEquals(Settings(), store.settings.value)
        assertFalse(store.isKnown.value, "user-b's mode has not arrived yet")
    }

    @Test
    fun beforeAuthAnswers_onlySettingsAPullLandedInAreKnown() = runUnitTest {
        // A phone signed in to an account whose first pull never landed (offline), then restarted:
        // the stored copy is the defaults, Full numbers, and must not show until auth answers.
        user.user.value = ActiveUser.Unresolved
        assertFalse(store.isKnown.value, "defaults no pull reached are nobody's settings")

        user.user.value = ActiveUser.SignedIn("user-a")
        store.markPulled("user-a", pulledSettings = true)
        user.user.value = ActiveUser.Unresolved
        assertTrue(store.isKnown.value, "the next cold boot trusts what user-a's pull left")
    }

    @Test
    fun nobodySignedIn_isKnownOnceAuthSaysSo() = runUnitTest {
        user.user.value = ActiveUser.Unresolved
        assertFalse(store.isKnown.value)

        user.user.value = ActiveUser.SignedOut
        assertTrue(store.isKnown.value, "a fresh install has nothing else to wait for")
    }

    @Test
    fun aRefusedChangeRestoresTheCopyBeforeIt() = runUnitTest {
        store.applyPulled(row(Settings(palette = PaletteChoice.Plum), updatedAt = 5))
        store.update(stampNumbersChange = true) { it.copy(palette = PaletteChoice.Clay) }

        store.revert(db.outbox.rows.single().basePayload)

        assertEquals(PaletteChoice.Plum, store.settings.value.palette)
    }

    private fun row(settings: Settings, updatedAt: Long = 1) = SettingsRowDto(
        settings = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject,
        clientUpdatedAtEpochMs = updatedAt,
    )
}
