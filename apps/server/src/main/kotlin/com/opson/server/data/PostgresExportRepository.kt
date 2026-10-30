package com.opson.server.data

import com.opson.server.db.CustomFoodsTable
import com.opson.server.db.Database
import com.opson.server.db.DayRecordsTable
import com.opson.server.db.ExercisesTable
import com.opson.server.db.FastsTable
import com.opson.server.db.LogEntriesTable
import com.opson.server.db.MealFeelingsTable
import com.opson.server.db.RecipesTable
import com.opson.server.db.SavedMealsTable
import com.opson.server.db.SyncedTable
import com.opson.server.db.UserSettingsTable
import com.opson.server.db.WeighInsTable
import com.opson.server.di.ServerScope
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EqOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.GreaterEqOp
import org.jetbrains.exposed.sql.GreaterOp
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Postgres-backed [ExportRepository]. Rows are read [PAGE_SIZE] at a time, each
 * page in a transaction of its own, and handed to the sink between pages. The
 * sink writes the response, so a client that reads slowly holds no pooled
 * connection while it does: an open transaction waiting on the network has no
 * statement timeout to end it. Pages follow the sort key rather than an offset,
 * so the last page of a long history costs what the first does. Rows go
 * through the same mappers sync's pull uses.
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

    override suspend fun export(userId: UserId, sink: suspend (DiaryRow) -> Unit) {
        for (kind in KINDS) drain(kind, userId, sink)
    }

    private suspend fun drain(kind: Kind, userId: UserId, sink: suspend (DiaryRow) -> Unit) {
        var after: List<Any>? = null
        do {
            val from = after
            val rows = database.transaction { kind.page(userId, from) }
            for ((_, row) in rows) sink(row)
            after = rows.lastOrNull()?.first
        } while (rows.size == PAGE_SIZE)
    }

    /**
     * One kind of row and the order it comes out in. The last column of [key] is unique among a
     * user's rows, so the key names exactly one row and a page can start right after it.
     */
    private class Kind(val table: SyncedTable, val key: List<Column<*>>, val map: (ResultRow) -> DiaryRow) {

        fun page(userId: UserId, after: List<Any>?): List<Pair<List<Any>, DiaryRow>> {
            val live = (table.userId eq userId.value) and (table.deleted eq false)
            return table.selectAll()
                .where { if (after == null) live else live and keyAfter(after) }
                .orderBy(*key.map { it to SortOrder.ASC }.toTypedArray())
                .limit(PAGE_SIZE)
                .map { row -> key.map { column -> checkNotNull(row[column]) { "${column.name} is part of a key" } } to map(row) }
        }

        /**
         * `key > after`, compared column by column. With more than one column it also says
         * `first >= after.first`, so an index on `(user_id, first)` bounds a late page.
         */
        private fun keyAfter(after: List<Any>): Op<Boolean> {
            fun from(i: Int): Op<Boolean> {
                val column = key[i]
                val value = column.param(after[i])
                val greater = GreaterOp(column, value)
                return if (i == key.lastIndex) greater else greater or (EqOp(column, value) and from(i + 1))
            }
            val first = key.first()
            return if (key.size == 1) from(0) else GreaterEqOp(first, first.param(after.first())) and from(0)
        }

        @Suppress("UNCHECKED_CAST")
        private fun Column<*>.param(value: Any): Expression<Any> = QueryParameter(value, columnType as IColumnType<Any>)
    }

    internal companion object {
        const val PAGE_SIZE = 500

        /** In the order [ExportRepository] promises. */
        private val KINDS = listOf(
            Kind(LogEntriesTable, listOf(LogEntriesTable.day, LogEntriesTable.loggedAt, LogEntriesTable.id)) { it.toLogEntry() },
            Kind(WeighInsTable, listOf(WeighInsTable.measuredAt, WeighInsTable.id)) { it.toWeighIn() },
            Kind(DayRecordsTable, listOf(DayRecordsTable.day)) { it.toDayRecord() },
            Kind(MealFeelingsTable, listOf(MealFeelingsTable.day, MealFeelingsTable.slot)) { it.toMealFeeling() },
            Kind(ExercisesTable, listOf(ExercisesTable.startedAt, ExercisesTable.id)) { it.toExercise() },
            Kind(FastsTable, listOf(FastsTable.day)) { it.toFast() },
            Kind(CustomFoodsTable, listOf(CustomFoodsTable.name, CustomFoodsTable.id)) { it.toCustomFood() },
            Kind(SavedMealsTable, listOf(SavedMealsTable.name, SavedMealsTable.id)) { it.toSavedMeal() },
            Kind(RecipesTable, listOf(RecipesTable.name, RecipesTable.id)) { it.toRecipe() },
            Kind(UserSettingsTable, listOf(UserSettingsTable.userId)) { it.toUserSettings() },
        )
    }
}
