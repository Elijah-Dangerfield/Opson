package com.opson.server.data

import com.opson.server.db.AccessAuditTable
import com.opson.server.db.Database
import com.opson.server.db.EntitlementGrantsTable
import com.opson.server.db.UserRolesTable
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.AccessAuditRecord
import com.opson.server.domain.AccessAuditRepository
import com.opson.server.domain.AddGrantOutcome
import com.opson.server.domain.EntitlementGrant
import com.opson.server.domain.EntitlementGrantRepository
import com.opson.server.domain.GrantEffect
import com.opson.server.domain.GrantKind
import com.opson.server.domain.NewGrant
import com.opson.server.domain.Role
import com.opson.server.domain.RoleAssignment
import com.opson.server.domain.SetRoleOutcome
import com.opson.server.domain.UserId
import com.opson.server.domain.UserRoleRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.SQLException
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `user_roles` (V28). A write and its `access_audit` row share one transaction, so the trail
 * can't miss a change. An unknown account is the foreign key's call (SQLSTATE 23503), not a
 * pre-check.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresUserRoleRepository(
    private val database: Database,
    private val clock: Clock,
) : UserRoleRepository {

    override suspend fun assignment(userId: UserId): RoleAssignment? = database.transaction { find(userId) }

    override suspend fun setRole(userId: UserId, role: Role, actor: String, reason: String?): SetRoleOutcome = try {
        database.transaction {
            val before = find(userId)
            val now = clock.now()
            val after = if (role == Role.Member) {
                UserRolesTable.deleteWhere { UserRolesTable.userId eq userId.value }
                null
            } else {
                UserRolesTable.upsert {
                    it[UserRolesTable.userId] = userId.value
                    it[UserRolesTable.role] = role.wire
                    it[grantedBy] = actor
                    it[grantedAt] = now.toJavaInstant()
                    it[UserRolesTable.reason] = reason
                }
                RoleAssignment(userId, role, actor, now, reason)
            }
            writeAccessAudit(now, actor, if (after == null) "clear_role" else "set_role", userId, before?.snapshot(), after?.snapshot())
            SetRoleOutcome.Set(after)
        }
    } catch (e: ExposedSQLException) {
        if (!e.isForeignKeyViolation()) throw e
        SetRoleOutcome.NoSuchUser
    }

    private fun find(userId: UserId): RoleAssignment? = UserRolesTable.selectAll()
        .where { UserRolesTable.userId eq userId.value }
        .singleOrNull()
        ?.let { row ->
            // A role the code no longer knows reads as member rather than failing every request.
            val role = Role.fromWire(row[UserRolesTable.role]) ?: return@let null
            RoleAssignment(
                userId = userId,
                role = role,
                grantedBy = row[UserRolesTable.grantedBy],
                grantedAt = row[UserRolesTable.grantedAt].toKotlinInstant(),
                reason = row[UserRolesTable.reason],
            )
        }

    private fun RoleAssignment.snapshot(): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(role.wire))
        put("grantedBy", JsonPrimitive(grantedBy))
        put("grantedAtEpochMs", JsonPrimitive(grantedAt.toEpochMilliseconds()))
        put("reason", reason?.let(::JsonPrimitive) ?: JsonNull)
    }
}

