package com.opson.server.domain

/**
 * Server-of-record for player reports. Backs the in-app "Report a user"
 * action: a report is an append-only row a human moderator reads later. No
 * auto-ban, no dedup — the route's rate limit bounds abuse, and a reporter
 * may report the same user more than once (e.g. across contexts).
 *
 * See `V3__player_reports.sql` + [com.opson.server.db.PlayerReportsTable].
 */
interface PlayerReportRepository {

    /**
     * Record [reporter]'s report of [reported], captured in [context] (a
     * free-form tag for where it happened — null when there isn't one) with an
     * optional free-text [reason] and the reporter's selected reason
     * [categories] (canonical keys, already sanitized/capped by the caller;
     * empty when none were picked). Callers validate that [reporter] and
     * [reported] differ before calling.
     */
    suspend fun record(
        reporter: UserId,
        reported: UserId,
        context: String?,
        reason: String?,
        categories: List<String>,
    )
}
