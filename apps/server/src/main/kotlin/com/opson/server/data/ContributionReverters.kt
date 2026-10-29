package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.instantArg
import com.opson.server.db.queryRows
import com.opson.server.db.textArg
import com.opson.server.db.uuidArg
import com.opson.server.db.uuidOrNull
import com.opson.server.di.ServerScope
import com.opson.server.domain.ContributionReverter
import com.opson.server.domain.FoodReportRepository
import com.opson.server.domain.RevertOutcome
import com.opson.server.domain.RevertedContributions
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A person's foods from pack photos. Approved ones leave the catalog: `review_status` back to
 * `rejected`, the barcode released and `verified` off, so search and scans stop finding them and
 * anyone who logged one keeps their entry. Pending ones are rejected. One transaction.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class, multibinding = true)
@Inject
@OptIn(ExperimentalTime::class)
class SubmissionReverter(
    private val database: Database,
    private val clock: Clock,
) : ContributionReverter {

    override val kind: String = "submission"

    override suspend fun revert(userId: UserId, actor: String, reason: String, actionId: UUID?): RevertedContributions =
        database.transaction {
            val now = instantArg(clock.now())
            val rejectReason = textArg("Removed in moderation: $reason".take(MAX_REASON))
            val by = textArg(actor.take(MAX_ACTOR))
            val undone = queryRows(
                """
                WITH target AS (
                    SELECT id, food_id, ref_id FROM food_provenance
                    WHERE subject_key = public.subject_key(?) AND kind = 'submission' AND rolled_back_at IS NULL
                    FOR UPDATE
                ),
                unpublished AS (
                    UPDATE foods SET review_status = 'rejected', barcode = NULL, verified = FALSE
                    WHERE id IN (SELECT food_id FROM target) AND source = 'user_submitted'
                    RETURNING id
                ),
                decided AS (
                    UPDATE food_submissions SET status = 'rejected', reject_reason = ?, reviewed_at = ?, reviewed_by = ?
                    WHERE id IN (SELECT ref_id FROM target)
                    RETURNING id
                )
                UPDATE food_provenance SET rolled_back_at = ?, rolled_back_by = ?, rollback_action_id = ?
                WHERE id IN (SELECT id FROM target)
                RETURNING id
                """.trimIndent(),
                listOf(uuidArg(userId.value), rejectReason, now, by, now, by, uuidArg(actionId)),
            ) { true }.size
            val closed = queryRows(
                """
                WITH waiting AS (
                    UPDATE food_submissions SET status = 'rejected', reject_reason = ?, reviewed_at = ?, reviewed_by = ?
                    WHERE user_id = ? AND status = 'pending'
                    RETURNING food_id
                )
                UPDATE foods SET review_status = 'rejected' WHERE id IN (SELECT food_id FROM waiting) RETURNING id
                """.trimIndent(),
                listOf(rejectReason, now, by, uuidArg(userId.value)),
            ) { true }.size
            RevertedContributions(kind, undone = undone, closed = closed)
        }

    private companion object {
        const val MAX_REASON = 500
        const val MAX_ACTOR = 100
    }
}

/**
 * A person's reports. An accepted report led to a correction; that correction is reverted through
 * [FoodReportRepository.revert], which makes any older correction live again. One that has since
 * been replaced by a newer correction can't be reverted on its own and is counted as skipped,
 * for a reviewer to look at the food. Open reports are rejected.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class, multibinding = true)
@Inject
@OptIn(ExperimentalTime::class)
class ReportReverter(
    private val database: Database,
    private val reports: FoodReportRepository,
    private val clock: Clock,
) : ContributionReverter {

    override val kind: String = "report"

    override suspend fun revert(userId: UserId, actor: String, reason: String, actionId: UUID?): RevertedContributions {
        val accepted = database.transaction {
            queryRows(
                "SELECT id, correction_id FROM food_provenance " +
                    "WHERE subject_key = public.subject_key(?) AND kind = 'report' AND rolled_back_at IS NULL",
                listOf(uuidArg(userId.value)),
            ) { rs -> rs.getObject("id", UUID::class.java) to rs.uuidOrNull("correction_id") }
        }
        var undone = 0
        var skipped = 0
        accepted.mapNotNull { it.second }.distinct().forEach { correctionId ->
            when (reports.revert(correctionId, actor.take(MAX_ACTOR))) {
                is RevertOutcome.Reverted -> undone++
                RevertOutcome.NotLive, RevertOutcome.NotFound -> skipped++
            }
        }
        val now = clock.now()
        database.transaction {
            queryRows(
                "UPDATE food_provenance SET rolled_back_at = ?, rolled_back_by = ?, rollback_action_id = ? " +
                    "WHERE subject_key = public.subject_key(?) AND kind = 'report' AND rolled_back_at IS NULL RETURNING id",
                listOf(instantArg(now), textArg(actor.take(MAX_ACTOR)), uuidArg(actionId), uuidArg(userId.value)),
            ) { true }
        }
        val open = database.transaction {
            queryRows(
                "SELECT id FROM food_reports WHERE user_id = ? AND status = 'open'",
                listOf(uuidArg(userId.value)),
            ) { it.getObject("id", UUID::class.java) }
        }
        val closed = if (open.isEmpty()) 0 else reports.reject(open, "Removed in moderation: $reason".take(MAX_REASON), actor.take(MAX_ACTOR))
        return RevertedContributions(kind, undone = undone, closed = closed, skipped = skipped)
    }

    private companion object {
        const val MAX_REASON = 500
        const val MAX_ACTOR = 100
    }
}
