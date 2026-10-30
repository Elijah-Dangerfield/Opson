package com.opson.server.data

import com.opson.server.db.CustomFoodsTable
import com.opson.server.db.Database
import com.opson.server.db.DayRecordsTable
import com.opson.server.db.ExercisesTable
import com.opson.server.db.FastsTable
import com.opson.server.db.FoodsTable
import com.opson.server.db.LogEntriesTable
import com.opson.server.db.MealFeelingsTable
import com.opson.server.db.RecipesTable
import com.opson.server.db.SavedMealsTable
import com.opson.server.db.SyncedTable
import com.opson.server.db.UserSettingsTable
import com.opson.server.db.jsonbMerge
import com.opson.server.db.WeighInsTable
import com.opson.server.db.toJavaInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.CustomFood
import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryChanges
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.DiaryRules
import com.opson.server.domain.Exercise
import com.opson.server.domain.Fast
import com.opson.server.domain.LogEntry
import com.opson.server.domain.MealFeeling
import com.opson.server.domain.Recipe
import com.opson.server.domain.SavedMeal
import com.opson.server.domain.SyncMutation
import com.opson.server.domain.SyncRepository
import com.opson.server.domain.SyncResult
import com.opson.server.domain.UserId
import com.opson.server.domain.UserSettings
import com.opson.server.domain.WeighIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID

