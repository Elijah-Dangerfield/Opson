package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.instantArg
import com.opson.server.db.queryRows
import com.opson.server.db.textArg
import com.opson.server.db.uuidArg
import com.opson.server.di.ServerScope
import com.opson.server.domain.ContributionReverter
import com.opson.server.domain.RevertedContributions
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A person's shared recipes. Every one that is up, or hidden waiting for a look, is removed, and
 * the open reports on them are upheld. The recipes stay theirs to cook and log; only the public
 * copy goes. Recipes go with the account, so there is nothing to find for a deleted one.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class, multibinding = true)
@Inject
@OptIn(ExperimentalTime::class)
class RecipeReverter(
    private val database: Database,
    private val clock: Clock,
) : ContributionReverter {

    override val kind: String = "recipe"

    override suspend fun revert(userId: UserId, actor: String, reason: String, actionId: UUID?): RevertedContributions =
        // The owner's diary lock, as every write to their recipes takes (PostgresPublicRecipeRepository).
        database.readCommittedTransaction {
            lockUser(userId)
            val now = instantArg(clock.now())
            val by = textArg(actor.take(MAX_ACTOR))
            val removed = queryRows(
                """
                WITH taken AS (
                    UPDATE recipes SET public_state = 'removed', removed_at = ?, removed_by = ?, removed_reason = ?
                    WHERE user_id = ? AND public_state IN ('live', 'hidden')
                    RETURNING id
                ),
                upheld AS (
                    UPDATE recipe_reports SET status = 'upheld', decided_at = ?, decided_by = ?
                    WHERE status = 'open' AND recipe_id IN (SELECT id FROM taken)
                    RETURNING id
                )
                SELECT id FROM taken
                """.trimIndent(),
                listOf(now, by, textArg("Removed in moderation: $reason".take(MAX_REASON)), uuidArg(userId.value), now, by),
            ) { true }.size
            RevertedContributions(kind, undone = removed, closed = 0)
        }

    private companion object {
        const val MAX_ACTOR = 100
        const val MAX_REASON = 500
    }
}
