package com.opson.libraries.identity.impl.profile

import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Durable outbox for a profile edit made while session-less / offline on a
 * **real account** (a cached `Profile.Authenticated`). The edit applies
 * optimistically and is held here until a session returns, at which point
 * `ProfileRepositoryImpl` flushes it with a single `PATCH /v1/me`. Survives
 * process death so an edit made offline isn't lost on a kill before reconnect.
 *
 * Single-slot + **coalescing**: multiple offline edits collapse into the latest
 * desired state ([enqueue] merges onto whatever's pending), so the flush is one
 * PATCH carrying the user's final choice. Cleared once the server confirms it
 * (or on a terminal validation rejection — that edit can never succeed as-is).
 *
 * The never-had-an-account case does **not** use this: it rides
 * [PendingGuestAccountStore], which the Fallback profile reads its name from.
 */
@SingleIn(AppScope::class)
@Inject
class PendingProfileEditStore(
    cacheFactory: CacheFactory,
) {
    private val cache: Cache<PendingRecord> = cacheFactory.persistent(
        name = "pending_profile_edit",
        serializer = versionedJsonSerializer(defaultValue = { PendingRecord.EMPTY }),
    )

    /** The queued edit, or null if nothing's pending. */
    suspend fun read(): PendingProfileEdit? {
        val record = cache.get()
        return if (!record.pending) null
        else PendingProfileEdit(
            displayName = record.displayName,
        )
    }

    /**
     * Merge a new edit onto whatever's already queued and persist it. A `null`
     * field leaves the prior queued value alone.
     */
    suspend fun enqueue(
        displayName: String?,
    ) {
        val base = read()
        cache.set(
            PendingRecord(
                pending = true,
                displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: base?.displayName,
            ),
        )
    }

    /** Drop the queued edit — called once it's confirmed or terminally rejected. */
    suspend fun clear() {
        cache.set(PendingRecord.EMPTY)
    }

    @Serializable
    internal data class PendingRecord(
        val pending: Boolean = false,
        val displayName: String? = null,
    ) {
        companion object {
            val EMPTY = PendingRecord()
        }
    }
}

/** The desired profile fields a queued offline edit will PATCH on flush. */
data class PendingProfileEdit(
    val displayName: String? = null,
)
