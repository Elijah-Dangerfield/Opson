package com.opson.server.domain

import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What the model read off a pack: the [facts] in the catalog's units, how sure
 * it was of each field (0 to 1, by the form's field keys: `name`, `brand`,
 * `barcode`, `serving_size`, `servings_per_container`, and each
 * [NutrientKey.wire]), and whether the label photo showed a nutrition panel
 * at all. [unitChecks] are what only the raw reading can see (kJ and kcal
 * that disagree); everything else is [LabelChecks] on [facts].
 */
data class LabelReading(
    val facts: LabelFacts,
    val confidence: Map<String, Double>,
    val readable: Boolean,
    val unitChecks: List<LabelCheck> = emptyList(),
) {
    val checks: List<LabelCheck> get() = unitChecks + LabelChecks.check(facts)
}

sealed interface LabelParseResult {
    data class Read(val reading: LabelReading, val model: String) : LabelParseResult

    /** No model key on this server: the app offers to fill the form in by hand. */
    data object NotConfigured : LabelParseResult

    /** [reason] is a short code for the log line, never anything from the photos. */
    data class Failed(val reason: String) : LabelParseResult
}

/**
 * A second, independent read of a Builder's label photo for routing (ai-options.md, read B):
 * a cheap model given the photo alone, never the phone's OCR, so its agreement with the phone
 * means two systems read the same digits. Keeps nothing; logs only the outcome.
 */
interface CloudLabelReader {
    /** False with no model key on this server: routing then holds auto-approval to a higher bar. */
    val configured: Boolean

    suspend fun read(label: ByteArray, type: ImageType): CloudRead
}

sealed interface CloudRead {
    data class Read(val reading: LabelReading, val model: String, val latencyMs: Long) : CloudRead
    data object NotConfigured : CloudRead

    /** [reason] is a short code for the log line. */
    data class Failed(val reason: String) : CloudRead
}

/** A photo beyond the front and the label: the ingredients list, which Builder mode can add. */
class ExtraPhoto(val bytes: ByteArray, val type: ImageType)

/** The two photos of a pack, as the app sent them: the front (name, brand) and the nutrition label. */
class PackPhotos(val front: ByteArray, val frontType: ImageType, val label: ByteArray, val labelType: ImageType)

/** Reads a pack's two photos into a [LabelReading]. Keeps neither photo, logs nothing from them. */
interface LabelParser {
    suspend fun parse(photos: PackPhotos): LabelParseResult
}

/** Limits on the label routes. */
object FoodLabelLimits {
    /** Two images of up to [MAX_IMAGE_BYTES], base64 (4/3), and the form: checked before the body is read. */
    const val MAX_BODY_BYTES = 12L * 1024 * 1024

    const val MAX_IMAGE_BYTES = PhotoLimits.MAX_IMAGE_BYTES
    const val MAX_NAME = 200
    const val MAX_BRAND = 120
    const val MAX_SERVING_LABEL = 80
    const val MAX_ACKNOWLEDGED = 20

    /** Larger than any real serving or pack, smaller than a typo that adds three zeros. */
    const val MAX_SERVING_SIZE = 5_000.0
    const val MAX_SERVINGS_PER_CONTAINER = 1_000.0

    /**
     * Per 100 g, 900 kcal is pure fat; this is pure fat in kJ, so a label whose
     * kJ line was read as kcal still reaches review with the check that says so.
     */
    const val MAX_KCAL_PER_100 = 4_000.0
    const val MAX_AMOUNT = 100_000.0

    /** A dense panel in two languages is about 120 lines; this leaves room and no more. */
    const val MAX_CAPTURE_LINES = 400
    const val MAX_CAPTURE_LINE = 300
    const val MAX_CAPTURE_ENGINE = 32
    const val MAX_CAPTURE_SIGNALS = 8_000
}

enum class SubmissionStatus(val wire: String) {
    Pending("pending"),
    Approved("approved"),
    Rejected("rejected"),
    ;

