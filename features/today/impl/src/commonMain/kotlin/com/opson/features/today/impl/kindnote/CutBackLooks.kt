package com.opson.features.today.impl.kindnote

import com.opson.libraries.diary.HardStretch
import com.opson.libraries.opson.AppCache
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * When "Why not cut back?" was opened, for the hard-stretch check ([HardStretch]). Opening it
 * again and again after big days can be a sign of wanting to make up for them. Kept on the phone
 * in `AppData.cutBackLooks`, never synced or sent, and dropped on a user change.
 */
interface CutBackLooks {
    suspend fun record()

    suspend fun recent(): List<Instant>

    companion object {
        /** For tests and previews, and a default where a constructor must not grow a required argument. */
        val None: CutBackLooks = object : CutBackLooks {
            override suspend fun record() = Unit

            override suspend fun recent(): List<Instant> = emptyList()
        }
    }
}

@ContributesBinding(AppScope::class)
@Inject
class AppDataCutBackLooks(private val appCache: AppCache, private val clock: Clock) : CutBackLooks {

    override suspend fun record() {
        val now = clock.now()
        val keepFrom = (now - KEEP).toEpochMilliseconds()
        appCache.update { data ->
            data.copy(cutBackLooks = (data.cutBackLooks.filter { it >= keepFrom } + now.toEpochMilliseconds()).takeLast(MAX_KEPT))
        }
    }

    override suspend fun recent(): List<Instant> = appCache.get().cutBackLooks.map(Instant::fromEpochMilliseconds)

    private companion object {
        /** A little past the check's window, so nothing it reads has gone. */
        val KEEP = (HardStretch.WINDOW_DAYS + 1).days
        const val MAX_KEPT = 30
    }
}
