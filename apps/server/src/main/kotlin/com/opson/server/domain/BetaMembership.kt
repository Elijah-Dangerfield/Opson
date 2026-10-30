package com.opson.server.domain

import com.opson.server.http.ClientContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Who joined during the beta (owner, 2026-10-30): everyone who uses Opson while it is in beta
 * gets a lifetime discount on Pro when it arrives. The record is a grant in `entitlement_grants`,
 * so it needs no table of its own and shows in the console's access history: a `bundle` grant of
 * [BUNDLE] with source [SOURCE], no expiry. `entitlements.bundles` has no `beta` bundle, so the
 * grant unlocks nothing today; giving beta members something later is one config edit, and
 * honouring the discount is a query on `source = 'beta'`. What the discount is, is the owner's call.
 *
 * The app asks for it while `release.betaNotice` is on ([FLAG_PATH]), and the server checks the
 * same flag for the caller, so nobody joins once the beta is over. Joining again is a no-op that
 * answers with the first join, flag or not.
 */
object BetaProgram {
    const val SOURCE: String = "beta"
    const val BUNDLE: String = "beta"
    const val FLAG_PATH: String = "release.betaNotice"
    const val REASON: String = "Joined during the beta"

    /** The actor `access_audit` names: the person, through the app. */
    const val ACTOR: String = "app"

    /** Whether the beta is open for the caller whose resolved config this is. Off when absent. */
    fun isOpen(tree: JsonObject): Boolean {
        val release = tree["release"] as? JsonObject ?: return false
        return (release["betaNotice"] as? JsonPrimitive)?.booleanOrNull == true
    }
}

@OptIn(ExperimentalTime::class)
sealed interface BetaJoinOutcome {
    /** A member since [since]; [new] when this call made them one. */
    data class Joined(val since: Instant, val new: Boolean) : BetaJoinOutcome

    /** The beta is over for this caller, or was never on. */
    data object Closed : BetaJoinOutcome

    data object NoSuchUser : BetaJoinOutcome
}

/**
 * [userId] joins the beta, once. Reads the grants first so a second call, from another phone or
 * after the beta closed, answers with the first join. Two calls at the same moment can both
 * write a row; harmless, as the earliest is the one that counts.
 */
@OptIn(ExperimentalTime::class)
suspend fun joinBeta(
    userId: UserId,
    context: ClientContext,
    config: AppConfigSource,
    grants: EntitlementGrantRepository,
): BetaJoinOutcome {
    val existing = grants.history(userId)
        .filter { it.source == BetaProgram.SOURCE && it.removedAt == null }
        .minByOrNull { it.grantedAt }
    if (existing != null) return BetaJoinOutcome.Joined(existing.grantedAt, new = false)
    if (!BetaProgram.isOpen(config.read(context, userId))) return BetaJoinOutcome.Closed
    val grant = NewGrant(
        userId = userId,
        kind = GrantKind.Bundle,
        key = BetaProgram.BUNDLE,
        effect = GrantEffect.Grant,
        source = BetaProgram.SOURCE,
        reason = BetaProgram.REASON,
        expiresAt = null,
    )
    return when (val added = grants.add(grant, actor = BetaProgram.ACTOR)) {
        is AddGrantOutcome.Added -> BetaJoinOutcome.Joined(added.grant.grantedAt, new = true)
        AddGrantOutcome.NoSuchUser -> BetaJoinOutcome.NoSuchUser
    }
}
