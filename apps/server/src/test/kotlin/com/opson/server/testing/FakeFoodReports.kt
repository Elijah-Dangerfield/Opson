package com.opson.server.testing

import com.opson.server.domain.CorrectionOutcome
import com.opson.server.domain.CorrectionValues
import com.opson.server.domain.EnergyFlag
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCorrection
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
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** In-memory [FoodReportRepository]: enough for the routes. The real one is tested against Postgres. */
@OptIn(ExperimentalTime::class)
class FakeFoodReportRepository(private val clock: Clock = Clock.System) : FoodReportRepository {
    val reports = mutableListOf<FoodReport>()
    val photos = mutableMapOf<UUID, StoredPhoto>()
    val corrections = mutableListOf<FoodCorrection>()
    val energyFlagged = mutableListOf<UUID>()

    override suspend fun report(report: NewFoodReport): ReportOutcome {
        reports.firstOrNull { it.id == report.id }?.let { found ->
            return if (found.userId == report.userId) ReportOutcome.Existing(found) else ReportOutcome.Conflict
        }
        val stored = FoodReport(
            id = report.id,
            foodId = report.foodId,
            userId = report.userId,
            createdAt = clock.now(),
            reason = report.reason,
            suggested = report.suggested,
            note = report.note,
            snapshot = report.snapshot,
            status = ReportStatus.Open,
            hasPhoto = report.photo != null,
        )
        reports += stored
        report.photo?.let { photos[report.id] = StoredPhoto(it.type.mediaType, it.bytes) }
        return ReportOutcome.Created(stored)
    }

    override suspend fun flagEnergy(food: Food): Boolean {
        if (EnergyFlag.ZeroWithMacros !in food.energyFlags || food.id in energyFlagged) return false
        energyFlagged += food.id
        report(
            NewFoodReport(
                UUID.randomUUID(), null, food.id, ReportReason.EnergyCheck,
                SuggestedValues(kcal = food.per100g.kcal), null, FoodSnapshot.of(food), null,
            ),
        )
        return true
    }

    override suspend fun open(limit: Int) = reports.filter { it.status == ReportStatus.Open }.sortedBy { it.createdAt }.take(limit)

    override suspend fun countOpen() = reports.count { it.status == ReportStatus.Open }.toLong()

    override suspend fun forFood(foodId: UUID) = reports.filter { it.foodId == foodId }.sortedByDescending { it.createdAt }

    override suspend fun photo(reportId: UUID) = photos[reportId]

    override suspend fun liveCorrections(foodIds: Collection<UUID>) = corrections.filter { it.live && it.foodId in foodIds }.associateBy { it.foodId }

    override suspend fun corrections(foodId: UUID) = corrections.filter { it.foodId == foodId }.sortedByDescending { it.createdAt }

    override suspend fun applyCorrection(
        foodId: UUID,
        values: CorrectionValues,
        base: FoodSnapshot,
        reportIds: List<UUID>,
        by: String,
    ): CorrectionOutcome {
        if (values.isEmpty) return CorrectionOutcome.NothingToChange
        val open = reports.filter { it.id in reportIds && it.status == ReportStatus.Open && it.foodId == foodId }
        if (open.size != reportIds.distinct().size) return CorrectionOutcome.NotOpen
        val live = corrections.indexOfFirst { it.foodId == foodId && it.live }
        val correction = FoodCorrection(
            UUID.randomUUID(), foodId, clock.now(), by,
            values.over(corrections.getOrNull(live)?.values), base,
        )
        if (live >= 0) corrections[live] = corrections[live].copy(supersededBy = correction.id)
        corrections += correction
        close(reportIds, ReportStatus.Accepted, by) { it.copy(correctionId = correction.id) }
        return CorrectionOutcome.Applied(correction)
    }

    override suspend fun reject(reportIds: List<UUID>, reason: String?, by: String) =
        close(reportIds, ReportStatus.Rejected, by) { it.copy(resolution = reason) }

    override suspend fun markDuplicate(reportIds: List<UUID>, duplicateOf: UUID?, by: String) =
        close(reportIds, ReportStatus.Duplicate, by) { it.copy(duplicateOf = duplicateOf) }

    override suspend fun revert(correctionId: UUID, by: String): RevertOutcome {
        val i = corrections.indexOfFirst { it.id == correctionId }
        if (i < 0) return RevertOutcome.NotFound
        if (!corrections[i].live) return RevertOutcome.NotLive
        corrections[i] = corrections[i].copy(revertedAt = clock.now(), revertedBy = by)
        val older = corrections.indexOfFirst { it.supersededBy == correctionId && it.revertedAt == null }
        if (older >= 0) corrections[older] = corrections[older].copy(supersededBy = null)
        return RevertOutcome.Reverted(corrections[i], corrections.getOrNull(older))
    }

    override suspend fun purgeDecidedPhotos(before: Instant) = 0

    private fun close(ids: List<UUID>, status: ReportStatus, by: String, change: (FoodReport) -> FoodReport): Int {
        var closed = 0
        reports.replaceAll { report ->
            if (report.id in ids && report.status == ReportStatus.Open) {
                closed++
                change(report.copy(status = status, resolvedAt = clock.now(), resolvedBy = by))
            } else {
                report
            }
        }
        return closed
    }
}
