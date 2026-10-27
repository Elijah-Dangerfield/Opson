package com.opson.server.data

import com.opson.server.db.AppleRefreshTokensTable
import com.opson.server.db.Database
import com.opson.server.db.toJavaInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.AppleTokenRepository
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresAppleTokenRepository(
    private val database: Database,
    private val clock: Clock,
) : AppleTokenRepository {

    // Each sign-in brings a new code and so a new token; the newest is the one Apple
    // knows the session by, so it replaces the last.
    override suspend fun save(userId: UserId, refreshToken: String) {
        database.transaction {
            AppleRefreshTokensTable.upsert {
                it[AppleRefreshTokensTable.userId] = userId.value
                it[AppleRefreshTokensTable.refreshToken] = refreshToken
                it[updatedAt] = clock.now().toJavaInstant()
            }
        }
    }

    override suspend fun find(userId: UserId): String? = database.transaction {
        AppleRefreshTokensTable.selectAll()
            .where { AppleRefreshTokensTable.userId eq userId.value }
            .singleOrNull()
            ?.get(AppleRefreshTokensTable.refreshToken)
    }
}