/**
 * Postgres-backed [SyncRepository], the server half of docs/system-design.md §6.
 *
 * One READ COMMITTED transaction per request:
 *  1. `pg_advisory_xact_lock` on the user. Two devices syncing at once are
 *     serialized, so the rows one sync commits always carry `server_seq`
 *     values below anything the next sees, and a pull by cursor can never
 *     skip a row that committed late. (A sequence alone does not give that:
 *     seq 101 can commit before seq 100.)
 *  2. Each mutation is an `INSERT … ON CONFLICT DO UPDATE … WHERE` the stored
 *     `client_updated_at` is strictly older and the stored row belongs to this
 *     user. Older and replayed mutations change nothing and do not bump
 *     `server_seq`. The `user_id` predicate means a client that sends another
 *     user's row id cannot touch that row.
 *     An update writes only the columns the sending client knows. A 0.1 day
 *     record has no check-in, so its update leaves `check_in` as a 0.2 phone
 *     stored it rather than clearing it. A column added to a synced table
 *     later needs the same care.
 *  3. New entries that reference a catalog food bump `foods.popularity`, in id
 *     order so concurrent syncs cannot deadlock on the shared rows.
 *  4. Pull every entity past the cursor, [DiaryRules.PAGE_SIZE] per entity.
 *
 * A log entry naming a food id the catalog does not have keeps its own name
 * and nutrient snapshot and is stored with `food_id` null, rather than being
 * rejected. Losing a logged meal is worse than losing the link.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresSyncRepository(
    private val database: Database,
) : SyncRepository {

    override suspend fun sync(
        userId: UserId,
        cursor: Long,
        mutations: List<SyncMutation>,
        pullFasts: Boolean,
        pullExercises: Boolean,
        clientKnowsCheckIn: Boolean,
        pullRecipes: Boolean,
    ): SyncResult =
        database.readCommittedTransaction {
            lockUser(userId)

            val entries = mutations.map { it.row }.filterIsInstance<LogEntry>()
            val knownFoods = knownFoodIds(entries.mapNotNull { it.foodId }.toSet())
            val existingEntries = existingEntryIds(entries.map { it.id }.toSet()).toMutableSet()
            val picks = sortedMapOf<UUID, Int>()

            for (mutation in mutations) {
                when (val row = mutation.row) {
                    is LogEntry -> {
                        val entry = if (row.foodId != null && row.foodId !in knownFoods) row.copy(foodId = null) else row
                        val written = upsertEntry(userId, entry)
                        val foodId = entry.foodId
                        if (written && entry.id !in existingEntries && foodId != null && !entry.deleted) {
                            picks.merge(foodId, 1, Int::plus)
                        }
                        existingEntries += entry.id
                    }

                    else -> upsertOther(userId, row, clientKnowsCheckIn)
                }
            }
            bumpPopularity(picks)

            pull(userId, cursor, mutations.map { it.outboxId }, pullFasts, pullExercises, pullRecipes)
        }

    private fun knownFoodIds(ids: Set<UUID>): Set<UUID> =
        if (ids.isEmpty()) {
            emptySet()
        } else {
            FoodsTable.select(FoodsTable.id).where { FoodsTable.id inList ids }.map { it[FoodsTable.id] }.toSet()
        }

    /** Ids that already exist, for any user, so a pick is only counted on first insert. */
    private fun existingEntryIds(ids: Set<UUID>): Set<UUID> =
        if (ids.isEmpty()) {
            emptySet()
        } else {
            LogEntriesTable.select(LogEntriesTable.id)
                .where { LogEntriesTable.id inList ids }
                .map { it[LogEntriesTable.id] }
                .toSet()
        }

    private fun bumpPopularity(picks: Map<UUID, Int>) {
        for ((foodId, count) in picks) {
            FoodsTable.update({ FoodsTable.id eq foodId }) {
                it.update(FoodsTable.popularity, FoodsTable.popularity + count)
            }
        }
    }

    /** The conflict guard every upsert shares: same owner, and the stored copy is older. */
    private fun SyncedTable.newerThanStored(userId: UserId, row: DiaryRow): Op<Boolean> =
        with(SqlExpressionBuilder) {
            (this@newerThanStored.userId eq userId.value) and
                (clientUpdatedAt less row.clientUpdatedAt.toJavaInstant())
        }

    // TODO(review): a 0.1 edit sends `estimate` for a `label` entry (it reads label as estimate),
    // so the 0.2 phone loses "From label". Keep the stored label for requests without `accepts`;
    // docs/review/findings/server-data.md finding 2.
    private fun upsertEntry(userId: UserId, row: LogEntry): Boolean =
        LogEntriesTable.upsert(
            LogEntriesTable.id,
            where = { LogEntriesTable.newerThanStored(userId, row) },
        ) {
            it[id] = row.id
            it[this.userId] = userId.value
            it[day] = row.day
            it[slot] = row.slot.wire
            it[loggedAt] = row.loggedAt.toJavaInstant()
            it[foodId] = row.foodId
            it[customFoodId] = row.customFoodId
            it[name] = row.name
            it[amountText] = row.amountText
            it[quantity] = row.quantity?.toFloat()
            it[unit] = row.unit
            it[grams] = row.grams?.toFloat()
            it[precision] = row.precision.wire
            it[kcal] = row.nutrients.kcal?.toFloat()
            it[protein] = row.nutrients.protein?.toFloat()
            it[carbs] = row.nutrients.carbs?.toFloat()
            it[fat] = row.nutrients.fat?.toFloat()
            it[fiber] = row.nutrients.fiber?.toFloat()
            it[micros] = row.micros?.let { m -> JSON.encodeToString(MICROS_SERIALIZER, m) }
            it[method] = row.method.wire
            it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
            it[deleted] = row.deleted
        }.insertedCount > 0

    private fun upsertOther(userId: UserId, row: DiaryRow, knowsCheckIn: Boolean) {
        when (row) {
            is LogEntry -> upsertEntry(userId, row)

            is DayRecord -> DayRecordsTable.upsert(
                DayRecordsTable.userId,
                DayRecordsTable.day,
                onUpdateExclude = if (knowsCheckIn) null else listOf(DayRecordsTable.checkIn),
                where = { DayRecordsTable.newerThanStored(userId, row) },
            ) {
                it[this.userId] = userId.value
                it[day] = row.day
                it[waterMl] = row.waterMl
                it[closedAt] = row.closedAt?.toJavaInstant()
                it[note] = row.note
                it[ateNoDetails] = JSON.encodeToString(
                    SLOT_FLAGS_SERIALIZER,
                    row.ateNoDetails.mapKeys { (slot, _) -> slot.wire },
                )
                it[mealGroups] = row.mealGroups
                it[untracked] = row.untracked
                it[feeling] = row.feeling
                it[checkIn] = row.checkIn
                it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
                it[deleted] = row.deleted
            }

            is MealFeeling -> MealFeelingsTable.upsert(
                MealFeelingsTable.userId,
                MealFeelingsTable.day,
                MealFeelingsTable.slot,
                where = { MealFeelingsTable.newerThanStored(userId, row) },
            ) {
                it[this.userId] = userId.value
                it[day] = row.day
                it[slot] = row.slot.wire
                it[hunger] = row.hunger
                it[fullness] = row.fullness
                it[moods] = row.moods
                it[note] = row.note
                it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
                it[deleted] = row.deleted
            }

            is WeighIn -> WeighInsTable.upsert(
                WeighInsTable.id,
                where = { WeighInsTable.newerThanStored(userId, row) },
            ) {
                it[id] = row.id
                it[this.userId] = userId.value
                it[measuredAt] = row.measuredAt.toJavaInstant()
                it[kg] = row.kg.toFloat()
                it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
                it[deleted] = row.deleted
            }

            is CustomFood -> CustomFoodsTable.upsert(
                CustomFoodsTable.id,
                // A 0.1 row carries no details. Exposed would still update the columns to null, so they are left out of the update.
                onUpdateExclude = if (row.details == null) CustomFoodsTable.detailColumns else null,
                where = { CustomFoodsTable.newerThanStored(userId, row) },
            ) {
                it[id] = row.id
                it[this.userId] = userId.value
                it[name] = row.name
                it[brand] = row.brand
                it[servingAmount] = row.servingAmount.toFloat()
                it[servingUnit] = row.servingUnit
                it[kcal] = row.nutrients.kcal?.toFloat()
                it[protein] = row.nutrients.protein?.toFloat()
                it[carbs] = row.nutrients.carbs?.toFloat()
                it[fat] = row.nutrients.fat?.toFloat()
                it[fiber] = row.nutrients.fiber?.toFloat()
                row.details?.let { details ->
                    it[servingGrams] = details.servingGrams?.toFloat()
                    it[sizes] = JSON.encodeToString(SIZES_SERIALIZER, details.sizes.map { size -> SizeJson(size.label, size.grams) })
                    it[micros] = JSON.encodeToString(MICROS_SERIALIZER, details.micros)
                }
                it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
                it[deleted] = row.deleted
            }

            is SavedMeal -> SavedMealsTable.upsert(
                SavedMealsTable.id,
                where = { SavedMealsTable.newerThanStored(userId, row) },
            ) {
                it[id] = row.id
                it[this.userId] = userId.value
                it[name] = row.name
                it[usualSlot] = row.usualSlot?.wire
                it[items] = row.itemsJson
                it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
                it[deleted] = row.deleted
            }

            is Fast -> FastsTable.upsert(
                FastsTable.userId,
                FastsTable.day,
                where = { FastsTable.newerThanStored(userId, row) },
            ) {
                it[this.userId] = userId.value
                it[day] = row.day
                it[startedAt] = row.startedAt.toJavaInstant()
                it[endedAt] = row.endedAt?.toJavaInstant()
                it[goalHours] = row.goalHours?.toShort()
                it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
                it[deleted] = row.deleted
            }

            is Recipe -> upsertRecipe(userId, row)

            is Exercise -> ExercisesTable.upsert(
                ExercisesTable.id,
                where = { ExercisesTable.newerThanStored(userId, row) },
            ) {
                it[id] = row.id
                it[this.userId] = userId.value
                it[day] = row.day
                it[startedAt] = row.startedAt.toJavaInstant()
                it[activity] = row.activity
                it[name] = row.name
                it[minutes] = row.minutes.toShort()
                it[kcal] = row.kcal
                it[kcalTyped] = row.kcalTyped
                it[notOnWatch] = row.notOnWatch
                it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
                it[deleted] = row.deleted
            }

            // Settings is one object per user, last write wins, but merged rather than replaced:
            // a client drops keys it doesn't know, so without the merge a 0.1 phone saving its
            // settings would erase what a 0.2 phone added. Shallow, so new settings go in new
            // top-level keys; a key is never removed by leaving it out.
            is UserSettings -> UserSettingsTable.upsert(
                UserSettingsTable.userId,
                onUpdate = {
                    it[UserSettingsTable.settings] =
                        jsonbMerge(UserSettingsTable.settings, insertValue(UserSettingsTable.settings))
                    it[UserSettingsTable.changedAt] = insertValue(UserSettingsTable.changedAt)
                    it[UserSettingsTable.clientUpdatedAt] = insertValue(UserSettingsTable.clientUpdatedAt)
                    it[UserSettingsTable.deleted] = insertValue(UserSettingsTable.deleted)
                },
                where = { UserSettingsTable.newerThanStored(userId, row) },
            ) {
                it[this.userId] = userId.value
                it[settings] = row.settingsJson
                it[changedAt] = row.changedAt?.toJavaInstant()
                it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
                it[deleted] = row.deleted
            }
        }
    }

    private class Page<T>(val rows: List<T>, val lastSeq: Long?, val truncated: Boolean)

    private fun <T> page(table: SyncedTable, userId: UserId, cursor: Long, limit: Int, map: (ResultRow) -> T): Page<T> {
        val rows = table.selectAll()
            .where { (table.userId eq userId.value) and (table.serverSeq greater cursor) }
            .orderBy(table.serverSeq to SortOrder.ASC)
            .limit(limit + 1)
            .toList()
        val kept = rows.take(limit)
        return Page(kept.map(map), kept.lastOrNull()?.get(table.serverSeq), truncated = rows.size > limit)
    }

    /**
     * When any entity is truncated, the next cursor is the lowest last-seq
     * among the truncated pages, so nothing past it is skipped. Rows other
     * entities already returned beyond that point come back on the next call,
     * which is harmless: the client replaces a row with itself.
     *
     * Fasts are paged only when [pullFasts], exercises only when [pullExercises], recipes only when
     * [pullRecipes]. A client that does not ask neither sees them nor has its cursor held back by them.
     */
    private fun pull(
        userId: UserId,
        cursor: Long,
        applied: List<String>,
        pullFasts: Boolean,
        pullExercises: Boolean,
        pullRecipes: Boolean,
    ): SyncResult {
        val limit = DiaryRules.PAGE_SIZE
        val entries = page(LogEntriesTable, userId, cursor, limit) { it.toLogEntry() }
        val days = page(DayRecordsTable, userId, cursor, limit) { it.toDayRecord() }
        val feelings = page(MealFeelingsTable, userId, cursor, limit) { it.toMealFeeling() }
        val weighIns = page(WeighInsTable, userId, cursor, limit) { it.toWeighIn() }
        val customFoods = page(CustomFoodsTable, userId, cursor, limit) { it.toCustomFood() }
        val savedMeals = page(SavedMealsTable, userId, cursor, limit) { it.toSavedMeal() }
        val settings = page(UserSettingsTable, userId, cursor, 1) { it.toUserSettings() }
        val fasts = if (pullFasts) page(FastsTable, userId, cursor, limit) { it.toFast() } else null
        val exercises = if (pullExercises) page(ExercisesTable, userId, cursor, limit) { it.toExercise() } else null
        val recipes = if (pullRecipes) page(RecipesTable, userId, cursor, limit) { it.toRecipe() } else null

        val pages = listOfNotNull(entries, days, feelings, weighIns, customFoods, savedMeals, settings, fasts, exercises, recipes)
        val truncated = pages.filter { it.truncated }
        val nextCursor = if (truncated.isNotEmpty()) {
            truncated.minOf { it.lastSeq ?: cursor }
        } else {
            maxOf(cursor, pages.maxOf { it.lastSeq ?: cursor })
        }
        return SyncResult(
            applied = applied,
            changes = DiaryChanges(
                logEntries = entries.rows,
                dayRecords = days.rows,
                mealFeelings = feelings.rows,
                weighIns = weighIns.rows,
                customFoods = customFoods.rows,
                savedMeals = savedMeals.rows,
                settings = settings.rows.firstOrNull(),
                fasts = fasts?.rows,
                exercises = exercises?.rows,
                recipes = recipes?.rows,
            ),
            cursor = nextCursor,
            hasMore = truncated.isNotEmpty(),
        )
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val MICROS_SERIALIZER = MapSerializer(String.serializer(), Double.serializer())
        val SIZES_SERIALIZER = ListSerializer(SizeJson.serializer())
        val SLOT_FLAGS_SERIALIZER = MapSerializer(String.serializer(), Boolean.serializer())
    }
}

