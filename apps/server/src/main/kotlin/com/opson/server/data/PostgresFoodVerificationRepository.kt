package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.FoodVerificationPhotosTable
import com.opson.server.db.FoodVerificationsTable
import com.opson.server.db.instantArg
import com.opson.server.db.queryRows
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.CorrectionValues
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.FoodVerification
import com.opson.server.domain.FoodVerificationRepository
import com.opson.server.domain.PackSide
import com.opson.server.domain.ReportPhoto
import com.opson.server.domain.ReviewerCount
import com.opson.server.domain.StoredPhoto
import com.opson.server.domain.UserId
import com.opson.server.domain.VerificationDecision
import com.opson.server.domain.VerificationOutcome
import com.opson.server.domain.VerificationStatus
import com.opson.server.domain.VerifyOutcome
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `food_verifications` (V31) and, since V34, the ones that wait for a reviewer with their photos
 * (`food_verification_photos`). The id the app made is the idempotency key. Accepting one with
 * values lays a correction over the existing food through [FoodCorrectionsSql.lay], in the same
 * transaction, so a verification never makes a new food.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresFoodVerificationRepository(
    private val database: Database,
    private val clock: Clock,
) : FoodVerificationRepository {

    override suspend fun record(verification: FoodVerification, photos: Map<PackSide, ReportPhoto>): VerifyOutcome = database.transaction {
        find(verification.id)?.let { found ->
            return@transaction if (found.userId == verification.userId) VerifyOutcome.Existing(found) else VerifyOutcome.Conflict
        }
        val confirmedBefore = verification.outcome == VerificationOutcome.Confirmed &&
            FoodVerificationsTable.selectAll().where {
                (FoodVerificationsTable.userId eq verification.userId.value) and
                    (FoodVerificationsTable.foodId eq verification.foodId) and
                    (FoodVerificationsTable.outcome eq VerificationOutcome.Confirmed.wire)
            }.any()
        val stored = if (confirmedBefore) verification.copy(outcome = VerificationOutcome.Unconfirmed) else verification
        val queued = stored.status == VerificationStatus.Open
        FoodVerificationsTable.insert {
            it[id] = stored.id
            it[foodId] = stored.foodId
            it[userId] = stored.userId.value
            it[createdAt] = stored.createdAt.toJavaInstant()
            it[outcome] = stored.outcome.wire
            it[grounded] = stored.grounded
            it[groundable] = stored.groundable
            it[engine] = stored.engine?.take(MAX_ENGINE)
            it[status] = stored.status?.wire
            it[problem] = stored.problem?.let(FoodVerificationJson::problem)
            it[snapshot] = stored.snapshot?.let(FoodReportJson::snapshot)
        }
        // A check decided on arrival keeps no photo: nobody would look at it.
        if (queued) {
            for ((side, photo) in photos) {
                FoodVerificationPhotosTable.insert {
                    it[verificationId] = stored.id
                    it[kind] = side.wire
                    it[mediaType] = photo.type.mediaType
                    it[bytes] = photo.bytes
                }
            }
        }
        VerifyOutcome.Recorded(stored.copy(photos = if (queued) photos.keys else emptySet()))
    }

    override suspend fun get(id: UUID): FoodVerification? = database.transaction { find(id) }

    override suspend fun open(limit: Int): List<FoodVerification> = database.transaction {
        withPhotos(
            FoodVerificationsTable.selectAll()
                .where { FoodVerificationsTable.status eq VerificationStatus.Open.wire }
                .orderBy(FoodVerificationsTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .toList(),
        )
    }

    override suspend fun countOpen(): Long = database.transaction {
        FoodVerificationsTable.selectAll().where { FoodVerificationsTable.status eq VerificationStatus.Open.wire }.count()
    }

    override suspend fun decided(limit: Int): List<FoodVerification> = database.transaction {
        withPhotos(
            FoodVerificationsTable.selectAll()
                .where { FoodVerificationsTable.resolvedAt.isNotNull() }
                .orderBy(FoodVerificationsTable.resolvedAt to SortOrder.DESC)
                .limit(limit)
                .toList(),
        )
    }

    override suspend fun photo(id: UUID, side: PackSide): StoredPhoto? = database.transaction {
        FoodVerificationPhotosTable.selectAll()
            .where { (FoodVerificationPhotosTable.verificationId eq id) and (FoodVerificationPhotosTable.kind eq side.wire) }
            .singleOrNull()
            ?.let { StoredPhoto(it[FoodVerificationPhotosTable.mediaType], it[FoodVerificationPhotosTable.bytes]) }
    }

    override suspend fun accept(id: UUID, values: CorrectionValues?, base: FoodSnapshot?, by: String, note: String?): VerificationDecision =
        database.transaction {
            val found = openForUpdate(id) ?: return@transaction notOpen(id)
            val reviewer = by.take(MAX_ACTOR)
            val correction = values?.takeUnless { it.isEmpty }?.let { v ->
                FoodCorrectionsSql.lay(found.foodId, v, base ?: found.snapshot ?: return@transaction VerificationDecision.NotFound, reviewer, clock.now())
            }
            // V31's once-per-person rule: a second confirmed check of the same food never counts twice.
            val confirmedBefore = FoodVerificationsTable.selectAll().where {
                (FoodVerificationsTable.userId eq found.userId.value) and
                    (FoodVerificationsTable.foodId eq found.foodId) and
                    (FoodVerificationsTable.outcome eq VerificationOutcome.Confirmed.wire)
            }.any()
            FoodVerificationsTable.update({ FoodVerificationsTable.id eq id }) {
                it[status] = VerificationStatus.Accepted.wire
                if (!confirmedBefore) it[outcome] = VerificationOutcome.Confirmed.wire
                it[resolvedAt] = clock.now().toJavaInstant()
                it[resolvedBy] = reviewer
                it[resolution] = note?.take(MAX_RESOLUTION)
                it[correctionId] = correction?.id
            }
            VerificationDecision.Accepted(checkNotNull(find(id)), correction)
        }

    override suspend fun reject(id: UUID, reason: String?, by: String): VerificationDecision = database.transaction {
        openForUpdate(id) ?: return@transaction notOpen(id)
        FoodVerificationsTable.update({ FoodVerificationsTable.id eq id }) {
            it[status] = VerificationStatus.Rejected.wire
            it[resolvedAt] = clock.now().toJavaInstant()
            it[resolvedBy] = by.take(MAX_ACTOR)
            it[resolution] = reason?.take(MAX_RESOLUTION)
        }
        VerificationDecision.Rejected(checkNotNull(find(id)))
    }

    override suspend fun purgeDecidedPhotos(before: Instant): Int = database.transaction {
        val decided = FoodVerificationsTable.select(FoodVerificationsTable.id)
            .where { FoodVerificationsTable.resolvedAt lessEq before.toJavaInstant() }
            .map { it[FoodVerificationsTable.id] }
        if (decided.isEmpty()) return@transaction 0
        FoodVerificationPhotosTable.deleteWhere { verificationId inList decided }
    }

    override suspend fun reviewerCounts(since: Instant): List<ReviewerCount> = database.transaction {
        queryRows(REVIEWER_SQL, listOf(instantArg(since), instantArg(since))) { rs ->
            ReviewerCount(
                reviewer = rs.getString("reviewer"),
                reportsAccepted = rs.getInt("reports_accepted"),
                reportsRejected = rs.getInt("reports_rejected"),
                reportsDuplicate = rs.getInt("reports_duplicate"),
                verificationsAccepted = rs.getInt("verifications_accepted"),
                verificationsRejected = rs.getInt("verifications_rejected"),
            )
        }.sortedWith(compareByDescending<ReviewerCount> { it.total }.thenBy { it.reviewer })
    }

    private fun openForUpdate(id: UUID): FoodVerification? =
        FoodVerificationsTable.selectAll()
            .where { (FoodVerificationsTable.id eq id) and (FoodVerificationsTable.status eq VerificationStatus.Open.wire) }
            .forUpdate()
            .singleOrNull()
            ?.toVerification(emptySet())

    private fun notOpen(id: UUID): VerificationDecision = if (find(id) == null) VerificationDecision.NotFound else VerificationDecision.NotOpen

    private fun find(id: UUID): FoodVerification? =
        withPhotos(FoodVerificationsTable.selectAll().where { FoodVerificationsTable.id eq id }.toList()).singleOrNull()

    private fun withPhotos(rows: List<ResultRow>): List<FoodVerification> {
        if (rows.isEmpty()) return emptyList()
        val sides = FoodVerificationPhotosTable.select(FoodVerificationPhotosTable.verificationId, FoodVerificationPhotosTable.kind)
            .where { FoodVerificationPhotosTable.verificationId inList rows.map { it[FoodVerificationsTable.id] } }
            .groupBy({ it[FoodVerificationPhotosTable.verificationId] }, { PackSide.fromWire(it[FoodVerificationPhotosTable.kind]) })
        return rows.map { it.toVerification(sides[it[FoodVerificationsTable.id]].orEmpty().filterNotNull().toSet()) }
    }

    private fun ResultRow.toVerification(photos: Set<PackSide>) = FoodVerification(
        id = this[FoodVerificationsTable.id],
        foodId = this[FoodVerificationsTable.foodId],
        userId = UserId(this[FoodVerificationsTable.userId]),
        createdAt = this[FoodVerificationsTable.createdAt].toKotlinInstant(),
        outcome = if (this[FoodVerificationsTable.outcome] == VerificationOutcome.Confirmed.wire) VerificationOutcome.Confirmed else VerificationOutcome.Unconfirmed,
        grounded = this[FoodVerificationsTable.grounded],
        groundable = this[FoodVerificationsTable.groundable],
        engine = this[FoodVerificationsTable.engine],
        status = VerificationStatus.fromWire(this[FoodVerificationsTable.status]),
        problem = this[FoodVerificationsTable.problem]?.let(FoodVerificationJson::problem),
        snapshot = this[FoodVerificationsTable.snapshot]?.let(FoodReportJson::snapshot),
        resolvedAt = this[FoodVerificationsTable.resolvedAt]?.toKotlinInstant(),
        resolvedBy = this[FoodVerificationsTable.resolvedBy],
        resolution = this[FoodVerificationsTable.resolution],
        correctionId = this[FoodVerificationsTable.correctionId],
        photos = photos,
    )

    private companion object {
        const val MAX_ENGINE = 32
        const val MAX_ACTOR = 100
        const val MAX_RESOLUTION = 500

        val REVIEWER_SQL = """
            SELECT reviewer,
                   sum(ra)::int AS reports_accepted, sum(rr)::int AS reports_rejected, sum(rd)::int AS reports_duplicate,
                   sum(va)::int AS verifications_accepted, sum(vr)::int AS verifications_rejected
            FROM (
                SELECT resolved_by AS reviewer,
                       (status = 'accepted')::int AS ra, (status = 'rejected')::int AS rr, (status = 'duplicate')::int AS rd, 0 AS va, 0 AS vr
                FROM food_reports WHERE resolved_at >= ? AND resolved_by IS NOT NULL
                UNION ALL
                SELECT resolved_by, 0, 0, 0, (status = 'accepted')::int, (status = 'rejected')::int
                FROM food_verifications WHERE resolved_at >= ? AND resolved_by IS NOT NULL
            ) d
            GROUP BY reviewer
        """.trimIndent()
    }
}

/** Reading `food_verifications` inside whatever transaction is current, for the catalog's Verified mark. */
internal object FoodVerificationsSql {
    /** Which of [foodIds] a Builder's verification confirmed or a reviewer accepted. One query. */
    fun verified(foodIds: Collection<UUID>): Set<UUID> {
        if (foodIds.isEmpty()) return emptySet()
        return FoodVerificationsTable.select(FoodVerificationsTable.foodId)
            .where {
                (FoodVerificationsTable.foodId inList foodIds.distinct()) and
                    (
                        (FoodVerificationsTable.outcome eq VerificationOutcome.Confirmed.wire) or
                            (FoodVerificationsTable.status eq VerificationStatus.Accepted.wire)
                        )
            }
            .withDistinct()
            .mapTo(HashSet()) { it[FoodVerificationsTable.foodId] }
    }
}
