package com.opson.libraries.health.impl

import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.nutrition.ExerciseTotals
import com.opson.libraries.diary.totals
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.health.goalReadings
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.HealthActivity
import com.opson.libraries.nutrition.HealthReadings
import com.opson.libraries.nutrition.RestingEnergies
import com.opson.libraries.nutrition.burnedFor
import com.opson.libraries.nutrition.thermicEffectKcal
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
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.math.roundToInt

/**
 * [BurnedRepository] from settings, the latest weigh-in and, with `release.health` on and a store
 * connected, the health app. Everyday activity, and with `release.exercise` on the day's logged
 * exercise, are taken or left by [burnedFor]'s rule so the health app's activity and the manual
 * side never add up: with no health app reading activity, everyday activity is the day's
 * movement whatever `release.exercise` says. With "Count digesting food" on, a tenth of the
 * day's eaten joins it (`thermicEffectKcal`), and only then is the diary read. Holds nothing:
 * every value is read while a flow is collected.
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
    private val exercise: ExerciseRepository,
    private val exerciseRelease: ExerciseRelease,
    private val diary: DiaryRepository,
) : BurnedRepository {

    override fun burned(day: LocalDate): Flow<Burned?> {
        val manual = exerciseRelease.isOn()
        val logged = if (manual) exercise.observeDay(day).map { it.totals() } else flowOf(null)
        val manualSide = combine(logged, digesting(day)) { totals, food -> totals to food }
        return combine(settingsRepository.settings, latestWeightKg(), healthResting(), activity(day), manualSide) { settings, kg, healthKcal, activity, (totals, food) ->
            val resting = resting(settings, kg, healthKcal) ?: return@combine null
            burnedFor(
                resting = resting,
                health = activity?.toHealthActivity(),
                everyday = settings.everydayActivity,
                exercise = totals,
                activityCounted = settings.activityInBurned,
                foodKcal = food,
            )
        }.distinctUntilChanged()
    }

    /**
     * The span in one go: settings, the latest weigh-in and the health app's resting energy once,
     * the health app's activity with one range read (read again when the connection changes, not
     * every few minutes as [burned] does for today), and the diary once for digesting food. Each
     * day is put together exactly as [burned] does it.
     */
    override fun burnedBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Burned>> {
        val dates = generateSequence(from) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= to }.toList()
        if (dates.isEmpty()) return flowOf(emptyMap())
        val logged: Flow<Map<LocalDate, ExerciseTotals>?> = if (exerciseRelease.isOn()) {
            combine(dates.map { day -> exercise.observeDay(day).map { day to it.totals() } }) { it.toMap() }
        } else {
            flowOf(null)
        }
        val digesting: Flow<Map<LocalDate, Int>?> =
            settingsRepository.settings.map { it.thermicEffectInBurned }.distinctUntilChanged().flatMapLatest { on ->
                if (on) {
                    diary.observeDays(from, to).map { days -> days.associate { it.date to thermicEffectKcal(it.totals().kcal.known) } }.distinctUntilChanged()
                } else {
                    flowOf(null)
                }
            }
        val activity: Flow<Map<LocalDate, EnergyExpended>?> = connected().flatMapLatest { connected ->
            if (connected) flow { emit(health.energyBetween(from, to, calendar.timeZone())) } else flowOf(null)
        }
        val manualSide = combine(logged, digesting) { totals, food -> totals to food }
        return combine(settingsRepository.settings, latestWeightKg(), healthResting(), activity, manualSide) { settings, kg, healthKcal, activity, (totals, food) ->
            val resting = resting(settings, kg, healthKcal) ?: return@combine emptyMap()
            dates.associateWith { day ->
                burnedFor(
                    resting = resting,
                    health = activity?.get(day)?.toHealthActivity(),
                    everyday = settings.everydayActivity,
                    exercise = totals?.get(day),
                    activityCounted = settings.activityInBurned,
                    // A day with nothing in the diary digested nothing, as [burned] reads it.
                    foodKcal = food?.let { it[day] ?: 0 },
                )
            }
        }.distinctUntilChanged()
    }

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

    /**
     * One read of the last [BurnedRepository.HEALTH_DAYS] whole days: the most recent resting
     * energy, as [healthResting] picks it, and each day's movement plus workouts the store read.
     */
    override fun healthReadings(): Flow<HealthReadings> = connected().flatMapLatest { connected ->
        if (!connected) {
            flowOf(HealthReadings.None)
        } else {
            flow { emit(health.goalReadings(calendar.today(), calendar.timeZone())) }
        }
    }.distinctUntilChanged()

    private fun resting(settings: Settings, kg: Double?, healthKcal: Int?) = RestingEnergies.resolve(
        source = settings.restingSource,
        calculatedKcal = RestingEnergies.calculate(settings.bodyProfile(kg, calendar.today())),
        healthKcal = healthKcal,
        ownKcal = settings.restingOwnKcal,
    )

    /** The thermic effect of [day]'s food while "Count digesting food" is on; null, and the diary unread, while it is off. */
    private fun digesting(day: LocalDate): Flow<Int?> =
        settingsRepository.settings.map { it.thermicEffectInBurned }.distinctUntilChanged().flatMapLatest { on ->
            if (on) diary.observeDay(day).map { thermicEffectKcal(it.totals().kcal.known) }.distinctUntilChanged() else flowOf(null)
        }

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

    /** Null when the store read neither part, so the day falls back to everyday activity and logged exercise. */
    private fun EnergyExpended.toHealthActivity(): HealthActivity? {
        val movement = movementKcal?.wholeKcal()
        val workouts = workoutKcal?.wholeKcal()
        return if (movement == null && workouts == null) null else HealthActivity(movement, workouts)
    }

    private fun Double.wholeKcal(): Int? = takeIf { it.isFinite() }?.roundToInt()

    private companion object {
        const val WHOLE_DAYS_BACK = 7
    }
}