/**
 * The per-user lock every diary write holds, so a pull by cursor can never skip a row another
 * request committed late. The publish route takes it too, since it writes a recipe row.
 */
internal fun lockUser(userId: UserId) {
    val key = userId.value.mostSignificantBits xor userId.value.leastSignificantBits
    TransactionManager.current().exec(
        stmt = "SELECT pg_advisory_xact_lock(?)",
        args = listOf(LongColumnType() to key),
        explicitStatementType = StatementType.SELECT,
    ) { }
}

/**
 * A recipe row, last write wins, scoped to its owner: the same guard as every synced row. Only the
 * columns a client owns are written; the sharing columns are left as they are.
 */
internal fun upsertRecipe(userId: UserId, row: Recipe) {
    RecipesTable.upsert(
        RecipesTable.id,
        where = {
            with(SqlExpressionBuilder) {
                (RecipesTable.userId eq userId.value) and (RecipesTable.clientUpdatedAt less row.clientUpdatedAt.toJavaInstant())
            }
        },
    ) {
        it[id] = row.id
        it[this.userId] = userId.value
        it[name] = row.name
        it[servings] = row.servings.toFloat()
        it[items] = row.itemsJson
        it[notes] = row.notes
        it[savedFrom] = row.savedFrom
        it[clientUpdatedAt] = row.clientUpdatedAt.toJavaInstant()
        it[deleted] = row.deleted
    }
}

/**
 * Postgres REAL is a 4-byte float. Widening it with `toDouble()` exposes the
 * binary noise (1.1f → 1.100000023841858); going through the shortest decimal
 * string gives back the value the client sent.
 */
internal fun Float?.clean(): Double? = this?.toString()?.toDouble()
