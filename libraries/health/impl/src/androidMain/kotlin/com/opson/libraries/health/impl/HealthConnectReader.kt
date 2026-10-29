package com.opson.libraries.health.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.health.WorkoutKind
import com.opson.libraries.opson.ActivityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.reflect.KClass
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import java.time.Instant as JavaInstant
import java.time.LocalDateTime as JavaLocalDateTime
import java.time.Period

/**
 * Health Connect. Reads total and active energy, exercise sessions, weight and height; nothing
 * else is asked for and nothing is ever written. The permissions here and in the module's
 * manifest must match: Health Connect refuses to show a permission the manifest does not declare.
 *
 * Health Connect lets someone allow part of what is asked, and reading a type that is not
 * allowed throws, so every read first checks what is granted and leaves out the rest.
 *
 * Total energy is Health Connect's own aggregate, which fills hours with no data from the basal
 * rate, so resting is total minus active rather than a separate read.
 */
// java.time is API 26, and lint can't see that nothing here reads before `availability()` has
// said Android 9 or later (Health Connect doesn't exist below it), so the check is waived here.
@SuppressLint("NewApi")
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class HealthConnectReader(
    private val context: Context,
    private val activityProvider: ActivityProvider,
) : HealthReader {

    override val source: HealthSource = HealthSource.HealthConnect

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun availability(): HealthAvailability {
        // Health Connect does not exist before Android 9, and connect-client's minSdk is 26.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return HealthAvailability.Unsupported
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NeedsInstall
            else -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // 9 to 13 take the Health Connect app from Play; 14 and up have it built in.
                HealthAvailability.NeedsInstall
            } else {
                HealthAvailability.Unsupported
            }
        }
    }

    private suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions() intersect PERMISSIONS

    override suspend fun access(): HealthAccess {
        val granted = granted()
        return when {
            granted.isEmpty() -> HealthAccess.NotAsked
            granted.containsAll(PERMISSIONS) -> HealthAccess.All
            else -> HealthAccess.Partly
        }
    }

    override suspend fun requestAccess(): Boolean {
        val before = granted()
        if (before.containsAll(PERMISSIONS)) return true
        val activity = activityProvider.currentActivity() as? ComponentActivity
            ?: error("No foreground activity to ask for Health Connect permission from")
        val result = withContext(Dispatchers.Main) { launchRequest(activity) }
        return (before + result).any { it in PERMISSIONS }
    }

    /**
     * The unowned `register`, because this runs long after the activity started and the
     * lifecycle-owned one refuses that. Unregistered as soon as the result arrives or the caller
     * goes away, so nothing outlives one request.
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun launchRequest(activity: ComponentActivity): Set<String> = suspendCancellableCoroutine { cont ->
        lateinit var launcher: ActivityResultLauncher<Set<String>>
        launcher = activity.activityResultRegistry.register(
            "opson.health.${Uuid.random()}",
            PermissionController.createRequestPermissionResultContract(),
        ) { result ->
            launcher.unregister()
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation { activity.runOnUiThread { launcher.unregister() } }
        launcher.launch(PERMISSIONS)
    }

    override suspend fun read(from: Instant, to: Instant): EnergyExpended {
        val granted = granted()
        val start = from.toJava()
        val end = to.toJava()
        val range = TimeRangeFilter.between(start, end)
        val metrics = buildSet {
            if (TotalCaloriesBurnedRecord::class.allowedIn(granted)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (ActiveCaloriesBurnedRecord::class.allowedIn(granted)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        val totals = if (metrics.isEmpty()) null else client.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = range))
        val total = totals?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories
        val active = totals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories
        val workouts = if (active == null || !ExerciseSessionRecord::class.allowedIn(granted)) {
            null
        } else {
            sessions(range).sumOf { activeKcal(maxOf(it.startTime, start), minOf(it.endTime, end)) ?: 0.0 }
        }
        val resting = total?.let { (it - (active ?: 0.0)).coerceAtLeast(0.0) }
        return EnergyExpended(restingKcal = resting, activeKcal = active, workoutKcal = workouts)
    }

    /** Health Connect slices by local time itself, so the range goes in as local date-times in [timeZone]. */
    override suspend fun readDaily(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, EnergyExpended> {
        val granted = granted()
        val metrics = buildSet {
            if (TotalCaloriesBurnedRecord::class.allowedIn(granted)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (ActiveCaloriesBurnedRecord::class.allowedIn(granted)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        if (metrics.isEmpty()) return emptyMap()
        val days = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = metrics,
                timeRangeFilter = TimeRangeFilter.between(from.toJavaLocal(timeZone), to.toJavaLocal(timeZone)),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        return days.associate { slice ->
            val total = slice.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
            val active = slice.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            val resting = total?.let { (it - (active ?: 0.0)).coerceAtLeast(0.0) }
            slice.startTime.toLocalDate().toKotlinLocalDate() to EnergyExpended(restingKcal = resting, activeKcal = active, workoutKcal = null)
        }
    }

    override suspend fun workouts(from: Instant, to: Instant): List<HealthWorkout> {
        val granted = granted()
        if (!ExerciseSessionRecord::class.allowedIn(granted)) return emptyList()
        val withEnergy = ActiveCaloriesBurnedRecord::class.allowedIn(granted)
        return sessions(TimeRangeFilter.between(from.toJava(), to.toJava()))
            .filter { it.startTime >= from.toJava() }
            .map { session ->
                HealthWorkout(
                    id = session.metadata.id,
                    kind = workoutKind(session.exerciseType),
                    title = session.title?.trim()?.takeIf(String::isNotEmpty),
                    start = session.startTime.toKotlin(),
                    end = session.endTime.toKotlin(),
                    activeKcal = if (withEnergy) activeKcal(session.startTime, session.endTime) else null,
                    sourceName = appName(session.metadata.dataOrigin),
                )
            }
    }

    override suspend fun weighIns(from: Instant, to: Instant): List<HealthWeighIn> {
        if (!WeightRecord::class.allowedIn(granted())) return emptyList()
        return readAll(WeightRecord::class, TimeRangeFilter.between(from.toJava(), to.toJava())).map { record ->
            HealthWeighIn(
                id = record.metadata.id,
                measuredAt = record.time.toKotlin(),
                kg = record.weight.inKilograms,
                sourceName = appName(record.metadata.dataOrigin),
            )
        }
    }

    /** Health Connect keeps height, not a birth date or sex. */
    override suspend fun profile(): HealthProfile {
        if (!HeightRecord::class.allowedIn(granted())) return HealthProfile(null, null, null)
        val latest = client.readRecords(
            ReadRecordsRequest(
                HeightRecord::class,
                TimeRangeFilter.before(JavaInstant.now()),
                ascendingOrder = false,
                pageSize = 1,
            ),
        ).records.firstOrNull()
        return HealthProfile(heightCm = latest?.height?.inMeters?.times(CM_PER_M), birthDate = null, sex = null)
    }

    override suspend fun sources(from: Instant, to: Instant): Set<String> {
        val granted = granted()
        val range = TimeRangeFilter.between(from.toJava(), to.toJava())
        val origins = mutableSetOf<DataOrigin>()
        val metrics = buildSet {
            if (TotalCaloriesBurnedRecord::class.allowedIn(granted)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (ActiveCaloriesBurnedRecord::class.allowedIn(granted)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        // The aggregate says who contributed without reading every record.
        if (metrics.isNotEmpty()) origins += client.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = range)).dataOrigins
        if (ExerciseSessionRecord::class.allowedIn(granted)) origins += sessions(range).map { it.metadata.dataOrigin }
        if (WeightRecord::class.allowedIn(granted)) origins += readAll(WeightRecord::class, range).map { it.metadata.dataOrigin }
        return origins.mapNotNull(::appName).toSet()
    }

    /**
     * Nothing: Health Connect has no push, only a changes token to poll, and the repository
     * already re-reads every time the app comes forward, which is when a token would be checked.
     */
    override fun changes(): Flow<Unit> = emptyFlow()

    override suspend fun revokeAccess() {
        client.permissionController.revokeAllPermissions()
    }

    override fun openSettings(): Boolean {
        val intent = HealthConnectClient.getHealthConnectManageDataIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }

    private suspend fun sessions(range: TimeRangeFilter): List<ExerciseSessionRecord> = readAll(ExerciseSessionRecord::class, range)

    /** Every page, up to [MAX_PAGES]: a range this short never needs more. */
    private suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val records = mutableListOf<T>()
        var token: String? = null
        var pages = 0
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, pageToken = token))
            records += response.records
            token = response.pageToken
            pages++
        } while (token != null && pages < MAX_PAGES)
        return records
    }

    private suspend fun activeKcal(start: JavaInstant, end: JavaInstant): Double? {
        if (end <= start) return null
        return client.aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
    }

    /**
     * The app's own label. Health apps are visible to Opson through the manifest's `<queries>`
     * (they declare Health Connect's rationale intent); one that is not still gets a name from
     * [KNOWN_APPS], and anything else is left out rather than shown as a package name.
     */
    private fun appName(origin: DataOrigin): String? {
        val pkg = origin.packageName
        if (pkg.isBlank() || pkg == context.packageName) return null
        val label = try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return label?.takeIf { it.isNotBlank() && it != pkg } ?: KNOWN_APPS[pkg]
    }

    private fun KClass<out Record>.allowedIn(granted: Set<String>): Boolean = HealthPermission.getReadPermission(this) in granted

    private fun Instant.toJava(): JavaInstant = JavaInstant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

    private fun JavaInstant.toKotlin(): Instant = Instant.fromEpochSeconds(epochSecond, nano)

    private fun Instant.toJavaLocal(timeZone: TimeZone): JavaLocalDateTime = toLocalDateTime(timeZone).toJavaLocalDateTime()

    companion object {
        /** Mirrors the `uses-permission` entries in this module's AndroidManifest.xml. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
        )

        private const val CM_PER_M = 100.0
        private const val MAX_PAGES = 5

        /** Names for the usual writers when the package manager can't see them. */
        private val KNOWN_APPS: Map<String, String> = mapOf(
            "com.fitbit.FitbitMobile" to "Fitbit",
            "com.google.android.apps.fitness" to "Google Fit",
            "com.google.android.apps.healthdata" to "Health Connect",
            "com.sec.android.app.shealth" to "Samsung Health",
            "com.garmin.android.apps.connectmobile" to "Garmin Connect",
            "com.ouraring.oura" to "Oura",
            "com.whoop.android" to "WHOOP",
            "com.strava" to "Strava",
            "com.onepeloton.callisto" to "Peloton",
            "com.myfitnesspal.android" to "MyFitnessPal",
            "com.withings.wiscale2" to "Withings",
            "fi.polar.polarflow" to "Polar Flow",
            "com.coros.coros" to "COROS",
            "com.huami.watch.hmwatchmanager" to "Zepp",
            "com.suunto.china" to "Suunto",
            "com.xiaomi.wearable" to "Mi Fitness",
        )

        /** Health Connect's exercise types as the kinds Opson shows. */
        fun workoutKind(type: Int): WorkoutKind = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> WorkoutKind.Running
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR -> WorkoutKind.Walking
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING, ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING -> WorkoutKind.Hiking
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> WorkoutKind.Cycling
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> WorkoutKind.Swimming
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
            -> WorkoutKind.Strength
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> WorkoutKind.Hiit
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> WorkoutKind.Yoga
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> WorkoutKind.Pilates
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> WorkoutKind.Rowing
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> WorkoutKind.Elliptical
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING, ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> WorkoutKind.Stairs
            ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> WorkoutKind.Dance
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS, ExerciseSessionRecord.EXERCISE_TYPE_BOXING, ExerciseSessionRecord.EXERCISE_TYPE_FENCING -> WorkoutKind.MartialArts
            ExerciseSessionRecord.EXERCISE_TYPE_SKIING, ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING, ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING -> WorkoutKind.SnowSports
            ExerciseSessionRecord.EXERCISE_TYPE_PADDLING,
            ExerciseSessionRecord.EXERCISE_TYPE_SURFING,
            ExerciseSessionRecord.EXERCISE_TYPE_SAILING,
            ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO,
            ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING,
            -> WorkoutKind.WaterSports
            ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON,
            ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_CRICKET,
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN,
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN,
            ExerciseSessionRecord.EXERCISE_TYPE_GOLF,
            ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY,
            ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY,
            ExerciseSessionRecord.EXERCISE_TYPE_RUGBY,
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER,
            ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_SQUASH,
            ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS,
            ExerciseSessionRecord.EXERCISE_TYPE_TENNIS,
            ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC,
            -> WorkoutKind.Sports
            else -> WorkoutKind.Other
        }
    }
}
