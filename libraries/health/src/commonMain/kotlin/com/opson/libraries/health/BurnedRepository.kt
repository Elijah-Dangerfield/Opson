package com.opson.libraries.health

import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.HealthReadings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

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

    /**
     * Each day's Burned from [from] to [to], both included, the same as [burned] gives for that
     * day: Trends' on-track calendar and running total. A day is missing when [burned] would be
     * null for it. Past days use today's resting energy, as [burned] does for them, since
     * resting energy comes from the latest weigh-in and today's age.
     *
     * The default asks [burned] once per day, which is right for a fake; the real repository
     * reads the health app once for the whole span.
     */
    fun burnedBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Burned>> {
        val dates = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= to }.toList()
        if (dates.isEmpty()) return flowOf(emptyMap())
        return combine(dates.map { day -> burned(day).map { day to it } }) { days ->
            days.mapNotNull { (day, burned) -> burned?.let { day to it } }.toMap()
        }
    }

    /** What each resting energy source would give right now, for the Resting energy page. */
    fun restingChoices(): Flow<RestingChoices>

    /**
     * What a goal's typical day reads from the health app (docs/plans/goals-v2.md): its most recent
     * whole day's resting energy, and each of the last [HEALTH_DAYS] whole days' activity it read.
     * [HealthReadings.None] without a connected store, which is also the default for fakes.
     */
    fun healthReadings(): Flow<HealthReadings> = flowOf(HealthReadings.None)

    companion object {
        /** The whole days a goal's health activity averages: the last week, never today so far. */
        const val HEALTH_DAYS = 7
    }
}

/**
 * The Resting energy page's numbers. [calculatedKcal] is null while [missing] is not empty;
 * [healthKcal] is the store's most recent whole day, null without one (or `release.health` off).
 * [weightKg] is the latest weigh-in the calculation uses. [unusable] is [missing] plus any detail
 * given but outside what the equation takes: what Today names when Burned can't be worked out.
 */
data class RestingChoices(
    val calculatedKcal: Int?,
    val missing: Set<BodyDetail>,
    val healthKcal: Int?,
    val healthSource: HealthSource?,
    val weightKg: Double?,
    val unusable: Set<BodyDetail> = missing,
)
