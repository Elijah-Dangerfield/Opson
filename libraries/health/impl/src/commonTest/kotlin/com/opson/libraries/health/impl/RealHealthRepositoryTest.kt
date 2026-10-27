package com.opson.libraries.health.impl

import app.cash.turbine.test
import com.opson.libraries.core.fixed
import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * [RealHealthRepository]: when it asks, what it keeps, what [energy] emits for each status, and
 * the app events, which must name the source and nothing else.
 */
class RealHealthRepositoryTest {

    private val today = LocalDate(2026, 10, 25)
    private val noon = Instant.parse("2026-10-25T12:00:00Z")
    private val energy = EnergyExpended(restingKcal = 1580.0, activeKcal = 410.0, workoutKcal = 150.0)
    private val events = EventTree()

    @BeforeTest
    fun plant() = KLog.plant(events)

    @AfterTest
    fun uproot() = KLog.uproot(events)

    private fun repository(
        reader: FakeReader = FakeReader(),
        cache: FakeAppCache = FakeAppCache(),
    ) = RealHealthRepository(reader, cache, Clock.fixed(noon))

    @Test
    fun refresh_neverPrompts_andStaysNotConnectedUntilTheyConnect() = runTest {
        val reader = FakeReader(access = true)
        val repository = repository(reader)
        repository.refresh()
        assertEquals(HealthStatus.NotConnected, repository.status.value)
        assertEquals(0, reader.requests)
    }

    @Test
    fun connect_granted_keepsTheChoiceAndReportsTheSource() = runTest {
        val cache = FakeAppCache()
        val repository = repository(FakeReader(grant = true), cache)
        assertEquals(ConnectOutcome.Connected, repository.connect())
        assertEquals(HealthStatus.Connected, repository.status.value)
        assertTrue(cache.get().healthConnected)
        assertEquals(listOf(mapOf<String, Any?>("source" to "health_connect")), events.named("health.connected"))
    }

    @Test
    fun connect_denied_keepsNothingAndSaysSo() = runTest {
        val cache = FakeAppCache()
        val repository = repository(FakeReader(grant = false), cache)
        assertEquals(ConnectOutcome.Denied, repository.connect())
        assertEquals(HealthStatus.NotConnected, repository.status.value)
        assertFalse(cache.get().healthConnected)
        assertEquals(listOf(mapOf<String, Any?>("source" to "health_connect")), events.named("health.permission_denied"))
        assertTrue(events.named("health.connected").isEmpty())
    }

    @Test
    fun connect_whenThePromptThrows_isADenial() = runTest {
        val repository = repository(FakeReader(grant = true, failRequest = true))
        assertEquals(ConnectOutcome.Denied, repository.connect())
    }

    @Test
    fun connect_needsInstall_doesNotPrompt() = runTest {
        val reader = FakeReader(availability = HealthAvailability.NeedsInstall)
        val repository = repository(reader)
        assertEquals(ConnectOutcome.NeedsInstall, repository.connect())
        assertEquals(HealthStatus.NeedsInstall, repository.status.value)
        assertEquals(0, reader.requests)
    }

    @Test
    fun connect_unsupported_doesNotPrompt() = runTest {
        val reader = FakeReader(availability = HealthAvailability.Unsupported)
        val repository = repository(reader)
        assertEquals(ConnectOutcome.Unsupported, repository.connect())
        assertEquals(HealthStatus.Unsupported, repository.status.value)
        assertEquals(0, reader.requests)
    }

    @Test
    fun disconnect_forgetsTheChoice_revokes_andReports() = runTest {
        val reader = FakeReader(access = true)
        val cache = FakeAppCache(AppData(healthConnected = true))
        val repository = repository(reader, cache)
        repository.disconnect()
        assertFalse(cache.get().healthConnected)
        assertEquals(1, reader.revokes)
        assertEquals(HealthStatus.NotConnected, repository.status.value)
        assertEquals(listOf(mapOf<String, Any?>("source" to "health_connect")), events.named("health.disconnected"))
    }

    @Test
    fun refresh_accessRevokedInTheStore_readsAsNotConnected() = runTest {
        val repository = repository(FakeReader(access = false), FakeAppCache(AppData(healthConnected = true)))
        repository.refresh()
        assertEquals(HealthStatus.NotConnected, repository.status.value)
    }

    @Test
    fun refresh_unknownAccess_trustsTheChoice() = runTest {
        // HealthKit never says; a finished prompt is as much as iOS will tell.
        val repository = repository(FakeReader(access = null), FakeAppCache(AppData(healthConnected = true)))
        repository.refresh()
        assertEquals(HealthStatus.Connected, repository.status.value)
    }

