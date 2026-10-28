package com.opson.libraries.diary.impl

import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.impl.wire.FastRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * [FastingRepositoryImpl] over the in-memory database: when it writes a fast and when it
 * refuses to, how an end is stamped, that logging food ends a fast through the diary, the
 * outbox rows, and the app events (never a duration). The schedule itself is `FastsTest`.
 */
class FastingRepositoryImplTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val clock = FakeClock(Instant.parse("2026-10-25T21:30:00Z"))
    private val calendar = UtcCalendar()
    private val scheduler = FakeScheduler()
    private val writer = DiaryWriter(db.tables, clock, scheduler)
    private val settings = FakeSettingsRepository(timerOn())
    private var released = true
    private val events = FastEventTree()
    private val fasting = FastingRepositoryImpl(db.tables, writer, settings, FastingRelease { released }, calendar, clock)
    private val diary = DiaryRepositoryImpl(db.tables, writer, settings, calendar, fasting)

    private val oct25 = LocalDate(2026, 10, 25)
    private val close = Instant.parse("2026-10-25T19:00:00Z")

    @BeforeTest
    fun plant() = KLog.plant(events)

    @AfterTest
    fun uproot() = KLog.uproot(events)

    @Test
    fun startDue_writesTheEveningsFastFromTheClose() = runUnitTest {
        fasting.startDue()

        assertEquals(Fast(oct25, close, goalHours = 14), fasting.observeLatest().first())
        val row = db.fasts.rows.single()
        assertEquals(close.toEpochMilliseconds(), row.clientUpdatedAtEpochMs, "stamped at the start, so any real end wins")
        val outbox = db.outbox.rows.single()
        assertEquals(SyncEntity.Fast.wire, outbox.entity)
        assertEquals("2026-10-25", outbox.entityId)
        assertEquals("2026-10-25", outbox.day)
        assertNull(outbox.basePayload)
        assertEquals(14, WireJson.decodeFromString(FastRowDto.serializer(), outbox.payload).goalHours)
        assertEquals(listOf(mapOf<String, Any?>("plan" to "14:10")), events.named("fast.started"))
    }

    @Test
    fun startDue_isIdempotent() = runUnitTest {
        fasting.startDue()
        fasting.startDue()

        assertEquals(1, db.fasts.rows.size)
        assertEquals(1, db.outbox.rows.size)
        assertEquals(1, events.named("fast.started").size)
    }

    /**
     * The opt-in rule: a fast only ever starts for someone who opened Fasting and turned its
     * timer on. With the settings anyone else has (the defaults: timer off), in every mode,
     * Full and Sprint included, and with the flag on, after the window has closed: no row, no
     * outbox change and no event, whether a screen asks or food is logged.
     */
    @Test
    fun nobodyWhoNeverTurnedTheTimerOnGetsAFast_inAnyMode() = runUnitTest {
        PresetId.entries.forEach { preset ->
            settings.state.value = Settings(numbers = NumbersSettings(preset = preset))
            assertEquals(false, settings.state.value.fasting, "the timer is off by default")

            fasting.startDue()
            diary.addItems(oct25, MealSlot.Snack, listOf(draft()), LogMethod.Search).written()
            diary.setAteNoDetails(oct25, MealSlot.Dinner, ate = true)
            fasting.startDue()

            assertTrue(db.fasts.rows.isEmpty(), "no fast in $preset")
            assertTrue(db.outbox.rows.none { it.entity == SyncEntity.Fast.wire }, "nothing queued in $preset")
        }
        assertTrue(events.named("fast.started").isEmpty())
        assertTrue(events.named("fast.ended").isEmpty())
    }

    @Test
    fun startDue_nothingWhileTheTimerIsOff() = runUnitTest {
        settings.state.value = timerOn().copy(fasting = false)
        fasting.startDue()
        assertTrue(db.fasts.rows.isEmpty())
    }

    @Test
    fun startDue_nothingWhenTheModeDoesNotOfferFasting() = runUnitTest {
        settings.state.value = timerOn().copy(numbers = NumbersSettings(preset = PresetId.MacrosOnly))
        fasting.startDue()
        assertTrue(db.fasts.rows.isEmpty())
    }

    @Test
    fun startDue_nothingForSomeoneWhoAgreedNumbersHaveBeenHard() = runUnitTest {
        settings.state.value = timerOn().copy(onboarding = OnboardingAnswers(hard = true))
        fasting.startDue()
        assertTrue(db.fasts.rows.isEmpty())
    }

    @Test
    fun startDue_nothingWhileTheReleaseFlagIsOff() = runUnitTest {
        released = false
        fasting.startDue()
        assertTrue(db.fasts.rows.isEmpty())
    }

    @Test
    fun startDue_nothingBeforeTheUsersOwnSettingsHaveLanded() = runUnitTest {
        settings.known.value = false
        fasting.startDue()
        assertTrue(db.fasts.rows.isEmpty())
    }

    @Test
    fun startDue_nothingWhenFoodWasLoggedAfterTheClose() = runUnitTest {
        clock.now = Instant.parse("2026-10-25T20:10:00Z")
        released = false // log without the fast ender doing anything
        diary.addItems(oct25, MealSlot.Snack, listOf(draft()), LogMethod.Search).written()
        released = true
        clock.now = Instant.parse("2026-10-25T21:30:00Z")

        fasting.startDue()

        assertTrue(db.fasts.rows.isEmpty())
    }

    @Test
    fun end_stampsTheEndNowAndKeepsTheBase() = runUnitTest {
        fasting.startDue()
        db.outbox.clear()
        clock.now = Instant.parse("2026-10-25T23:00:00Z")

        fasting.end()

        val fast = fasting.observeLatest().first()
        assertEquals(clock.now, fast?.endedAt)
        val row = db.fasts.rows.single()
        assertTrue(row.clientUpdatedAtEpochMs > close.toEpochMilliseconds())
        val outbox = db.outbox.rows.single()
        assertEquals(close.toEpochMilliseconds(), WireJson.decodeFromString(FastRowDto.serializer(), outbox.basePayload!!).clientUpdatedAtEpochMs)
        assertEquals(listOf(mapOf<String, Any?>("by" to "user")), events.named("fast.ended"))
    }

    @Test
    fun end_nothingWhenNoFastIsRunning() = runUnitTest {
        fasting.end()
        assertTrue(db.fasts.rows.isEmpty())

        fasting.startDue()
        clock.now = close + 15.hours // past the 14 hour goal
        fasting.end()
        assertNull(db.fasts.rows.single().endedAtEpochMs, "reaching the goal is derived, never written")
        assertTrue(events.named("fast.ended").isEmpty())
    }

    @Test
    fun endForTimerOff_endsTheRunningFast() = runUnitTest {
        fasting.startDue()
        fasting.endForTimerOff()
        assertEquals(clock.now, fasting.observeLatest().first()?.endedAt)
        assertEquals("timer_off", events.named("fast.ended").single()["by"])
    }

    @Test
    fun loggingFoodEndsARunningFast() = runUnitTest {
        fasting.startDue()
        clock.now = Instant.parse("2026-10-26T06:20:00Z")
        calendar.today = LocalDate(2026, 10, 26)

        diary.addItems(LocalDate(2026, 10, 26), MealSlot.Breakfast, listOf(draft()), LogMethod.Search).written()

        assertEquals(clock.now, fasting.observeLatest().first()?.endedAt)
        assertEquals("food", events.named("fast.ended").single()["by"])
    }

    @Test
    fun loggingFoodAfterTheCloseRecordsTheFastItEnded() = runUnitTest {
        clock.now = Instant.parse("2026-10-25T20:10:00Z")

        diary.addItems(oct25, MealSlot.Snack, listOf(draft()), LogMethod.Search).written()

        assertEquals(Fast(oct25, close, endedAt = clock.now, goalHours = 14), fasting.observeLatest().first())
    }

    @Test
    fun copyingFoodAfterTheCloseRecordsTheFastItEnded_too() = runUnitTest {
        val oct24 = LocalDate(2026, 10, 24)
        clock.now = Instant.parse("2026-10-24T12:00:00Z")
        calendar.today = oct24
        diary.addItems(oct24, MealSlot.Lunch, listOf(draft()), LogMethod.Search).written()
        clock.now = Instant.parse("2026-10-25T20:10:00Z")
        calendar.today = oct25

        diary.copySlot(oct24, oct25, MealSlot.Lunch).written()

        assertEquals(Fast(oct25, close, endedAt = clock.now, goalHours = 14), fasting.observeLatest().first())
    }

    @Test
    fun planningTomorrowDoesNotEndAFast() = runUnitTest {
        fasting.startDue()

        diary.addItems(LocalDate(2026, 10, 26), MealSlot.Lunch, listOf(draft()), LogMethod.Search).written()

        assertNull(fasting.observeLatest().first()?.endedAt)
    }

    @Test
    fun ateNoDetailsEndsAFast() = runUnitTest {
        fasting.startDue()

        diary.setAteNoDetails(oct25, MealSlot.Dinner, ate = true)

        assertEquals(clock.now, fasting.observeLatest().first()?.endedAt)
    }

    @Test
    fun aRunningFastKeepsItsGoalWhenThePlanChanges() = runUnitTest {
        fasting.startDue()
        settings.state.value = timerOn().copy(fastPlan = FastingPlan.SixteenEight)
        fasting.startDue()
        assertEquals(14, fasting.observeLatest().first()?.goalHours)
    }

    @Test
    fun eventsNeverCarryADurationOrATime() = runUnitTest {
        fasting.startDue()
        fasting.end()
        val keys = (events.named("fast.started") + events.named("fast.ended")).flatMap { it.keys }.toSet()
        assertEquals(setOf("plan", "by"), keys)
    }

    private fun timerOn() = Settings(numbers = NumbersSettings(preset = PresetId.Full), fasting = true)

    private fun draft() = LogDraft(name = "Toast", precision = Precision.Estimate, kcal = 90.0)
}

/** A calendar in UTC, so the window's 19:00 close is 19:00Z. */
class UtcCalendar(var today: LocalDate = LocalDate(2026, 10, 25)) : com.opson.libraries.diary.LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = today
}

private class FastEventTree : LogTree() {
    private val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        if (entry.context.extras.containsKey(EXTRA_APP_EVENT)) entries += entry
        return null
    }

    fun named(name: String): List<Map<String, Any?>> =
        entries.filter { it.context.extras[EXTRA_APP_EVENT] == name }.map { it.context.extras - EXTRA_APP_EVENT }
}
