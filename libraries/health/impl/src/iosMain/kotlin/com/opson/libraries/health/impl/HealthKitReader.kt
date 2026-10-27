package com.opson.libraries.health.impl

import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierBasalEnergyBurned
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionStrictStartDate
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKStatisticsCollectionQuery
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.HKWorkout
import platform.HealthKit.kilocalorieUnit
import platform.HealthKit.predicateForSamplesWithStartDate
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.time.Instant

/**
 * HealthKit, in Kotlin through `platform.HealthKit`, so there is no Swift half to keep in step.
 * Reads active energy, resting (basal) energy and workouts; never writes.
 *
 * HealthKit never tells an app whether it may read: a refused type simply returns no samples.
 * So [hasAccess] is unknown, [requestAccess] is true once the sheet closes without an error, and
 * a part with no data comes back null. A query error is the same as no data here.
 *
 * Needs the HealthKit entitlement, `NSHealthShareUsageDescription` in Info.plist and
 * HealthKit.framework linked into the app (the Kotlin framework is static).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class HealthKitReader @Inject constructor() : HealthReader {

    override val source: HealthSource = HealthSource.AppleHealth

    private val store: HKHealthStore by lazy { HKHealthStore() }

    private val activeEnergy: HKQuantityType
        get() = requireNotNull(HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned))

    private val restingEnergy: HKQuantityType
        get() = requireNotNull(HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBasalEnergyBurned))

    override suspend fun availability(): HealthAvailability =
        if (HKHealthStore.isHealthDataAvailable()) HealthAvailability.Available else HealthAvailability.Unsupported

    override suspend fun hasAccess(): Boolean? = null

    override suspend fun requestAccess(): Boolean = suspendCancellableCoroutine { cont ->
        store.requestAuthorizationToShareTypes(
            typesToShare = null,
            readTypes = setOf(activeEnergy, restingEnergy, HKObjectType.workoutType()),
        ) { success, error -> if (cont.isActive) cont.resume(success && error == null) }
    }

    override suspend fun read(from: Instant, to: Instant): EnergyExpended {
        val start = from.toNSDate()
        val end = to.toNSDate()
        val active = sum(activeEnergy, start, end)
        return EnergyExpended(
            restingKcal = sum(restingEnergy, start, end),
            activeKcal = active,
            workoutKcal = if (active == null) null else workouts(start, end),
        )
    }

    override suspend fun readDaily(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, EnergyExpended> {
        val resting = dailySums(restingEnergy, from, to, timeZone)
        val active = dailySums(activeEnergy, from, to, timeZone)
        return (resting.keys + active.keys).associateWith { day ->
            EnergyExpended(restingKcal = resting[day], activeKcal = active[day], workoutKcal = null)
        }
    }

    override suspend fun revokeAccess() {
        // HealthKit has no API for it: the person removes access in the Health app.
    }

    override fun openSettings(): Boolean = false

    private suspend fun sum(type: HKQuantityType, start: NSDate, end: NSDate): Double? = suspendCancellableCoroutine { cont ->
        val query = HKStatisticsQuery(
            quantityType = type,
            quantitySamplePredicate = HKQuery.predicateForSamplesWithStartDate(start, end, HKQueryOptionStrictStartDate),
            options = HKStatisticsOptionCumulativeSum,
        ) { _, statistics, _ ->
            if (cont.isActive) cont.resume(statistics?.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit()))
        }
        cont.invokeOnCancellation { store.stopQuery(query) }
        store.executeQuery(query)
    }

    /**
     * One statistics collection, a day per bucket anchored on [from]'s midnight, so a range costs
     * one query rather than one per day. HealthKit steps the buckets in the device calendar, the
     * same zone as [timeZone] on a phone.
     */
    // enumerateStatistics hands its block a `stop` pointer.
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun dailySums(type: HKQuantityType, from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, Double> =
        suspendCancellableCoroutine { cont ->
            val start = from.toNSDate()
            val end = to.toNSDate()
            val query = HKStatisticsCollectionQuery(
                quantityType = type,
                quantitySamplePredicate = HKQuery.predicateForSamplesWithStartDate(start, end, HKQueryOptionStrictStartDate),
                options = HKStatisticsOptionCumulativeSum,
                anchorDate = start,
                intervalComponents = NSDateComponents().apply { day = 1 },
            )
            query.initialResultsHandler = { _, collection, _ ->
                val sums = mutableMapOf<LocalDate, Double>()
                collection?.enumerateStatisticsFromDate(start, toDate = end) { statistics, _ ->
                    val kcal = statistics?.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit())
                    if (statistics != null && kcal != null) sums[statistics.startDate.toInstant().toLocalDateTime(timeZone).date] = kcal
                }
                if (cont.isActive) cont.resume(sums)
            }
            cont.invokeOnCancellation { store.stopQuery(query) }
            store.executeQuery(query)
        }

    /** Active energy inside each workout that started today, from the workout's own statistics. */
    private suspend fun workouts(start: NSDate, end: NSDate): Double = suspendCancellableCoroutine { cont ->
        val query = HKSampleQuery(
            sampleType = HKObjectType.workoutType(),
            predicate = HKQuery.predicateForSamplesWithStartDate(start, end, HKQueryOptionStrictStartDate),
            limit = 0u,
            sortDescriptors = null,
        ) { _, samples, _ ->
            val kcal = samples.orEmpty().filterIsInstance<HKWorkout>().sumOf { workout ->
                workout.statisticsForType(activeEnergy)?.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit()) ?: 0.0
            }
            if (cont.isActive) cont.resume(kcal)
        }
        cont.invokeOnCancellation { store.stopQuery(query) }
        store.executeQuery(query)
    }

    private fun Instant.toNSDate(): NSDate =
        NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble() + nanosecondsOfSecond / NANOS_PER_SECOND)

    private fun NSDate.toInstant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * MILLIS_PER_SECOND).toLong())

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
