package com.opson.libraries.health.impl

import app.cash.turbine.test
import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.TestDispatcherProvider
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthRefresh
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.health.WorkoutKind
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import com.opson.libraries.opson.HealthEnergySnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * [RealHealthRepository]: what counts as connected on each platform's terms, when it asks, what it
 * keeps, what [RealHealthRepository.energy] emits and when it reads, and the app events, which
 * must name the source and nothing else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealHealthRepositoryTest {

    private val today = LocalDate(2026, 10, 25)
    private val noon = Instant.parse("2026-10-25T12:00:00Z")
    private val energy = EnergyExpended(restingKcal = 1580.0, activeKcal = 410.0, workoutKcal = 150.0)
    private val events = EventTree()
    private val connected = AppData(healthConnected = true)

    @BeforeTest
    fun plant() = KLog.plant(events)

    @AfterTest
    fun uproot() = KLog.uproot(events)

    /** A repository in the foreground on this test's clock; the app scope is cancelled when [block] ends. */
    private fun repoTest(
        reader: FakeReader = FakeReader(),
        cache: FakeAppCache = FakeAppCache(),
        inFront: Boolean = true,
        block: suspend TestScope.(RealHealthRepository, HealthAppForeground) -> Unit,
    ) = runTest {
        val scope = AppCoroutineScope(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        val foreground = HealthAppForeground().apply { set(inFront) }
        try {
            // The clock follows the test's virtual time, so the floor between reads behaves as on a phone.
            val clock = object : Clock {
                override fun now(): Instant = noon + testScheduler.currentTime.milliseconds
            }
            block(RealHealthRepository(reader, cache, clock, scope, foreground), foreground)
        } finally {
            scope.cancel()
        }
    }

    // Connected, on the store's terms

    @Test
    fun refresh_neverPrompts_andNeverAskedIsNotConnected() = repoTest(FakeReader(access = HealthAccess.NotAsked)) { repository, _ ->
        repository.refresh()
        assertEquals(HealthStatus.NotConnected, repository.status.value)
    }

    @Test
    fun refresh_aGrantMadeInTheStore_isConnected_withoutTappingConnect() = run {
        // The bug from TestFlight: Health says "sharing", Opson said "not connected".
        val cache = FakeAppCache()
        val reader = FakeReader(access = HealthAccess.All)
        repoTest(reader, cache) { repository, _ ->
            repository.refresh()
            assertEquals(HealthStatus.Connected, repository.status.value)
            assertTrue(cache.get().healthConnected, "remembered, for a store that later won't say")
            assertEquals(0, reader.requests)
        }
    }

    @Test
    fun refresh_turnedOffInOpson_staysNotConnected_whateverTheStoreAllows() =
        repoTest(FakeReader(access = HealthAccess.All), FakeAppCache(AppData(healthPaused = true))) { repository, _ ->
            repository.refresh()
            assertEquals(HealthStatus.NotConnected, repository.status.value)
        }

    @Test
    fun refresh_partOfIt_isConnected_withMoreToAskFor() = repoTest(FakeReader(access = HealthAccess.Partly)) { repository, _ ->
        repository.refresh()
        assertEquals(HealthStatus.Connected, repository.status.value)
        assertTrue(repository.canAskForMore.value)
    }

    @Test
    fun refresh_allOfIt_hasNothingMoreToAskFor() = repoTest(FakeReader(access = HealthAccess.All)) { repository, _ ->
        repository.refresh()
        assertFalse(repository.canAskForMore.value)
    }

    @Test
    fun refresh_revokedInTheStore_readsAsNotConnected() = repoTest(FakeReader(access = HealthAccess.NotAsked), FakeAppCache(connected)) { repository, _ ->
        repository.refresh()
        assertEquals(HealthStatus.NotConnected, repository.status.value)
    }

    @Test
    fun refresh_aStoreThatWontSay_trustsTheChoice() = repoTest(FakeReader(access = HealthAccess.Unknown), FakeAppCache(connected)) { repository, _ ->
        repository.refresh()
        assertEquals(HealthStatus.Connected, repository.status.value)
    }

    @Test
    fun refresh_aStoreThatWontSay_withNoChoiceMade_isNotConnected() = repoTest(FakeReader(access = HealthAccess.Unknown)) { repository, _ ->
        repository.refresh()
        assertEquals(HealthStatus.NotConnected, repository.status.value)
    }

    @Test
    fun refresh_aCheckThatThrows_fallsBackToTheChoice_atWarn() =
        repoTest(FakeReader(access = HealthAccess.All, failAccess = true), FakeAppCache(connected)) { repository, _ ->
            repository.refresh()
            assertEquals(HealthStatus.Connected, repository.status.value)
            assertTrue(events.errors().isEmpty())
        }

    // Connect and disconnect

    @Test
    fun connect_granted_keepsTheChoice_turnsItBackOn_andReportsTheSource() = run {
        val cache = FakeAppCache(AppData(healthPaused = true))
        repoTest(FakeReader(access = HealthAccess.NotAsked, grant = true), cache) { repository, _ ->
            assertEquals(ConnectOutcome.Connected, repository.connect())
            assertEquals(HealthStatus.Connected, repository.status.value)
            assertTrue(cache.get().healthConnected)
            assertFalse(cache.get().healthPaused)
            assertEquals(listOf(mapOf<String, Any?>("source" to "health_connect")), events.named("health.connected"))
        }
    }

    @Test
    fun connect_denied_keepsNothingAndSaysSo() = run {
        val cache = FakeAppCache()
        repoTest(FakeReader(access = HealthAccess.NotAsked, grant = false), cache) { repository, _ ->
            assertEquals(ConnectOutcome.Denied, repository.connect())
            assertEquals(HealthStatus.NotConnected, repository.status.value)
            assertFalse(cache.get().healthConnected)
            assertEquals(listOf(mapOf<String, Any?>("source" to "health_connect")), events.named("health.permission_denied"))
            assertTrue(events.named("health.connected").isEmpty())
        }
    }

    @Test
    fun connect_whenThePromptThrows_isAFailure_reportedAtErrorOncePerProcess() = repoTest(FakeReader(failRequest = true)) { repository, _ ->
        // A build without the HealthKit entitlement answers like this, and it must reach Sentry.
        assertEquals(ConnectOutcome.Failed, repository.connect())
        assertEquals(ConnectOutcome.Failed, repository.connect())
        assertEquals(1, events.errors().size)
        assertTrue(events.named("health.permission_denied").isEmpty(), "not a refusal: nobody was asked")
    }

    @Test
    fun connect_needsInstall_doesNotPrompt() = run {
        val reader = FakeReader(availability = HealthAvailability.NeedsInstall)
        repoTest(reader) { repository, _ ->
            assertEquals(ConnectOutcome.NeedsInstall, repository.connect())
            assertEquals(HealthStatus.NeedsInstall, repository.status.value)
            assertEquals(0, reader.requests)
        }
    }

    @Test
    fun connect_unsupported_doesNotPrompt() = run {
        val reader = FakeReader(availability = HealthAvailability.Unsupported)
        repoTest(reader) { repository, _ ->
            assertEquals(ConnectOutcome.Unsupported, repository.connect())
            assertEquals(HealthStatus.Unsupported, repository.status.value)
            assertEquals(0, reader.requests)
        }
    }

    @Test
    fun disconnect_turnsItOffInOpson_revokesWhereItCan_dropsTheSavedReading_andReports() = run {
        // HealthKit can't revoke, so the store still says All: the switch in Opson is what holds.
        val reader = FakeReader(access = HealthAccess.All, revokes = false)
        val cache = FakeAppCache(connected.copy(healthEnergy = snapshot(today)))
        repoTest(reader, cache) { repository, _ ->
            repository.disconnect()
            assertTrue(cache.get().healthPaused)
            assertNull(cache.get().healthEnergy)
            assertEquals(1, reader.revokeCalls)
            assertEquals(HealthStatus.NotConnected, repository.status.value)
            assertEquals(listOf(mapOf<String, Any?>("source" to "health_connect")), events.named("health.disconnected"))
        }
    }

    // Energy

    @Test
    fun energy_isNullWhileNotConnected_andNeverReads() = run {
        val reader = FakeReader(access = HealthAccess.NotAsked, energy = energy)
        repoTest(reader) { repository, _ ->
            repository.energy(today, TimeZone.UTC).test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0, reader.reads.size)
        }
    }

    @Test
    fun energy_whenConnected_readsMidnightToNow() = run {
        val reader = FakeReader(energy = energy)
        repoTest(reader) { repository, _ ->
            repository.energy(today, TimeZone.UTC).test {
                assertEquals(energy, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(Instant.parse("2026-10-25T00:00:00Z") to noon, reader.reads.first())
        }
    }

    @Test
    fun energy_rereadsWhileCollected() = run {
        val reader = FakeReader(energy = energy)
        repoTest(reader) { repository, _ ->
            repository.refreshEvery = 1.minutes
            repository.energy(today, TimeZone.UTC).test {
                assertEquals(energy, awaitItem())
                reader.energy = energy.copy(activeKcal = 500.0)
                assertEquals(500.0, awaitItem()?.activeKcal)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun energy_aPastDay_readsTheWholeDay() = run {
        val reader = FakeReader(energy = energy)
        repoTest(reader) { repository, _ ->
            repository.energy(LocalDate(2026, 10, 24), TimeZone.UTC).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(Instant.parse("2026-10-24T00:00:00Z") to Instant.parse("2026-10-25T00:00:00Z"), reader.reads.first())
        }
    }

    @Test
    fun energy_aFailedFirstRead_isNullNotZero() = repoTest(FakeReader(energy = energy, failRead = true)) { repository, _ ->
        repository.energy(today, TimeZone.UTC).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, events.errors().size, "a failed read is an error, once")
    }

    @Test
    fun energy_aFailedLaterRead_keepsTheLastGoodValue() = run {
        val reader = FakeReader(energy = energy)
        repoTest(reader) { repository, _ ->
            repository.refreshEvery = 1.minutes
            repository.energy(today, TimeZone.UTC).test {
                assertEquals(energy, awaitItem())
                reader.failRead = true
                advanceTimeBy(3.minutes)
                runCurrent()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(reader.reads.size >= 3)
        }
    }

    @Test
    fun energy_isOneReadingShared_byEveryoneAskingForTheDay() = run {
        val reader = FakeReader(energy = energy)
        repoTest(reader) { repository, _ ->
            repository.energy(today, TimeZone.UTC).test {
                awaitItem()
                repository.energy(today, TimeZone.UTC).test {
                    assertEquals(energy, awaitItem(), "the second gets the reading the first made")
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, reader.reads.size)
        }
    }

    @Test
    fun energy_inTheBackground_neverReads_andShowsTheSavedReading() = run {
        val reader = FakeReader(energy = energy)
        val saved = energy.copy(activeKcal = 300.0)
        repoTest(reader, FakeAppCache(connected.copy(healthEnergy = snapshot(today, saved))), inFront = false) { repository, foreground ->
            repository.energy(today, TimeZone.UTC).test {
                assertEquals(saved, awaitItem())
                advanceTimeBy(30.minutes)
                runCurrent()
                expectNoEvents()
                assertTrue(reader.reads.isEmpty(), "Health Connect refuses a background read")
                foreground.set(true)
                assertEquals(energy, awaitItem(), "coming to the front reads at once")
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun energy_comingBackToTheApp_readsAgain() = run {
        val reader = FakeReader(energy = energy)
        repoTest(reader) { repository, foreground ->
            repository.energy(today, TimeZone.UTC).test {
                awaitItem()
                foreground.set(false)
                runCurrent()
                reader.energy = energy.copy(workoutKcal = 320.0)
                advanceTimeBy(1.minutes)
                foreground.set(true)
                assertEquals(320.0, awaitItem()?.workoutKcal)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun energy_theStoreSayingSomethingChanged_readsAgain() = run {
        val reader = FakeReader(energy = energy)
        repoTest(reader) { repository, _ ->
            repository.energy(today, TimeZone.UTC).test {
                awaitItem()
                reader.energy = energy.copy(workoutKcal = 320.0)
                advanceTimeBy(1.minutes)
                reader.changes.emit(Unit)
                assertEquals(320.0, awaitItem()?.workoutKcal)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun energy_aColdStart_showsTheSavedReadingFirst_thenTheStores() = run {
        val saved = energy.copy(activeKcal = 300.0)
        val cache = FakeAppCache(connected.copy(healthEnergy = snapshot(today, saved)))
        repoTest(FakeReader(energy = energy), cache) { repository, _ ->
            repository.energy(today, TimeZone.UTC).test {
                assertEquals(saved, awaitItem())
                assertEquals(energy, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(410.0, cache.get().healthEnergy?.activeKcal, "the new reading is saved")
        }
    }

    @Test
    fun energy_yesterdaysSavedReading_isNotToday() =
        repoTest(FakeReader(energy = energy, failRead = true), FakeAppCache(connected.copy(healthEnergy = snapshot(LocalDate(2026, 10, 24))))) { repository, _ ->
            repository.energy(today, TimeZone.UTC).test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun energy_followsAConnectMadeWhileCollecting() = run {
        val reader = FakeReader(access = HealthAccess.NotAsked, grant = true, energy = energy)
        repoTest(reader) { repository, _ ->
            repository.energy(today, TimeZone.UTC).test {
                assertNull(awaitItem())
                repository.connect()
                assertEquals(energy, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // Pull to refresh

    @Test
    fun refreshNow_saysWhetherAnythingNewCameIn() = run {
        val reader = FakeReader(energy = energy)
        repoTest(reader) { repository, _ ->
            assertEquals(HealthRefresh.NewData, repository.refreshNow(today, TimeZone.UTC))
            assertEquals(HealthRefresh.NothingNew, repository.refreshNow(today, TimeZone.UTC))
            reader.energy = energy.copy(workoutKcal = 320.0)
            assertEquals(HealthRefresh.NewData, repository.refreshNow(today, TimeZone.UTC))
            reader.failRead = true
            assertEquals(HealthRefresh.Failed, repository.refreshNow(today, TimeZone.UTC))
        }
    }

    @Test
    fun refreshNow_notConnected_readsNothing() = run {
        val reader = FakeReader(access = HealthAccess.NotAsked)
        repoTest(reader) { repository, _ ->
            assertEquals(HealthRefresh.NotConnected, repository.refreshNow(today, TimeZone.UTC))
            assertTrue(reader.reads.isEmpty())
        }
    }

    // By day, records

    @Test
    fun energyBetween_isNullWhileNotConnected_andNeverReads() = run {
        val reader = FakeReader(access = HealthAccess.NotAsked)
        repoTest(reader) { repository, _ ->
            assertNull(repository.energyBetween(LocalDate(2026, 10, 19), today, TimeZone.UTC))
            assertTrue(reader.dailyReads.isEmpty())
        }
    }

    @Test
    fun energyBetween_whenConnected_readsOnceFromTheFirstMidnightToNow() = run {
        val week = (19..25).associate { LocalDate(2026, 10, it) to energy }
        val reader = FakeReader().apply { daily = week + (LocalDate(2026, 10, 18) to energy) }
        repoTest(reader) { repository, _ ->
            val read = repository.energyBetween(LocalDate(2026, 10, 19), today, TimeZone.UTC)
            assertEquals(week, read, "a bucket outside the asked days is dropped")
            assertEquals(listOf(Instant.parse("2026-10-19T00:00:00Z") to noon), reader.dailyReads)
        }
    }

    @Test
    fun energyBetween_pastDays_endAtTheLastOnesMidnight() = run {
        val reader = FakeReader()
        repoTest(reader) { repository, _ ->
            repository.energyBetween(LocalDate(2026, 10, 12), LocalDate(2026, 10, 18), TimeZone.UTC)
            assertEquals(listOf(Instant.parse("2026-10-12T00:00:00Z") to Instant.parse("2026-10-19T00:00:00Z")), reader.dailyReads)
        }
    }

    @Test
    fun energyBetween_aFailedRead_isNullNotAnEmptyWeek() = repoTest(FakeReader(failRead = true)) { repository, _ ->
        assertNull(repository.energyBetween(LocalDate(2026, 10, 19), today, TimeZone.UTC))
    }

    @Test
    fun workouts_oldestFirst_whileConnected() = run {
        val run = workout("run", "2026-10-25T07:10:00Z")
        val lift = workout("lift", "2026-10-25T06:00:00Z")
        repoTest(FakeReader(workouts = listOf(run, lift))) { repository, _ ->
            repository.refresh()
            repository.workouts(today, TimeZone.UTC).test {
                assertEquals(listOf(lift, run), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun workouts_andProfile_areNullWhileNotConnected() = repoTest(FakeReader(access = HealthAccess.NotAsked)) { repository, _ ->
        repository.refresh()
        repository.workouts(today, TimeZone.UTC).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(repository.profile())
        assertNull(repository.sources())
    }

    @Test
    fun sources_areSortedNames() = repoTest(FakeReader(sources = setOf("Oura", "Apple Watch", "garmin Connect"))) { repository, _ ->
        repository.refresh()
        assertEquals(listOf("Apple Watch", "garmin Connect", "Oura"), repository.sources())
    }

    @Test
    fun noEvent_carriesAValue() = repoTest(FakeReader(grant = true, energy = energy)) { repository, _ ->
        repository.connect()
        repository.disconnect()
        val attributes = events.all().flatMap { it.keys }.toSet()
        assertEquals(setOf("source"), attributes)
    }

    private fun snapshot(day: LocalDate, value: EnergyExpended = energy) =
        HealthEnergySnapshot(day.toString(), 0L, value.restingKcal, value.activeKcal, value.workoutKcal)

    private fun workout(id: String, start: String) = HealthWorkout(
        id = id,
        kind = WorkoutKind.Running,
        title = null,
        start = Instant.parse(start),
        end = Instant.parse(start) + 30.minutes,
        activeKcal = 300.0,
        sourceName = "Garmin Connect",
    )
}

private class FakeReader(
    var availability: HealthAvailability = HealthAvailability.Available,
    var access: HealthAccess = HealthAccess.All,
    var grant: Boolean = true,
    var energy: EnergyExpended = EnergyExpended(null, null, null),
    var failRead: Boolean = false,
    var failRequest: Boolean = false,
    var failAccess: Boolean = false,
    /** Health Connect gives permission back on revoke; HealthKit can't. */
    private val revokes: Boolean = true,
    var workouts: List<HealthWorkout> = emptyList(),
    var sources: Set<String> = emptySet(),
) : HealthReader {
    var requests = 0
    var revokeCalls = 0
    val reads = mutableListOf<Pair<Instant, Instant>>()
    val changes = MutableSharedFlow<Unit>()

    override val source = HealthSource.HealthConnect

    override suspend fun availability() = availability

    override suspend fun access(): HealthAccess {
        if (failAccess) error("store unavailable")
        return access
    }

    override suspend fun requestAccess(): Boolean {
        requests++
        if (failRequest) error("Missing com.apple.developer.healthkit entitlement")
        if (grant) access = HealthAccess.All
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

    override suspend fun workouts(from: Instant, to: Instant): List<HealthWorkout> = workouts

    override suspend fun weighIns(from: Instant, to: Instant): List<HealthWeighIn> = emptyList()

    override suspend fun profile(): HealthProfile = HealthProfile(null, null, null)

    override suspend fun sources(from: Instant, to: Instant): Set<String> = sources

    override fun changes(): Flow<Unit> = changes

    override suspend fun revokeAccess() {
        revokeCalls++
        if (revokes) access = HealthAccess.NotAsked
    }

    override fun openSettings() = true
}

internal class FakeAppCache(initial: AppData = AppData()) : AppCache {
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

/** Collects `logEvent` entries (the event name and its attributes) and anything logged at error. */
private class EventTree : LogTree() {
    private val entries = mutableListOf<LogEntry>()
    private val errors = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        if (entry.context.extras.containsKey(EXTRA_APP_EVENT)) entries += entry
        if (entry.level == LogLevel.Error) errors += entry
        return null
    }

    fun named(name: String): List<Map<String, Any?>> =
        entries.filter { it.context.extras[EXTRA_APP_EVENT] == name }.map { it.context.extras - EXTRA_APP_EVENT }

    fun all(): List<Map<String, Any?>> = entries.map { it.context.extras - EXTRA_APP_EVENT }

    fun errors(): List<LogEntry> = errors
}
