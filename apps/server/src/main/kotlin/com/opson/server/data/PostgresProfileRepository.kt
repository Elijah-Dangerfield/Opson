package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.ProfilesTable
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.FindOrCreateProfileResult
import com.opson.server.domain.Profile
import com.opson.server.domain.ProfileRepository
import com.opson.server.domain.UpdateProfileOutcome
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.SQLException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Postgres-backed [ProfileRepository]. The reference repository shape:
 *  - `@Inject` constructor takes the [Database] handle + an injected [Clock]
 *    (never `Clock.System` directly, so tests control time),
 *  - every method body is `database.transaction { … }`,
 *  - idempotency comes from DB constraints + catching SQLSTATE 23505, not from
 *    pre-checking for races,
 *  - rows map to domain types via a private `ResultRow.toProfile()` extension.
 *
 * Copy this for new repositories.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresProfileRepository(
    private val database: Database,
    private val clock: Clock,
) : ProfileRepository {

    override suspend fun findOrCreate(userId: UserId): Profile =
        findOrCreateResult(userId).profile

    override suspend fun findOrCreateResult(userId: UserId): FindOrCreateProfileResult =
        database.transaction {
            val found = existing(userId)
            if (found != null) {
                return@transaction FindOrCreateProfileResult(found, created = false)
            }
            val now = clock.now().toJavaInstant()
            // ON CONFLICT DO NOTHING rather than catching the unique violation: an
            // error aborts the transaction, so the re-read after it could never run.
            // When a concurrent first contact committed the row after this
            // transaction's snapshot, Postgres refuses the insert as a serialization
            // failure, Database runs the block again, and the read above finds it.
            val wonInsert = ProfilesTable.insertIgnore {
                it[ProfilesTable.userId] = userId.value
                it[displayName] = defaultDisplayName(userId)
                it[createdAt] = now
                it[updatedAt] = now
            }.insertedCount > 0
            val profile = existing(userId) ?: error("Profile insert for $userId did not persist")
            FindOrCreateProfileResult(profile, created = wonInsert)
        }

    override suspend fun delete(userId: UserId) {
        database.transaction {
            ProfilesTable.deleteWhere { ProfilesTable.userId eq userId.value }
        }
    }

    override suspend fun updateDisplayName(
        userId: UserId,
        displayName: String,
    ): UpdateProfileOutcome = database.transaction {
        val updated = try {
            ProfilesTable.update({ ProfilesTable.userId eq userId.value }) {
                it[ProfilesTable.displayName] = displayName
                it[updatedAt] = clock.now().toJavaInstant()
            }
        } catch (e: ExposedSQLException) {
            if (e.isUniqueViolation()) return@transaction UpdateProfileOutcome.DisplayNameTaken
            throw e
        }
        if (updated == 0) {
            UpdateProfileOutcome.NotFound
        } else {
            UpdateProfileOutcome.Success(existing(userId) ?: error("Profile vanished mid-update"))
        }
    }

    private fun existing(userId: UserId): Profile? =
        ProfilesTable.selectAll()
            .where { ProfilesTable.userId eq userId.value }
            .singleOrNull()
            ?.toProfile()

    private fun ResultRow.toProfile(): Profile = Profile(
        userId = UserId(this[ProfilesTable.userId]),
        displayName = this[ProfilesTable.displayName],
        createdAt = this[ProfilesTable.createdAt].toKotlinInstant(),
        updatedAt = this[ProfilesTable.updatedAt].toKotlinInstant(),
    )

    private fun defaultDisplayName(userId: UserId): String =
        "user-" + userId.value.toString().take(8)

    /** Postgres SQLSTATE 23505 = unique_violation. */
    private fun ExposedSQLException.isUniqueViolation(): Boolean =
        sqlState == UNIQUE_VIOLATION || (cause as? SQLException)?.sqlState == UNIQUE_VIOLATION

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
