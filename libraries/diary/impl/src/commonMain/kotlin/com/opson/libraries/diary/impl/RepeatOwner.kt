package com.opson.libraries.diary.impl

import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Whose repeats are on this phone. Rules never sync, so the sign-out dump, which every other
 * diary table can afford because the server gives it back, would lose them for good. They stay
 * through a sign-out instead, and belong to one account at a time: the account that made them.
 *
 * - The same account signing back in finds them as they were.
 * - A different account signing in has them deleted, with their ledger, before it can see or
 *   log one ([settle]). Until someone is signed in, nobody sees or logs any.
 * - Rules from before an owner was recorded are claimed by the first account seen signed in;
 *   a user change before that deletes them as the dump always did ([clear]), since whose they
 *   are is unknown.
 *
 * One owner for the phone rather than one per rule: a different account's sign-in deletes the
 * last one's, so the table never holds two accounts' rules, and a marker needs no migration.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class RepeatOwner(
    private val tables: DiaryTables,
    private val activeUser: ActiveUserSource,
    cacheFactory: CacheFactory,
) : UserScopedClearer {

    private val cache: Cache<Owner> = cacheFactory.persistent(
        name = "repeats_owner",
        serializer = versionedJsonSerializer(defaultValue = { Owner() }),
    )
    private val mutex = Mutex()

    /** The signed-in account once the phone's repeats are its own; null while nobody is signed in. */
    suspend fun current(): String? {
        val userId = (activeUser.user.value as? ActiveUser.SignedIn)?.userId ?: return null
        settle(userId)
        return userId
    }

    /** [current] as it changes, each account settled before it is emitted. */
    fun observe(): Flow<String?> = activeUser.user
        .map { (it as? ActiveUser.SignedIn)?.userId }
        .distinctUntilChanged()
        .onEach { userId -> if (userId != null) settle(userId) }

    /** Makes the phone's repeats [userId]'s, deleting another account's first. */
    suspend fun settle(userId: String): Unit = mutex.withLock {
        val owner = cache.get().userId
        if (owner == userId) return@withLock
        if (owner != null) {
            tables.transactor.inTransaction {
                tables.repeats.deleteAll()
                tables.repeatLogs.deleteAll()
            }
        }
        cache.set(Owner(userId))
    }

    /** A user change: rules with an owner wait for [settle]; rules with none go, as they always did. */
    override suspend fun clear(previousUserId: String): Unit = mutex.withLock {
        if (cache.get().userId == null) tables.repeats.deleteAll()
    }

    @Serializable
    internal data class Owner(val userId: String? = null)
}
