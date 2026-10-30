@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.entitlements.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import com.opson.libraries.core.Catching
import com.opson.libraries.entitlements.BetaRelease
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.opson.UserScopedSyncer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** The flag behind [BetaRelease]. Off by default, so a store build says nothing until the owner turns it on. */
@HiddenFromObjC
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = BetaRelease::class)
class BetaReleaseFlag(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap), BetaRelease {
    override val name = "Beta notice"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.betaNotice"
    override val default = false
    override val description = "The beta: Welcome says Pro isn't here yet and beta members get a lifetime discount, and accounts are recorded as beta members"

    override fun isOn(): Boolean = value
}

/** Whether this account's beta membership reached the server. Per account: a user change resets it. */
@Serializable
internal data class StoredBeta(val recorded: Boolean = false)

/**
 * Records the signed-in account as joining during the beta (`POST /v1/me/beta`) while
 * [BetaRelease] is on, once per account on this phone. Runs on the sync edges (sign-in,
 * foreground, reconnect, [UserScopedSyncer]), so an account made offline, or one that signed up
 * on an older build, is recorded the next time it is online during the beta.
 *
 * The server keeps it as a `beta` grant and refuses one once the beta is over there too. A
 * server from before the route (404), a closed beta (409) and a guest session (403) are not
 * failures: nothing is recorded, and the next edge asks again. Only an unexpected answer fails,
 * so the coordinator's retry applies.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class BetaMembership(
    cacheFactory: CacheFactory,
    private val networkClient: NetworkClient,
    private val beta: BetaRelease,
) : UserScopedSyncer, UserScopedClearer {

    private val cache: Cache<StoredBeta> = cacheFactory.persistent(
        name = "beta_membership",
        serializer = versionedJsonSerializer(defaultValue = { StoredBeta() }),
    )

    override suspend fun sync(): Result<Unit> = Catching {
        if (!beta.isOn() || cache.get().recorded) return@Catching
        val recorded = networkClient.authedCall("beta.join") { client ->
            val response = client.post(PATH) { expectSuccess = false }
            when {
                response.status.isSuccess() -> true
                response.status in NOT_NOW -> false
                else -> error("beta answered ${response.status.value}")
            }
        }.getOrThrow()
        if (recorded) cache.set(StoredBeta(recorded = true))
    }

    override suspend fun clear(previousUserId: String) {
        cache.set(StoredBeta())
    }

    internal companion object {
        const val PATH = "/v1/me/beta"

        /** A server from before the route, a guest session, and a beta the server has closed. */
        val NOT_NOW = setOf(HttpStatusCode.NotFound, HttpStatusCode.Forbidden, HttpStatusCode.Conflict)
    }
}
