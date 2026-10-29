package com.opson.libraries.health.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthRefresh
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.opson.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** [WeighInMerge]'s rules, and [RealHealthWeighInImport] bringing weigh-ins in only when asked, once each. */
class WeighInMergeTest {

    private val morning = Instant.parse("2026-10-25T07:00:00Z")

    private fun sample(id: String, at: Instant, kg: Double = 72.4) = HealthWeighIn(id, at, kg, "Withings")

    @Test
    fun aWeighInOpsonAlreadyHas_withinMinutes_isTheSameOne() {
        val typed = WeighIn("w1", morning + 4.minutes, 72.4)
        assertEquals(emptyList(), WeighInMerge.newOnes(listOf(sample("a", morning)), listOf(typed), emptySet()))
    }

    @Test
    fun oneHourApart_isAnotherWeighIn() {
        val typed = WeighIn("w1", morning + 60.minutes, 72.4)
        assertEquals(listOf("a"), WeighInMerge.newOnes(listOf(sample("a", morning)), listOf(typed), emptySet()).map { it.id })
    }

    @Test
    fun oneBroughtInBefore_neverComesBack_evenAfterItIsDeletedFromTheLog() {
        assertEquals(emptyList(), WeighInMerge.newOnes(listOf(sample("a", morning)), existing = emptyList(), importedIds = setOf("a")))
    }

    @Test
    fun aScaleAndItsAppWritingTheSameReading_comeInOnce_theFirst() {
        val merged = WeighInMerge.newOnes(listOf(sample("app", morning + 1.minutes), sample("scale", morning)), emptyList(), emptySet())
        assertEquals(listOf("scale"), merged.map { it.id })
    }

    @Test
    fun aStrayReading_isLeftOut() {
        assertEquals(emptyList(), WeighInMerge.newOnes(listOf(sample("a", morning, kg = 0.4), sample("b", morning + 2.days, kg = 950.0)), emptyList(), emptySet()))
    }
}

class RealHealthWeighInImportTest {

    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val samples = listOf(
        HealthWeighIn("a", Instant.parse("2026-10-20T07:00:00Z"), 72.8, "Withings"),
        HealthWeighIn("b", Instant.parse("2026-10-24T07:00:00Z"), 72.4, "Withings"),
    )

    private fun importer(
        status: HealthStatus = HealthStatus.Connected,
        cache: FakeAppCache = FakeAppCache(),
        weights: FakeWeights = FakeWeights(),
        records: FakeRecords = FakeRecords(samples),
    ) = RealHealthWeighInImport(FakeStatus(status), records, weights, cache, Clock.fixed(now))

    @Test
    fun off_bringsNothingIn() = runTest {
        val weights = FakeWeights()
        assertNull(importer(weights = weights).importNew())
        assertTrue(weights.added.isEmpty())
    }

    @Test
    fun turningItOn_bringsInTheLastFewMonths_andRemembersWhich() = runTest {
        val cache = FakeAppCache()
        val weights = FakeWeights()
        val records = FakeRecords(samples)
        assertEquals(2, importer(cache = cache, weights = weights, records = records).turnOn())
        assertEquals(listOf(72.8, 72.4), weights.added.map { it.kg })
        assertEquals(listOf("a", "b"), cache.get().healthImportedWeighIns)
        assertEquals(now - RealHealthWeighInImport.LOOK_BACK, records.since)
    }

    @Test
    fun theNextImport_addsOnlyWhatIsNew() = runTest {
        val cache = FakeAppCache(AppData(healthWeighIns = true, healthImportedWeighIns = listOf("a", "b")))
        val weights = FakeWeights()
        val later = samples + HealthWeighIn("c", Instant.parse("2026-10-25T07:00:00Z"), 72.1, "Withings")
        assertEquals(1, importer(cache = cache, weights = weights, records = FakeRecords(later)).importNew())
        assertEquals(listOf(72.1), weights.added.map { it.kg })
    }

    @Test
    fun notConnected_bringsNothingIn() = runTest {
        val weights = FakeWeights()
        assertNull(importer(status = HealthStatus.NotConnected, cache = FakeAppCache(AppData(healthWeighIns = true)), weights = weights).importNew())
        assertTrue(weights.added.isEmpty())
    }

    @Test
    fun turningItOff_isRemembered() = runTest {
        val cache = FakeAppCache(AppData(healthWeighIns = true))
        importer(cache = cache).turnOff()
        assertFalse(cache.get().healthWeighIns)
    }

    @Test
    fun aStoreThatCantBeRead_addsNothing_andSaysSo() = runTest {
        val weights = FakeWeights()
        assertNull(importer(cache = FakeAppCache(AppData(healthWeighIns = true)), weights = weights, records = FakeRecords(null)).importNew())
        assertTrue(weights.added.isEmpty())
    }

    class FakeWeights : WeightRepository {
        val added = mutableListOf<WeighIn>()
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = MutableStateFlow(added.toList()).map { it }
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn =
            WeighIn("w${added.size}", requireNotNull(measuredAt), kg).also { added += it }
        override suspend fun removeWeighIn(id: String) = Unit
    }

    class FakeRecords(private val weighIns: List<HealthWeighIn>?) : HealthRecords {
        var since: Instant? = null
        override val canAskForMore: StateFlow<Boolean> = MutableStateFlow(false)
        override fun workouts(day: LocalDate, timeZone: TimeZone): Flow<List<HealthWorkout>?> = error("not used")
        override suspend fun profile(): HealthProfile? = error("not used")
        override suspend fun weighIns(since: Instant): List<HealthWeighIn>? {
            this.since = since
            return weighIns
        }
        override suspend fun sources(): List<String>? = error("not used")
        override suspend fun refreshNow(day: LocalDate, timeZone: TimeZone): HealthRefresh = error("not used")
    }

    private class FakeStatus(status: HealthStatus) : HealthRepository {
        override val source = HealthSource.AppleHealth
        override val status: StateFlow<HealthStatus> = MutableStateFlow(status)
        override suspend fun refresh() = Unit
        override suspend fun connect(): ConnectOutcome = error("not used")
        override suspend fun disconnect() = error("not used")
        override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> = error("not used")
        override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended>? = error("not used")
        override fun openSourceSettings(): Boolean = error("not used")
    }
}
