package com.opson.features.trends.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.Burned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/** Burned for every day, as a test sets it; null until one is set. */
class FakeBurnedRepository(burned: Burned? = null) : BurnedRepository {
    val burnedFlow = MutableStateFlow(burned)

    override fun burned(day: LocalDate): Flow<Burned?> = burnedFlow

    override fun restingChoices(): Flow<RestingChoices> = error("not read here")
}

/** `release.goalsAndEnergy`, on or off. */
fun goalsAndEnergyFlag(on: Boolean = false): GoalsAndEnergyEnabled = GoalsAndEnergyEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("goalsAndEnergy" to on))
    },
)
