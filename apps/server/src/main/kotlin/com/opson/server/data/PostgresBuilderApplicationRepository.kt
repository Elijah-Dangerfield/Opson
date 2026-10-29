package com.opson.server.data

import com.opson.server.db.BuilderApplicationsTable
import com.opson.server.db.Database
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.ApplicantSummary
import com.opson.server.domain.ApplicationStatus
import com.opson.server.domain.ApplyWrite
import com.opson.server.domain.BuilderApplication
import com.opson.server.domain.BuilderApplicationRepository
import com.opson.server.domain.LabelRegion
import com.opson.server.domain.NewBuilderApplication
import com.opson.server.domain.ShoppingFrequency
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `builder_applications` (V32). The partial unique index on a pending application per account is
 * the arbiter between two taps of Apply, as the id is for a submission: the loser reads back the
 * winner. A decision only moves a pending row, so accepting and a withdrawal racing each other
 * leave exactly one of them done.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresBuilderApplicationRepository(private val database: Database) : BuilderApplicationRepository {

    override suspend fun latest(userId: UserId): BuilderApplication? = database.transaction {
        BuilderApplicationsTable.selectAll()
            .where { BuilderApplicationsTable.userId eq userId.value }
            .orderBy(BuilderApplicationsTable.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toApplication()
    }

    override suspend fun find(id: UUID): BuilderApplication? = database.transaction { byId(id) }

    override suspend fun submit(application: NewBuilderApplication, now: Instant): ApplyWrite {
        pendingFor(application.userId)?.let { return ApplyWrite.AlreadyPending(it) }
        return try {
            database.transaction {
                BuilderApplicationsTable.insert {
                    it[id] = application.id
                    it[userId] = application.userId.value
                    it[status] = ApplicationStatus.Pending.wire
                    it[createdAt] = now.toJavaInstant()
                    it[termsVersion] = application.termsVersion
                    it[photoRetention] = application.photoRetention
                    it[motivation] = application.motivation?.take(MAX_MOTIVATION)
                    it[shopping] = application.shopping?.wire
                    it[region] = application.region?.wire
                    it[via] = application.via?.take(MAX_VIA)
                }
            }
            ApplyWrite.Created(checkNotNull(find(application.id)))
        } catch (e: ExposedSQLException) {
            when (e.sqlState()) {
                UNIQUE_VIOLATION -> pendingFor(application.userId)?.let(ApplyWrite::AlreadyPending) ?: throw e
                FOREIGN_KEY_VIOLATION -> ApplyWrite.NoSuchUser
                else -> throw e
            }
        }
    }

    override suspend fun withdraw(userId: UserId, now: Instant): BuilderApplication? {
        val pending = pendingFor(userId) ?: return null
        val moved = database.transaction {
            BuilderApplicationsTable.update({ (BuilderApplicationsTable.id eq pending.id) and isPending() }) {
                it[status] = ApplicationStatus.Withdrawn.wire
                it[withdrawnAt] = now.toJavaInstant()
            }
        }
        return if (moved > 0) find(pending.id) else null
    }

    override suspend fun decide(id: UUID, status: ApplicationStatus, actor: String, note: String?, now: Instant): BuilderApplication? {
        require(status == ApplicationStatus.Accepted || status == ApplicationStatus.Declined) { "a decision is accepted or declined" }
        val moved = database.transaction {
            BuilderApplicationsTable.update({ (BuilderApplicationsTable.id eq id) and isPending() }) {
                it[BuilderApplicationsTable.status] = status.wire
                it[decidedAt] = now.toJavaInstant()
                it[decidedBy] = actor.take(MAX_ACTOR)
                it[decisionNote] = note?.take(MAX_NOTE)
            }
        }
        return if (moved > 0) find(id) else null
    }

    override suspend fun pending(limit: Int): List<BuilderApplication> = database.transaction {
        BuilderApplicationsTable.selectAll()
            .where { isPending() }
            .orderBy(BuilderApplicationsTable.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { it.toApplication() }
    }

    override suspend fun pendingCount(): Long = database.transaction {
        BuilderApplicationsTable.selectAll().where { isPending() }.count()
    }

    override suspend fun decided(limit: Int): List<BuilderApplication> = database.transaction {
        BuilderApplicationsTable.selectAll()
            .where { BuilderApplicationsTable.status inList listOf(ApplicationStatus.Accepted.wire, ApplicationStatus.Declined.wire) }
            .orderBy(BuilderApplicationsTable.decidedAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toApplication() }
    }

    override suspend fun summary(userId: UserId, excluding: UUID?): ApplicantSummary = database.transaction {
        var summary: ApplicantSummary? = null
        val args = listOf(uuid to (excluding ?: NO_ID), uuid to (excluding ?: NO_ID), uuid to userId.value)
        TransactionManager.current().exec(stmt = SUMMARY_SQL, args = args) { rs ->
            if (rs.next()) {
                summary = ApplicantSummary(
                    email = rs.getString("email"),
                    displayName = rs.getString("display_name"),
                    isGuest = rs.getBoolean("is_guest"),
                    accountCreatedAt = rs.getObject("created_at", OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant(),
                    entries = rs.getLong("entries"),
                    daysLogged = rs.getLong("days_logged"),
                    lastLogDay = rs.getString("last_log_day"),
                    submissionsApproved = rs.getInt("submissions_approved"),
                    submissionsRejected = rs.getInt("submissions_rejected"),
                    submissionsPending = rs.getInt("submissions_pending"),
                    reportsAccepted = rs.getInt("reports_accepted"),
                    reportsRejected = rs.getInt("reports_rejected"),
                    reportsOpen = rs.getInt("reports_open"),
                    checks = rs.getInt("checks"),
                    moderationActions = rs.getInt("moderation_actions"),
                    moderationActive = rs.getInt("moderation_active"),
                    lastModerationReason = rs.getString("last_moderation_reason"),
                    earlierApplications = rs.getInt("earlier_applications"),
                    earlierDeclines = rs.getInt("earlier_declines"),
                    membership = rs.getString("membership"),
                )
            }
        }
        summary ?: ApplicantSummary(
            email = null, displayName = null, isGuest = false, accountCreatedAt = null, entries = 0, daysLogged = 0, lastLogDay = null,
            submissionsApproved = 0, submissionsRejected = 0, submissionsPending = 0, reportsAccepted = 0, reportsRejected = 0,
            reportsOpen = 0, checks = 0, moderationActions = 0, moderationActive = 0, lastModerationReason = null,
            earlierApplications = 0, earlierDeclines = 0, membership = null,
        )
    }

    private suspend fun pendingFor(userId: UserId): BuilderApplication? = database.transaction {
        BuilderApplicationsTable.selectAll()
            .where { (BuilderApplicationsTable.userId eq userId.value) and isPending() }
            .singleOrNull()
            ?.toApplication()
    }

    private fun byId(id: UUID): BuilderApplication? =
        BuilderApplicationsTable.selectAll().where { BuilderApplicationsTable.id eq id }.singleOrNull()?.toApplication()

    private fun isPending() = BuilderApplicationsTable.status eq ApplicationStatus.Pending.wire

    private fun ResultRow.toApplication() = BuilderApplication(
        id = this[BuilderApplicationsTable.id],
        userId = UserId(this[BuilderApplicationsTable.userId]),
        status = ApplicationStatus.fromWire(this[BuilderApplicationsTable.status]) ?: ApplicationStatus.Pending,
        createdAt = this[BuilderApplicationsTable.createdAt].toKotlinInstant(),
        termsVersion = this[BuilderApplicationsTable.termsVersion],
        photoRetention = this[BuilderApplicationsTable.photoRetention],
        motivation = this[BuilderApplicationsTable.motivation],
        shopping = ShoppingFrequency.fromWire(this[BuilderApplicationsTable.shopping]),
        region = LabelRegion.fromWire(this[BuilderApplicationsTable.region]),
        via = this[BuilderApplicationsTable.via],
        decidedAt = this[BuilderApplicationsTable.decidedAt]?.toKotlinInstant(),
        decidedBy = this[BuilderApplicationsTable.decidedBy],
        decisionNote = this[BuilderApplicationsTable.decisionNote],
        withdrawnAt = this[BuilderApplicationsTable.withdrawnAt]?.toKotlinInstant(),
    )

    private fun ExposedSQLException.sqlState(): String? = sqlState ?: (cause as? SQLException)?.sqlState

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val FOREIGN_KEY_VIOLATION = "23503"
        const val MAX_MOTIVATION = 500
        const val MAX_VIA = 16
        const val MAX_ACTOR = 200
        const val MAX_NOTE = 500
        val uuid = UUIDColumnType()

        /** Stands in for "no application to leave out", so the query keeps one shape. */
        val NO_ID: UUID = UUID(0, 0)

        /** One row for one account: every count reads an index on the person's id. */
        val SUMMARY_SQL = """
            SELECT
              u.email,
              p.display_name,
              coalesce(u.is_anonymous, false) AS is_guest,
              u.created_at,
              (SELECT count(*) FROM public.log_entries WHERE user_id = u.id AND NOT deleted) AS entries,
              (SELECT count(DISTINCT day) FROM public.log_entries WHERE user_id = u.id AND NOT deleted) AS days_logged,
              (SELECT max(day)::text FROM public.log_entries WHERE user_id = u.id AND NOT deleted) AS last_log_day,
              (SELECT count(*) FROM public.food_submissions WHERE user_id = u.id AND status = 'approved') AS submissions_approved,
              (SELECT count(*) FROM public.food_submissions WHERE user_id = u.id AND status = 'rejected') AS submissions_rejected,
              (SELECT count(*) FROM public.food_submissions WHERE user_id = u.id AND status = 'pending') AS submissions_pending,
              (SELECT count(*) FROM public.food_reports WHERE user_id = u.id AND status = 'accepted') AS reports_accepted,
              (SELECT count(*) FROM public.food_reports WHERE user_id = u.id AND status IN ('rejected', 'duplicate')) AS reports_rejected,
              (SELECT count(*) FROM public.food_reports WHERE user_id = u.id AND status = 'open') AS reports_open,
              (SELECT count(*) FROM public.food_verifications WHERE user_id = u.id) AS checks,
              (SELECT count(*) FROM public.moderation_actions WHERE user_id = u.id) AS moderation_actions,
              (SELECT count(*) FROM public.moderation_actions WHERE user_id = u.id AND lifted_at IS NULL AND (until IS NULL OR until > now())) AS moderation_active,
              (SELECT reason FROM public.moderation_actions WHERE user_id = u.id ORDER BY created_at DESC LIMIT 1) AS last_moderation_reason,
              (SELECT count(*) FROM public.builder_applications WHERE user_id = u.id AND id <> ?) AS earlier_applications,
              (SELECT count(*) FROM public.builder_applications WHERE user_id = u.id AND id <> ? AND status = 'declined') AS earlier_declines,
              (SELECT status FROM public.builder_members WHERE user_id = u.id) AS membership
            FROM auth.users u
            LEFT JOIN public.profiles p ON p.user_id = u.id
            WHERE u.id = ?
        """.trimIndent()
    }
}
