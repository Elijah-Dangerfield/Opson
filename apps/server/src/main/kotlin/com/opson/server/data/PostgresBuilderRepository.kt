package com.opson.server.data

import com.opson.server.db.BuilderMembersTable
import com.opson.server.db.Database
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.BuilderMembership
import com.opson.server.domain.BuilderRepository
import com.opson.server.domain.BuilderRules
import com.opson.server.domain.BuilderStatus
import com.opson.server.domain.ContributionHistory
import com.opson.server.domain.HeadlineAgreement
import com.opson.server.domain.JoinWrite
import com.opson.server.domain.RejectCode
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.JavaInstantColumnType
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `builder_members` (V30), and the reads a Builder's trust and Pro come from: their submissions'
 * decisions and audits, their accepted reports and their confirmed checks. Every read is by the
 * person's id on an index, a few dozen rows at most.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresBuilderRepository(private val database: Database) : BuilderRepository {

    override suspend fun membership(userId: UserId): BuilderMembership? = database.transaction { find(userId) }

    override suspend fun join(userId: UserId, termsVersion: String, photoRetention: Boolean, now: Instant): JoinWrite = try {
        database.transaction {
            val existing = find(userId)
            when {
                existing == null -> BuilderMembersTable.insert {
                    it[BuilderMembersTable.userId] = userId.value
                    it[status] = BuilderStatus.Active.wire
                    it[joinedAt] = now.toJavaInstant()
                    it[BuilderMembersTable.termsVersion] = termsVersion
                    it[BuilderMembersTable.photoRetention] = photoRetention
                    it[trustHold] = false
                }
                // A rejoin keeps the first join date, so the starting month of Pro is never renewed.
                existing.status == BuilderStatus.Left -> BuilderMembersTable.update({ BuilderMembersTable.userId eq userId.value }) {
                    it[status] = BuilderStatus.Active.wire
                    it[BuilderMembersTable.termsVersion] = termsVersion
                    it[BuilderMembersTable.photoRetention] = photoRetention
                    it[leftAt] = null
                }
                else -> Unit
            }
            JoinWrite.Joined(checkNotNull(find(userId)))
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState() != FOREIGN_KEY_VIOLATION) throw e
        JoinWrite.NoSuchUser
    }

    override suspend fun leave(userId: UserId, now: Instant): Boolean = database.transaction {
        BuilderMembersTable.update({ (BuilderMembersTable.userId eq userId.value) and active() }) {
            it[status] = BuilderStatus.Left.wire
            it[leftAt] = now.toJavaInstant()
            it[trustedSince] = null
        } > 0
    }

    override suspend fun setPhotoRetention(userId: UserId, keep: Boolean): Boolean = database.transaction {
        BuilderMembersTable.update({ (BuilderMembersTable.userId eq userId.value) and active() }) {
            it[photoRetention] = keep
        } > 0
    }

    override suspend fun remove(userId: UserId, actor: String, reason: String?, now: Instant): Boolean = database.transaction {
        BuilderMembersTable.update({ BuilderMembersTable.userId eq userId.value }) {
            it[status] = BuilderStatus.Removed.wire
            it[removedAt] = now.toJavaInstant()
            it[removedBy] = actor.take(MAX_ACTOR)
            it[removedReason] = reason?.take(MAX_REASON)
            it[trustedSince] = null
        } > 0
    }

    override suspend fun restore(userId: UserId): Boolean = database.transaction {
        BuilderMembersTable.update({
            (BuilderMembersTable.userId eq userId.value) and (BuilderMembersTable.status eq BuilderStatus.Removed.wire)
        }) {
            it[status] = BuilderStatus.Active.wire
            it[removedAt] = null
            it[removedBy] = null
            it[removedReason] = null
        } > 0
    }

    override suspend fun setTrustHold(userId: UserId, hold: Boolean): Boolean = database.transaction {
        BuilderMembersTable.update({ BuilderMembersTable.userId eq userId.value }) {
            it[trustHold] = hold
            if (hold) it[trustedSince] = null
        } > 0
    }

    override suspend fun setTrustedSince(userId: UserId, since: Instant?) {
        database.transaction {
            BuilderMembersTable.update({ BuilderMembersTable.userId eq userId.value }) { it[trustedSince] = since?.toJavaInstant() }
        }
    }

    override suspend fun activeCount(): Long = database.transaction {
        BuilderMembersTable.selectAll().where { active() }.count()
    }

    override suspend fun history(userId: UserId, now: Instant): ContributionHistory = database.transaction {
        val id = userId.value
        val since30 = (now - 30.days).toJavaInstant()
        val trustedSince = find(userId)?.trustedSince
        val accepted = mutableListOf<Instant>()
        query(ACCEPTED_SQL, listOf(uuid to id, uuid to id, uuid to id, int to BuilderRules.PRO_THRESHOLD)) { rs ->
            while (rs.next()) accepted += rs.instant("accepted_at")
        }
        var accepted30d = 0
        query(ACCEPTED_30D_SQL, listOf(uuid to id, time to since30, uuid to id, time to since30, uuid to id, time to since30)) { rs ->
            if (rs.next()) accepted30d = rs.getInt(1)
        }
        val agreed = mutableListOf<Boolean>()
        query(REVIEWED_SQL, listOf(uuid to id, int to BuilderRules.AGREEMENT_WINDOW)) { rs ->
            while (rs.next()) {
                val kept = rs.getString("status") == "approved" &&
                    (rs.getString("reviewed")?.let { reviewed -> HeadlineAgreement.reviewerKept(LabelJson.facts(rs.getString("submitted")).toFacts(), LabelJson.facts(reviewed).toFacts()) } ?: true)
                agreed += kept
            }
        }
        var stats = Stats()
        query(STATS_SQL, listOf(time to since30, time to since30, time to since30, time to since30, time to (trustedSince ?: now + 1.days).toJavaInstant(), uuid to id)) { rs ->
            if (rs.next()) {
                stats = Stats(
                    approved = rs.getInt("approved"),
                    reviewed = rs.getInt("reviewed"),
                    fakeOrCopied = rs.getInt("fake_or_copied"),
                    decided30d = rs.getInt("decided_30d"),
                    rejected30d = rs.getInt("rejected_30d"),
                    audits30d = rs.getInt("audits_30d"),
                    auditsFailed30d = rs.getInt("audits_failed_30d"),
                    autoSinceTrusted = rs.getInt("auto_since_trusted"),
                    rejected = rs.getInt("rejected"),
                )
            }
        }
        query("SELECT count(*) FROM food_verifications WHERE user_id = ? AND outcome = 'confirmed'", listOf(uuid to id)) { rs ->
            if (rs.next()) stats = stats.copy(checks = rs.getInt(1))
        }
        var created: Instant? = null
        query("SELECT created_at FROM auth.users WHERE id = ?", listOf(uuid to id)) { rs ->
            if (rs.next()) created = rs.getObject("created_at", OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant()
        }
        ContributionHistory(
            accountCreatedAt = created,
            acceptedAt = accepted,
            accepted30d = accepted30d,
            approvedSubmissions = stats.approved,
            reviewerAgreed = agreed,
            reviewedTotal = stats.reviewed,
            fakeOrCopied = stats.fakeOrCopied,
            decided30d = stats.decided30d,
            rejected30d = stats.rejected30d,
            audits30d = stats.audits30d,
            auditsFailed30d = stats.auditsFailed30d,
            autoApprovedSinceTrusted = stats.autoSinceTrusted,
            rejectedSubmissions = stats.rejected,
            confirmedChecks = stats.checks,
        )
    }

    private data class Stats(
        val approved: Int = 0,
        val reviewed: Int = 0,
        val fakeOrCopied: Int = 0,
        val decided30d: Int = 0,
        val rejected30d: Int = 0,
        val audits30d: Int = 0,
        val auditsFailed30d: Int = 0,
        val autoSinceTrusted: Int = 0,
        val rejected: Int = 0,
        val checks: Int = 0,
    )

    private fun find(userId: UserId): BuilderMembership? =
        BuilderMembersTable.selectAll().where { BuilderMembersTable.userId eq userId.value }.singleOrNull()?.toMembership()

    private fun active() = BuilderMembersTable.status eq BuilderStatus.Active.wire

    private fun ResultRow.toMembership() = BuilderMembership(
        userId = UserId(this[BuilderMembersTable.userId]),
        status = BuilderStatus.fromWire(this[BuilderMembersTable.status]) ?: BuilderStatus.Active,
        joinedAt = this[BuilderMembersTable.joinedAt].toKotlinInstant(),
        termsVersion = this[BuilderMembersTable.termsVersion],
        photoRetention = this[BuilderMembersTable.photoRetention],
        trustedSince = this[BuilderMembersTable.trustedSince]?.toKotlinInstant(),
        trustHold = this[BuilderMembersTable.trustHold],
        leftAt = this[BuilderMembersTable.leftAt]?.toKotlinInstant(),
        removedAt = this[BuilderMembersTable.removedAt]?.toKotlinInstant(),
        removedBy = this[BuilderMembersTable.removedBy],
        removedReason = this[BuilderMembersTable.removedReason],
    )

    private fun query(sql: String, args: List<Pair<IColumnType<*>, Any?>>, read: (ResultSet) -> Unit) {
        TransactionManager.current().exec(stmt = sql, args = args) { read(it) }
    }

    private fun ResultSet.instant(column: String): Instant = getObject(column, OffsetDateTime::class.java).toInstant().toKotlinInstant()

    private fun ExposedSQLException.sqlState(): String? = sqlState ?: (cause as? SQLException)?.sqlState

    companion object {
        private const val FOREIGN_KEY_VIOLATION = "23503"
        private const val MAX_ACTOR = 200
        private const val MAX_REASON = 500
        private val uuid = UUIDColumnType()
        private val int = org.jetbrains.exposed.sql.IntegerColumnType()
        private val time = JavaInstantColumnType()

        /** A person decided it, not the routing: `auto:` and `owner:` reviewers are the lanes. */
        private const val BY_A_PERSON = "reviewed_by IS NOT NULL AND reviewed_by NOT LIKE 'auto:%' AND reviewed_by NOT LIKE 'owner:%'"

        /**
         * Accepted contributions, newest first: approved submissions not overturned by an audit,
         * accepted reports, and checks that agreed with the catalog (builder-program.md, "What
         * Builders get").
         */
        private val ACCEPTED_UNION = """
            SELECT reviewed_at AS accepted_at FROM food_submissions
             WHERE user_id = ? AND status = 'approved' AND reviewed_at IS NOT NULL AND audit IS DISTINCT FROM 'failed'
            UNION ALL
            SELECT resolved_at FROM food_reports WHERE user_id = ? AND status = 'accepted' AND resolved_at IS NOT NULL
            UNION ALL
            SELECT created_at FROM food_verifications WHERE user_id = ? AND outcome = 'confirmed'
        """.trimIndent()

        private val ACCEPTED_SQL = "SELECT accepted_at FROM ($ACCEPTED_UNION) a ORDER BY accepted_at DESC LIMIT ?"

        private val ACCEPTED_30D_SQL = """
            SELECT
              (SELECT count(*) FROM food_submissions WHERE user_id = ? AND status = 'approved' AND reviewed_at > ? AND audit IS DISTINCT FROM 'failed')
            + (SELECT count(*) FROM food_reports WHERE user_id = ? AND status = 'accepted' AND resolved_at > ?)
            + (SELECT count(*) FROM food_verifications WHERE user_id = ? AND outcome = 'confirmed' AND created_at > ?)
        """.trimIndent()

        private val REVIEWED_SQL = """
            SELECT status, submitted::text AS submitted, reviewed::text AS reviewed FROM food_submissions
             WHERE user_id = ? AND status IN ('approved', 'rejected') AND $BY_A_PERSON
             ORDER BY reviewed_at DESC LIMIT ?
        """.trimIndent()

        private val STATS_SQL = """
            SELECT
              count(*) FILTER (WHERE status = 'approved' AND audit IS DISTINCT FROM 'failed') AS approved,
              count(*) FILTER (WHERE status IN ('approved', 'rejected') AND $BY_A_PERSON) AS reviewed,
              count(*) FILTER (WHERE reject_code IN ('${RejectCode.FakeData.wire}', '${RejectCode.CopiedData.wire}')) AS fake_or_copied,
              count(*) FILTER (WHERE status IN ('approved', 'rejected') AND $BY_A_PERSON AND reviewed_at > ?) AS decided_30d,
              count(*) FILTER (WHERE status = 'rejected' AND $BY_A_PERSON AND reviewed_at > ?) AS rejected_30d,
              count(*) FILTER (WHERE audit IN ('passed', 'failed') AND audited_at > ?) AS audits_30d,
              count(*) FILTER (WHERE audit = 'failed' AND audited_at > ?) AS audits_failed_30d,
              count(*) FILTER (WHERE lane = 'auto' AND reviewed_at >= ?) AS auto_since_trusted,
              count(*) FILTER (WHERE status = 'rejected') AS rejected
            FROM food_submissions WHERE user_id = ?
        """.trimIndent()
    }
}
