package com.opson.server.testing

import com.opson.server.domain.BarcodeLinkRepository
import com.opson.server.domain.BarcodeLinkSuggestion
import com.opson.server.domain.Barcodes
import com.opson.server.domain.FoodSubmission
import com.opson.server.domain.FoodSubmissionRepository
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.LabelParseResult
import com.opson.server.domain.LabelParser
import com.opson.server.domain.LinkOutcome
import com.opson.server.domain.LinkStatus
import com.opson.server.domain.NewFoodSubmission
import com.opson.server.domain.PackPhotos
import com.opson.server.domain.PhotoKind
import com.opson.server.domain.ReviewOutcome
import com.opson.server.domain.StoredPhoto
import com.opson.server.domain.SubmissionStatus
import com.opson.server.domain.SubmitOutcome
import com.opson.server.domain.UserId
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A [LabelParser] that answers [result] and remembers what it was shown. */
class FakeLabelParser(var result: LabelParseResult) : LabelParser {
    val seen = mutableListOf<PackPhotos>()

    override suspend fun parse(photos: PackPhotos): LabelParseResult {
        seen += photos
        return result
    }
}

/** [FoodSubmissionRepository] in memory, for route tests. The real rules live in the Postgres repository and its test. */
@OptIn(ExperimentalTime::class)
class FakeFoodSubmissionRepository : FoodSubmissionRepository {
    val submissions = linkedMapOf<UUID, FoodSubmission>()
    val photos = mutableMapOf<Pair<UUID, PhotoKind>, StoredPhoto>()
    var purgedBefore: Instant? = null

    override suspend fun submit(submission: NewFoodSubmission): SubmitOutcome {
        submissions[submission.id]?.let {
            return if (it.userId == submission.userId) SubmitOutcome.Existing(it) else SubmitOutcome.Conflict
        }
        val stored = FoodSubmission(
            id = submission.id,
            userId = submission.userId,
            foodId = UUID.randomUUID(),
            createdAt = Instant.parse("2026-10-27T12:00:00Z"),
            status = SubmissionStatus.Pending,
            origin = submission.origin,
            barcode = submission.barcode,
            gtin = submission.barcode?.let(Barcodes::gtin14),
            model = submission.model,
            parsed = submission.parsed,
            submitted = submission.facts,
            reviewed = null,
            acknowledged = submission.acknowledged,
            checks = submission.checks,
            reviewedAt = null,
            reviewedBy = null,
            rejectReason = null,
            hasPhotos = true,
            customFoodId = submission.customFoodId,
        )
        submissions[submission.id] = stored
        photos[submission.id to PhotoKind.Front] = StoredPhoto(submission.photos.frontType.mediaType, submission.photos.front)
        photos[submission.id to PhotoKind.Label] = StoredPhoto(submission.photos.labelType.mediaType, submission.photos.label)
        return SubmitOutcome.Created(stored)
    }

    override suspend fun isVisible(foodId: UUID, userId: UserId): Boolean {
        val submission = submissions.values.firstOrNull { it.foodId == foodId } ?: return true
        return submission.status == SubmissionStatus.Approved || submission.userId == userId
    }

    override suspend fun mine(userId: UserId, limit: Int): List<FoodSubmission> =
        submissions.values.filter { it.userId == userId }.reversed().take(limit)

    override suspend fun pendingFoodFor(userId: UserId, barcode: String): UUID? = submissions.values.lastOrNull {
        it.userId == userId && it.status == SubmissionStatus.Pending && it.gtin != null && it.gtin == Barcodes.gtin14(barcode)
    }?.foodId

    override suspend fun list(status: SubmissionStatus, limit: Int): List<FoodSubmission> =
        submissions.values.filter { it.status == status }.take(limit)

    override suspend fun count(status: SubmissionStatus): Long = submissions.values.count { it.status == status }.toLong()

    override suspend fun get(id: UUID): FoodSubmission? = submissions[id]

    override suspend fun photo(id: UUID, kind: PhotoKind): StoredPhoto? = photos[id to kind]

    override suspend fun edit(id: UUID, facts: LabelFacts, reviewer: String): ReviewOutcome =
        decide(id) { it.copy(reviewed = facts, checks = LabelChecks.check(facts), reviewedBy = reviewer) }

    override suspend fun approve(id: UUID, facts: LabelFacts?, reviewer: String): ReviewOutcome {
        val current = submissions[id] ?: return ReviewOutcome.NotFound
        val after = facts ?: current.current
        LabelChecks.check(after).firstOrNull { it.code.blocking && it.code.wire == "missing_required" }?.let {
            if (current.status == SubmissionStatus.Pending) return ReviewOutcome.Incomplete(it.fields)
        }
        return decide(id) {
            it.copy(status = SubmissionStatus.Approved, reviewed = facts ?: it.reviewed, reviewedBy = reviewer, reviewedAt = Instant.parse("2026-10-28T12:00:00Z"))
        }
    }

    override suspend fun reject(id: UUID, reason: String, reviewer: String): ReviewOutcome = decide(id) {
        it.copy(status = SubmissionStatus.Rejected, rejectReason = reason, reviewedBy = reviewer, reviewedAt = Instant.parse("2026-10-28T12:00:00Z"))
    }

    override suspend fun purgeDecidedPhotos(before: Instant): Int {
        purgedBefore = before
        return 0
    }

    private fun decide(id: UUID, change: (FoodSubmission) -> FoodSubmission): ReviewOutcome {
        val current = submissions[id] ?: return ReviewOutcome.NotFound
        if (current.status != SubmissionStatus.Pending) return ReviewOutcome.AlreadyDecided
        return ReviewOutcome.Done(change(current).also { submissions[id] = it })
    }
}

@OptIn(ExperimentalTime::class)
class FakeBarcodeLinkRepository : BarcodeLinkRepository {
    val recorded = mutableListOf<Pair<String, UUID>>()
    val suggestions = mutableListOf<BarcodeLinkSuggestion>()
    var attachOutcome: LinkOutcome = LinkOutcome.Done
    val decided = mutableListOf<Triple<String, String, UUID>>()

    override suspend fun record(barcode: String, foodId: UUID): Boolean {
        recorded += barcode to foodId
        return true
    }

    override suspend fun list(status: LinkStatus, limit: Int): List<BarcodeLinkSuggestion> =
        suggestions.filter { it.status == status }.take(limit)

    override suspend fun attach(gtin: String, foodId: UUID): LinkOutcome {
        decided += Triple("attach", gtin, foodId)
        return attachOutcome
    }

    override suspend fun dismiss(gtin: String, foodId: UUID): LinkOutcome {
        decided += Triple("dismiss", gtin, foodId)
        return LinkOutcome.Done
    }
}
