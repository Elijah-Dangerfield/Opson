package com.opson.libraries.health.impl

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import com.opson.libraries.opson.ActivityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import java.time.Instant as JavaInstant
import java.time.LocalDateTime as JavaLocalDateTime
import java.time.Period

/**
 * Health Connect. Reads total and active energy and exercise sessions; nothing else is asked
 * for and nothing is ever written. The permissions here and in the module's manifest must
 * match: Health Connect refuses to show a permission the manifest does not declare.
 *
 * Total energy is Health Connect's own aggregate, which fills hours with no data from the basal
 * rate, so resting is total minus active rather than a separate read.
 */
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

    override suspend fun hasAccess(): Boolean = client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    override suspend fun requestAccess(): Boolean {
        if (hasAccess()) return true
        val activity = activityProvider.currentActivity() as? ComponentActivity
            ?: error("No foreground activity to ask for Health Connect permission from")
        val granted = withContext(Dispatchers.Main) { launchRequest(activity) }
        return granted.containsAll(PERMISSIONS)
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
        val start = from.toJava()
        val end = to.toJava()
        val range = TimeRangeFilter.between(start, end)
        val totals = client.aggregate(
            AggregateRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = range,
            ),
        )
        val total = totals[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
        val active = totals[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        val sessions = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records
        val workouts = if (sessions.isEmpty()) {
            0.0
        } else {
            sessions.sumOf { session ->
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(maxOf(session.startTime, start), minOf(session.endTime, end)),
                    ),
                )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            }
        }
        val resting = total?.let { (it - (active ?: 0.0)).coerceAtLeast(0.0) }
        return EnergyExpended(restingKcal = resting, activeKcal = active, workoutKcal = workouts.takeIf { active != null })
    }

    /** Health Connect slices by local time itself, so the range goes in as local date-times in [timeZone]. */
    override suspend fun readDaily(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, EnergyExpended> {
        val days = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
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

    override suspend fun revokeAccess() {
        client.permissionController.revokeAllPermissions()
    }

    override fun openSettings(): Boolean {
        val intent = HealthConnectClient.getHealthConnectManageDataIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }

    private fun Instant.toJava(): JavaInstant = JavaInstant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

    private fun Instant.toJavaLocal(timeZone: TimeZone): JavaLocalDateTime = toLocalDateTime(timeZone).toJavaLocalDateTime()

    companion object {
        /** Mirrors the `uses-permission` entries in this module's AndroidManifest.xml. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )
    }
}
