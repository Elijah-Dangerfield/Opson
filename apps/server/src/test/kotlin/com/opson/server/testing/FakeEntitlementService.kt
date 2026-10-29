package com.opson.server.testing

import com.opson.server.domain.Entitlement
import com.opson.server.domain.EntitlementPolicy
import com.opson.server.domain.EntitlementService
import com.opson.server.domain.EntitlementSource
import com.opson.server.domain.ResolvedEntitlements
import com.opson.server.domain.Role
import com.opson.server.domain.UserAccess
import com.opson.server.domain.UserId
import com.opson.server.http.ClientContext

/** Every caller gets [granted] (by default what `entitlements.everyone` seeds) and [role]. */
class FakeEntitlementService(
    var granted: Set<Entitlement> = EntitlementPolicy.DEFAULT.everyone,
    var role: Role = Role.Member,
) : EntitlementService {
    override suspend fun access(userId: UserId, context: ClientContext): UserAccess = UserAccess(
        role = role,
        roleAssignment = null,
        entitlements = ResolvedEntitlements(granted.associateWith { listOf(EntitlementSource.Everyone) }, emptyMap()),
    )

    override suspend fun policy(context: ClientContext): EntitlementPolicy = EntitlementPolicy.DEFAULT
}
