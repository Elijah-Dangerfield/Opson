package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.diary.LoggedFoods
import com.opson.libraries.foods.db.FoodCacheDao
import com.opson.libraries.networking.retry.isTransientNetworkFailure
import com.opson.libraries.opson.UserScopedSyncer
import kotlinx.coroutines.flow.first
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Puts the catalog foods of the person's long tail ([LoggedFoods]) on the phone, so a food they
 * logged on another device, or before this install, opens and logs at any amount offline too.
 * The diary already has each one's last amount; this adds what the amount sheet needs to change
 * it (per 100 g and portions).
 *
 * A few foods each sync, newest first, and only the ones the phone lacks, so after the first
 * runs it asks for nothing. A food the server says it no longer has (404 or 410), or refuses for
 * good (another 4xx, a body that isn't a food), is not asked for again this run of the app. That
 * is an answer, not trouble: it neither fails the sync nor logs at error, which would reach
 * Sentry and hold back a rating ask. Only a transport failure (offline, a timeout, a 5xx) three
 * times in a row stops the run and fails it, for the coordinator to retry.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@Inject
class LongTailFoodCache(
    private val loggedFoods: LoggedFoods,
    private val api: FoodApi,
    private val cache: FoodCacheDao,
) : UserScopedSyncer {

    private val logger = KLog.withTag("LongTailFoodCache")
    private val gone = mutableSetOf<String>()

    override suspend fun sync(): Result<Unit> = Catching {
        val wanted = loggedFoods.observe(LoggedFoods.LONG_TAIL).first()
            .mapNotNull { logged -> logged.latest.foodId?.let { it to logged.lastLoggedAt.toEpochMilliseconds() } }
            .filter { (id, _) -> id !in gone }
        var fetched = 0
        var failedInARow = 0
        var lastFailure: Throwable? = null
        for ((id, lastLoggedAt) in wanted) {
            if (fetched >= PER_SYNC || failedInARow >= FAILURES_TO_STOP) break
            if (cache.get(id) != null) continue
            val answer = api.detailOrGone(id)
            val failure = answer.exceptionOrNull()
            if (failure != null && isTransientNetworkFailure(failure)) {
                failedInARow++
                lastFailure = failure
                continue
            }
            failedInARow = 0
            val food = answer.getOrNull()?.toDomain()
            if (food == null) {
                gone += id
                if (failure == null) {
                    logger.i { "long-tail food is gone from the server" }
                } else {
                    logger.w(failure) { "long-tail food refused, not asked for again this run" }
                }
                continue
            }
            cache.upsert(food.toCacheRow(lastUsedAt = lastLoggedAt, favorite = false))
            fetched++
        }
        if (fetched > 0) cache.trim(keep = FoodCatalogImpl.CACHE_LIMIT)
        if (failedInARow >= FAILURES_TO_STOP) throw checkNotNull(lastFailure)
    }

    private companion object {
        const val PER_SYNC = 25
        const val FAILURES_TO_STOP = 3
    }
}
