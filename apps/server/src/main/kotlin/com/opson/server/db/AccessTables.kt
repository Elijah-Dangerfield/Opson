package com.opson.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** Accounts that are more than a member. No row means [com.opson.server.domain.Role.Member]. See `V28__roles_and_entitlements.sql`. */
object UserRolesTable : Table("user_roles") {
    val userId = uuid("user_id")
    val role = text("role")
    val grantedBy = text("granted_by")
    val grantedAt = timestamp("granted_at")
    val reason = text("reason").nullable()
    override val primaryKey = PrimaryKey(userId)
}

/** Per-account grants and revocations of an entitlement or a bundle. Removed rows stay, with `removed_at`. See V28. */
object EntitlementGrantsTable : Table("entitlement_grants") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val kind = text("kind")
    val key = text("key")
    val effect = text("effect")
    val grantSource = text("source")
    val reason = text("reason").nullable()
    val grantedBy = text("granted_by")
    val grantedAt = timestamp("granted_at")
    val expiresAt = timestamp("expires_at").nullable()
    val removedAt = timestamp("removed_at").nullable()
    val removedBy = text("removed_by").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Every role and grant change, with the row before and after. See V28. */
object AccessAuditTable : Table("access_audit") {
    val id = uuid("id")
    val at = timestamp("at")
    val actor = text("actor")
    val action = text("action")
    val userId = uuid("user_id")
    val beforeJsonb = jsonb("before_jsonb").nullable()
    val afterJsonb = jsonb("after_jsonb").nullable()
    override val primaryKey = PrimaryKey(id)
}
