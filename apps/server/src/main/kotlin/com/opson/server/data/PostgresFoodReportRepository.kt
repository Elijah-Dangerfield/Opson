package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.FoodCorrectionsTable
import com.opson.server.db.FoodReportPhotosTable
import com.opson.server.db.FoodReportsTable
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.CorrectionOutcome
import com.opson.server.domain.CorrectionValues
import com.opson.server.domain.EnergyFlag
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCorrection
import com.opson.server.domain.FoodEnergy
import com.opson.server.domain.FoodReport
import com.opson.server.domain.FoodReportRepository
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.NewFoodReport
import com.opson.server.domain.ReportOutcome
import com.opson.server.domain.ReportReason
import com.opson.server.domain.ReportStatus
import com.opson.server.domain.RevertOutcome
import com.opson.server.domain.StoredPhoto
import com.opson.server.domain.SuggestedValues
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `food_reports`, their photos and `food_corrections` (V29). Every write is one
 * transaction; the report id the app made is the idempotency key, and a unique
 * violation on it is the arbiter between two copies of the same request, as
 * for submissions.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresFoodReportRepository(
    private val database: Database,
    private val clock: Clock,
) : FoodReportRepository {

    /** Foods this machine already filed an energy check for, so serving one again costs no write. */
    private val flagged = ConcurrentHashMap.newKeySet<UUID>()

    override suspend fun report(report: NewFoodReport): ReportOutcome {
        existing(report)?.let { return it }
        return try {
            database.transaction {
                insertReport(report)
                report.photo?.let { photo ->
                    FoodReportPhotosTable.insert {
                        it[reportId] = report.id
                        it[mediaType] = photo.type.mediaType
                        it[bytes] = photo.bytes
                    }
                }
            }
            ReportOutcome.Created(checkNotNull(get(report.id)))
        } catch (e: ExposedSQLException) {
            if (!e.isUniqueViolation()) throw e
            existing(report) ?: throw e
        }
    }

    private suspend fun existing(report: NewFoodReport): ReportOutcome? {
        val found = get(report.id) ?: return null
        return if (found.userId == report.userId && found.foodId == report.foodId) ReportOutcome.Existing(found) else ReportOutcome.Conflict
    }

    private fun insertReport(report: NewFoodReport) {
        FoodReportsTable.insert {
            it[id] = report.id
            it[foodId] = report.foodId
            it[userId] = report.userId?.value
            it[createdAt] = clock.now().toJavaInstant()
            it[reason] = report.reason.wire
            it[suggested] = report.suggested?.takeUnless(SuggestedValues::isEmpty)?.let(FoodReportJson::suggested)
            it[note] = report.note
            it[snapshot] = FoodReportJson.snapshot(report.snapshot)
            it[status] = ReportStatus.Open.wire
        }
    }

    override suspend fun flagEnergy(food: Food): Boolean {
        if (EnergyFlag.ZeroWithMacros !in food.energyFlags || food.correction != null || food.id in flagged) return false
        val added = try {
            database.transaction {
                val already = FoodReportsTable.select(FoodReportsTable.id)
                    .where { (FoodReportsTable.foodId eq food.id) and (FoodReportsTable.reason eq ReportReason.EnergyCheck.wire) }
                    .any()
                if (!already) {
                    insertReport(
                        NewFoodReport(
                            id = UUID.randomUUID(),
                            userId = null,
                            foodId = food.id,
                            reason = ReportReason.EnergyCheck,
                            // What is served now is the estimate: the suggestion a reviewer can accept as it is.
                            suggested = SuggestedValues(kcal = food.per100g.kcal),
                            note = "Stored as 0 kcal, but the macros come to ${macrosKcal(food)} kcal per 100. Served as ${food.per100g.kcal} kcal per 100.",
                            snapshot = FoodSnapshot.of(food),
                            photo = null,
                        ),
                    )
                }
                !already
            }
        } catch (e: ExposedSQLException) {
            // Two machines flagging the same food at once: the unique index already holds the other's.
            if (!e.isUniqueViolation()) throw e
            false
        }
        flagged += food.id
        return added
    }

    private fun macrosKcal(food: Food): String = FoodEnergy.atwater(food.per100g)?.let { "%.1f".format(it) } ?: "?"

    override suspend fun open(limit: Int): List<FoodReport> = database.transaction {
        val rows = FoodReportsTable.selectAll()
            .where { FoodReportsTable.status eq ReportStatus.Open.wire }
            .orderBy(FoodReportsTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .toList()
        val withPhotos = photoOwners(rows.map { it[FoodReportsTable.id] })
        rows.map { it.toReport(it[FoodReportsTable.id] in withPhotos) }
    }

    override suspend fun countOpen(): Long = database.transaction {
        FoodReportsTable.selectAll().where { FoodReportsTable.status eq ReportStatus.Open.wire }.count()
    }

    override suspend fun forFood(foodId: UUID): List<FoodReport> = database.transaction {
        val rows = FoodReportsTable.selectAll()
            .where { FoodReportsTable.foodId eq foodId }
            .orderBy(FoodReportsTable.createdAt to SortOrder.DESC)
            .limit(MAX_PER_FOOD)
            .toList()
        val withPhotos = photoOwners(rows.map { it[FoodReportsTable.id] })
        rows.map { it.toReport(it[FoodReportsTable.id] in withPhotos) }
    }

    private suspend fun get(id: UUID): FoodReport? = database.transaction {
        FoodReportsTable.selectAll().where { FoodReportsTable.id eq id }.singleOrNull()
            ?.let { it.toReport(photoOwners(listOf(id)).isNotEmpty()) }
    }

    override suspend fun photo(reportId: UUID): StoredPhoto? = database.transaction {
        FoodReportPhotosTable.selectAll().where { FoodReportPhotosTable.reportId eq reportId }.singleOrNull()
            ?.let { StoredPhoto(it[FoodReportPhotosTable.mediaType], it[FoodReportPhotosTable.bytes]) }
    }

    override suspend fun liveCorrections(foodIds: Collection<UUID>): Map<UUID, FoodCorrection> =
        if (foodIds.isEmpty()) emptyMap() else database.transaction { FoodCorrectionsSql.live(foodIds) }

    override suspend fun corrections(foodId: UUID): List<FoodCorrection> = database.transaction {
        FoodCorrectionsTable.selectAll()
            .where { FoodCorrectionsTable.foodId eq foodId }
            .orderBy(FoodCorrectionsTable.createdAt to SortOrder.DESC)
            .limit(MAX_PER_FOOD)
            .map(FoodCorrectionsSql::toCorrection)
    }

    override suspend fun applyCorrection(
        foodId: UUID,
        values: CorrectionValues,
        base: FoodSnapshot,
        reportIds: List<UUID>,
        by: String,
    ): CorrectionOutcome {
        if (values.isEmpty) return CorrectionOutcome.NothingToChange
        return database.transaction {
            val open = openReports(reportIds, foodId)
            if (open.size != reportIds.distinct().size) return@transaction CorrectionOutcome.NotOpen
            val live = FoodCorrectionsSql.live(listOf(foodId), forUpdate = true)[foodId]
            val now = clock.now().toJavaInstant()
            val id = UUID.randomUUID()
            val reviewer = by.take(MAX_ACTOR)
            // The newer goes in not live, the older then points at it, and only then is the newer
            // opened: the unique index on live corrections never sees two for one food.
            FoodCorrectionsTable.insert {
                it[this.id] = id
                it[this.foodId] = foodId
                it[createdAt] = now
                it[createdBy] = reviewer
                it[correctionFields] = FoodReportJson.values(values.over(live?.values))
                it[this.base] = FoodReportJson.snapshot(base)
                it[revertedAt] = now
            }
            live?.let { older ->
                FoodCorrectionsTable.update({ FoodCorrectionsTable.id eq older.id }) { it[supersededBy] = id }
            }
            FoodCorrectionsTable.update({ FoodCorrectionsTable.id eq id }) { it[revertedAt] = null }
            close(reportIds, ReportStatus.Accepted, reviewer, resolution = null) { it[correctionId] = id }
            CorrectionOutcome.Applied(FoodCorrectionsSql.toCorrection(FoodCorrectionsTable.selectAll().where { FoodCorrectionsTable.id eq id }.single()))
        }
    }

    override suspend fun reject(reportIds: List<UUID>, reason: String?, by: String): Int = database.transaction {
        close(reportIds, ReportStatus.Rejected, by.take(MAX_ACTOR), reason?.take(MAX_RESOLUTION)) {}
    }

    override suspend fun markDuplicate(reportIds: List<UUID>, duplicateOf: UUID?, by: String): Int = database.transaction {
        close(reportIds, ReportStatus.Duplicate, by.take(MAX_ACTOR), resolution = null) { it[this.duplicateOf] = duplicateOf }
    }

    override suspend fun revert(correctionId: UUID, by: String): RevertOutcome = database.transaction {
        val row = FoodCorrectionsTable.selectAll().where { FoodCorrectionsTable.id eq correctionId }.forUpdate().singleOrNull()
            ?: return@transaction RevertOutcome.NotFound
        val correction = FoodCorrectionsSql.toCorrection(row)
        if (!correction.live) return@transaction RevertOutcome.NotLive
        FoodCorrectionsTable.update({ FoodCorrectionsTable.id eq correctionId }) {
            it[revertedAt] = clock.now().toJavaInstant()
            it[revertedBy] = by.take(MAX_ACTOR)
        }
        // The one it superseded is live again, unless that one had been reverted itself.
        val older = FoodCorrectionsTable.selectAll()
            .where { (FoodCorrectionsTable.supersededBy eq correctionId) and FoodCorrectionsTable.revertedAt.isNull() }
            .singleOrNull()
            ?.let(FoodCorrectionsSql::toCorrection)
        older?.let { FoodCorrectionsTable.update({ FoodCorrectionsTable.id eq it.id }) { row -> row[supersededBy] = null } }
        val reverted = FoodCorrectionsSql.toCorrection(FoodCorrectionsTable.selectAll().where { FoodCorrectionsTable.id eq correctionId }.single())
        RevertOutcome.Reverted(reverted, older?.copy(supersededBy = null))
    }

    override suspend fun purgeDecidedPhotos(before: Instant): Int = database.transaction {
        val decided = FoodReportsTable.select(FoodReportsTable.id)
            .where { FoodReportsTable.resolvedAt lessEq before.toJavaInstant() }
            .map { it[FoodReportsTable.id] }
        if (decided.isEmpty()) return@transaction 0
        FoodReportPhotosTable.deleteWhere { reportId inList decided }
    }

    private fun openReports(ids: List<UUID>, foodId: UUID?): List<UUID> {
        if (ids.isEmpty()) return emptyList()
        return FoodReportsTable.select(FoodReportsTable.id)
            .where {
                val open = (FoodReportsTable.id inList ids) and (FoodReportsTable.status eq ReportStatus.Open.wire)
                if (foodId == null) open else open and (FoodReportsTable.foodId eq foodId)
            }
            .forUpdate()
            .map { it[FoodReportsTable.id] }
    }

    /** Closes the open ones among [ids]; returns how many it closed. */
    private fun close(
        ids: List<UUID>,
        status: ReportStatus,
        by: String,
        resolution: String?,
        extra: FoodReportsTable.(org.jetbrains.exposed.sql.statements.UpdateStatement) -> Unit,
    ): Int {
        val open = openReports(ids, foodId = null)
        if (open.isEmpty()) return 0
        return FoodReportsTable.update({ FoodReportsTable.id inList open }) {
            it[this.status] = status.wire
            it[resolvedAt] = clock.now().toJavaInstant()
            it[resolvedBy] = by
            it[this.resolution] = resolution
            extra(it)
        }
    }

    private fun photoOwners(ids: List<UUID>): Set<UUID> = if (ids.isEmpty()) {
        emptySet()
    } else {
        FoodReportPhotosTable.select(FoodReportPhotosTable.reportId)
            .where { FoodReportPhotosTable.reportId inList ids }
            .map { it[FoodReportPhotosTable.reportId] }
            .toSet()
    }

    private fun ResultRow.toReport(hasPhoto: Boolean) = FoodReport(
        id = this[FoodReportsTable.id],
        foodId = this[FoodReportsTable.foodId],
        userId = this[FoodReportsTable.userId]?.let(::UserId),
        createdAt = this[FoodReportsTable.createdAt].toKotlinInstant(),
        reason = ReportReason.fromWire(this[FoodReportsTable.reason]) ?: ReportReason.Other,
        suggested = this[FoodReportsTable.suggested]?.let(FoodReportJson::suggested),
        note = this[FoodReportsTable.note],
        snapshot = FoodReportJson.snapshot(this[FoodReportsTable.snapshot]),
        status = ReportStatus.fromWire(this[FoodReportsTable.status]) ?: ReportStatus.Open,
        resolvedAt = this[FoodReportsTable.resolvedAt]?.toKotlinInstant(),
        resolvedBy = this[FoodReportsTable.resolvedBy],
        resolution = this[FoodReportsTable.resolution],
        correctionId = this[FoodReportsTable.correctionId],
        duplicateOf = this[FoodReportsTable.duplicateOf],
        hasPhoto = hasPhoto,
    )

    private fun ExposedSQLException.isUniqueViolation(): Boolean =
        sqlState == UNIQUE_VIOLATION || (cause as? SQLException)?.sqlState == UNIQUE_VIOLATION

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val MAX_ACTOR = 100
        const val MAX_RESOLUTION = 500
        const val MAX_PER_FOOD = 200
    }
}

