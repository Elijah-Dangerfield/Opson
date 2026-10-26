package com.opson.features.settings.impl.integrations

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** A health store in memory: [connectOutcome] is what Connect answers, [energy] what a read returns. */
class FakeHealthRepository(
    status: HealthStatus = HealthStatus.NotConnected,
    var connectOutcome: ConnectOutcome = ConnectOutcome.Connected,
    energy: EnergyExpended? = null,
    override val source: HealthSource = HealthSource.HealthConnect,
) : HealthRepository {
    val statusFlow = MutableStateFlow(status)
    val energyFlow = MutableStateFlow(energy)
    var refreshes = 0
    var connects = 0
    var disconnects = 0
    var settingsOpened = 0
    val energyDays = mutableListOf<LocalDate>()

    override val status: StateFlow<HealthStatus> = statusFlow

    override suspend fun refresh() {
        refreshes++
    }

    override suspend fun connect(): ConnectOutcome {
        connects++
        statusFlow.value = when (connectOutcome) {
            ConnectOutcome.Connected -> HealthStatus.Connected
            ConnectOutcome.Denied -> HealthStatus.NotConnected
            ConnectOutcome.NeedsInstall -> HealthStatus.NeedsInstall
            ConnectOutcome.Unsupported -> HealthStatus.Unsupported
        }
        return connectOutcome
    }

    override suspend fun disconnect() {
        disconnects++
        statusFlow.value = HealthStatus.NotConnected
    }

    override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> {
        energyDays += day
        return combine(statusFlow, energyFlow) { status, energy -> energy.takeIf { status == HealthStatus.Connected } }
    }

    override fun openSourceSettings(): Boolean {
        settingsOpened++
        return true
    }
}

class FakeCalendar(private val today: LocalDate = IntegrationsFixtures.today) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC
    override fun today(): LocalDate = today
}

fun healthFlag(on: Boolean): HealthReleaseFlag = HealthReleaseFlag(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("health" to on))
    },
)
