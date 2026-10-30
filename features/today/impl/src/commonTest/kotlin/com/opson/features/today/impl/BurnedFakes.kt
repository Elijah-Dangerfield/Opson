package com.opson.features.today.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.Burned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Burned for every day, as a test sets it; null until one is set. [unusable] is what the
 * Resting energy page says is missing, which Today reads only while Burned is null.
 */
class FakeBurnedRepository(burned: Burned? = null, unusable: Set<BodyDetail> = emptySet()) : BurnedRepository {
    val burnedFlow = MutableStateFlow(burned)
    val unusableFlow = MutableStateFlow(unusable)

    override fun burned(day: LocalDate): Flow<Burned?> = burnedFlow

    override fun restingChoices(): Flow<RestingChoices> = unusableFlow.map { unusable ->
        RestingChoices(calculatedKcal = null, missing = unusable, healthKcal = null, healthSource = null, weightKg = null)
    }
}

/** `release.goalsAndEnergy`, on or off. */
fun goalsAndEnergyFlag(on: Boolean = false): GoalsAndEnergyEnabled = GoalsAndEnergyEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("goalsAndEnergy" to on))
    },
)
