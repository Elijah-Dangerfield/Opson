@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.entitlements.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.entitlements.Entitlement
import com.opson.libraries.entitlements.EntitlementRepository
import com.opson.libraries.entitlements.Permission
import com.opson.libraries.entitlements.Role
import com.opson.libraries.entitlements.RoleRepository
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.opson.UserScopedSyncer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock

/** `GET /v1/me/entitlements`, mirrored field for field from the server's `MyAccessResponse`. */
@Serializable
internal data class MyAccessResponse(
    val role: String,
    val permissions: List<String> = emptyList(),
    val entitlements: List<MyEntitlementDto> = emptyList(),
)

@Serializable
internal data class MyEntitlementDto(val key: String, val sources: List<MyEntitlementSourceDto> = emptyList())

@Serializable
internal data class MyEntitlementSourceDto(
    val type: String,
    val bundle: String? = null,
    val role: String? = null,
    val expiresAtEpochMs: Long? = null,
)

/** One held entitlement as the phone keeps it. [expiresAtEpochMs] is null when something holds it for good. */
@Serializable
internal data class StoredEntitlement(val key: String, val expiresAtEpochMs: Long? = null)

/**
 * The last answer, kept as strings so a key this build doesn't know survives an app update
 * that does. [answered] is false until the server has answered for this account.
 */
@Serializable
internal data class StoredAccess(
    val answered: Boolean = false,
    val role: String = Role.Member.wire,
    val permissions: List<String> = emptyList(),
    val entitlements: List<StoredEntitlement> = emptyList(),
)

/**
 * [EntitlementRepository] and [RoleRepository] on disk, per account: a user change wipes it
 * before the next account's state is emitted ([UserScopedClearer]), and [sync] pulls that
 * account's answer on sign-in, foreground and reconnect ([UserScopedSyncer]).
 *
 * Until an answer arrives (a new install, offline since sign-in, or a server from before the
 * route, which answers 404) it holds [Entitlement.DEFAULTS], the server's own default, so
 * the app never hides something that was free because it doesn't know yet. A failed fetch
 * keeps the last answer. A grant that expires stops counting at its expiry, without waiting
 * for the next sync.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = EntitlementRepository::class)
@ContributesBinding(AppScope::class, boundType = RoleRepository::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class AccessStore(
    cacheFactory: CacheFactory,
    private val networkClient: NetworkClient,
    private val clock: Clock,
    appScope: AppCoroutineScope,
) : EntitlementRepository, RoleRepository, UserScopedSyncer, UserScopedClearer {

    private val cache: Cache<StoredAccess> = cacheFactory.persistent(
        name = "account_access",
        serializer = versionedJsonSerializer(defaultValue = { StoredAccess() }),
    )

    private val stored: StateFlow<StoredAccess> = cache.updates.stateIn(appScope, SharingStarted.Eagerly, StoredAccess())

    override val entitlements: StateFlow<Set<Entitlement>> = stored
        .map { held(it, clock.now().toEpochMilliseconds()) }
        .stateIn(appScope, SharingStarted.Eagerly, Entitlement.DEFAULTS)

    override val role: StateFlow<Role> = stored
        .map { Role.fromWire(it.role) }
        .stateIn(appScope, SharingStarted.Eagerly, Role.Member)

    override val permissions: StateFlow<Set<Permission>> = stored
        .map { access -> access.permissions.mapNotNull(Permission::fromKey).toSet() }
        .stateIn(appScope, SharingStarted.Eagerly, emptySet())

    override fun has(entitlement: Entitlement): Boolean =
        entitlement in held(stored.value, clock.now().toEpochMilliseconds())

    override suspend fun sync(): Result<Unit> = Catching {
        val answer = networkClient.authedCall("access.mine") { client ->
            val response = client.get(PATH) { expectSuccess = false }
            when (response.status) {
                HttpStatusCode.OK -> response.body<MyAccessResponse>()
                // A server from before entitlements: keep what we have, defaults included.
                HttpStatusCode.NotFound -> null
                else -> error("entitlements answered ${response.status.value}")
            }
        }.getOrThrow() ?: return@Catching
        cache.set(answer.toStored())
    }

    override suspend fun clear(previousUserId: String) {
        cache.set(StoredAccess())
    }

    internal companion object {
        const val PATH = "/v1/me/entitlements"

        /** Defaults before an answer; afterwards exactly what the server said, less what has expired. */
        fun held(access: StoredAccess, nowEpochMs: Long): Set<Entitlement> {
            if (!access.answered) return Entitlement.DEFAULTS
            return access.entitlements
                .filter { it.expiresAtEpochMs == null || it.expiresAtEpochMs > nowEpochMs }
                .mapNotNull { Entitlement.fromKey(it.key) }
                .toSet()
        }

        fun MyAccessResponse.toStored(): StoredAccess = StoredAccess(
            answered = true,
            role = role,
            permissions = permissions,
            entitlements = entitlements.map { entitlement ->
                // Held for good if any source is; otherwise until the last source ends.
                val expiries = entitlement.sources.map { it.expiresAtEpochMs }
                val expiresAt = if (expiries.isEmpty() || null in expiries) null else expiries.filterNotNull().max()
                StoredEntitlement(entitlement.key, expiresAt)
            },
        )
    }
}
