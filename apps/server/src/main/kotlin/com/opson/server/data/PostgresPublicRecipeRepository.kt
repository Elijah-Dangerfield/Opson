package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.boolArg
import com.opson.server.db.execute
import com.opson.server.db.instantArg
import com.opson.server.db.intArg
import com.opson.server.db.instantOrNull
import com.opson.server.db.queryRows
import com.opson.server.db.textArg
import com.opson.server.db.uuidArg
import com.opson.server.di.ServerScope
import com.opson.server.domain.AdminRecipeFilter
import com.opson.server.domain.AdminRecipeSummary
import com.opson.server.domain.NewRecipeReport
import com.opson.server.domain.PublicRecipeRepository
import com.opson.server.domain.PublicRecipes
import com.opson.server.domain.PublicState
import com.opson.server.domain.PublishOutcome
import com.opson.server.domain.Recipe
import com.opson.server.domain.RecipeReport
import com.opson.server.domain.RecipeReportOutcome
import com.opson.server.domain.RecipeReportReason
import com.opson.server.domain.RecipeRules
import com.opson.server.domain.StoredRecipe
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.exceptions.ExposedSQLException
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Postgres-backed [PublicRecipeRepository] over the sharing columns of `recipes` and
 * `recipe_reports` (V35). The owner's display name comes from `profiles`, and a name the server
 * made up (`user-1a2b3c4d`) counts as none.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresPublicRecipeRepository(
    private val database: Database,
    private val clock: Clock,
) : PublicRecipeRepository {

    private val random: Random = Random.Default

    /**
     * Writes the row as sync would, then shares it. A new code is a fresh random draw; the one in
     * 800 billion chance it is taken fails the insert on the unique index, and the whole
     * transaction runs again with another.
     */
    override suspend fun publish(userId: UserId, recipe: Recipe, showName: Boolean): PublishOutcome {
        var attempt = 0
        while (true) {
            try {
                return database.readCommittedTransaction { publishOnce(userId, recipe, showName) }
            } catch (e: ExposedSQLException) {
                if (!e.isUniqueViolation() || ++attempt >= CODE_ATTEMPTS) throw e
            }
        }
    }

    private fun publishOnce(userId: UserId, recipe: Recipe, showName: Boolean): PublishOutcome {
        lockUser(userId)
        upsertRecipe(userId, recipe)
        val current = queryRows(
            "SELECT public_state, deleted FROM recipes WHERE id = ? AND user_id = ? FOR UPDATE",
            listOf(uuidArg(recipe.id), uuidArg(userId.value)),
        ) { PublicState.fromWire(it.getString("public_state")) to it.getBoolean("deleted") }.singleOrNull()
        val (state, deleted) = current ?: return PublishOutcome.NotYours
        if (deleted) return PublishOutcome.NotYours
        if (state == PublicState.Removed) return PublishOutcome.Removed
        return queryRows(
            """
            UPDATE recipes SET
                public_code = coalesce(public_code, ?),
                public_state = CASE WHEN public_state = 'hidden' THEN 'hidden' ELSE 'live' END,
                public_show_name = ?,
                published_at = coalesce(published_at, ?)
            WHERE id = ? AND user_id = ?
            RETURNING public_code, public_state
            """.trimIndent(),
            listOf(textArg(RecipeRules.newCode(random)), boolArg(showName), instantArg(clock.now()), uuidArg(recipe.id), uuidArg(userId.value)),
        ) { PublishOutcome.Published(it.getString("public_code"), PublicState.fromWire(it.getString("public_state"))) }.single()
    }

    override suspend fun unpublish(userId: UserId, recipeId: UUID): Boolean = database.transaction {
        queryRows(
            """
            UPDATE recipes SET public_state = CASE WHEN public_state = 'removed' THEN 'removed' ELSE 'private' END
            WHERE id = ? AND user_id = ?
            RETURNING id
            """.trimIndent(),
            listOf(uuidArg(recipeId), uuidArg(userId.value)),
        ) { true }.isNotEmpty()
    }

    override suspend fun byCode(code: String): StoredRecipe? = database.transaction {
        queryRows("$SELECT_RECIPE WHERE r.public_code = ?", listOf(textArg(code))) { it.toStored() }.singleOrNull()
    }

    override suspend fun search(query: String, limit: Int): List<StoredRecipe> = database.transaction {
        val words = query.trim().lowercase()
        val like = "%" + words.escapeLike() + "%"
        queryRows(
            """
            $SELECT_RECIPE
            WHERE r.public_state = 'live' AND NOT r.deleted AND lower(r.name) LIKE ? ESCAPE '\'
            ORDER BY (lower(r.name) = ?) DESC, (lower(r.name) LIKE ? ESCAPE '\') DESC, r.save_count DESC, r.published_at DESC, r.id
            LIMIT ?
            """.trimIndent(),
            listOf(textArg(like), textArg(words), textArg(words.escapeLike() + "%"), intArg(limit)),
        ) { it.toStored() }
    }

    override suspend fun countSave(code: String): Boolean = database.transaction {
        queryRows(
            "UPDATE recipes SET save_count = save_count + 1 WHERE public_code = ? AND public_state = 'live' AND NOT deleted RETURNING id",
            listOf(textArg(code)),
        ) { true }.isNotEmpty()
    }

    override suspend fun report(report: NewRecipeReport): RecipeReportOutcome = database.transaction {
        val target = queryRows(
            "SELECT id, user_id FROM recipes WHERE public_code = ? AND public_state IN ('live', 'hidden') AND NOT deleted FOR UPDATE",
            listOf(textArg(report.code)),
        ) { rs -> rs.getObject("id", UUID::class.java) to rs.getObject("user_id", UUID::class.java) }.singleOrNull()
            ?: return@transaction RecipeReportOutcome.NotFound
        val (recipeId, ownerId) = target
        if (ownerId == report.reporter.value) return@transaction RecipeReportOutcome.OwnRecipe
        val inserted = queryRows(
            """
            INSERT INTO recipe_reports (id, recipe_id, user_id, created_at, reason, note)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            RETURNING id
            """.trimIndent(),
            listOf(
                uuidArg(report.id),
                uuidArg(recipeId),
                uuidArg(report.reporter.value),
                instantArg(clock.now()),
                textArg(report.reason.wire),
                textArg(report.note),
            ),
        ) { true }.isNotEmpty()
        if (!inserted) return@transaction RecipeReportOutcome.Repeat
        execute(
            """
            UPDATE recipes SET public_state = 'hidden'
            WHERE id = ? AND public_state = 'live'
              AND (SELECT count(DISTINCT user_id) FROM recipe_reports WHERE recipe_id = ? AND status = 'open') >= ?
            """.trimIndent(),
            listOf(uuidArg(recipeId), uuidArg(recipeId), intArg(RecipeRules.HIDE_AFTER_REPORTS)),
        )
        RecipeReportOutcome.Created
    }

    override suspend fun adminList(filter: AdminRecipeFilter, limit: Int): List<AdminRecipeSummary> = database.transaction {
        val where = when (filter) {
            AdminRecipeFilter.Reported -> "open_reports > 0"
            AdminRecipeFilter.Hidden -> "r.public_state = 'hidden'"
            AdminRecipeFilter.Live -> "r.public_state = 'live'"
            AdminRecipeFilter.Removed -> "r.public_state = 'removed'"
        }
        val order = when (filter) {
            AdminRecipeFilter.Reported, AdminRecipeFilter.Hidden -> "open_reports DESC, oldest_open_report_at, r.id"
            AdminRecipeFilter.Live -> "r.published_at DESC, r.id"
            AdminRecipeFilter.Removed -> "r.removed_at DESC NULLS LAST, r.id"
        }
        queryRows(
            """
            SELECT * FROM (
                SELECT r.*, p.display_name,
                    (SELECT count(*) FROM recipe_reports rr WHERE rr.recipe_id = r.id AND rr.status = 'open') AS open_reports,
                    (SELECT min(created_at) FROM recipe_reports rr WHERE rr.recipe_id = r.id AND rr.status = 'open') AS oldest_open_report_at,
                    (SELECT coalesce(string_agg(rr.reason, ','), '') FROM recipe_reports rr WHERE rr.recipe_id = r.id AND rr.status = 'open') AS open_reasons
                FROM recipes r LEFT JOIN profiles p ON p.user_id = r.user_id
                WHERE r.public_code IS NOT NULL AND NOT r.deleted
            ) r
            WHERE $where
            ORDER BY $order
            LIMIT ?
            """.trimIndent(),
            listOf(intArg(limit)),
        ) { rs ->
            val stored = rs.toStored()
            AdminRecipeSummary(
                recipe = stored,
                itemCount = PublicRecipes.items(stored.itemsJson).size,
                openReports = rs.getInt("open_reports"),
                reasons = rs.getString("open_reasons").split(',').filter { it.isNotEmpty() }.groupingBy { it }.eachCount(),
                oldestOpenReportAt = rs.instantOrNull("oldest_open_report_at"),
            )
        }
    }

    override suspend fun adminGet(id: UUID): StoredRecipe? = database.transaction {
        queryRows("$SELECT_RECIPE WHERE r.id = ? AND r.public_code IS NOT NULL", listOf(uuidArg(id))) { it.toStored() }.singleOrNull()
    }

    override suspend fun reports(recipeId: UUID): List<RecipeReport> = database.transaction {
        queryRows(
            "SELECT id, reason, note, created_at, status FROM recipe_reports WHERE recipe_id = ? ORDER BY created_at DESC, id",
            listOf(uuidArg(recipeId)),
        ) { rs ->
            RecipeReport(
                id = rs.getObject("id", UUID::class.java),
                reason = RecipeReportReason.fromWire(rs.getString("reason")) ?: RecipeReportReason.Other,
                note = rs.getString("note"),
                createdAt = checkNotNull(rs.instantOrNull("created_at")),
                status = rs.getString("status"),
            )
        }
    }

    override suspend fun remove(id: UUID, actor: String, reason: String): Boolean = database.transaction {
        val now = instantArg(clock.now())
        val removed = queryRows(
            """
            UPDATE recipes SET public_state = 'removed', removed_at = ?, removed_by = ?, removed_reason = ?
            WHERE id = ? AND public_code IS NOT NULL
            RETURNING id
            """.trimIndent(),
            listOf(now, textArg(actor.take(MAX_ACTOR)), textArg(reason.take(MAX_REASON)), uuidArg(id)),
        ) { true }.isNotEmpty()
        if (removed) decideOpenReports(id, "upheld", actor)
        removed
    }

    override suspend fun restore(id: UUID, actor: String): Boolean = database.transaction {
        val restored = queryRows(
            """
            UPDATE recipes SET
                public_state = CASE WHEN public_state = 'hidden' THEN 'live' WHEN public_state = 'removed' THEN 'private' ELSE public_state END,
                removed_at = NULL, removed_by = NULL, removed_reason = NULL
            WHERE id = ? AND public_code IS NOT NULL
            RETURNING id
            """.trimIndent(),
            listOf(uuidArg(id)),
        ) { true }.isNotEmpty()
        if (restored) decideOpenReports(id, "dismissed", actor)
        restored
    }

    override suspend fun dismissReports(id: UUID, actor: String): Int = database.transaction {
        val dismissed = decideOpenReports(id, "dismissed", actor)
        execute("UPDATE recipes SET public_state = 'live' WHERE id = ? AND public_state = 'hidden'", listOf(uuidArg(id)))
        dismissed
    }

    private fun decideOpenReports(recipeId: UUID, status: String, actor: String): Int =
        queryRows(
            "UPDATE recipe_reports SET status = ?, decided_at = ?, decided_by = ? WHERE recipe_id = ? AND status = 'open' RETURNING id",
            listOf(textArg(status), instantArg(clock.now()), textArg(actor.take(MAX_ACTOR)), uuidArg(recipeId)),
        ) { true }.size

    private fun ResultSet.toStored(): StoredRecipe = StoredRecipe(
        id = getObject("id", UUID::class.java),
        ownerId = UserId(getObject("user_id", UUID::class.java)),
        name = getString("name"),
        servings = getFloat("servings").toString().toDouble(),
        itemsJson = getString("items"),
        notes = getString("notes"),
        code = getString("public_code"),
        state = PublicState.fromWire(getString("public_state")),
        showName = getBoolean("public_show_name"),
        ownerName = getString("display_name")?.takeUnless { GENERATED_NAME.matches(it) },
        deleted = getBoolean("deleted"),
        publishedAt = instantOrNull("published_at"),
        updatedAt = checkNotNull(instantOrNull("client_updated_at")),
        saveCount = getInt("save_count"),
        removedReason = getString("removed_reason"),
    )

    private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun ExposedSQLException.isUniqueViolation(): Boolean =
        sqlState == UNIQUE_VIOLATION || (cause as? SQLException)?.sqlState == UNIQUE_VIOLATION

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val CODE_ATTEMPTS = 3
        const val MAX_ACTOR = 100
        const val MAX_REASON = 500
        const val SELECT_RECIPE = "SELECT r.*, p.display_name FROM recipes r LEFT JOIN profiles p ON p.user_id = r.user_id"

        /** The name `PostgresProfileRepository` gives an account that never chose one. */
        val GENERATED_NAME = Regex("user-[0-9a-f]{8}")
    }
}
