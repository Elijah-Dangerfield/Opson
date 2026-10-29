package com.opson.features.today.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthRefresh
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Today only reads [energy] and [workouts], and pulls to refresh; it never connects, disconnects
 * or opens anything. [refreshAnswer] is what a pull finds.
 */
class FakeHealthRepository(
    connected: Boolean = false,
    energy: EnergyExpended? = null,
    workouts: List<HealthWorkout> = emptyList(),
    var refreshAnswer: HealthRefresh = HealthRefresh.NothingNew,
) : HealthRepository, HealthRecords {
    val energyFlow = MutableStateFlow(energy)
    val workoutsFlow = MutableStateFlow(workouts)
    val energyDays = mutableListOf<LocalDate>()
    val workoutDays = mutableListOf<LocalDate>()
    var refreshes = 0

    override val source = HealthSource.HealthConnect
    override val status: StateFlow<HealthStatus> =
        MutableStateFlow(if (connected) HealthStatus.Connected else HealthStatus.NotConnected)

    override suspend fun refresh() = Unit

    override suspend fun connect(): ConnectOutcome = error("Today never asks for permission")

    override suspend fun disconnect() = error("Today never disconnects")

    override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> {
        energyDays += day
        return energyFlow.map { it.takeIf { status.value == HealthStatus.Connected } }
    }

    override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended>? =
        error("Today reads one day at a time")

    override fun openSourceSettings(): Boolean = error("Today never opens the store")

    override val canAskForMore: StateFlow<Boolean> = MutableStateFlow(false)

    override fun workouts(day: LocalDate, timeZone: TimeZone): Flow<List<HealthWorkout>?> {
        workoutDays += day
        return workoutsFlow.map { it.takeIf { status.value == HealthStatus.Connected } }
    }

    override suspend fun profile(): HealthProfile? = error("Today never reads the profile")

    override suspend fun weighIns(since: Instant): List<HealthWeighIn>? = error("Today never reads weigh-ins")

    override suspend fun sources(): List<String>? = error("Today never lists sources")

    override suspend fun refreshNow(day: LocalDate, timeZone: TimeZone): HealthRefresh {
        refreshes++
        return refreshAnswer
    }
}

fun healthFlag(on: Boolean): HealthReleaseFlag = HealthReleaseFlag(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("health" to on))
    },
)
