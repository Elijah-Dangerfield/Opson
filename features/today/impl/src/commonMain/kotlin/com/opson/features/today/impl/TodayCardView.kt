package com.opson.features.today.impl

import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * How the summary card was left on this phone: the page it was swiped to and whether it is
 * collapsed. A null was never set here, and the builder falls back to what synced Settings held
 * before this moved, so nobody's choice is lost on the update.
 */
@Serializable
data class TodayCardView(val page: Int? = null, val collapsed: Boolean? = null)

/**
 * Keeps [TodayCardView] on the device. It used to live in synced Settings, where a swipe queued the
 * whole settings object: a second phone that had not pulled yet pushed its stale copy, and the
 * server's shallow merge put back the mode the first phone had just left (calories showing again
 * after a switch to No numbers). View state never syncs.
 */
@SingleIn(AppScope::class)
@Inject
class TodayCardViewStore(cacheFactory: CacheFactory) {
    private val cache: Cache<TodayCardView> = cacheFactory.persistent(
        name = "today_card_view",
        serializer = versionedJsonSerializer(defaultValue = { TodayCardView() }),
    )

    val view: Flow<TodayCardView> get() = cache.updates

    suspend fun update(change: (TodayCardView) -> TodayCardView) {
        cache.update(change)
    }
}
