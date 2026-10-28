package com.opson.libraries.identity.impl.auth

import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** The account whose session expired while this device kept its local data. */
data class ExpiredAccount(val userId: String, val wasAnonymous: Boolean)

/**
 * Remembers, across launches, whose data the device is still holding after a
 * session expiry. An expired session is not a change of user: the person is
 * asked to sign in again, and when the same account comes back its unsynced
 * meals are still in the outbox. Only once someone else signs in is the held
 * data wiped (see `SupabaseAuthRepositoryImpl.emitLocked`). The expired screen's
 * sign-out leaves it, since that tap is how the person gets to sign in again.
 */
@SingleIn(AppScope::class)
@Inject
class ExpiredAccountStore(
    cacheFactory: CacheFactory,
) {
    private val cache: Cache<Record> = cacheFactory.persistent(
        name = "expired_session_account",
        serializer = versionedJsonSerializer(defaultValue = { Record() }),
    )

    suspend fun read(): ExpiredAccount? {
        val record = cache.get()
        val userId = record.userId ?: return null
        return ExpiredAccount(userId = userId, wasAnonymous = record.wasAnonymous)
    }

    suspend fun hold(account: ExpiredAccount) {
        cache.set(Record(userId = account.userId, wasAnonymous = account.wasAnonymous))
    }

    suspend fun release() {
        cache.set(Record())
    }

    @Serializable
    internal data class Record(
        val userId: String? = null,
        val wasAnonymous: Boolean = false,
    )
}