    @Test
    fun energy_isNullWhileNotConnected_andNeverReads() = runTest {
        val reader = FakeReader(access = true, energy = energy)
        repository(reader).energy(today, TimeZone.UTC).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, reader.reads.size)
    }

    @Test
    fun energy_whenConnected_readsMidnightToNow() = runTest {
        val reader = FakeReader(access = true, energy = energy)
        repository(reader, FakeAppCache(AppData(healthConnected = true))).energy(today, TimeZone.UTC).test {
            assertEquals(energy, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(Instant.parse("2026-10-25T00:00:00Z") to noon, reader.reads.single())
    }

    @Test
    fun energy_rereadsWhileCollected() = runTest {
        val reader = FakeReader(access = true, energy = energy)
        val repository = repository(reader, FakeAppCache(AppData(healthConnected = true)))
        repository.refreshEvery = 1.minutes
        repository.energy(today, TimeZone.UTC).test {
            awaitItem()
            reader.energy = energy.copy(activeKcal = 500.0)
            assertEquals(500.0, awaitItem()?.activeKcal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun energy_aPastDay_readsTheWholeDay() = runTest {
        val reader = FakeReader(access = true, energy = energy)
        repository(reader, FakeAppCache(AppData(healthConnected = true))).energy(LocalDate(2026, 10, 24), TimeZone.UTC).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(Instant.parse("2026-10-24T00:00:00Z") to Instant.parse("2026-10-25T00:00:00Z"), reader.reads.single())
    }

    @Test
    fun energy_aFailedRead_isNullNotZero() = runTest {
        val reader = FakeReader(access = true, energy = energy, failRead = true)
        repository(reader, FakeAppCache(AppData(healthConnected = true))).energy(today, TimeZone.UTC).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun energy_followsAConnectMadeWhileCollecting() = runTest {
        val repository = repository(FakeReader(access = true, grant = true, energy = energy))
        repository.energy(today, TimeZone.UTC).test {
            assertNull(awaitItem())
            repository.connect()
            assertEquals(energy, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun energyBetween_isNullWhileNotConnected_andNeverReads() = runTest {
        val reader = FakeReader(access = true)
        assertNull(repository(reader).energyBetween(LocalDate(2026, 10, 19), today, TimeZone.UTC))
        assertTrue(reader.dailyReads.isEmpty())
    }

    @Test
    fun energyBetween_whenConnected_readsOnceFromTheFirstMidnightToNow() = runTest {
        val week = (19..25).associate { LocalDate(2026, 10, it) to energy }
        val reader = FakeReader(access = true).apply { daily = week + (LocalDate(2026, 10, 18) to energy) }
        val repository = repository(reader, FakeAppCache(AppData(healthConnected = true)))

        val read = repository.energyBetween(LocalDate(2026, 10, 19), today, TimeZone.UTC)

        assertEquals(week, read, "a bucket outside the asked days is dropped")
        assertEquals(listOf(Instant.parse("2026-10-19T00:00:00Z") to noon), reader.dailyReads)
    }

    @Test
    fun energyBetween_pastDays_endAtTheLastOnesMidnight() = runTest {
        val reader = FakeReader(access = true)
        repository(reader, FakeAppCache(AppData(healthConnected = true))).energyBetween(LocalDate(2026, 10, 12), LocalDate(2026, 10, 18), TimeZone.UTC)
        assertEquals(listOf(Instant.parse("2026-10-12T00:00:00Z") to Instant.parse("2026-10-19T00:00:00Z")), reader.dailyReads)
    }

    @Test
    fun energyBetween_aFailedRead_isNullNotAnEmptyWeek() = runTest {
        val reader = FakeReader(access = true, failRead = true)
        assertNull(repository(reader, FakeAppCache(AppData(healthConnected = true))).energyBetween(LocalDate(2026, 10, 19), today, TimeZone.UTC))
    }

    @Test
    fun noEvent_carriesAValue() = runTest {
        val repository = repository(FakeReader(access = true, grant = true, energy = energy))
        repository.connect()
        repository.disconnect()
        val attributes = events.all().flatMap { it.keys }.toSet()
        assertEquals(setOf("source"), attributes)
    }
}

private class FakeReader(
    var availability: HealthAvailability = HealthAvailability.Available,
    var access: Boolean? = false,
    var grant: Boolean = true,
    var energy: EnergyExpended = EnergyExpended(null, null, null),
    var failRead: Boolean = false,
    var failRequest: Boolean = false,
) : HealthReader {
    var requests = 0
    var revokes = 0
    val reads = mutableListOf<Pair<Instant, Instant>>()

    override val source = HealthSource.HealthConnect

    override suspend fun availability() = availability

    override suspend fun hasAccess() = access

    override suspend fun requestAccess(): Boolean {
        requests++
        if (failRequest) error("prompt failed")
        access = grant
        return grant
    }

    override suspend fun read(from: Instant, to: Instant): EnergyExpended {
        reads += from to to
        if (failRead) error("store unavailable")
        return energy
    }

    val dailyReads = mutableListOf<Pair<Instant, Instant>>()

    /** What [readDaily] answers, already bucketed. */
    var daily: Map<LocalDate, EnergyExpended> = emptyMap()

    override suspend fun readDaily(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, EnergyExpended> {
        dailyReads += from to to
        if (failRead) error("store unavailable")
        return daily
    }

    override suspend fun revokeAccess() {
        revokes++
        access = false
    }

    override fun openSettings() = true
}

private class FakeAppCache(initial: AppData = AppData()) : AppCache {
    private val state = MutableStateFlow(initial)
    override val updates: Flow<AppData> = state
    override suspend fun get(): AppData = state.value
    override suspend fun set(value: AppData) {
        state.value = value
    }
    override suspend fun clear() {
        state.value = AppData()
    }
}

/** Collects `logEvent` entries: the event name and its attributes. */
private class EventTree : LogTree() {
    private val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        if (entry.context.extras.containsKey(EXTRA_APP_EVENT)) entries += entry
        return null
    }

    fun named(name: String): List<Map<String, Any?>> =
        entries.filter { it.context.extras[EXTRA_APP_EVENT] == name }.map { it.context.extras - EXTRA_APP_EVENT }

    fun all(): List<Map<String, Any?>> = entries.map { it.context.extras - EXTRA_APP_EVENT }
}
