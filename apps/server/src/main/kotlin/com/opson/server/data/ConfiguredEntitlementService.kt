package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.AppConfigSource
import com.opson.server.domain.BuilderService
import com.opson.server.domain.EntitlementGrantRepository
import com.opson.server.domain.EntitlementPolicy
import com.opson.server.domain.EntitlementService
import com.opson.server.domain.ResolvedEntitlements
import com.opson.server.domain.Role
import com.opson.server.domain.UserAccess
import com.opson.server.domain.UserId
import com.opson.server.domain.UserRoleRepository
import com.opson.server.http.ClientContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * [EntitlementService] over the three stores: the policy from remote config (through
 * [AppConfigSource], so its 30-second cache and its targeting rules apply), the role from
 * `user_roles`, the grants from `entitlement_grants`, and for a Builder how long the role's
 * bundles hold ([BuilderService.roleWindow]). Two indexed reads per call and no cache
 * of its own, so a grant or a revocation applies on the next request.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class ConfiguredEntitlementService(
    private val config: AppConfigSource,
    private val roles: UserRoleRepository,
    private val grants: EntitlementGrantRepository,
    private val builders: BuilderService,
    private val clock: Clock,
) : EntitlementService {

    override suspend fun access(userId: UserId, context: ClientContext): UserAccess {
        val policy = EntitlementPolicy.from(config.read(context, userId))
        val assignment = roles.assignment(userId)
        val role = assignment?.role ?: Role.Member
        val active = grants.active(userId, clock.now())
        return UserAccess(role, assignment, ResolvedEntitlements.resolve(policy, role, active, builders.roleWindow(userId, role)))
    }

    override suspend fun policy(context: ClientContext): EntitlementPolicy =
        EntitlementPolicy.from(config.read(context, userId = null))
}
