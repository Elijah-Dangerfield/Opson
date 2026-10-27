package com.opson.libraries.health

import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.Burned
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * What the person burns (design sync 4): resting energy for the whole day from the source they
 * picked, plus the activity their health app has synced so far when they count it. The one
 * question Targets, Energy today and the summary card ask; a set deficit or surplus is aimed at
 * [Burned.totalKcal] through `Targets.rangeFor(date, preset, burnKcal)`.
 *
 * Nothing is read until a flow is collected, and the health app only with `release.health` on
 * and a store connected.
 */
interface BurnedRepository {
    /**
     * [day]'s Burned, updated as activity syncs and as settings or weigh-ins change. Null when no
     * source can say what resting energy is: no details for the calculation, no health app
     * reading and nothing entered.
     */
    fun burned(day: LocalDate): Flow<Burned?>

    /** What each resting energy source would give right now, for the Resting energy page. */
    fun restingChoices(): Flow<RestingChoices>
}

/**
 * The Resting energy page's numbers. [calculatedKcal] is null while [missing] is not empty;
 * [healthKcal] is the store's most recent whole day, null without one (or `release.health` off).
 * [weightKg] is the latest weigh-in the calculation uses.
 */
data class RestingChoices(
    val calculatedKcal: Int?,
    val missing: Set<BodyDetail>,
    val healthKcal: Int?,
    val healthSource: HealthSource?,
    val weightKg: Double?,
)
