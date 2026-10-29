package com.opson.libraries.health.impl

import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWorkout
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The platform seam under [RealHealthRepository]: HealthKit on iOS, Health Connect on Android.
 * Everything that decides anything lives in the repository, so this stays a thin translation of
 * the store's API and the repository is tested against a fake of it.
 *
 * A read throws when the store cannot be read; a store with nothing in it answers empty.
 */
interface HealthReader {
    val source: HealthSource

    suspend fun availability(): HealthAvailability

    /** How far Opson has been let in, as far as the platform will say. Never prompts. */
    suspend fun access(): HealthAccess

    /**
     * Shows the platform's permission prompt. True when anything was allowed (Health Connect),
     * or, on iOS, when the sheet finished, since HealthKit never reveals a refusal to read.
     * Throws when the store answers with an error instead.
     */
    suspend fun requestAccess(): Boolean

    /** Energy between [from] and [to]. */
    suspend fun read(from: Instant, to: Instant): EnergyExpended

    /**
     * Resting and active energy per local day in [timeZone], from [from] (a midnight) to [to], in
     * one bucketed query rather than a read per day. Workouts are not read. Days with nothing
     * recorded may be missing.
     */
    suspend fun readDaily(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, EnergyExpended>

    /** Workouts that started between [from] and [to], oldest first. */
    suspend fun workouts(from: Instant, to: Instant): List<HealthWorkout>

    /** Weigh-ins measured between [from] and [to], oldest first. */
    suspend fun weighIns(from: Instant, to: Instant): List<HealthWeighIn>

    /** The latest height, and the birth date and sex where the store keeps them. */
    suspend fun profile(): HealthProfile

    /** Names of the apps and devices that wrote energy, workouts or weight between [from] and [to]. */
    suspend fun sources(from: Instant, to: Instant): Set<String>

    /**
     * Fires when the store gets new energy or workouts, for as long as it is collected (HealthKit's
     * observer query). Never ends on its own; empty where the platform has no such thing.
     */
    fun changes(): Flow<Unit>

    /** Gives back what [requestAccess] got, where the platform allows it (Health Connect). */
    suspend fun revokeAccess()

    /** Opens the store's own app or permission screen. False where there is none to open. */
    fun openSettings(): Boolean
}

enum class HealthAvailability { Available, NeedsInstall, Unsupported }

/**
 * What the store says about Opson's access. Neither store tells a refusal from never asking, so
 * [NotAsked] covers both.
 */
enum class HealthAccess {
    /** Nothing allowed (Health Connect), or the sheet never shown (HealthKit). */
    NotAsked,

    /**
     * Some of it: part allowed (Health Connect), or the sheet shown for energy and not yet for
     * what a newer build reads (HealthKit). Enough to read energy, with more to ask for.
     */
    Partly,

    /** Everything allowed (Health Connect), or the sheet shown for everything Opson reads (HealthKit). */
    All,

    /** The store would not say. */
    Unknown,
}
