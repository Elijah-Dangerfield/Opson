package com.opson.libraries.opson.impl

import com.opson.libraries.core.logging.KLog
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Reference [UserScopedSyncer] — the registration recipe every real syncer
 * copies:
 *
 * ```kotlin
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class, boundType = MyRepository::class)
 * @ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
 * @Inject
 * class MyRepositoryImpl(...) : MyRepository, UserScopedSyncer {
 *     override suspend fun sync(): Result<Unit> = runCatching {
 *         // one idempotent pull/reconcile of this store's server state
 *     }
 * }
 * ```
 *
 * That's the whole contract: implement one idempotent [sync], contribute to
 * the multibinding, and [UserScopedSyncCoordinator] owns *when* it runs
 * (account became active, warm foreground, connectivity regained — with
 * exponential retry). Delete this stub once your first real syncer exists.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@Inject
class ExampleUserScopedSyncer : UserScopedSyncer {
    override suspend fun sync(): Result<Unit> {
        KLog.withTag("ExampleSyncer").d { "sync() — replace me with a real user-scoped store" }
        return Result.success(Unit)
    }
}
