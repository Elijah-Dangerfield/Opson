package com.opson.libraries.health

import com.opson.libraries.nutrition.BodySex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * What the health store holds beyond energy (each workout, weigh-ins, body details and the apps
 * the data came from), and a read on demand. Apple Health and Health Connect are where Garmin, Fitbit, Oura, Whoop,
 * Strava, Peloton and the rest write, so reading more of them is how Opson hears from those apps.
 * Its own interface so the many [HealthRepository] fakes stay as they are.
 *
 * Every read is null while not connected or when the store could not be read, never an empty
 * list standing in for a failure.
 */
interface HealthRecords {
    /**
     * Connected, and some of what Opson reads has not been asked for yet: a newer build reads
     * more than the person was asked about (HealthKit), or they allowed only part of it (Health
     * Connect). Asking again is [HealthRepository.connect].
     */
    val canAskForMore: StateFlow<Boolean>

    /** Workouts that started on [day] in [timeZone], oldest first, re-read every few minutes while collected. */
    fun workouts(day: LocalDate, timeZone: TimeZone): Flow<List<HealthWorkout>?>

    /** The store's height, birth date and sex, each null when it has none. */
    suspend fun profile(): HealthProfile?

    /** Weigh-ins measured from [since] to now, oldest first. */
    suspend fun weighIns(since: Instant): List<HealthWeighIn>?

    /** The apps and devices that wrote energy, workouts or weight in the last few weeks, by name. */
    suspend fun sources(): List<String>?

    /**
     * Reads [day] again now, for pull to refresh, and every screen showing it updates from the
     * same reading. Re-checks the connection first.
     */
    suspend fun refreshNow(day: LocalDate, timeZone: TimeZone): HealthRefresh
}

/** What a pull to refresh found in the health store. */
enum class HealthRefresh {
    /** The reading changed: a workout or more activity came in. */
    NewData,

    /** Read, and the same as before. The watch may not have synced to the store yet. */
    NothingNew,
    NotConnected,
    Failed,
}

/**
 * One workout as the store has it. [title] is the app's own name for it ("Morning Run") where
 * the store keeps one, which Health Connect does and HealthKit does not. [sourceName] is the app
 * or device that recorded it ("Garmin Connect", "Apple Watch").
 */
data class HealthWorkout(
    val id: String,
    val kind: WorkoutKind,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val activeKcal: Double?,
    val sourceName: String?,
)

/** The handful of kinds a workout is shown as. The stores have dozens; the rest are [Other]. */
enum class WorkoutKind {
    Running,
    Walking,
    Hiking,
    Cycling,
    Swimming,
    Strength,
    Hiit,
    Yoga,
    Pilates,
    Rowing,
    Elliptical,
    Stairs,
    Dance,
    MartialArts,
    Sports,
    SnowSports,
    WaterSports,
    Other,
}

/** A weigh-in in the store. [id] is the store's own, stable across reads. */
data class HealthWeighIn(val id: String, val measuredAt: Instant, val kg: Double, val sourceName: String?)

/** Body details the store keeps. Health Connect has no birth date or sex, so those are null there. */
data class HealthProfile(val heightCm: Double?, val birthDate: LocalDate?, val sex: BodySex?) {
    val isEmpty: Boolean get() = heightCm == null && birthDate == null && sex == null
}

/**
 * Weigh-ins from the health store, brought into Opson's weight log when the person turns it on
 * (off by default). A weigh-in brought in joins the log like a typed one, so it syncs to their
 * account; one Opson already has, within a few minutes, is skipped, and one deleted from the log
 * is never brought back.
 */
interface HealthWeighInImport {
    val on: Flow<Boolean>

    /** Turns it on and brings in the last few months at once. How many were added; null when the store could not be read. */
    suspend fun turnOn(): Int?

    suspend fun turnOff()

    /** Brings in what is new since the last time, while on and connected. How many were added; null when nothing ran. */
    suspend fun importNew(): Int?
}
