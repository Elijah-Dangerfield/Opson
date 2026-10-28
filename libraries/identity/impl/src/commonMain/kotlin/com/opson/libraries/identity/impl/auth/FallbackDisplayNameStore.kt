package com.opson.libraries.identity.impl.auth

import com.opson.libraries.identity.auth.PendingIdentity
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Holds a display name chosen while the device had no session, so the local
 * `Profile.Fallback` can show it instead of a generic name. It never reaches
 * the server.
 *
 * Guest accounts were once created from this record when onboarding finished
 * offline. Nothing creates them any more (accounts are required), so all that
 * is left is the name the Fallback shows. The cache keeps its old name,
 * `pending_guest_account`, so an update reads what an older build wrote.
 *
 * Only [set] writes it, through `ProfileRepository.update`, which no screen
 * calls today: in production this only reads what an older build left.
 */
@SingleIn(AppScope::class)
@Inject
class FallbackDisplayNameStore(
    cacheFactory: CacheFactory,
) {
    private val cache: Cache<PendingRecord> = cacheFactory.persistent(
        name = "pending_guest_account",
        serializer = versionedJsonSerializer(defaultValue = { PendingRecord.EMPTY }),
    )

    /** The held name, or null when none is held. */
    suspend fun read(): PendingIdentity? {
        val record = cache.get()
        return if (!record.pending) null
        else PendingIdentity(
            displayName = record.displayName,
        )
    }

    /** Hold [identity] until a session exists to save it to. */
    suspend fun set(identity: PendingIdentity) {
        cache.set(
            PendingRecord(
                pending = true,
                displayName = identity.displayName,
            ),
        )
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
