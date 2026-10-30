package com.opson.libraries.health.impl

import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.health.WorkoutKind
import com.opson.libraries.nutrition.BodySex
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateComponentUndefined
import platform.Foundation.NSError
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.HealthKit.HKAuthorizationRequestStatusShouldRequest
import platform.HealthKit.HKAuthorizationRequestStatusUnnecessary
import platform.HealthKit.HKBiologicalSexFemale
import platform.HealthKit.HKBiologicalSexMale
import platform.HealthKit.HKBiologicalSexOther
import platform.HealthKit.HKCharacteristicType
import platform.HealthKit.HKCharacteristicTypeIdentifierBiologicalSex
import platform.HealthKit.HKCharacteristicTypeIdentifierDateOfBirth
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKMetricPrefixKilo
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKObserverQuery
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierBasalEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierBodyMass
import platform.HealthKit.HKQuantityTypeIdentifierHeight
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionStrictStartDate
import platform.HealthKit.HKSample
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKSampleSortIdentifierEndDate
import platform.HealthKit.HKSampleSortIdentifierStartDate
import platform.HealthKit.HKSampleType
import platform.HealthKit.HKSource
import platform.HealthKit.HKSourceQuery
import platform.HealthKit.HKStatisticsCollectionQuery
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.HKWorkout
import platform.HealthKit.HKWorkoutActivityType
import platform.HealthKit.HKWorkoutActivityTypeBarre
import platform.HealthKit.HKWorkoutActivityTypeBaseball
import platform.HealthKit.HKWorkoutActivityTypeBasketball
import platform.HealthKit.HKWorkoutActivityTypeBoxing
import platform.HealthKit.HKWorkoutActivityTypeCardioDance
import platform.HealthKit.HKWorkoutActivityTypeClimbing
import platform.HealthKit.HKWorkoutActivityTypeCooldown
import platform.HealthKit.HKWorkoutActivityTypeCoreTraining
import platform.HealthKit.HKWorkoutActivityTypeCrossCountrySkiing
import platform.HealthKit.HKWorkoutActivityTypeCrossTraining
import platform.HealthKit.HKWorkoutActivityTypeCycling
import platform.HealthKit.HKWorkoutActivityTypeDanceInspiredTraining
import platform.HealthKit.HKWorkoutActivityTypeDownhillSkiing
import platform.HealthKit.HKWorkoutActivityTypeElliptical
import platform.HealthKit.HKWorkoutActivityTypeFlexibility
import platform.HealthKit.HKWorkoutActivityTypeFunctionalStrengthTraining
import platform.HealthKit.HKWorkoutActivityTypeGolf
import platform.HealthKit.HKWorkoutActivityTypeHandCycling
import platform.HealthKit.HKWorkoutActivityTypeHighIntensityIntervalTraining
import platform.HealthKit.HKWorkoutActivityTypeHiking
import platform.HealthKit.HKWorkoutActivityTypeHockey
import platform.HealthKit.HKWorkoutActivityTypeKickboxing
import platform.HealthKit.HKWorkoutActivityTypeMartialArts
import platform.HealthKit.HKWorkoutActivityTypeMindAndBody
import platform.HealthKit.HKWorkoutActivityTypeMixedCardio
import platform.HealthKit.HKWorkoutActivityTypePaddleSports
import platform.HealthKit.HKWorkoutActivityTypePickleball
import platform.HealthKit.HKWorkoutActivityTypePilates
import platform.HealthKit.HKWorkoutActivityTypeRacquetball
import platform.HealthKit.HKWorkoutActivityTypeRowing
import platform.HealthKit.HKWorkoutActivityTypeRugby
import platform.HealthKit.HKWorkoutActivityTypeRunning
import platform.HealthKit.HKWorkoutActivityTypeSailing
import platform.HealthKit.HKWorkoutActivityTypeSkatingSports
import platform.HealthKit.HKWorkoutActivityTypeSnowSports
import platform.HealthKit.HKWorkoutActivityTypeSnowboarding
import platform.HealthKit.HKWorkoutActivityTypeSoccer
import platform.HealthKit.HKWorkoutActivityTypeSocialDance
import platform.HealthKit.HKWorkoutActivityTypeSoftball
import platform.HealthKit.HKWorkoutActivityTypeSquash
import platform.HealthKit.HKWorkoutActivityTypeStairClimbing
import platform.HealthKit.HKWorkoutActivityTypeStairs
import platform.HealthKit.HKWorkoutActivityTypeStepTraining
import platform.HealthKit.HKWorkoutActivityTypeSurfingSports
import platform.HealthKit.HKWorkoutActivityTypeSwimming
import platform.HealthKit.HKWorkoutActivityTypeTableTennis
import platform.HealthKit.HKWorkoutActivityTypeTennis
import platform.HealthKit.HKWorkoutActivityTypeTraditionalStrengthTraining
import platform.HealthKit.HKWorkoutActivityTypeVolleyball
import platform.HealthKit.HKWorkoutActivityTypeWalking
import platform.HealthKit.HKWorkoutActivityTypeWaterFitness
import platform.HealthKit.HKWorkoutActivityTypeWaterPolo
import platform.HealthKit.HKWorkoutActivityTypeWaterSports
import platform.HealthKit.HKWorkoutActivityTypeWheelchairRunPace
import platform.HealthKit.HKWorkoutActivityTypeWheelchairWalkPace
import platform.HealthKit.HKWorkoutActivityTypeWrestling
import platform.HealthKit.HKWorkoutActivityTypeYoga
import platform.HealthKit.gramUnitWithMetricPrefix
import platform.HealthKit.countUnit
import platform.HealthKit.kilocalorieUnit
import platform.HealthKit.meterUnit
import platform.HealthKit.predicateForSamplesWithStartDate
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * HealthKit, in Kotlin through `platform.HealthKit`, so there is no Swift half to keep in step.
 * Reads active and resting energy, steps, workouts, weight, height, date of birth and biological
 * sex; never writes.
 *
 * HealthKit never tells an app whether it may read: a refused type simply returns no samples.
 * So [access] answers from `getRequestStatusForAuthorization`, which only says whether the
 * sheet has been shown for these types ([HealthAccess.All] once it has for everything,
 * [HealthAccess.Partly] when it has for energy but not yet for what a newer build reads), and a
 * read that finds nothing is empty, never an error.
 *
 * The store is built on the main thread, like every HealthKit and UIKit object here
 * (docs/plans/0.2/README.md), and kept. Needs the HealthKit entitlement in both entitlements
 * files, `NSHealthShareUsageDescription` in Info.plist and HealthKit.framework linked into the
 * app (the Kotlin framework is static).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class HealthKitReader @Inject constructor() : HealthReader {

    override val source: HealthSource = HealthSource.AppleHealth

    private val storeLock = Mutex()
    private var built: HKHealthStore? = null

    private suspend fun store(): HKHealthStore = storeLock.withLock {
        built ?: withContext(Dispatchers.Main) { HKHealthStore() }.also { built = it }
    }

    private val activeEnergy: HKQuantityType
        get() = requireNotNull(HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned))

    private val restingEnergy: HKQuantityType
        get() = requireNotNull(HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBasalEnergyBurned))

    private val bodyMass: HKQuantityType
        get() = requireNotNull(HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass))

    private val height: HKQuantityType
        get() = requireNotNull(HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeight))

    private val stepCount: HKQuantityType
        get() = requireNotNull(HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount))

    /** What Opson read before 0.2's workouts, weight and profile: what Expended and Burned need. */
    private val energyTypes: Set<HKObjectType>
        get() = setOf(activeEnergy, restingEnergy, HKObjectType.workoutType())

    /** Everything Opson reads. Info.plist's NSHealthShareUsageDescription names each of them. */
    private val readTypes: Set<HKObjectType>
        get() = energyTypes + setOfNotNull(
            stepCount,
            bodyMass,
            height,
            HKCharacteristicType.characteristicTypeForIdentifier(HKCharacteristicTypeIdentifierDateOfBirth),
            HKCharacteristicType.characteristicTypeForIdentifier(HKCharacteristicTypeIdentifierBiologicalSex),
        )

    override suspend fun availability(): HealthAvailability =
        if (HKHealthStore.isHealthDataAvailable()) HealthAvailability.Available else HealthAvailability.Unsupported

    override suspend fun access(): HealthAccess = when {
        requestStatus(readTypes) == HKAuthorizationRequestStatusUnnecessary -> HealthAccess.All
        requestStatus(energyTypes) == HKAuthorizationRequestStatusUnnecessary -> HealthAccess.Partly
        else -> HealthAccess.NotAsked
    }

    /** Whether the sheet still has anything to ask about [types]: never what was answered. Throws on a store error. */
    private suspend fun requestStatus(types: Set<HKObjectType>): Long {
        val store = store()
        return suspendCancellableCoroutine { cont ->
            store.getRequestStatusForAuthorizationToShareTypes(typesToShare = emptySet<HKSampleType>(), readTypes = types) { status, error ->
                if (!cont.isActive) return@getRequestStatusForAuthorizationToShareTypes
                if (error != null) cont.resumeWithException(HealthKitException("request status", error)) else cont.resume(status)
            }
        }.also { check(it == HKAuthorizationRequestStatusUnnecessary || it == HKAuthorizationRequestStatusShouldRequest) { "Unknown request status" } }
    }

    override suspend fun requestAccess(): Boolean {
        val store = store()
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                store.requestAuthorizationToShareTypes(typesToShare = null, readTypes = readTypes) { success, error ->
                    if (!cont.isActive) return@requestAuthorizationToShareTypes
                    // Missing entitlement, a type the build can't ask for: an error, not a refusal.
                    if (error != null) cont.resumeWithException(HealthKitException("request authorization", error)) else cont.resume(success)
                }
            }
        }
    }

    override suspend fun read(from: Instant, to: Instant): EnergyExpended {
        val start = from.toNSDate()
        val end = to.toNSDate()
        val active = sum(activeEnergy, start, end)
        return EnergyExpended(
            restingKcal = sum(restingEnergy, start, end),
            activeKcal = active,
            workoutKcal = if (active == null) null else workoutSamples(start, end).sumOf { it.kcal() ?: 0.0 },
        )
    }

    override suspend fun readDaily(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, EnergyExpended> {
        val resting = dailySums(restingEnergy, HKUnit.kilocalorieUnit(), from, to, timeZone)
        val active = dailySums(activeEnergy, HKUnit.kilocalorieUnit(), from, to, timeZone)
        return (resting.keys + active.keys).associateWith { day ->
            EnergyExpended(restingKcal = resting[day], activeKcal = active[day], workoutKcal = null)
        }
    }

    /**
     * A statistics collection's cumulative sum is how the Health app itself totals steps: where an
     * iPhone and an Apple Watch both counted the same walk, it keeps the source higher in the
     * person's Data Sources order for that stretch rather than adding the two.
     */
    override suspend fun dailySteps(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, Int> =
        dailySums(stepCount, HKUnit.countUnit(), from, to, timeZone).mapValues { (_, steps) -> steps.roundToInt() }

    override suspend fun workouts(from: Instant, to: Instant): List<HealthWorkout> =
        workoutSamples(from.toNSDate(), to.toNSDate()).map { workout ->
            HealthWorkout(
                id = workout.UUID.UUIDString,
                kind = workoutKind(workout.workoutActivityType),
                title = null,
                start = workout.startDate.toInstant(),
                end = workout.endDate.toInstant(),
                activeKcal = workout.kcal(),
                sourceName = workout.sourceRevision.source.displayName(),
            )
        }

    override suspend fun weighIns(from: Instant, to: Instant): List<HealthWeighIn> {
        val kilograms = HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo)
        return samples(bodyMass, from.toNSDate(), to.toNSDate(), limit = MAX_WEIGH_INS).filterIsInstance<HKQuantitySample>().map { sample ->
            HealthWeighIn(
                id = sample.UUID.UUIDString,
                measuredAt = sample.startDate.toInstant(),
                kg = sample.quantity.doubleValueForUnit(kilograms),
                sourceName = sample.sourceRevision.source.displayName(),
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun profile(): HealthProfile {
        val store = store()
        val latestHeight = latest(height) as? HKQuantitySample
        // A characteristic the person hasn't set, or reading isn't allowed: nil, with the error left unread.
        val birth = store.dateOfBirthComponentsWithError(null)
        val sex = store.biologicalSexWithError(null)?.biologicalSex
        return HealthProfile(
            heightCm = latestHeight?.quantity?.doubleValueForUnit(HKUnit.meterUnit())?.times(CM_PER_M),
            birthDate = birth?.toLocalDate(),
            sex = when (sex) {
                HKBiologicalSexFemale -> BodySex.Female
                HKBiologicalSexMale -> BodySex.Male
                HKBiologicalSexOther -> BodySex.Unsaid
                else -> null
            },
        )
    }

    override suspend fun sources(from: Instant, to: Instant): Set<String> {
        val start = from.toNSDate()
        val end = to.toNSDate()
        return listOf(activeEnergy, stepCount, HKObjectType.workoutType(), bodyMass).flatMap { sourcesOf(it, start, end) }.toSet()
    }

    /**
     * An observer query on workouts, active energy and steps while collected: the repository collects it
     * only while the app is in front, so there is no background delivery and no entitlement for it.
     */
    override fun changes(): Flow<Unit> = callbackFlow {
        val store = store()
        val queries = listOf<HKSampleType>(HKObjectType.workoutType(), activeEnergy, stepCount).map { type ->
            HKObserverQuery(sampleType = type, predicate = null) { _, completion, error ->
                if (error == null) trySend(Unit)
                completion?.invoke()
            }
        }
        queries.forEach(store::executeQuery)
        awaitClose { queries.forEach(store::stopQuery) }
    }

    override suspend fun revokeAccess() {
        // HealthKit has no API for it: the person removes access in the Health app.
    }

    /** The Health app. There is no link to Opson's own page in it, so the copy says where to go from there. */
    override fun openSettings(): Boolean {
        val url = NSURL.URLWithString(HEALTH_APP_URL) ?: return false
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
        return true
    }

    private suspend fun sum(type: HKQuantityType, start: NSDate, end: NSDate): Double? {
        val store = store()
        return suspendCancellableCoroutine { cont ->
            val query = HKStatisticsQuery(
                quantityType = type,
                quantitySamplePredicate = HKQuery.predicateForSamplesWithStartDate(start, end, HKQueryOptionStrictStartDate),
                options = HKStatisticsOptionCumulativeSum,
            ) { _, statistics, error ->
                if (!cont.isActive) return@HKStatisticsQuery
                when {
                    statistics != null -> cont.resume(statistics.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit()))
                    // "No data" is how HealthKit answers a sum over nothing, and a refused read looks the same.
                    error == null || error.isNoData() -> cont.resume(null)
                    else -> cont.resumeWithException(HealthKitException("sum", error))
                }
            }
            cont.invokeOnCancellation { store.stopQuery(query) }
            store.executeQuery(query)
        }
    }

    /**
     * One statistics collection, a day per bucket anchored on [from]'s midnight, so a range costs
     * one query rather than one per day. HealthKit steps the buckets in the device calendar, the
     * same zone as [timeZone] on a phone.
     */
    // enumerateStatistics hands its block a `stop` pointer.
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun dailySums(type: HKQuantityType, unit: HKUnit, from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, Double> {
        val store = store()
        return suspendCancellableCoroutine { cont ->
            val start = from.toNSDate()
            val end = to.toNSDate()
            val query = HKStatisticsCollectionQuery(
                quantityType = type,
                quantitySamplePredicate = HKQuery.predicateForSamplesWithStartDate(start, end, HKQueryOptionStrictStartDate),
                options = HKStatisticsOptionCumulativeSum,
                anchorDate = start,
                intervalComponents = NSDateComponents().apply { day = 1 },
            )
            query.initialResultsHandler = { _, collection, error ->
                if (cont.isActive) {
                    if (collection == null && error != null && !error.isNoData()) {
                        cont.resumeWithException(HealthKitException("daily sums", error))
                    } else {
                        val sums = mutableMapOf<LocalDate, Double>()
                        collection?.enumerateStatisticsFromDate(start, toDate = end) { statistics, _ ->
                            val sum = statistics?.sumQuantity()?.doubleValueForUnit(unit)
                            if (statistics != null && sum != null) sums[statistics.startDate.toInstant().toLocalDateTime(timeZone).date] = sum
                        }
                        cont.resume(sums)
                    }
                }
            }
            cont.invokeOnCancellation { store.stopQuery(query) }
            store.executeQuery(query)
        }
    }

    private suspend fun workoutSamples(start: NSDate, end: NSDate): List<HKWorkout> =
        samples(HKObjectType.workoutType(), start, end, limit = 0).filterIsInstance<HKWorkout>()

    /** Samples of [type] that started between [start] and [end], oldest first. [limit] 0 is all of them. */
    private suspend fun samples(type: HKSampleType, start: NSDate, end: NSDate, limit: Int): List<HKSample> {
        val store = store()
        return suspendCancellableCoroutine { cont ->
            val query = HKSampleQuery(
                sampleType = type,
                predicate = HKQuery.predicateForSamplesWithStartDate(start, end, HKQueryOptionStrictStartDate),
                limit = limit.toULong(),
                sortDescriptors = listOf(NSSortDescriptor.sortDescriptorWithKey(HKSampleSortIdentifierStartDate, ascending = true)),
            ) { _, samples, error ->
                if (!cont.isActive) return@HKSampleQuery
                if (samples == null && error != null && !error.isNoData()) {
                    cont.resumeWithException(HealthKitException("samples", error))
                } else {
                    cont.resume(samples.orEmpty().filterIsInstance<HKSample>())
                }
            }
            cont.invokeOnCancellation { store.stopQuery(query) }
            store.executeQuery(query)
        }
    }

    /** The most recent sample of [type], whenever it was. */
    private suspend fun latest(type: HKSampleType): HKSample? {
        val store = store()
        return suspendCancellableCoroutine { cont ->
            val query = HKSampleQuery(
                sampleType = type,
                predicate = null,
                limit = 1u,
                sortDescriptors = listOf(NSSortDescriptor.sortDescriptorWithKey(HKSampleSortIdentifierEndDate, ascending = false)),
            ) { _, samples, error ->
                if (!cont.isActive) return@HKSampleQuery
                if (samples == null && error != null && !error.isNoData()) {
                    cont.resumeWithException(HealthKitException("latest", error))
                } else {
                    cont.resume(samples?.firstOrNull() as? HKSample)
                }
            }
            cont.invokeOnCancellation { store.stopQuery(query) }
            store.executeQuery(query)
        }
    }

    private suspend fun sourcesOf(type: HKSampleType, start: NSDate, end: NSDate): List<String> {
        val store = store()
        return suspendCancellableCoroutine { cont ->
            val query = HKSourceQuery(
                sampleType = type,
                samplePredicate = HKQuery.predicateForSamplesWithStartDate(start, end, HKQueryOptionStrictStartDate),
            ) { _, sources, error ->
                if (!cont.isActive) return@HKSourceQuery
                if (sources == null && error != null && !error.isNoData()) {
                    cont.resumeWithException(HealthKitException("sources", error))
                } else {
                    cont.resume(sources.orEmpty().filterIsInstance<HKSource>().mapNotNull { it.displayName() })
                }
            }
            cont.invokeOnCancellation { store.stopQuery(query) }
            store.executeQuery(query)
        }
    }

    /** Active energy inside the workout, from its own statistics. */
    private fun HKWorkout.kcal(): Double? = statisticsForType(activeEnergy)?.sumQuantity()?.doubleValueForUnit(HKUnit.kilocalorieUnit())

    private fun Instant.toNSDate(): NSDate =
        NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble() + nanosecondsOfSecond / NANOS_PER_SECOND)

    private fun NSDate.toInstant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * MILLIS_PER_SECOND).toLong())

    private fun NSDateComponents.toLocalDate(): LocalDate? {
        if (year == NSDateComponentUndefined || month == NSDateComponentUndefined || day == NSDateComponentUndefined) return null
        return runCatchingDate { LocalDate(year.toInt(), month.toInt(), day.toInt()) }
    }

    private inline fun runCatchingDate(block: () -> LocalDate): LocalDate? = try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    }

    /** HKErrorNoData: a query over nothing, or over a type the person did not allow. */
    private fun NSError.isNoData(): Boolean = domain == HK_ERROR_DOMAIN && code == HK_ERROR_NO_DATA

    private class HealthKitException(what: String, error: NSError) :
        IllegalStateException("HealthKit $what failed: ${error.domain} ${error.code} ${error.localizedDescription}")

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val MILLIS_PER_SECOND = 1_000.0
        private const val CM_PER_M = 100.0
        private const val MAX_WEIGH_INS = 1000
        private const val HEALTH_APP_URL = "x-apple-health://"
        private const val HK_ERROR_DOMAIN = "com.apple.healthkit"
        private const val HK_ERROR_NO_DATA = 11L

        /**
         * What "Data from" calls a source. The phone's and the watch's own data come from Apple's
         * health services under the device's name ("Elijah's Apple Watch"), which says less than
         * the kind of device.
         */
        internal fun sourceName(name: String, bundleIdentifier: String): String? = when {
            name.isBlank() -> null
            !bundleIdentifier.startsWith("com.apple.health") -> name
            name.contains("Watch", ignoreCase = true) -> "Apple Watch"
            name.contains("iPhone", ignoreCase = true) -> "iPhone"
            else -> name
        }

        private fun HKSource.displayName(): String? = sourceName(name, bundleIdentifier)

        /** HealthKit's activity types as the kinds Opson shows. */
        internal fun workoutKind(type: HKWorkoutActivityType): WorkoutKind = when (type) {
            HKWorkoutActivityTypeRunning, HKWorkoutActivityTypeWheelchairRunPace -> WorkoutKind.Running
            HKWorkoutActivityTypeWalking, HKWorkoutActivityTypeWheelchairWalkPace -> WorkoutKind.Walking
            HKWorkoutActivityTypeHiking -> WorkoutKind.Hiking
            HKWorkoutActivityTypeCycling, HKWorkoutActivityTypeHandCycling -> WorkoutKind.Cycling
            HKWorkoutActivityTypeSwimming, HKWorkoutActivityTypeWaterFitness -> WorkoutKind.Swimming
            HKWorkoutActivityTypeTraditionalStrengthTraining,
            HKWorkoutActivityTypeFunctionalStrengthTraining,
            HKWorkoutActivityTypeCoreTraining,
            -> WorkoutKind.Strength
            HKWorkoutActivityTypeHighIntensityIntervalTraining, HKWorkoutActivityTypeCrossTraining, HKWorkoutActivityTypeMixedCardio -> WorkoutKind.Hiit
            HKWorkoutActivityTypeYoga, HKWorkoutActivityTypeFlexibility, HKWorkoutActivityTypeMindAndBody, HKWorkoutActivityTypeCooldown -> WorkoutKind.Yoga
            HKWorkoutActivityTypePilates, HKWorkoutActivityTypeBarre -> WorkoutKind.Pilates
            HKWorkoutActivityTypeRowing -> WorkoutKind.Rowing
            HKWorkoutActivityTypeElliptical -> WorkoutKind.Elliptical
            HKWorkoutActivityTypeStairClimbing, HKWorkoutActivityTypeStairs, HKWorkoutActivityTypeStepTraining -> WorkoutKind.Stairs
            HKWorkoutActivityTypeCardioDance, HKWorkoutActivityTypeSocialDance, HKWorkoutActivityTypeDanceInspiredTraining -> WorkoutKind.Dance
            HKWorkoutActivityTypeMartialArts, HKWorkoutActivityTypeBoxing, HKWorkoutActivityTypeKickboxing, HKWorkoutActivityTypeWrestling -> WorkoutKind.MartialArts
            HKWorkoutActivityTypeDownhillSkiing,
            HKWorkoutActivityTypeCrossCountrySkiing,
            HKWorkoutActivityTypeSnowboarding,
            HKWorkoutActivityTypeSnowSports,
            HKWorkoutActivityTypeSkatingSports,
            -> WorkoutKind.SnowSports
            HKWorkoutActivityTypePaddleSports,
            HKWorkoutActivityTypeSurfingSports,
            HKWorkoutActivityTypeSailing,
            HKWorkoutActivityTypeWaterPolo,
            HKWorkoutActivityTypeWaterSports,
            -> WorkoutKind.WaterSports
            HKWorkoutActivityTypeBaseball,
            HKWorkoutActivityTypeBasketball,
            HKWorkoutActivityTypeGolf,
            HKWorkoutActivityTypeHockey,
            HKWorkoutActivityTypePickleball,
            HKWorkoutActivityTypeRacquetball,
            HKWorkoutActivityTypeRugby,
            HKWorkoutActivityTypeSoccer,
            HKWorkoutActivityTypeSoftball,
            HKWorkoutActivityTypeSquash,
            HKWorkoutActivityTypeTableTennis,
            HKWorkoutActivityTypeTennis,
            HKWorkoutActivityTypeVolleyball,
            HKWorkoutActivityTypeClimbing,
            -> WorkoutKind.Sports
            else -> WorkoutKind.Other
        }
    }
}
