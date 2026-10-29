package com.opson.features.settings.impl.integrations

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate

/**
 * Integrations inputs for previews and `CaloriesHiddenLeakTest`, read at noon on Oct 25 2026 like
 * the rest of the settings fixtures. Real energy on purpose (v3: 1,580 resting, 260 movement,
 * 150 workouts, 1,990 in all), so Full has amounts to show and the leak test's control means
 * something. "Some of it" picked and Sun, Oct 18 left out for a family visit, as v3 draws them.
 * Never builds a `Display`.
 */
object IntegrationsFixtures {

    val today: LocalDate = LocalDate(2026, 10, 25)

    val energy: EnergyExpended = EnergyExpended(restingKcal = 1580.0, activeKcal = 410.0, workoutKcal = 150.0)

    val settings: Settings = Settings(exerciseRange = ExerciseAdjustment.Some)

    val familyVisit: LocalDate = LocalDate(2026, 10, 18)

    /** The window's days as the diary returns them: an ordinary day, and Oct 18 left out with its note. */
    val days: List<Day> = listOf(
        Day.empty(LocalDate(2026, 10, 17)),
        Day.empty(familyVisit).copy(untracked = true, note = "Family visit"),
        Day.empty(today),
    )

    fun inputs(
        status: HealthStatus = HealthStatus.Connected,
        source: HealthSource = HealthSource.HealthConnect,
        energy: EnergyExpended? = this.energy,
        settings: Settings = this.settings,
        days: List<Day> = this.days,
        canAskForMore: Boolean = false,
        sources: List<String>? = null,
        weighInsOn: Boolean = false,
    ): IntegrationsInputs = IntegrationsInputs(
        source = source,
        status = status,
        energy = energy,
        today = today,
        settings = settings,
        days = days,
        canAskForMore = canAskForMore,
        sources = sources,
        weighInsOn = weighInsOn,
    )

    fun state(
        policy: VisibilityPolicy,
        status: HealthStatus = HealthStatus.Connected,
        source: HealthSource = HealthSource.HealthConnect,
        energy: EnergyExpended? = this.energy,
        denied: Boolean = false,
        settings: Settings = this.settings,
        days: List<Day> = this.days,
        canAskForMore: Boolean = false,
        sources: List<String>? = null,
        weighInsOn: Boolean = false,
        failed: Boolean = false,
    ): IntegrationsState = IntegrationsStateBuilder.build(
        inputs(status, source, energy, settings, days, canAskForMore, sources, weighInsOn),
        policy,
        denied = denied,
        failed = failed,
    )

    /** The Leave out a day sheet on October, with the family visit left out. */
    fun leaveOutState(): LeaveOutState =
        LeaveOutStateBuilder.build(LeaveOutStateBuilder.monthOf(today), today, setOf(familyVisit))
}
