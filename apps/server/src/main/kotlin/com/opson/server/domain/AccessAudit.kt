package com.opson.server.domain

import kotlinx.serialization.json.JsonElement
import java.util.UUID

/**
 * One row of `access_audit` (V28): a role set or cleared, a grant or revocation added or
 * removed, with who did it and the row before and after. The config-change trail is
 * `app_config_audit`; policy edits (`entitlements.*` flags) land there, as every config edit does.
 */
data class AccessAuditRecord(
    val id: UUID,
    val atEpochMs: Long,
    val actor: String,
    val action: String,
    val userId: UserId,
    val before: JsonElement?,
    val after: JsonElement?,
)

/** Reads the access trail. Writes happen inside the role and grant repositories' own transactions. */
interface AccessAuditRepository {
    /** Newest first, for one account or (null) everyone. */
    suspend fun list(userId: UserId?, limit: Int): List<AccessAuditRecord>
}
