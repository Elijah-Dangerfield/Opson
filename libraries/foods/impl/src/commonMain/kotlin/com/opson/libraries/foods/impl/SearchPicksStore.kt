package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.foods.LearnedPicks
import com.opson.libraries.foods.SearchPicks
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

/**
 * [SearchPicks] in a file on the phone. What someone searches for is about what they eat, so this
 * has no syncer, logs nothing, and a user change wipes it before the next account's state shows.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = SearchPicks::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class SearchPicksStore(
    cacheFactory: CacheFactory,
    private val clock: Clock,
    appScope: AppCoroutineScope,
) : SearchPicks, UserScopedClearer {

    private val cache: Cache<LearnedPicks> = cacheFactory.persistent(
        name = "search_picks",
        serializer = versionedJsonSerializer(defaultValue = { LearnedPicks() }),
    )
    private val mutex = Mutex()

    override val learned: StateFlow<LearnedPicks> = cache.updates.stateIn(appScope, SharingStarted.Eagerly, LearnedPicks())

    override suspend fun picked(query: String, foodKey: String, weight: Double) {
        mutex.withLock { cache.update { it.picked(query, foodKey, clock.now(), weight) } }
    }

    override suspend fun clear(previousUserId: String) {
        mutex.withLock { cache.set(LearnedPicks()) }
    }
}
