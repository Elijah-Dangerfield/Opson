package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.PlayerReportsTable
import com.opson.server.db.toJavaInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.PlayerReportRepository
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.insert
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Exposed-backed [PlayerReportRepository]. Append-only inserts into
 * `player_reports`; every read is deferred to the future moderation-review
 * surface (see [PlayerReportsTable]).
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresPlayerReportRepository(
    private val database: Database,
    private val clock: Clock,
) : PlayerReportRepository {

    override suspend fun record(
        reporter: UserId,
        reported: UserId,
        context: String?,
        reason: String?,
        categories: List<String>,
    ) {
        val now = clock.now().toJavaInstant()
        database.transaction {
            PlayerReportsTable.insert {
                it[reporterUserId] = reporter.value
                it[reportedUserId] = reported.value
                it[PlayerReportsTable.context] = context
                it[PlayerReportsTable.reason] = reason
                it[reasonCategories] = categories.joinToString(",").takeIf { s -> s.isNotEmpty() }
                it[createdAt] = now
            }
        }
    }
}