    companion object {
        fun fromWire(value: String): SubmissionStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** Where the person started: a barcode nothing knew, or a search with no results. */
enum class SubmissionOrigin(val wire: String) {
    Barcode("barcode"),
    Search("search"),
    ;

    companion object {
        fun fromWire(value: String?): SubmissionOrigin? = entries.firstOrNull { it.wire == value }
    }
}

/** A new submission, as the route validated it. [parsed] is the server's own copy of the model's reading, when it still has it. */
data class NewFoodSubmission(
    val id: UUID,
    val userId: UserId,
    val barcode: String?,
    val origin: SubmissionOrigin,
    val facts: LabelFacts,
    val checks: List<LabelCheck>,
    val acknowledged: List<LabelCheckCode>,
    val parsed: LabelReading?,
    val model: String?,
    val photos: PackPhotos,
    /** The submitter's own copy (a custom food the app saved first), so the app can mark it while review runs. */
    val customFoodId: UUID? = null,
    /** Builder mode's optional photo of the ingredients list. */
    val ingredients: ExtraPhoto? = null,
    /** What the phone read off the label, in Builder mode. */
    val capture: LabelCapture? = null,
)

/** The review record, for the submitter's own lookups and the admin queue. */
@OptIn(ExperimentalTime::class)
data class FoodSubmission(
    val id: UUID,
    val userId: UserId,
    val foodId: UUID,
    val createdAt: Instant,
    val status: SubmissionStatus,
    val origin: SubmissionOrigin,
    val barcode: String?,
    val gtin: String?,
    val model: String?,
    val parsed: LabelReading?,
    val submitted: LabelFacts,
    /** A reviewer's corrections, or null when they changed nothing. */
    val reviewed: LabelFacts?,
    val acknowledged: List<LabelCheckCode>,
    /** What the checks say about the food as it stands now (the submission, or a reviewer's edit). */
    val checks: List<LabelCheck>,
    val reviewedAt: Instant?,
    val reviewedBy: String?,
    val rejectReason: String?,
    val hasPhotos: Boolean,
    /** The submitter's own copy of the food, when the app said which. */
    val customFoodId: UUID? = null,
    val lane: Lane? = null,
    val routing: RoutingDecision? = null,
    val capture: LabelCapture? = null,
    val audit: AuditState? = null,
    val auditNote: String? = null,
    val rejectCode: RejectCode? = null,
) {
    /** The food as it stands: the reviewer's version once there is one. */
    val current: LabelFacts get() = reviewed ?: submitted
}

enum class PhotoKind(val wire: String) {
    Front("front"),
    Label("label"),
    Ingredients("ingredients"),
    ;

    companion object {
        fun fromWire(value: String?): PhotoKind? = entries.firstOrNull { it.wire == value }
    }
}

class StoredPhoto(val mediaType: String, val bytes: ByteArray)

/** The random audit of an auto-approval (builder-program.md, "Audits"). */
enum class AuditState(val wire: String) {
    Pending("pending"),
    Passed("passed"),

    /** The food went back to pending and the Builder's record took the miss. */
    Failed("failed"),
    ;

    companion object {
        fun fromWire(value: String?): AuditState? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Why a reviewer rejected a submission, from a short list, beside the free-text reason the
 * submitter reads. Trust reads it: [FakeData] and [CopiedData] end a Builder's chance of
 * Trusted, a blurry photo does not.
 */
enum class RejectCode(val wire: String) {
    Blurry("blurry"),
    NotFood("not_food"),
    Duplicate("duplicate"),
    WrongColumn("wrong_column"),
    WrongValues("wrong_values"),
    FakeData("fake_data"),
    CopiedData("copied_data"),
    Other("other"),
    ;

    companion object {
        fun fromWire(value: String?): RejectCode? = entries.firstOrNull { it.wire == value }
    }
}

sealed interface ReviewOutcome {
    data class Done(val submission: FoodSubmission) : ReviewOutcome
    data object NotFound : ReviewOutcome

    /** Already approved or rejected. */
    data object AlreadyDecided : ReviewOutcome

    /** Approving needs the required fields; [fields] are the missing ones. */
    data class Incomplete(val fields: List<String>) : ReviewOutcome

    /** Another food already answers this barcode; [foodId] is that food. */
    data class BarcodeTaken(val foodId: UUID) : ReviewOutcome
}

sealed interface SubmitOutcome {
    data class Created(val submission: FoodSubmission) : SubmitOutcome

    /** The same id again: the first submission, unchanged. A different person's id is [Conflict]. */
    data class Existing(val submission: FoodSubmission) : SubmitOutcome
    data object Conflict : SubmitOutcome
}

@OptIn(ExperimentalTime::class)
interface FoodSubmissionRepository {
    /** Stores the submission, its photos and its pending food in one transaction. Idempotent on the id. */
    suspend fun submit(submission: NewFoodSubmission): SubmitOutcome

    /** Whether [userId] may see the catalog food [foodId]: approved, or their own submission. */
    suspend fun isVisible(foodId: UUID, userId: UserId): Boolean

    /** [userId]'s own submissions, newest first: what the app marks as unverified or checked. */
    suspend fun mine(userId: UserId, limit: Int): List<FoodSubmission>

    /** [userId]'s own pending food for any form of [barcode], so a second scan finds what they just added. */
    suspend fun pendingFoodFor(userId: UserId, barcode: String): UUID?

    suspend fun list(status: SubmissionStatus, limit: Int): List<FoodSubmission>

    suspend fun count(status: SubmissionStatus): Long

    suspend fun get(id: UUID): FoodSubmission?

    suspend fun photo(id: UUID, kind: PhotoKind): StoredPhoto?

    /** A reviewer's correction, before deciding. Re-runs the checks. */
    suspend fun edit(id: UUID, facts: LabelFacts, reviewer: String): ReviewOutcome

    /** Into the catalog for everyone, with [facts] when the reviewer changed something. */
    suspend fun approve(id: UUID, facts: LabelFacts?, reviewer: String): ReviewOutcome

    suspend fun reject(id: UUID, reason: String, reviewer: String, code: RejectCode? = null): ReviewOutcome

    /**
     * Deletes the photos of submissions decided before [before], except those of active Builders
     * who let Opson keep them, which go once decided before [retainedBefore]. Returns how many
     * submissions lost theirs.
     */
    suspend fun purgeDecidedPhotos(before: Instant, retainedBefore: Instant = before): Int

    /** Keeps how routing decided, and opens the audit when an auto-approval drew one. */
    suspend fun recordRouting(id: UUID, decision: RoutingDecision)

    /** Auto-approvals waiting for their audit, oldest first. */
    suspend fun audits(limit: Int): List<FoodSubmission>

    suspend fun auditCount(): Long

    /**
     * Closes an audit. A failed one puts the food back to pending for everyone (search and scans
     * stop finding it) and marks its provenance rolled back; [note] is what was wrong.
     */
    suspend fun closeAudit(id: UUID, passed: Boolean, note: String?, reviewer: String): ReviewOutcome
}

enum class LinkStatus(val wire: String) {
    Pending("pending"),
    Attached("attached"),
    Dismissed("dismissed"),
    ;

    companion object {
        fun fromWire(value: String): LinkStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** A barcode someone scanned and missed, then found as [foodId] by searching. [pairings] counts the times, across people. */
@OptIn(ExperimentalTime::class)
data class BarcodeLinkSuggestion(
    val gtin: String,
    val barcode: String,
    val foodId: UUID,
    val foodName: String,
    val foodBrand: String?,
    /** The food's own barcode, when it already has one. */
    val foodBarcode: String?,
    val pairings: Int,
    val firstAt: Instant,
    val lastAt: Instant,
    val status: LinkStatus,
)

sealed interface LinkOutcome {
    data object Done : LinkOutcome
    data object NotFound : LinkOutcome

    /** Another food already answers this barcode. */
    data class BarcodeTaken(val foodId: UUID) : LinkOutcome

    /** The food already has a barcode of its own; a food carries one. */
    data object FoodHasBarcode : LinkOutcome
}

@OptIn(ExperimentalTime::class)
interface BarcodeLinkRepository {
    /** Counts one more pairing of [barcode] with [foodId]. False when the food is not an approved catalog food. */
    suspend fun record(barcode: String, foodId: UUID): Boolean

    suspend fun list(status: LinkStatus, limit: Int): List<BarcodeLinkSuggestion>

    /** Gives [foodId] the barcode, when no other food has it. */
    suspend fun attach(gtin: String, foodId: UUID): LinkOutcome

    suspend fun dismiss(gtin: String, foodId: UUID): LinkOutcome
}
