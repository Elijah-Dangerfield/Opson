package com.opson.features.onboarding.impl

import com.opson.features.onboarding.OnboardingSetupEnabled
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The fixtures' day, 2026-10-25, in UTC. */
internal object FixedCalendar : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = LocalDate(2026, 10, 25)
}

/** Records every weigh-in onboarding logs. */
internal class FakeWeightRepository(private val fail: Boolean = false, existing: List<WeighIn> = emptyList()) : WeightRepository {
    val added = mutableListOf<Double>()
    private val weighIns = MutableStateFlow(existing)

    override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = weighIns

    override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn {
        if (fail) error("disk full")
        added += kg
        val weighIn = WeighIn(id = "w${added.size}", measuredAt = measuredAt ?: Instant.parse("2026-10-25T12:00:00Z"), kg = kg)
        weighIns.value += weighIn
        return weighIn
    }

    override suspend fun removeWeighIn(id: String) = Unit
}

/** A health store whose Connect answers [connectsTo]. */
internal class FakeHealthRepository(
    initial: HealthStatus = HealthStatus.NotConnected,
    private val connectsTo: HealthStatus = HealthStatus.Connected,
    override val source: HealthSource = HealthSource.AppleHealth,
) : HealthRepository {
    private val current = MutableStateFlow(initial)
    var connects = 0

    override val status: StateFlow<HealthStatus> = current

    override suspend fun refresh() = Unit

    override suspend fun connect(): ConnectOutcome {
        connects++
        current.value = connectsTo
        return if (connectsTo == HealthStatus.Connected) ConnectOutcome.Connected else ConnectOutcome.Denied
    }

    override suspend fun disconnect() {
        current.value = HealthStatus.NotConnected
    }

    override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> = flowOf(null)

    override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended>? = null

    override fun openSourceSettings(): Boolean = false
}

/** `release.onboardingSetup`, on or off. */
internal fun onboardingSetupFlag(on: Boolean = false): OnboardingSetupEnabled = OnboardingSetupEnabled(releaseFlag("onboardingSetup", on))

/** `release.health`, on or off. */
internal fun healthFlag(on: Boolean = false): HealthReleaseFlag = HealthReleaseFlag(releaseFlag("health", on))

private fun releaseFlag(name: String, on: Boolean) = object : AppConfigMap() {
    override val map: Map<String, *> = mapOf("release" to mapOf(name to on))
}