/** Reading `food_corrections` inside whatever transaction is current, for this repository and the catalog's overlay. */
@OptIn(ExperimentalTime::class)
internal object FoodCorrectionsSql {
    fun live(foodIds: Collection<UUID>, forUpdate: Boolean = false): Map<UUID, FoodCorrection> {
        if (foodIds.isEmpty()) return emptyMap()
        val query = FoodCorrectionsTable.selectAll().where {
            (FoodCorrectionsTable.foodId inList foodIds.distinct()) and
                FoodCorrectionsTable.revertedAt.isNull() and
                FoodCorrectionsTable.supersededBy.isNull()
        }
        return (if (forUpdate) query.forUpdate() else query).map(::toCorrection).associateBy { it.foodId }
    }

    fun toCorrection(row: ResultRow) = FoodCorrection(
        id = row[FoodCorrectionsTable.id],
        foodId = row[FoodCorrectionsTable.foodId],
        createdAt = row[FoodCorrectionsTable.createdAt].toKotlinInstant(),
        createdBy = row[FoodCorrectionsTable.createdBy],
        values = FoodReportJson.values(row[FoodCorrectionsTable.correctionFields]),
        base = FoodReportJson.snapshot(row[FoodCorrectionsTable.base]),
        revertedAt = row[FoodCorrectionsTable.revertedAt]?.toKotlinInstant(),
        revertedBy = row[FoodCorrectionsTable.revertedBy],
        supersededBy = row[FoodCorrectionsTable.supersededBy],
    )
}
