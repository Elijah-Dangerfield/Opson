package com.opson.libraries.health.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.ActivityLevel
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.RestingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * [RealBurnedRepository]: the resting source picked, its fall back to the calculation, the
 * latest weigh-in, and activity only from a connected store with `release.health` on.
 */
class RealBurnedRepositoryTest {

    private val today = LocalDate(2026, 10, 27)

    /** 70 kg, 170 cm, 35, female: 1,430 resting. */
    private val profiled = Settings(birthYear = 1991, heightCm = 170.0, bodySex = BodySex.Female)

    private fun repository(
        settings: Settings = profiled,
        weighIns: List<WeighIn> = listOf(WeighIn("w1", Instant.parse("2026-10-01T08:00:00Z"), 80.0), WeighIn("w2", Instant.parse("2026-10-20T08:00:00Z"), 70.0)),
        health: FakeHealth = FakeHealth(),
        healthOn: Boolean = true,
    ) = RealBurnedRepository(FakeSettings(settings), FakeWeights(weighIns), health, healthFlag(healthOn), FixedCalendar(today))

    @Test
    fun calculated_fromTheLatestWeighIn() = runTest {
        val burned = repository(health = FakeHealth(connected = false)).burned(today).first()
        assertEquals(Burned(RestingEnergy(1430, RestingFrom.Calculated), activityCounted = true, everydayKcal = 290), burned)
        assertEquals(1720, burned?.totalKcal, "sedentary until the person says otherwise: 1,430 plus 0.2 of it")
    }

    @Test
    fun withNoHealthApp_theActivityLevelIsTheDaysMovement() = runTest {
        val light = repository(settings = profiled.copy(activityLevel = ActivityLevel.Light), health = FakeHealth(connected = false)).burned(today).first()
        assertEquals(true, light?.movementEstimated)
        assertEquals(1430 + 540, light?.totalKcal)
        // Connected, the reading wins and the level is not used.
        val connected = repository(settings = profiled.copy(activityLevel = ActivityLevel.Active)).burned(today).first()
        assertEquals(false, connected?.movementEstimated)
        assertEquals(1430 + 570, connected?.totalKcal)
    }

    @Test
    fun nothingToCalculateFrom_isNoBurned_andSaysWhatIsMissing() = runTest {
        val repository = repository(settings = Settings(), weighIns = emptyList(), health = FakeHealth(connected = false))
        assertNull(repository.burned(today).first())
        val choices = repository.restingChoices().first()
        assertNull(choices.calculatedKcal)
        assertEquals(setOf(BodyDetail.Weight, BodyDetail.Height, BodyDetail.Age, BodyDetail.Sex), choices.missing)
    }

    @Test
    fun activityFromAConnectedStore_countsWhenSwitchedOn() = runTest {
        val burned = repository().burned(today).first()
        assertEquals(390, burned?.movementKcal)
        assertEquals(180, burned?.workoutKcal)
        assertEquals(1430 + 570, burned?.totalKcal)

        val off = repository(settings = profiled.copy(activityInBurned = false)).burned(today).first()
        assertEquals(1430, off?.totalKcal)
    }

    @Test
    fun theHealthApp_givesTheMostRecentWholeDaysResting_elseTheCalculation() = runTest {
        val health = repository(settings = profiled.copy(restingSource = RestingSource.Health)).burned(today).first()
        assertEquals(RestingEnergy(1560, RestingFrom.Health), health?.resting)

        val none = repository(settings = profiled.copy(restingSource = RestingSource.Health), health = FakeHealth(week = emptyMap()))
        assertEquals(RestingEnergy(1430, RestingFrom.Calculated), none.burned(today).first()?.resting)
    }

    @Test
    fun withReleaseHealthOff_theStoreIsNeverAsked() = runTest {
        val health = FakeHealth()
        val burned = repository(settings = profiled.copy(restingSource = RestingSource.Health), health = health, healthOn = false).burned(today).first()
        assertEquals(Burned(RestingEnergy(1430, RestingFrom.Calculated), everydayKcal = 290), burned)
        assertEquals(0, health.reads)
        assertNull(repository(healthOn = false).restingChoices().first().healthSource)
    }

    @Test
    fun yourOwnNumber_wins() = runTest {
        val own = repository(settings = profiled.copy(restingSource = RestingSource.Own, restingOwnKcal = 1600)).burned(today).first()
        assertEquals(RestingEnergy(1600, RestingFrom.Own), own?.resting)
    }

    private fun healthFlag(on: Boolean) = HealthReleaseFlag(
        object : AppConfigMap() {
            override val map: Map<String, *> = mapOf("release" to mapOf("health" to on))
        },
    )

    private class FakeSettings(initial: Settings) : SettingsRepository {
        override val settings = MutableStateFlow(initial)
        override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings {
            settings.value = transform(settings.value)
            return settings.value
        }
    }

    private class FakeWeights(private val weighIns: List<WeighIn>) : WeightRepository {
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = flowOf(weighIns)
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("not used")
        override suspend fun removeWeighIn(id: String) = Unit
    }

    private class FixedCalendar(private val today: LocalDate) : LocalCalendar {
        override fun timeZone(): TimeZone = TimeZone.UTC
        override fun today(): LocalDate = today
    }

    /** Connected, with 390 movement and 180 workout today, and 1,562 resting the day before yesterday. */
    private inner class FakeHealth(
        connected: Boolean = true,
        private val week: Map<LocalDate, EnergyExpended> = mapOf(
            LocalDate(2026, 10, 25) to EnergyExpended(restingKcal = 1562.0, activeKcal = 500.0, workoutKcal = null),
            LocalDate(2026, 10, 20) to EnergyExpended(restingKcal = 1700.0, activeKcal = 500.0, workoutKcal = null),
        ),
    ) : HealthRepository {
        var reads = 0
        override val source = HealthSource.HealthConnect
        override val status: StateFlow<HealthStatus> = MutableStateFlow(if (connected) HealthStatus.Connected else HealthStatus.NotConnected)
        override suspend fun refresh() = Unit
        override suspend fun connect(): ConnectOutcome = ConnectOutcome.Connected
        override suspend fun disconnect() = Unit
        override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> =
            flowOf(EnergyExpended(restingKcal = 900.0, activeKcal = 570.0, workoutKcal = 180.0)).map { reads++; it }

        override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended> {
            reads++
            return week.filterKeys { it in from..to }
        }

        override fun openSourceSettings(): Boolean = false
    }
}