/** `entitlement_grants` (V28). Removal marks the row, it never deletes it; each write is audited in its own transaction. */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresEntitlementGrantRepository(
    private val database: Database,
    private val clock: Clock,
) : EntitlementGrantRepository {

    override suspend fun active(userId: UserId, now: Instant): List<EntitlementGrant> = database.transaction {
        EntitlementGrantsTable.selectAll()
            .where { (EntitlementGrantsTable.userId eq userId.value) and EntitlementGrantsTable.removedAt.isNull() }
            .mapNotNull { it.toGrant() }
            .filter { it.isActive(now) }
    }

    override suspend fun history(userId: UserId): List<EntitlementGrant> = database.transaction {
        EntitlementGrantsTable.selectAll()
            .where { EntitlementGrantsTable.userId eq userId.value }
            .orderBy(EntitlementGrantsTable.grantedAt to SortOrder.DESC)
            .mapNotNull { it.toGrant() }
    }

    override suspend fun add(grant: NewGrant, actor: String): AddGrantOutcome = try {
        database.transaction {
            val now = clock.now()
            val stored = EntitlementGrant(
                id = UUID.randomUUID(),
                userId = grant.userId,
                kind = grant.kind,
                key = grant.key,
                effect = grant.effect,
                source = grant.source,
                reason = grant.reason,
                grantedBy = actor,
                grantedAt = now,
                expiresAt = grant.expiresAt,
            )
            EntitlementGrantsTable.insert {
                it[id] = stored.id
                it[userId] = stored.userId.value
                it[kind] = stored.kind.wire
                it[key] = stored.key
                it[effect] = stored.effect.wire
                it[grantSource] = stored.source
                it[reason] = stored.reason
                it[grantedBy] = actor
                it[grantedAt] = now.toJavaInstant()
                it[expiresAt] = stored.expiresAt?.toJavaInstant()
            }
            writeAccessAudit(now, actor, stored.effect.wire, stored.userId, before = null, after = stored.snapshot())
            AddGrantOutcome.Added(stored)
        }
    } catch (e: ExposedSQLException) {
        if (!e.isForeignKeyViolation()) throw e
        AddGrantOutcome.NoSuchUser
    }

    override suspend fun remove(userId: UserId, grantId: UUID, actor: String): Boolean = database.transaction {
        val existing = EntitlementGrantsTable.selectAll()
            .where {
                (EntitlementGrantsTable.id eq grantId) and
                    (EntitlementGrantsTable.userId eq userId.value) and
                    EntitlementGrantsTable.removedAt.isNull()
            }
            .singleOrNull()
            ?.toGrant()
            ?: return@transaction false
        val now = clock.now()
        EntitlementGrantsTable.update({ EntitlementGrantsTable.id eq grantId }) {
            it[removedAt] = now.toJavaInstant()
            it[removedBy] = actor
        }
        val removed = existing.copy(removedAt = now, removedBy = actor)
        writeAccessAudit(now, actor, "remove_${existing.effect.wire}", userId, existing.snapshot(), removed.snapshot())
        true
    }

    private fun ResultRow.toGrant(): EntitlementGrant? {
        val kind = GrantKind.fromWire(this[EntitlementGrantsTable.kind]) ?: return null
        val effect = GrantEffect.fromWire(this[EntitlementGrantsTable.effect]) ?: return null
        return EntitlementGrant(
            id = this[EntitlementGrantsTable.id],
            userId = UserId(this[EntitlementGrantsTable.userId]),
            kind = kind,
            key = this[EntitlementGrantsTable.key],
            effect = effect,
            source = this[EntitlementGrantsTable.grantSource],
            reason = this[EntitlementGrantsTable.reason],
            grantedBy = this[EntitlementGrantsTable.grantedBy],
            grantedAt = this[EntitlementGrantsTable.grantedAt].toKotlinInstant(),
            expiresAt = this[EntitlementGrantsTable.expiresAt]?.toKotlinInstant(),
            removedAt = this[EntitlementGrantsTable.removedAt]?.toKotlinInstant(),
            removedBy = this[EntitlementGrantsTable.removedBy],
        )
    }

    private fun EntitlementGrant.snapshot(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id.toString()))
        put("kind", JsonPrimitive(kind.wire))
        put("key", JsonPrimitive(key))
        put("effect", JsonPrimitive(effect.wire))
        put("source", JsonPrimitive(source))
        put("reason", reason?.let(::JsonPrimitive) ?: JsonNull)
        put("expiresAtEpochMs", expiresAt?.let { JsonPrimitive(it.toEpochMilliseconds()) } ?: JsonNull)
        put("removedBy", removedBy?.let(::JsonPrimitive) ?: JsonNull)
    }
}

@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresAccessAuditRepository(private val database: Database) : AccessAuditRepository {

    override suspend fun list(userId: UserId?, limit: Int): List<AccessAuditRecord> = database.transaction {
        val all = AccessAuditTable.selectAll()
        val rows = if (userId != null) all.where { AccessAuditTable.userId eq userId.value } else all
        rows.orderBy(AccessAuditTable.at to SortOrder.DESC)
            .limit(limit.coerceIn(1, MAX_LIMIT))
            .map { row ->
                AccessAuditRecord(
                    id = row[AccessAuditTable.id],
                    atEpochMs = row[AccessAuditTable.at].toEpochMilli(),
                    actor = row[AccessAuditTable.actor],
                    action = row[AccessAuditTable.action],
                    userId = UserId(row[AccessAuditTable.userId]),
                    before = row[AccessAuditTable.beforeJsonb]?.let(auditJson::parseToJsonElement),
                    after = row[AccessAuditTable.afterJsonb]?.let(auditJson::parseToJsonElement),
                )
            }
    }

    private companion object {
        const val MAX_LIMIT = 500
    }
}

private val auditJson = Json { ignoreUnknownKeys = true }

/** One `access_audit` row. Call inside the transaction that made the change. */
@OptIn(ExperimentalTime::class)
private fun writeAccessAudit(now: Instant, actor: String, action: String, userId: UserId, before: JsonElement?, after: JsonElement?) {
    AccessAuditTable.insert {
        it[id] = UUID.randomUUID()
        it[at] = now.toJavaInstant()
        it[AccessAuditTable.actor] = actor
        it[AccessAuditTable.action] = action
        it[AccessAuditTable.userId] = userId.value
        it[beforeJsonb] = before?.let { e -> auditJson.encodeToString(JsonElement.serializer(), e) }
        it[afterJsonb] = after?.let { e -> auditJson.encodeToString(JsonElement.serializer(), e) }
    }
}

/** Postgres SQLSTATE 23503: the account the row names does not exist. */
private fun ExposedSQLException.isForeignKeyViolation(): Boolean =
    sqlState == FOREIGN_KEY_VIOLATION || (cause as? SQLException)?.sqlState == FOREIGN_KEY_VIOLATION

private const val FOREIGN_KEY_VIOLATION = "23503"
