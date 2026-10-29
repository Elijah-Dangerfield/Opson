package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.instantArg
import com.opson.server.db.instantOrNull
import com.opson.server.db.queryRows
import com.opson.server.db.uuidArg
import com.opson.server.di.ServerScope
import com.opson.server.domain.BanReason
import com.opson.server.domain.BanStatus
import com.opson.server.domain.ModerationRepository
import com.opson.server.domain.UserId
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Ban standing for the ban gate, from two places in one query:
 *
 *  - Supabase's `auth.users.banned_until`, set from the dashboard or the Admin API. A future
 *    timestamp means blocked until then. It carries no reason, so it reads as [BanReason.Banned].
 *  - `moderation_actions` (V29), the admin console's bans and suspensions: `full` scope, not
 *    lifted, not ended. A contributions restriction is not read here.
 *
 * The strictest wins: a ban over a suspension, an indefinite block over a dated one, a later
 * end over an earlier one. Raw SQL because the read crosses into Supabase's `auth` schema.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresModerationRepository(
    private val database: Database,
    private val clock: Clock,
) : ModerationRepository {

    override suspend fun banStatusFor(userId: UserId): BanStatus? = database.transaction {
        val now = clock.now()
        val blocks = queryRows(
            """
            SELECT 'ban' AS kind, banned_until AS until FROM auth.users WHERE id = ? AND banned_until > ?
            UNION ALL
            SELECT kind, until FROM public.moderation_actions
            WHERE user_id = ? AND scope = 'full' AND lifted_at IS NULL AND (until IS NULL OR until > ?)
            """.trimIndent(),
            listOf(uuidArg(userId.value), instantArg(now), uuidArg(userId.value), instantArg(now)),
        ) { rs -> Block(if (rs.getString("kind") == "suspension") BanReason.Suspended else BanReason.Banned, rs.instantOrNull("until")) }
        strictest(blocks)
    }

    private data class Block(val reason: BanReason, val until: Instant?)

    private fun strictest(blocks: List<Block>): BanStatus? {
        if (blocks.isEmpty()) return null
        val reason = if (blocks.any { it.reason == BanReason.Banned }) BanReason.Banned else BanReason.Suspended
        val until = if (blocks.any { it.until == null }) null else blocks.mapNotNull { it.until }.max()
        return BanStatus(reason, until)
    }
}
