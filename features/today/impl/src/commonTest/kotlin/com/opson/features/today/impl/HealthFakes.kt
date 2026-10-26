package com.opson.features.today.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Today only reads [energy]; it never connects, disconnects or opens anything. */
class FakeHealthRepository(connected: Boolean = false, energy: EnergyExpended? = null) : HealthRepository {
    val energyFlow = MutableStateFlow(energy)
    val energyDays = mutableListOf<LocalDate>()

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

    override fun openSourceSettings(): Boolean = error("Today never opens the store")
}

fun healthFlag(on: Boolean): HealthReleaseFlag = HealthReleaseFlag(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("health" to on))
    },
)
