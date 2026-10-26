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

    data object NotConnected : HealthStatus

    data object Connected : HealthStatus
}

enum class ConnectOutcome { Connected, Denied, NeedsInstall, Unsupported }

/**
 * Reads energy from the platform's health store, on the device. Values never leave it: nothing
 * here writes them to disk, the server, logs or telemetry.
 *
 * Permission is only ever requested by [connect], which a screen calls from a tap. [refresh]
 * re-checks without prompting, so returning from the store's own settings updates [status].
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

    /** Opens the store's own permission screen. False where the platform has none to open. */
    fun openSourceSettings(): Boolean
}
