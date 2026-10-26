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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.selectAll
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Postgres-backed [ExportRepository]. Each query sets a JDBC fetch size, and
 * with autocommit off (the pool default) the Postgres driver then reads the
 * rows through a cursor in chunks instead of all at once, so a long history
 * streams straight into the response.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresExportRepository(
    private val database: Database,
) : ExportRepository {

    override suspend fun export(userId: UserId, sink: suspend (ExportRecord) -> Unit) {
        database.transaction {
            val entries = LogEntriesTable.selectAll()
                .where { (LogEntriesTable.userId eq userId.value) and (LogEntriesTable.deleted eq false) }
                .orderBy(LogEntriesTable.day to SortOrder.ASC, LogEntriesTable.loggedAt to SortOrder.ASC)
                .fetchSize(FETCH_SIZE)
            for (row in entries) {
                sink(
                    ExportRecord.Entry(
                        day = row[LogEntriesTable.day],
                        loggedAt = row[LogEntriesTable.loggedAt].toKotlinInstant(),
                        slot = MealSlot.fromWire(row[LogEntriesTable.slot]) ?: MealSlot.Uncategorized,
                        name = row[LogEntriesTable.name],
                        amountText = row[LogEntriesTable.amountText],
                        grams = row[LogEntriesTable.grams].clean(),
                        nutrients = Nutrients(
                            kcal = row[LogEntriesTable.kcal].clean(),
                            protein = row[LogEntriesTable.protein].clean(),
                            carbs = row[LogEntriesTable.carbs].clean(),
                            fat = row[LogEntriesTable.fat].clean(),
                            fiber = row[LogEntriesTable.fiber].clean(),
                        ),
                        precision = Precision.fromWire(row[LogEntriesTable.precision]) ?: Precision.Estimate,
                        method = LogMethod.fromWire(row[LogEntriesTable.method]) ?: LogMethod.Search,
                    ),
                )
            }

            val weighIns = WeighInsTable.selectAll()
                .where { (WeighInsTable.userId eq userId.value) and (WeighInsTable.deleted eq false) }
                .orderBy(WeighInsTable.measuredAt to SortOrder.ASC)
                .fetchSize(FETCH_SIZE)
            for (row in weighIns) {
                sink(
                    ExportRecord.Weight(
                        measuredAt = row[WeighInsTable.measuredAt].toKotlinInstant(),
                        kg = row[WeighInsTable.kg].clean() ?: 0.0,
                    ),
                )
            }

            val days = DayRecordsTable.selectAll()
                .where {
                    (DayRecordsTable.userId eq userId.value) and
                        (DayRecordsTable.deleted eq false) and
                        DayRecordsTable.waterMl.isNotNull()
                }
                .orderBy(DayRecordsTable.day to SortOrder.ASC)
                .fetchSize(FETCH_SIZE)
            for (row in days) {
                val water = row[DayRecordsTable.waterMl] ?: continue
                sink(ExportRecord.Water(day = row[DayRecordsTable.day], waterMl = water))
            }
        }
    }

    private companion object {
        const val FETCH_SIZE = 500
    }
}
