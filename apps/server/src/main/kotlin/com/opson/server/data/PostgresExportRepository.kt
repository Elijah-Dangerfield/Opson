package com.opson.server.data

import com.opson.server.db.CustomFoodsTable
import com.opson.server.db.Database
import com.opson.server.db.DayRecordsTable
import com.opson.server.db.ExercisesTable
import com.opson.server.db.FastsTable
import com.opson.server.db.LogEntriesTable
import com.opson.server.db.MealFeelingsTable
import com.opson.server.db.SavedMealsTable
import com.opson.server.db.SyncedTable
import com.opson.server.db.UserSettingsTable
import com.opson.server.db.WeighInsTable
import com.opson.server.di.ServerScope
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Postgres-backed [ExportRepository]. Each query sets a JDBC fetch size, and
 * with autocommit off (the pool default) the Postgres driver then reads the
 * rows through a cursor in chunks instead of all at once, so a long history
 * streams straight into the response. Rows go through the same mappers sync's
 * pull uses.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresExportRepository(
    private val database: Database,
) : ExportRepository {

    override suspend fun export(userId: UserId, sink: suspend (DiaryRow) -> Unit) {
        database.transaction {
            stream(LogEntriesTable, userId, sink, LogEntriesTable.day, LogEntriesTable.loggedAt) { it.toLogEntry() }
            stream(WeighInsTable, userId, sink, WeighInsTable.measuredAt) { it.toWeighIn() }
            stream(DayRecordsTable, userId, sink, DayRecordsTable.day) { it.toDayRecord() }
            stream(MealFeelingsTable, userId, sink, MealFeelingsTable.day, MealFeelingsTable.slot) { it.toMealFeeling() }
            stream(ExercisesTable, userId, sink, ExercisesTable.startedAt) { it.toExercise() }
            stream(FastsTable, userId, sink, FastsTable.day) { it.toFast() }
            stream(CustomFoodsTable, userId, sink, CustomFoodsTable.name) { it.toCustomFood() }
            stream(SavedMealsTable, userId, sink, SavedMealsTable.name) { it.toSavedMeal() }
            stream(UserSettingsTable, userId, sink) { it.toUserSettings() }
        }
    }

    private suspend fun stream(
        table: SyncedTable,
        userId: UserId,
        sink: suspend (DiaryRow) -> Unit,
        vararg order: Expression<*>,
        map: (ResultRow) -> DiaryRow,
    ) {
        val rows = table.selectAll()
            .where { (table.userId eq userId.value) and (table.deleted eq false) }
            .orderBy(*order.map { it to SortOrder.ASC }.toTypedArray())
            .fetchSize(FETCH_SIZE)
        for (row in rows) sink(map(row))
    }

    private companion object {
        const val FETCH_SIZE = 500
    }
}
