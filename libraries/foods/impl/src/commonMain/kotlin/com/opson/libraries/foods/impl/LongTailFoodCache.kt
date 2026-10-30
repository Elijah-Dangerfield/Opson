package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.diary.LoggedFoods
import com.opson.libraries.foods.db.FoodCacheDao
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
 * runs it asks for nothing. A food the server no longer has is not asked for again this run of
 * the app.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@Inject
class LongTailFoodCache(
    private val loggedFoods: LoggedFoods,
    private val api: FoodApi,
    private val cache: FoodCacheDao,
) : UserScopedSyncer {

    private val gone = mutableSetOf<String>()

    override suspend fun sync(): Result<Unit> = Catching {
        val wanted = loggedFoods.observe(LoggedFoods.LONG_TAIL).first()
            .mapNotNull { logged -> logged.latest.foodId?.let { it to logged.lastLoggedAt.toEpochMilliseconds() } }
            .filter { (id, _) -> id !in gone }
        var fetched = 0
        var failedInARow = 0
        val failed = mutableListOf<String>()
        for ((id, lastLoggedAt) in wanted) {
            if (fetched >= PER_SYNC || failedInARow >= FAILURES_TO_STOP) break
            if (cache.get(id) != null) continue
            val food = api.detail(id).getOrNull()?.toDomain()
            if (food == null) {
                failedInARow++
                failed += id
                continue
            }
            failedInARow = 0
            cache.upsert(food.toCacheRow(lastUsedAt = lastLoggedAt, favorite = false))
            fetched++
        }
        // A failure only means the food is gone when the server answered for others in the same run.
        if (fetched > 0) {
            gone += failed
            cache.trim(keep = FoodCatalogImpl.CACHE_LIMIT)
        }
        check(failedInARow < FAILURES_TO_STOP) { "food details unavailable" }
    }

    private companion object {
        const val PER_SYNC = 25
        const val FAILURES_TO_STOP = 3
    }
}
