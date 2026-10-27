package com.opson.features.onboarding.impl

import com.opson.features.settings.GoalsAndEnergyEnabled
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The fixtures' day, 2026-10-25, in UTC. */
internal object FixedCalendar : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = LocalDate(2026, 10, 25)
}

/** Records every weigh-in onboarding logs. */
internal class FakeWeightRepository(private val fail: Boolean = false) : WeightRepository {
    val added = mutableListOf<Double>()
    private val weighIns = MutableStateFlow<List<WeighIn>>(emptyList())

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

/** `release.onboardingSetup`, on or off. */
internal fun onboardingSetupFlag(on: Boolean = false): OnboardingSetupEnabled = OnboardingSetupEnabled(releaseFlag("onboardingSetup", on))

/** `release.goalsAndEnergy`, on or off. */
internal fun goalsAndEnergyFlag(on: Boolean = false): GoalsAndEnergyEnabled = GoalsAndEnergyEnabled(releaseFlag("goalsAndEnergy", on))

private fun releaseFlag(name: String, on: Boolean) = object : AppConfigMap() {
    override val map: Map<String, *> = mapOf("release" to mapOf(name to on))
}
