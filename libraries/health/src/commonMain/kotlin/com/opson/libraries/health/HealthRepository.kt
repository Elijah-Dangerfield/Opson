package com.opson.libraries.health

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The platform's own health store. One per platform: Opson reads from it and nothing else. */
enum class HealthSource(val key: String) {
    AppleHealth("apple_health"),
    HealthConnect("health_connect"),
}

/**
 * Energy expended over a day so far, in kcal. A null part was not available from the store
 * (no permission for it, or nothing recorded), which is different from a recorded zero.
 *
 * [activeKcal] is everything above resting, workouts included; [workoutKcal] is the part of it
 * that fell inside a workout.
 */
data class EnergyExpended(
    val restingKcal: Double?,
    val activeKcal: Double?,
    val workoutKcal: Double?,
) {
    /** Active energy outside workouts. Never below zero, even when the store's sums disagree. */
    val movementKcal: Double?
        get() = activeKcal?.let { (it - (workoutKcal ?: 0.0)).coerceAtLeast(0.0) }

    /** Resting plus active: what "Expended so far" says. Null only when the store had neither. */
    val totalKcal: Double?
        get() = if (restingKcal == null && activeKcal == null) null else (restingKcal ?: 0.0) + (activeKcal ?: 0.0)
}

/** Whether energy can be read right now. Checking it never asks the person for anything. */
sealed interface HealthStatus {
    /** This device has no health store: Android before 9, or an iPad without Health. */
    data object Unsupported : HealthStatus

    /** Health Connect is missing or out of date (Android 9 to 13). */
    data object NeedsInstall : HealthStatus

    /** Never asked, nothing allowed (Health Connect), or turned off in Opson with Disconnect. */
    data object NotConnected : HealthStatus

    /**
     * Opson may read. On iOS that means the permission sheet has been shown for what Opson reads
     * (or the person connected before): HealthKit never says whether reading was allowed, so a
     * refusal there looks like a store with no data.
     */
    data object Connected : HealthStatus
}

/**
 * What Connect came to. [Failed] is the store answering with an error rather than a choice (a
 * build without the HealthKit entitlement, Health Connect unbound): nothing was asked, so it is
 * not [Denied].
 */
enum class ConnectOutcome { Connected, Denied, NeedsInstall, Unsupported, Failed }

/**
 * Reads energy from the platform's health store, on the device. Values never leave it: nothing
 * here writes them to disk, the server, logs or telemetry.
 *
 * Permission is only ever requested by [connect], which a screen calls from a tap. [refresh]
 * re-checks without prompting, so returning from the store's own settings updates [status]; the
 * app also refreshes on every foreground.
 *
 * [disconnect] is Opson's own switch: it stops reading. Health Connect also gives the permission
 * back; HealthKit has no way for an app to do that, so on iOS the person removes it in Health.
 */
interface HealthRepository {
    val source: HealthSource

    val status: StateFlow<HealthStatus>

    suspend fun refresh()

    suspend fun connect(): ConnectOutcome

    suspend fun disconnect()

    /**
     * [day] from midnight in [timeZone] up to now, re-read every few minutes while collected.
     * Null while not connected, or when the read failed: a failed read is never a zero.
     */
    fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?>

    /**
     * Each day from [from] to [to], both included, midnight to midnight in [timeZone] (today up to
     * now), read once: Trends' daily average. A day the store has nothing for is missing, and
     * [EnergyExpended.workoutKcal] is not read (null). Null while not connected, or when the read
     * failed, never an empty week standing in for one.
     */
    suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended>?

    /** Opens the store's own app or permission screen: Health Connect's, or the Health app. False where there is none. */
    fun openSourceSettings(): Boolean
}
