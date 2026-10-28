package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.FoodSubmissionPhotosTable
import com.opson.server.db.FoodSubmissionsTable
import com.opson.server.db.SubmittedFoodsTable
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.Barcodes
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.FoodSubmission
import com.opson.server.domain.FoodSubmissionRepository
import com.opson.server.domain.LabelCheckCode
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.NewFoodSubmission
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.PhotoKind
import com.opson.server.domain.ReviewOutcome
import com.opson.server.domain.ServingUnit
import com.opson.server.domain.StoredPhoto
import com.opson.server.domain.SubmissionOrigin
import com.opson.server.domain.SubmissionStatus
import com.opson.server.domain.SubmitOutcome
import com.opson.server.domain.UserId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.SQLException
import java.util.UUID
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `food_submissions`, their photos, and the pending `foods` row each one
 * makes. Every write is one transaction; the id the app made is the
 * idempotency key, and a unique violation on it is the arbiter between two
 * copies of the same request.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresFoodSubmissionRepository(
    private val database: Database,
    private val clock: Clock,
) : FoodSubmissionRepository {

    override suspend fun submit(submission: NewFoodSubmission): SubmitOutcome {
        existing(submission)?.let { return it }
        return try {
            database.transaction {
                val foodId = UUID.randomUUID()
                SubmittedFoodsTable.insert {
                    it[id] = foodId
                    it[sourceKind] = CatalogSource.UserSubmitted.wire
                    it[sourceId] = submission.id.toString()
                    it[reviewStatus] = SubmissionStatus.Pending.wire
                    // The barcode waits on the submission until a reviewer approves it.
                    it[barcode] = null
                    writeFacts(it, submission.facts)
                }
                FoodSubmissionsTable.insert {
                    it[id] = submission.id
                    it[userId] = submission.userId.value
                    it[this.foodId] = foodId
                    it[createdAt] = clock.now().toJavaInstant()
                    it[status] = SubmissionStatus.Pending.wire
                    it[barcode] = submission.barcode
                    it[gtin] = submission.barcode?.let(Barcodes::gtin14)
                    it[origin] = submission.origin.wire
                    it[model] = submission.model?.take(MAX_MODEL)
                    it[parsed] = submission.parsed?.let(LabelJson::reading)
                    it[submitted] = LabelJson.facts(submission.facts, submission.acknowledged)
                    it[checks] = LabelJson.checks(submission.checks)
                    it[customFoodId] = submission.customFoodId
                }
                listOf(
                    Triple(PhotoKind.Front, submission.photos.front, submission.photos.frontType.mediaType),
                    Triple(PhotoKind.Label, submission.photos.label, submission.photos.labelType.mediaType),
                ).forEach { (kind, bytes, type) ->
                    FoodSubmissionPhotosTable.insert {
                        it[submissionId] = submission.id
                        it[this.kind] = kind.wire
                        it[mediaType] = type
                        it[this.bytes] = bytes
                    }
                }
            }
            SubmitOutcome.Created(checkNotNull(get(submission.id)))
        } catch (e: ExposedSQLException) {
            if (!e.isUniqueViolation()) throw e
            existing(submission) ?: throw e
        }
    }

    private suspend fun existing(submission: NewFoodSubmission): SubmitOutcome? {
        val found = get(submission.id) ?: return null
        return if (found.userId == submission.userId) SubmitOutcome.Existing(found) else SubmitOutcome.Conflict
    }

    override suspend fun isVisible(foodId: UUID, userId: UserId): Boolean = database.transaction {
        val status = SubmittedFoodsTable.select(SubmittedFoodsTable.reviewStatus)
            .where { SubmittedFoodsTable.id eq foodId }
            .singleOrNull()
            ?.get(SubmittedFoodsTable.reviewStatus)
        status == null || status == SubmissionStatus.Approved.wire ||
            FoodSubmissionsTable.select(FoodSubmissionsTable.id)
                .where { (FoodSubmissionsTable.foodId eq foodId) and (FoodSubmissionsTable.userId eq userId.value) }
                .limit(1)
                .any()
    }

    override suspend fun mine(userId: UserId, limit: Int): List<FoodSubmission> = database.transaction {
        val rows = FoodSubmissionsTable.selectAll()
            .where { FoodSubmissionsTable.userId eq userId.value }
            .orderBy(FoodSubmissionsTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .toList()
        val withPhotos = photoOwners(rows.map { it[FoodSubmissionsTable.id] })
        rows.map { it.toSubmission(it[FoodSubmissionsTable.id] in withPhotos) }
    }

    override suspend fun pendingFoodFor(userId: UserId, barcode: String): UUID? {
        val gtin = Barcodes.gtin14(barcode) ?: return null
        return database.transaction {
            FoodSubmissionsTable.select(FoodSubmissionsTable.foodId)
                .where {
                    (FoodSubmissionsTable.userId eq userId.value) and
                        (FoodSubmissionsTable.gtin eq gtin) and
                        (FoodSubmissionsTable.status eq SubmissionStatus.Pending.wire)
                }
                .orderBy(FoodSubmissionsTable.createdAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.get(FoodSubmissionsTable.foodId)
        }
    }

    override suspend fun list(status: SubmissionStatus, limit: Int): List<FoodSubmission> = database.transaction {
        val order = if (status == SubmissionStatus.Pending) {
            FoodSubmissionsTable.createdAt to SortOrder.ASC
        } else {
            FoodSubmissionsTable.reviewedAt to SortOrder.DESC
        }
        val rows = FoodSubmissionsTable.selectAll()
            .where { FoodSubmissionsTable.status eq status.wire }
            .orderBy(order)
            .limit(limit)
            .toList()
        val withPhotos = photoOwners(rows.map { it[FoodSubmissionsTable.id] })
        rows.map { it.toSubmission(it[FoodSubmissionsTable.id] in withPhotos) }
    }

    override suspend fun count(status: SubmissionStatus): Long = database.transaction {
        FoodSubmissionsTable.selectAll().where { FoodSubmissionsTable.status eq status.wire }.count()
    }

    override suspend fun get(id: UUID): FoodSubmission? = database.transaction {
        FoodSubmissionsTable.selectAll().where { FoodSubmissionsTable.id eq id }.singleOrNull()
            ?.let { it.toSubmission(id in photoOwners(listOf(id))) }
    }

    override suspend fun photo(id: UUID, kind: PhotoKind): StoredPhoto? = database.transaction {
        FoodSubmissionPhotosTable.selectAll()
            .where { (FoodSubmissionPhotosTable.submissionId eq id) and (FoodSubmissionPhotosTable.kind eq kind.wire) }
            .singleOrNull()
            ?.let { StoredPhoto(it[FoodSubmissionPhotosTable.mediaType], it[FoodSubmissionPhotosTable.bytes]) }
    }

    override suspend fun edit(id: UUID, facts: LabelFacts, reviewer: String): ReviewOutcome = decide(id) { row ->
        val foodId = row[FoodSubmissionsTable.foodId]
        SubmittedFoodsTable.update({ SubmittedFoodsTable.id eq foodId }) { writeFacts(it, facts) }
        FoodSubmissionsTable.update({ FoodSubmissionsTable.id eq id }) {
            it[reviewed] = LabelJson.facts(facts)
            it[checks] = LabelJson.checks(LabelChecks.check(facts))
            it[reviewedBy] = reviewer.take(MAX_REVIEWER)
        }
        null
    }

    override suspend fun approve(id: UUID, facts: LabelFacts?, reviewer: String): ReviewOutcome = try {
        decide(id) { row ->
            val current = facts ?: row.current()
            LabelChecks.check(current).firstOrNull { it.code == LabelCheckCode.MissingRequired }?.let {
                return@decide ReviewOutcome.Incomplete(it.fields)
            }
            val foodId = row[FoodSubmissionsTable.foodId]
            val gtin = (current.barcode ?: row[FoodSubmissionsTable.barcode])?.let(Barcodes::gtin14)
            if (gtin != null) foodWithBarcode(gtin, except = foodId)?.let { return@decide ReviewOutcome.BarcodeTaken(it) }
            SubmittedFoodsTable.update({ SubmittedFoodsTable.id eq foodId }) {
                writeFacts(it, current)
                it[barcode] = gtin
                it[reviewStatus] = SubmissionStatus.Approved.wire
                // A reviewer has checked it against the photos: V10 left `verified` for exactly this.
                it[verified] = true
            }
            FoodSubmissionsTable.update({ FoodSubmissionsTable.id eq id }) {
                it[status] = SubmissionStatus.Approved.wire
                if (facts != null) it[reviewed] = LabelJson.facts(facts)
                it[checks] = LabelJson.checks(LabelChecks.check(current))
                it[reviewedAt] = clock.now().toJavaInstant()
                it[reviewedBy] = reviewer.take(MAX_REVIEWER)
            }
            null
        }
    } catch (e: ExposedSQLException) {
        // Another approval or a barcode lookup's cache took the code in the meantime.
        if (!e.isUniqueViolation()) throw e
        val submission = get(id) ?: return ReviewOutcome.NotFound
        val gtin = (facts?.barcode ?: submission.barcode)?.let(Barcodes::gtin14) ?: throw e
        database.transaction { foodWithBarcode(gtin, except = submission.foodId) }?.let { ReviewOutcome.BarcodeTaken(it) } ?: throw e
    }

    override suspend fun reject(id: UUID, reason: String, reviewer: String): ReviewOutcome = decide(id) { row ->
        SubmittedFoodsTable.update({ SubmittedFoodsTable.id eq row[FoodSubmissionsTable.foodId] }) {
            it[reviewStatus] = SubmissionStatus.Rejected.wire
        }
        FoodSubmissionsTable.update({ FoodSubmissionsTable.id eq id }) {
            it[status] = SubmissionStatus.Rejected.wire
            it[rejectReason] = reason.take(MAX_REASON)
            it[reviewedAt] = clock.now().toJavaInstant()
            it[reviewedBy] = reviewer.take(MAX_REVIEWER)
        }
        null
    }

    override suspend fun purgeDecidedPhotos(before: Instant): Int = database.transaction {
        val decided = FoodSubmissionsTable.select(FoodSubmissionsTable.id)
            .where { FoodSubmissionsTable.reviewedAt lessEq before.toJavaInstant() }
            .map { it[FoodSubmissionsTable.id] }
        if (decided.isEmpty()) return@transaction 0
        FoodSubmissionPhotosTable.deleteWhere { submissionId inList decided }
    }

    /**
     * Runs [change] on a pending submission's row, inside one transaction.
     * [change] returns an outcome to stop with, or null when it made its change.
     */
    private suspend fun decide(id: UUID, change: (ResultRow) -> ReviewOutcome?): ReviewOutcome {
        val stopped = database.transaction {
            val row = FoodSubmissionsTable.selectAll().where { FoodSubmissionsTable.id eq id }.forUpdate().singleOrNull()
                ?: return@transaction ReviewOutcome.NotFound
            if (row[FoodSubmissionsTable.status] != SubmissionStatus.Pending.wire) return@transaction ReviewOutcome.AlreadyDecided
            change(row)
        }
        return stopped ?: ReviewOutcome.Done(checkNotNull(get(id)))
    }

    /** Any other food answering any form of [gtin]. */
    private fun foodWithBarcode(gtin: String, except: UUID): UUID? =
        SubmittedFoodsTable.select(SubmittedFoodsTable.id)
            .where { SubmittedFoodsTable.barcode inList Barcodes.equivalents(gtin) }
            .map { it[SubmittedFoodsTable.id] }
            .firstOrNull { it != except }

    private fun photoOwners(ids: List<UUID>): Set<UUID> =
        if (ids.isEmpty()) {
            emptySet()
        } else {
            FoodSubmissionPhotosTable.select(FoodSubmissionPhotosTable.submissionId)
                .where { FoodSubmissionPhotosTable.submissionId inList ids }
                .mapTo(HashSet()) { it[FoodSubmissionPhotosTable.submissionId] }
        }

    private fun ResultRow.current(): LabelFacts =
        (this[FoodSubmissionsTable.reviewed] ?: this[FoodSubmissionsTable.submitted]).let { LabelJson.facts(it).toFacts() }

    private fun ResultRow.toSubmission(hasPhotos: Boolean): FoodSubmission {
        val submitted = LabelJson.facts(this[FoodSubmissionsTable.submitted])
        return FoodSubmission(
            id = this[FoodSubmissionsTable.id],
            userId = UserId(this[FoodSubmissionsTable.userId]),
            foodId = this[FoodSubmissionsTable.foodId],
            createdAt = this[FoodSubmissionsTable.createdAt].toKotlinInstant(),
            status = SubmissionStatus.fromWire(this[FoodSubmissionsTable.status]) ?: SubmissionStatus.Pending,
            origin = SubmissionOrigin.fromWire(this[FoodSubmissionsTable.origin]) ?: SubmissionOrigin.Barcode,
            barcode = this[FoodSubmissionsTable.barcode],
            gtin = this[FoodSubmissionsTable.gtin],
            model = this[FoodSubmissionsTable.model],
            parsed = this[FoodSubmissionsTable.parsed]?.let(LabelJson::reading),
            submitted = submitted.toFacts(),
            reviewed = this[FoodSubmissionsTable.reviewed]?.let { LabelJson.facts(it).toFacts() },
            acknowledged = submitted.acknowledgedCodes(),
            checks = LabelJson.checks(this[FoodSubmissionsTable.checks]),
            reviewedAt = this[FoodSubmissionsTable.reviewedAt]?.toKotlinInstant(),
            reviewedBy = this[FoodSubmissionsTable.reviewedBy],
            rejectReason = this[FoodSubmissionsTable.rejectReason],
            hasPhotos = hasPhotos,
            customFoodId = this[FoodSubmissionsTable.customFoodId],
        )
    }

    @Serializable
    internal data class PortionJson(val label: String, val grams: Double?)

    private fun writeFacts(row: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>, facts: LabelFacts) {
        fun per100(key: NutrientKey): Float? = facts.per100(key)?.toFloat()
        row[SubmittedFoodsTable.name] = facts.name?.trim()?.ifEmpty { null } ?: UNNAMED
        row[SubmittedFoodsTable.brand] = facts.brand?.trim()?.ifEmpty { null }
        row[SubmittedFoodsTable.kcal100g] = per100(NutrientKey.Energy)
        row[SubmittedFoodsTable.protein100g] = per100(NutrientKey.Protein)
        row[SubmittedFoodsTable.carbs100g] = per100(NutrientKey.Carbs)
        row[SubmittedFoodsTable.fat100g] = per100(NutrientKey.Fat)
        row[SubmittedFoodsTable.fiber100g] = per100(NutrientKey.Fiber)
        val micros = NutrientKey.entries.filterNot { it.isColumn }.mapNotNull { key -> facts.per100(key)?.let { key.wire to it } }.toMap()
        row[SubmittedFoodsTable.micros] = LabelJson.json.encodeToString(MapSerializer(String.serializer(), Double.serializer()), micros)
        row[SubmittedFoodsTable.portions] = LabelJson.json.encodeToString(ListSerializer(PortionJson.serializer()), portions(facts))
    }

    private fun ExposedSQLException.isUniqueViolation(): Boolean =
        sqlState == UNIQUE_VIOLATION || (cause as? SQLException)?.sqlState == UNIQUE_VIOLATION

    companion object {
        private const val UNIQUE_VIOLATION = "23505"
        private const val MAX_MODEL = 64
        private const val MAX_REVIEWER = 100
        private const val MAX_REASON = 500

        /** A food with no name yet: only possible while pending, and approval refuses it. */
        private const val UNNAMED = "Unnamed food"

        /**
         * The label's serving as the food's one portion. Grams only for a
         * serving stated in grams: a drink's ml has no honest weight without a
         * density, as with Branded Foods.
         */
        internal fun portions(facts: LabelFacts): List<PortionJson> {
            val size = facts.servingSize?.takeIf { it > 0 } ?: return emptyList()
            val unit = facts.servingUnit ?: return emptyList()
            val amount = if (size == size.roundToLong().toDouble()) size.roundToLong().toString() else "%.1f".format(size)
            val printed = "$amount ${unit.wire}"
            val label = facts.servingLabel?.let { if (it.contains(printed)) it else "$it ($printed)" } ?: "1 serving ($printed)"
            return listOf(PortionJson(label, size.takeIf { unit == ServingUnit.Gram }))
        }
    }
}
