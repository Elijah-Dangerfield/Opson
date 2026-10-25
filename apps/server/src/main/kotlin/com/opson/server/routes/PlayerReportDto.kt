package com.opson.server.routes

import kotlinx.serialization.Serializable

/**
 * Wire formats for the player-report endpoint.
 *
 * `reportedUserId` is the Supabase `auth.users` UUID as a string. `context`,
 * `reason`, and `categories` are optional: `context` is a free-form tag for
 * where the report happened (a room code, chat id, post id — whatever the
 * app's surfaces are), `categories` are the reporter's selected reason tags
 * (canonical keys like `harassment` / `spam`), and `reason` is optional
 * free-text detail. `categories` defaults empty for wire back-compat.
 */
@Serializable
data class PlayerReportBody(
    val reportedUserId: String,
    val context: String? = null,
    val reason: String? = null,
    val categories: List<String> = emptyList(),
)

/**
 * Result of filing a report. `status` is always `received` on success. Kept to
 * exactly one field on purpose: the server encodes with `encodeDefaults = true`,
 * so an extra defaulted field would go on the wire and a strict client JSON
 * (unknown keys throw) would reject the response even though the report was
 * recorded.
 */
@Serializable
data class PlayerReportResult(
    val status: String,
)
