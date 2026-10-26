package com.opson.features.settings.impl.integrations

import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate

/**
 * Integrations inputs for previews and `BlindModeLeakTest`, read at noon on Oct 25 2026 like
 * the rest of the settings fixtures. Real energy on purpose (v3: 1,580 resting, 260 movement,
 * 150 workouts, 1,990 in all), so Full has amounts to show and the leak test's control means
 * something. Never builds a `Display`.
 */
object IntegrationsFixtures {

    val today: LocalDate = LocalDate(2026, 10, 25)

    val energy: EnergyExpended = EnergyExpended(restingKcal = 1580.0, activeKcal = 410.0, workoutKcal = 150.0)

    fun inputs(
        status: HealthStatus = HealthStatus.Connected,
        source: HealthSource = HealthSource.HealthConnect,
        energy: EnergyExpended? = this.energy,
    ): IntegrationsInputs = IntegrationsInputs(source = source, status = status, energy = energy, today = today)

    fun state(
        policy: VisibilityPolicy,
        status: HealthStatus = HealthStatus.Connected,
        source: HealthSource = HealthSource.HealthConnect,
        energy: EnergyExpended? = this.energy,
        denied: Boolean = false,
    ): IntegrationsState = IntegrationsStateBuilder.build(inputs(status, source, energy), policy, denied = denied)
}
