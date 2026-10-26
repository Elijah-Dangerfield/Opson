package com.opson.libraries.health.impl

import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import kotlin.time.Instant

/**
 * The platform seam under [RealHealthRepository]: HealthKit on iOS, Health Connect on Android.
 * Everything that decides anything lives in the repository, so this stays a thin translation of
 * the store's API and the repository is tested against a fake of it.
 */
interface HealthReader {
    val source: HealthSource

    suspend fun availability(): HealthAvailability

    /** Whether read access is granted. Null where the platform will not say (HealthKit, by design). */
    suspend fun hasAccess(): Boolean?

    /**
     * Shows the platform's permission prompt. True when access was granted, or, on iOS, when the
     * prompt finished without an error, since HealthKit never reveals a refusal to read.
     */
    suspend fun requestAccess(): Boolean

    /** Energy between [from] and [to]. Throws when the store cannot be read. */
    suspend fun read(from: Instant, to: Instant): EnergyExpended

    /** Gives back what [requestAccess] got, where the platform allows it (Health Connect). */
    suspend fun revokeAccess()

    /** Opens the store's own permission screen. False where there is none to open. */
    fun openSettings(): Boolean
}

enum class HealthAvailability { Available, NeedsInstall, Unsupported }
