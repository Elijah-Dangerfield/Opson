package com.opson.server.domain

import com.opson.server.di.ServerScope
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.random.Random

/** A number in 0..1 for the audit lottery. Its own seam so a test can say who is audited. */
fun interface AuditDraw {
    fun next(): Double
}

@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class RandomAuditDraw : AuditDraw {
    override fun next(): Double = Random.nextDouble()
}

/**
 * Routes a submission once it is stored (pending, as every submission starts): the owner's go
 * straight in, a Builder's is read a second time and either auto-approved in the narrow lane or
 * queued with its reasons, everyone else's is queued as before. Whatever happens, the decision
 * is kept on the submission (`routing`), so the shadow lane can be measured against reviewers
 * before anyone turns it on.
 */
@SingleIn(ServerScope::class)
@Inject
class ContributionRouter(
    private val permissions: Permissions,
    private val builders: BuilderService,
    private val reader: CloudLabelReader,
    private val submissions: FoodSubmissionRepository,
    private val draw: AuditDraw,
) {
    suspend fun route(submission: FoodSubmission, photos: PackPhotos): RoutingDecision {
        val checks = LabelChecks.check(submission.submitted)
        val capture = submission.capture
        val grounding = capture?.let { LabelGrounding.check(submission.submitted, it.lines) }
        if (permissions.can(submission.userId, Permission.FoodEditDirect)) {
            return routeOwner(submission, checks, grounding)
        }
        val standing = builders.standing(submission.userId)
        val policy = builders.policy()
        val cloud = if (capture != null && standing.trust != TrustLevel.Anyone && standing.trust != TrustLevel.Removed) {
            when (val read = reader.read(photos.label, photos.labelType)) {
                is CloudRead.Read -> {
                    val differs = HeadlineAgreement.readsDiffer(submission.submitted, read.reading.facts)
                    CloudCheck(read.model, read.reading.readable, read.reading.readable && differs.isEmpty(), differs, read.latencyMs)
                }
                CloudRead.NotConfigured, is CloudRead.Failed -> null
            }
        } else {
            null
        }
        var decision = SubmissionRouter.decide(standing.trust, policy.autoApprove, capture, checks, grounding, cloud, reader.configured)
        if (decision.lane == Lane.Auto) {
            val outcome = submissions.approve(submission.id, null, "auto:$AUTO_RULE")
            decision = if (outcome is ReviewOutcome.Done) {
                decision.copy(audit = SubmissionRouter.audits(standing.history.autoApprovedSinceTrusted, policy.auditRate, draw.next()))
            } else {
                decision.copy(lane = Lane.Review, reasons = decision.reasons + outcome.code())
            }
        }
        submissions.recordRouting(submission.id, decision)
        return decision
    }

    /**
     * The owner's and admins' foods skip the queue, not the record: approved on arrival as
     * `owner:<id>`, photos and checks kept as for anyone. A blocking check still stops it, for
     * them to fix in the console.
     */
    private suspend fun routeOwner(submission: FoodSubmission, checks: List<LabelCheck>, grounding: Grounding?): RoutingDecision {
        val blocking = checks.filter { it.code.blocking }
        val base = RoutingDecision(
            lane = Lane.Owner,
            wouldAutoApprove = false,
            reasons = emptyList(),
            confidence = null,
            trust = TrustLevel.Builder,
            mode = AutoApproveMode.On,
            grounding = grounding,
            cloud = null,
        )
        val decision = if (blocking.isNotEmpty()) {
            base.copy(lane = Lane.Review, reasons = blocking.map { "check:${it.code.wire}" })
        } else {
            when (val outcome = submissions.approve(submission.id, null, "owner:${submission.userId.value}")) {
                is ReviewOutcome.Done -> base
                else -> base.copy(lane = Lane.Review, reasons = listOf(outcome.code()))
            }
        }
        submissions.recordRouting(submission.id, decision)
        return decision
    }

    private fun ReviewOutcome.code(): String = when (this) {
        is ReviewOutcome.Done -> "approved"
        ReviewOutcome.NotFound -> "not_found"
        ReviewOutcome.AlreadyDecided -> "already_decided"
        is ReviewOutcome.Incomplete -> "incomplete"
        is ReviewOutcome.BarcodeTaken -> "barcode_taken"
    }

    companion object {
        /** The rule's name in `reviewed_by` (`auto:builder_v1`); a new rule gets a new name, so the record says which approved what. */
        const val AUTO_RULE = "builder_v1"
    }
}
