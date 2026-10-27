package com.opson.libraries.health.impl

import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.RestingEnergies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.math.roundToInt

/**
 * [BurnedRepository] from settings, the latest weigh-in and, with `release.health` on and a store
 * connected, the health app. Holds nothing: every value is read while a flow is collected.
 *
 * The health app's resting energy is a whole day's, so it is the most recent full day the store
 * has, within the last week, never today's so far.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ContributesBinding(AppScope::class)
@Inject
class RealBurnedRepository(
    private val settingsRepository: SettingsRepository,
    private val weights: WeightRepository,
    private val health: HealthRepository,
    private val healthFlag: HealthReleaseFlag,
    private val calendar: LocalCalendar,
) : BurnedRepository {

    override fun burned(day: LocalDate): Flow<Burned?> =
        combine(settingsRepository.settings, latestWeightKg(), healthResting(), activity(day)) { settings, kg, healthKcal, activity ->
            val resting = resting(settings, kg, healthKcal) ?: return@combine null
            Burned(
                resting = resting,
                movementKcal = activity?.movementKcal?.wholeKcal(),
                workoutKcal = activity?.workoutKcal?.wholeKcal(),
                activityCounted = settings.activityInBurned,
            )
        }.distinctUntilChanged()

    override fun restingChoices(): Flow<RestingChoices> =
        combine(settingsRepository.settings, latestWeightKg(), healthResting()) { settings, kg, healthKcal ->
            val profile = settings.bodyProfile(kg, calendar.today())
            RestingChoices(
                calculatedKcal = RestingEnergies.calculate(profile),
                missing = profile.missing,
                healthKcal = healthKcal,
                healthSource = if (healthFlag()) health.source else null,
                weightKg = kg,
            )
        }.distinctUntilChanged()

    private fun resting(settings: Settings, kg: Double?, healthKcal: Int?) = RestingEnergies.resolve(
        source = settings.restingSource,
        calculatedKcal = RestingEnergies.calculate(settings.bodyProfile(kg, calendar.today())),
        healthKcal = healthKcal,
        ownKcal = settings.restingOwnKcal,
    )

    private fun latestWeightKg(): Flow<Double?> =
        weights.observeWeighIns().map { weighIns -> weighIns.maxByOrNull { it.measuredAt }?.kg }.distinctUntilChanged()

    private fun connected(): Flow<Boolean> =
        if (healthFlag()) health.status.map { it == HealthStatus.Connected }.distinctUntilChanged() else flowOf(false)

    private fun healthResting(): Flow<Int?> = connected().flatMapLatest { connected ->
        if (!connected) {
            flowOf(null)
        } else {
            flow {
                val today = calendar.today()
                val week = health.energyBetween(today.minus(DatePeriod(days = WHOLE_DAYS_BACK)), today.minus(DatePeriod(days = 1)), calendar.timeZone())
                emit(week?.entries?.sortedByDescending { it.key }?.firstNotNullOfOrNull { it.value.restingKcal?.takeIf { kcal -> kcal > 0 } }?.wholeKcal())
            }
        }
    }

    private fun activity(day: LocalDate): Flow<EnergyExpended?> = connected().flatMapLatest { connected ->
        if (connected) health.energy(day, calendar.timeZone()) else flowOf(null)
    }

    private fun Double.wholeKcal(): Int? = takeIf { it.isFinite() }?.roundToInt()

    private companion object {
        const val WHOLE_DAYS_BACK = 7
    }
}
