package com.opson.libraries.prompts.impl

import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** What one prompt has done with one account on this phone. Epoch ms; 0 is never. */
@Serializable
data class PromptRecord(
    val shows: Int = 0,
    val dismissals: Int = 0,
    val accepted: Boolean = false,
    val lastShownAtMs: Long = 0L,
)

/** One account's prompts on this phone. Epoch ms; 0 is never. */
@Serializable
data class AccountLedger(
    /** When this account first reached Today here: the quiet start counts from it. */
    val firstSeenAtMs: Long = 0L,
    /** The last time any prompt showed: the gap between prompts counts from it. */
    val lastPromptAtMs: Long = 0L,
    val prompts: Map<String, PromptRecord> = emptyMap(),
) {
    fun record(id: String): PromptRecord = prompts[id] ?: PromptRecord()

    fun with(id: String, change: (PromptRecord) -> PromptRecord): AccountLedger = copy(prompts = prompts + (id to change(record(id))))
}

@Serializable
data class PromptLedger(val accounts: Map<String, AccountLedger> = emptyMap())

/** Where the counts live. One interface so the processor's tests can hold them in memory. */
interface PromptLedgerStore {
    suspend fun account(userId: String): AccountLedger

    suspend fun update(userId: String, change: (AccountLedger) -> AccountLedger): AccountLedger
}

/**
 * The counts on this phone, per account and never synced: a second phone may offer a prompt once
 * more, which is cheaper than an outbox row and a server table for every Not now.
 *
 * Keyed by user id rather than wiped on a user change (it is no `UserScopedClearer`), because
 * "never more than once" has to survive signing out and back in. It holds ids and counts, nothing
 * the person wrote.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class CachedPromptLedgerStore(cacheFactory: CacheFactory) : PromptLedgerStore {
    private val cache: Cache<PromptLedger> = cacheFactory.persistent(
        name = "prompt_ledger",
        serializer = versionedJsonSerializer(defaultValue = { PromptLedger() }),
    )

    override suspend fun account(userId: String): AccountLedger = cache.get().accounts[userId] ?: AccountLedger()

    override suspend fun update(userId: String, change: (AccountLedger) -> AccountLedger): AccountLedger {
        var next = AccountLedger()
        cache.update { ledger ->
            next = change(ledger.accounts[userId] ?: AccountLedger())
            ledger.copy(accounts = ledger.accounts + (userId to next))
        }
        return next
    }
}
