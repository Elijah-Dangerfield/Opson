package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.DayRecordsTable
import com.opson.server.db.LogEntriesTable
import com.opson.server.db.WeighInsTable
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.ExportRecord
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.LogMethod
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Precision
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Postgres-backed [ExportRepository]. Rows are read [PAGE_SIZE] at a time, each
 * page in a transaction of its own, and handed to the sink between pages. The
 * sink writes the response, so a client that reads slowly holds no pooled
 * connection while it does: an open transaction waiting on the network has no
 * statement timeout to end it. Pages follow the sort key rather than an offset,
 * so the last page of a long history costs what the first does.
 *
 * The export is not one snapshot. A row edited between two pages comes out as
 * its page read it, which for a file someone downloads is fine.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresExportRepository(
    private val database: Database,
) : ExportRepository {

    override suspend fun export(userId: UserId, sink: suspend (ExportRecord) -> Unit) {
        drain(sink) { after: EntryKey? -> entriesAfter(userId, after) }
        drain(sink) { after: WeighInKey? -> weighInsAfter(userId, after) }
        drain(sink) { after: LocalDate? -> waterAfter(userId, after) }
    }

    private suspend fun <K : Any> drain(
        sink: suspend (ExportRecord) -> Unit,
        page: (after: K?) -> List<Pair<K, ExportRecord>>,
    ) {
        var after: K? = null
        do {
            val from = after
            val rows = database.transaction { page(from) }
            for ((_, record) in rows) sink(record)
            after = rows.lastOrNull()?.first
        } while (rows.size == PAGE_SIZE)
    }

    private data class EntryKey(val day: LocalDate, val loggedAt: Instant, val id: UUID)

    private fun entriesAfter(userId: UserId, after: EntryKey?): List<Pair<EntryKey, ExportRecord>> {
        val t = LogEntriesTable
        return t.selectAll()
            .where {
                val live = (t.userId eq userId.value) and (t.deleted eq false)
                if (after == null) {
                    live
                } else {
                    // (day, logged_at, id) > after, with `day >=` so the (user_id, day) index bounds the read.
                    live and (t.day greaterEq after.day) and (
                        (t.day greater after.day) or
                            ((t.day eq after.day) and ((t.loggedAt greater after.loggedAt) or ((t.loggedAt eq after.loggedAt) and (t.id greater after.id))))
                        )
                }
            }
            .orderBy(t.day to SortOrder.ASC, t.loggedAt to SortOrder.ASC, t.id to SortOrder.ASC)
            .limit(PAGE_SIZE)
            .map { row ->
                EntryKey(row[t.day], row[t.loggedAt], row[t.id]) to ExportRecord.Entry(
                    day = row[t.day],
                    loggedAt = row[t.loggedAt].toKotlinInstant(),
                    slot = MealSlot.fromWire(row[t.slot]) ?: MealSlot.Uncategorized,
                    name = row[t.name],
                    amountText = row[t.amountText],
                    grams = row[t.grams].clean(),
                    nutrients = Nutrients(
                        kcal = row[t.kcal].clean(),
                        protein = row[t.protein].clean(),
                        carbs = row[t.carbs].clean(),
                        fat = row[t.fat].clean(),
                        fiber = row[t.fiber].clean(),
                    ),
                    precision = Precision.fromWire(row[t.precision]) ?: Precision.Estimate,
                    method = LogMethod.fromWire(row[t.method]) ?: LogMethod.Search,
                )
            }
    }

    private data class WeighInKey(val measuredAt: Instant, val id: UUID)

    private fun weighInsAfter(userId: UserId, after: WeighInKey?): List<Pair<WeighInKey, ExportRecord>> {
        val t = WeighInsTable
        return t.selectAll()
            .where {
                val live = (t.userId eq userId.value) and (t.deleted eq false)
                if (after == null) {
                    live
                } else {
                    live and ((t.measuredAt greater after.measuredAt) or ((t.measuredAt eq after.measuredAt) and (t.id greater after.id)))
                }
            }
            .orderBy(t.measuredAt to SortOrder.ASC, t.id to SortOrder.ASC)
            .limit(PAGE_SIZE)
            .map { row ->
                WeighInKey(row[t.measuredAt], row[t.id]) to ExportRecord.Weight(
                    measuredAt = row[t.measuredAt].toKotlinInstant(),
                    kg = row[t.kg].clean() ?: 0.0,
                )
            }
    }

    /** A day's water; `day` is unique per user, so it is the whole key. */
    private fun waterAfter(userId: UserId, after: LocalDate?): List<Pair<LocalDate, ExportRecord>> {
        val t = DayRecordsTable
        return t.selectAll()
            .where {
                val withWater: Op<Boolean> = (t.userId eq userId.value) and (t.deleted eq false) and t.waterMl.isNotNull()
                if (after == null) withWater else withWater and (t.day greater after)
            }
            .orderBy(t.day to SortOrder.ASC)
            .limit(PAGE_SIZE)
            .map { row ->
                val water = checkNotNull(row[t.waterMl]) { "the query reads only days with water" }
                row[t.day] to ExportRecord.Water(day = row[t.day], waterMl = water)
            }
    }

    internal companion object {
        const val PAGE_SIZE = 500
    }
}
