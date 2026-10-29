package com.opson.features.today.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** The day the late-day card ([LowLoggingNudge]) was last put away with Not now, as `LocalDate.toString()`. */
@Serializable
data class TodayNudges(val lowLoggingDismissedOn: String? = null)

/**
 * Keeps [TodayNudges] on this phone. A card put away is put away for the day it showed on; it
 * never syncs, so it costs no outbox row and needs nothing from the server, and the worst a second
 * phone does is offer it once more. A user change wipes it with the rest of the person's view.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class TodayNudgeStore(cacheFactory: CacheFactory) : UserScopedClearer {
    private val cache: Cache<TodayNudges> = cacheFactory.persistent(
        name = "today_nudges",
        serializer = versionedJsonSerializer(defaultValue = { TodayNudges() }),
    )

    /** The day the card was put away, or null. */
    val lowLoggingDismissedOn: Flow<LocalDate?>
        get() = cache.updates.map { nudges -> nudges.lowLoggingDismissedOn?.let { Catching { LocalDate.parse(it) }.getOrNull() } }

    suspend fun dismissLowLogging(on: LocalDate) {
        cache.update { it.copy(lowLoggingDismissedOn = on.toString()) }
    }

    override suspend fun clear(previousUserId: String) {
        cache.clear()
    }
}
