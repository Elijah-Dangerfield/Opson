package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.WaitlistTable
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.WaitlistEntry
import com.opson.server.domain.WaitlistPage
import com.opson.server.domain.WaitlistRepository
import com.opson.server.domain.WaitlistSignup
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * `waitlist` (V16). The unique index on `lower(email)` is the arbiter for a
 * repeat sign-up: `ON CONFLICT DO NOTHING` makes it a no-op, so there is no
 * read-then-write race between two posts of the same address.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresWaitlistRepository(
    private val database: Database,
    private val clock: Clock,
) : WaitlistRepository {

    override suspend fun add(signup: WaitlistSignup): Boolean = database.transaction {
        val inserted = WaitlistTable.insertIgnore {
            it[id] = UUID.randomUUID()
            it[email] = signup.email
            it[signupSource] = signup.source
            it[createdAt] = clock.now().toJavaInstant()
        }
        inserted.insertedCount > 0
    }

    override suspend fun page(limit: Int, offset: Long): WaitlistPage = database.transaction {
        val active = WaitlistTable.unsubscribedAt.isNull()
        val total = WaitlistTable.selectAll().where { active }.count()
        val entries = WaitlistTable.selectAll()
            .where { active }
            .orderBy(WaitlistTable.createdAt to SortOrder.DESC, WaitlistTable.id to SortOrder.ASC)
            .limit(limit)
            .offset(offset)
            .map { it.toEntry() }
        WaitlistPage(total = total, entries = entries)
    }

    override suspend fun export(sink: suspend (WaitlistEntry) -> Unit) {
        database.transaction {
            val rows = WaitlistTable.selectAll()
                .where { WaitlistTable.unsubscribedAt.isNull() }
                .orderBy(WaitlistTable.createdAt to SortOrder.ASC, WaitlistTable.id to SortOrder.ASC)
                .fetchSize(FETCH_SIZE)
            for (row in rows) sink(row.toEntry())
        }
    }

    override suspend fun remove(email: String): Boolean = database.transaction {
        val target = email.trim().lowercase()
        WaitlistTable.deleteWhere { WaitlistTable.email.lowerCase() eq target } > 0
    }

    private fun ResultRow.toEntry() = WaitlistEntry(
        id = this[WaitlistTable.id],
        email = this[WaitlistTable.email],
        source = this[WaitlistTable.signupSource],
        createdAt = this[WaitlistTable.createdAt].toKotlinInstant(),
    )

    private companion object {
        const val FETCH_SIZE = 500
    }
}
