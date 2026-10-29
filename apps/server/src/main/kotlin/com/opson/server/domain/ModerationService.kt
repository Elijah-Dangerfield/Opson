package com.opson.server.domain

import com.opson.server.di.ServerScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * What the admin console's Ban, Suspend, Restrict contributions and Roll back buttons do, in one
 * place:
 *
 *  - the action itself, and its event ([ModerationActions]);
 *  - for a ban or a restriction, the Builder perks: a `builder` role goes back to member and
 *    every grant from the Builder program (`source = builder`) is removed, through the role and
 *    grant repositories so `access_audit` records each change. Paid grants are never touched.
 *    Lifting the action doesn't give them back; the program grants them again if the person
 *    still qualifies, or an admin does;
 *  - optionally, rolling back what the person contributed ([ContributionReverter], one per
 *    kind), recorded as one `rollback` event with the counts.
 */
@SingleIn(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class ModerationService(
    private val actions: ModerationActions,
    private val roles: UserRoleRepository,
    private val grants: EntitlementGrantRepository,
    private val reverters: Set<ContributionReverter>,
    private val clock: Clock,
) {

    data class Acted(
        val outcome: ModerationOutcome,
        /** What was taken: `role:builder`, `grant:<key>`. */
        val perksRemoved: List<String> = emptyList(),
        val rolledBack: List<RevertedContributions>? = null,
    )

    suspend fun act(action: NewModerationAction, actor: String, rollback: Boolean): Acted {
        val outcome = actions.act(action, actor)
        val done = outcome as? ModerationOutcome.Done ?: return Acted(outcome)
        val why = "moderation: ${action.kind.wire}, ${action.reason.wire}"
        val perks = if (action.kind.removesBuilderPerks) removeBuilderPerks(action.userId, actor, why) else emptyList()
        if (perks.isNotEmpty()) {
            actions.record(actor, "remove_builder_perks", action.userId, done.action.id, buildJsonObject {
                put("removed", JsonArray(perks.map(::JsonPrimitive)))
            })
        }
        val reverted = if (rollback) rollback(action.userId, actor, action.reason.wire, done.action.id) else null
        return Acted(outcome, perks, reverted)
    }

    /** Undoes every kind of contribution the person made, and records one `rollback` event. */
    suspend fun rollback(userId: UserId, actor: String, reason: String, actionId: UUID?): List<RevertedContributions> {
        val results = reverters.sortedBy { it.kind }.map { it.revert(userId, actor, reason, actionId) }
        actions.record(actor, "rollback", userId, actionId, buildJsonObject {
            put("reason", JsonPrimitive(reason))
            results.forEach { r ->
                put(r.kind, JsonObject(mapOf("undone" to JsonPrimitive(r.undone), "closed" to JsonPrimitive(r.closed), "skipped" to JsonPrimitive(r.skipped))))
            }
        })
        return results
    }

    private suspend fun removeBuilderPerks(userId: UserId, actor: String, reason: String): List<String> {
        val removed = mutableListOf<String>()
        if (roles.roleOf(userId) == Role.Builder) {
            roles.setRole(userId, Role.Member, actor, reason)
            removed += "role:${Role.Builder.wire}"
        }
        grants.active(userId, clock.now())
            .filter { it.source == BUILDER_SOURCE && it.effect == GrantEffect.Grant }
            .forEach { grant -> if (grants.remove(userId, grant.id, actor)) removed += "grant:${grant.key}" }
        return removed
    }

    companion object {
        /** The `entitlement_grants.source` the Builder program writes. */
        const val BUILDER_SOURCE = "builder"
    }
}
