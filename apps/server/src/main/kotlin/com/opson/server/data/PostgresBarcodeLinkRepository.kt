package com.opson.server.data

import com.opson.server.db.BarcodeLinkSuggestionsTable
import com.opson.server.db.Database
import com.opson.server.db.SubmittedFoodsTable
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.BarcodeLinkRepository
import com.opson.server.domain.BarcodeLinkSuggestion
import com.opson.server.domain.Barcodes
import com.opson.server.domain.LinkOutcome
import com.opson.server.domain.LinkStatus
import com.opson.server.domain.SubmissionStatus
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * `barcode_link_suggestions`: one row per barcode and food, counted up each
 * time someone misses the barcode and then logs the food from search. Holds
 * no account and no search text.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresBarcodeLinkRepository(
    private val database: Database,
    private val clock: Clock,
) : BarcodeLinkRepository {

    override suspend fun record(barcode: String, foodId: UUID): Boolean {
        val gtin = Barcodes.gtin14(barcode) ?: return false
        return database.transaction {
            val food = SubmittedFoodsTable.select(SubmittedFoodsTable.reviewStatus, SubmittedFoodsTable.barcode)
                .where { SubmittedFoodsTable.id eq foodId }
                .singleOrNull()
                ?: return@transaction false
            if (food[SubmittedFoodsTable.reviewStatus] != SubmissionStatus.Approved.wire) return@transaction false
            // The food already answers this code: there is nothing to suggest.
            if (food[SubmittedFoodsTable.barcode] in Barcodes.equivalents(gtin)) return@transaction true
            val now = clock.now().toJavaInstant()
            BarcodeLinkSuggestionsTable.upsert(
                BarcodeLinkSuggestionsTable.gtin,
                BarcodeLinkSuggestionsTable.foodId,
                onUpdate = listOf(
                    BarcodeLinkSuggestionsTable.pairings to (BarcodeLinkSuggestionsTable.pairings + 1),
                    BarcodeLinkSuggestionsTable.lastAt to org.jetbrains.exposed.sql.javatime.timestampParam(now),
                ),
            ) {
                it[this.gtin] = gtin
                it[this.foodId] = foodId
                it[this.barcode] = barcode
                it[pairings] = 1
                it[firstAt] = now
                it[lastAt] = now
                it[status] = LinkStatus.Pending.wire
            }
            true
        }
    }

    override suspend fun list(status: LinkStatus, limit: Int): List<BarcodeLinkSuggestion> = database.transaction {
        BarcodeLinkSuggestionsTable
            .join(SubmittedFoodsTable, JoinType.INNER, BarcodeLinkSuggestionsTable.foodId, SubmittedFoodsTable.id)
            .selectAll()
            .where { BarcodeLinkSuggestionsTable.status eq status.wire }
            .orderBy(BarcodeLinkSuggestionsTable.pairings to SortOrder.DESC, BarcodeLinkSuggestionsTable.lastAt to SortOrder.DESC)
            .limit(limit)
            .map {
                BarcodeLinkSuggestion(
                    gtin = it[BarcodeLinkSuggestionsTable.gtin],
                    barcode = it[BarcodeLinkSuggestionsTable.barcode],
                    foodId = it[BarcodeLinkSuggestionsTable.foodId],
                    foodName = it[SubmittedFoodsTable.name],
                    foodBrand = it[SubmittedFoodsTable.brand],
                    foodBarcode = it[SubmittedFoodsTable.barcode],
                    pairings = it[BarcodeLinkSuggestionsTable.pairings],
                    firstAt = it[BarcodeLinkSuggestionsTable.firstAt].toKotlinInstant(),
                    lastAt = it[BarcodeLinkSuggestionsTable.lastAt].toKotlinInstant(),
                    status = LinkStatus.fromWire(it[BarcodeLinkSuggestionsTable.status]) ?: LinkStatus.Pending,
                )
            }
    }

    override suspend fun attach(gtin: String, foodId: UUID): LinkOutcome = database.transaction {
        if (!pending(gtin, foodId)) return@transaction LinkOutcome.NotFound
        val food = SubmittedFoodsTable.select(SubmittedFoodsTable.barcode)
            .where { SubmittedFoodsTable.id eq foodId }
            .forUpdate()
            .singleOrNull()
            ?: return@transaction LinkOutcome.NotFound
        if (food[SubmittedFoodsTable.barcode] != null) return@transaction LinkOutcome.FoodHasBarcode
        SubmittedFoodsTable.select(SubmittedFoodsTable.id)
            .where { SubmittedFoodsTable.barcode inList Barcodes.equivalents(gtin) }
            .firstOrNull()
            ?.let { return@transaction LinkOutcome.BarcodeTaken(it[SubmittedFoodsTable.id]) }
        SubmittedFoodsTable.update({ SubmittedFoodsTable.id eq foodId }) { it[barcode] = gtin }
        mark(gtin, foodId, LinkStatus.Attached)
        LinkOutcome.Done
    }

    override suspend fun dismiss(gtin: String, foodId: UUID): LinkOutcome = database.transaction {
        if (!pending(gtin, foodId)) return@transaction LinkOutcome.NotFound
        mark(gtin, foodId, LinkStatus.Dismissed)
        LinkOutcome.Done
    }

    private fun pending(gtin: String, foodId: UUID): Boolean =
        BarcodeLinkSuggestionsTable.selectAll()
            .where {
                (BarcodeLinkSuggestionsTable.gtin eq gtin) and
                    (BarcodeLinkSuggestionsTable.foodId eq foodId) and
                    (BarcodeLinkSuggestionsTable.status eq LinkStatus.Pending.wire)
            }
            .forUpdate()
            .any()

    private fun mark(gtin: String, foodId: UUID, status: LinkStatus) {
        BarcodeLinkSuggestionsTable.update({
            (BarcodeLinkSuggestionsTable.gtin eq gtin) and (BarcodeLinkSuggestionsTable.foodId eq foodId)
        }) {
            it[this.status] = status.wire
            it[reviewedAt] = clock.now().toJavaInstant()
        }
    }
}
