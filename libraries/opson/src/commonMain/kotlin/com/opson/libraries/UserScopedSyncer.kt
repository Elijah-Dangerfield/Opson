package com.opson.libraries.opson

/**
 * A user-scoped store whose server state should be (re)reconciled whenever an
 * account is active — on becoming active (sign-in, cold-boot session resolve,
 * switch, guest claim), on a warm resume, and on connectivity returning.
 *
 * Implementers contribute themselves to the [UserScopedSyncer] multibinding and
 * declare nothing else; `UserScopedSyncCoordinator` (in `:libraries:opson:impl`)
 * owns the when (one level-based `runWhen` loop per syncer, with retry). [sync]
 * must be idempotent — the coordinator retries failures and re-fires on every
 * trigger edge. `DiarySyncer` is the real one; the registration is:
 *
 * ```kotlin
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class, boundType = MyRepository::class)
 * @ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
 * @Inject
 * class MyRepositoryImpl(...) : MyRepository, UserScopedSyncer {
 *     // Catching, not runCatching: a sync cancelled by a user switch must stay cancelled.
 *     override suspend fun sync(): Result<Unit> = Catching { pullAndReconcile() }
 * }
 * ```
 *
 * The interface lives in the api module because the stores that sync are other
 * libraries' impl modules, and an impl may not depend on another impl.
 *
 * Not for stores with bespoke per-event work (e.g. clearing an in-memory cache on
 * a switch) — those stay direct [com.opson.libraries.opson.AppEventListener]s.
 */
interface UserScopedSyncer {
    suspend fun sync(): Result<Unit>
}
