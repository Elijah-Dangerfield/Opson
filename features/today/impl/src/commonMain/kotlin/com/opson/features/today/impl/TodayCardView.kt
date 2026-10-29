package com.opson.features.today.impl

import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * How the summary card was left on this phone: the page it was swiped to. A null was never set
 * here, and the builder falls back to what synced Settings held before this moved, so nobody's
 * choice is lost on the update. It also held whether the card was collapsed, until the collapse
 * went (2026-10-29); a stored value is ignored on read.
 */
@Serializable
data class TodayCardView(val page: Int? = null)

/**
 * Keeps [TodayCardView] on the device. It used to live in synced Settings, where a swipe queued the
 * whole settings object: a second phone that had not pulled yet pushed its stale copy, and the
 * server's shallow merge put back the mode the first phone had just left (calories showing again
 * after a switch to No numbers). View state never syncs.
 *
 * It is still the signed-in person's view, so a user change wipes it: the next account opens on its
 * own synced choice, not the last one's page.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class TodayCardViewStore(cacheFactory: CacheFactory) : UserScopedClearer {
    private val cache: Cache<TodayCardView> = cacheFactory.persistent(
        name = "today_card_view",
        serializer = versionedJsonSerializer(defaultValue = { TodayCardView() }),
    )

    val view: Flow<TodayCardView> get() = cache.updates

    suspend fun update(change: (TodayCardView) -> TodayCardView) {
        cache.update(change)
    }

    override suspend fun clear(previousUserId: String) {
        cache.clear()
    }
}
