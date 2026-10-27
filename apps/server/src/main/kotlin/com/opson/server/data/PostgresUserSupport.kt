package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.InstallSighting
import com.opson.server.domain.UserDetail
import com.opson.server.domain.UserId
import com.opson.server.domain.UserInstall
import com.opson.server.domain.UserInstallRepository
import com.opson.server.domain.UserLookupRepository
import com.opson.server.domain.UserSummary
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.javatime.JavaInstantColumnType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Raw SQL, like PostgresModerationRepository: the lookup reads the `support` views and, through
 * them, Supabase's `auth.users`, none of which is modelled in Exposed. The views are where the
 * allowlist of what support may see lives; this file only moves their rows.
 */

@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresUserInstallRepository(private val database: Database) : UserInstallRepository {

    override suspend fun record(sighting: InstallSighting, at: Instant) {
        database.transaction {
            exec(
                """
                INSERT INTO user_installs (user_id, install_id, platform, app_version, build_number, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, install_id, app_version, build_number)
                DO UPDATE SET last_seen_at = greatest(user_installs.last_seen_at, excluded.last_seen_at)
                """.trimIndent(),
                listOf(
                    UUIDColumnType() to sighting.userId.value,
                    TextColumnType() to sighting.installId.orEmpty().take(MAX_FIELD),
                    TextColumnType() to sighting.platform.take(MAX_FIELD),
                    TextColumnType() to sighting.appVersion.orEmpty().take(MAX_FIELD),
                    IntegerColumnType() to (sighting.buildNumber ?: 0),
                    JavaInstantColumnType() to at.toJavaInstant(),
                    JavaInstantColumnType() to at.toJavaInstant(),
                ),
            ) {}
        }
    }

    private companion object {
        /** Headers are the client's word; nothing longer than this is a real version or id. */
        const val MAX_FIELD = 64
    }
}

@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresUserLookupRepository(private val database: Database) : UserLookupRepository {

    override suspend fun search(query: String, limit: Int): List<UserSummary> = database.transaction {
        val trimmed = query.trim()
        val exactId = UserId.parse(trimmed)
        val results = mutableListOf<UserSummary>()
        when {
            exactId != null -> exec(
                "$SUMMARY_SELECT WHERE user_id = ?",
                listOf(UUIDColumnType() to exactId.value),
            ) { rs -> while (rs.next()) results += rs.toSummary() }
            trimmed.isEmpty() -> exec(
                "$SUMMARY_SELECT ORDER BY coalesce(last_seen_at, joined_at) DESC NULLS LAST LIMIT ?",
                listOf(IntegerColumnType() to limit),
            ) { rs -> while (rs.next()) results += rs.toSummary() }
            else -> {
                val pattern = "%" + trimmed.lowercase().escapeLike() + "%"
                exec(
                    "$SUMMARY_SELECT WHERE lower(email) LIKE ? ESCAPE '\\' OR lower(display_name) LIKE ? ESCAPE '\\' " +
                        "ORDER BY coalesce(last_seen_at, joined_at) DESC NULLS LAST LIMIT ?",
                    listOf(TextColumnType() to pattern, TextColumnType() to pattern, IntegerColumnType() to limit),
                ) { rs -> while (rs.next()) results += rs.toSummary() }
            }
        }
        results
    }

    override suspend fun detail(userId: UserId): UserDetail? = database.transaction {
        val id = listOf<Pair<IColumnType<*>, Any?>>(UUIDColumnType() to userId.value)
        var summary: UserSummary? = null
        var lastSynced: Instant? = null
        exec("$SUMMARY_SELECT WHERE user_id = ?", id) { rs ->
            if (rs.next()) {
                summary = rs.toSummary()
                lastSynced = rs.instant("last_synced_change_at")
            }
        }
        val found = summary ?: return@transaction null

        val installs = mutableListOf<UserInstall>()
        exec(
            "SELECT install_id, platform, app_version, build_number, first_seen_at, last_seen_at " +
                "FROM support.user_installs WHERE user_id = ? ORDER BY last_seen_at DESC",
            id,
        ) { rs ->
            while (rs.next()) {
                installs += UserInstall(
                    installId = rs.getString("install_id"),
                    platform = rs.getString("platform"),
                    appVersion = rs.getString("app_version"),
                    buildNumber = rs.getInt("build_number"),
                    firstSeenAt = rs.instant("first_seen_at")!!,
                    lastSeenAt = rs.instant("last_seen_at")!!,
                )
            }
        }

        val settings = mutableListOf<Pair<String, String?>>()
        exec("SELECT * FROM support.user_settings WHERE user_id = ?", id) { rs ->
            if (rs.next()) {
                val meta = rs.metaData
                for (column in 1..meta.columnCount) {
                    val name = meta.getColumnLabel(column)
                    // getObject, so a boolean reads `true` rather than Postgres's `t`.
                    if (name != "user_id") settings += name to rs.getObject(column)?.toString()
                }
            }
        }

        val methods = linkedMapOf<String, Long>()
        var last7 = 0L
        var last30 = 0L
        exec(
            "SELECT ${METHOD_COLUMNS.joinToString { "coalesce(sum($it), 0) AS $it" }}, " +
                "coalesce(sum(entries) FILTER (WHERE day > current_date - 7), 0) AS last7, " +
                "coalesce(sum(entries) FILTER (WHERE day > current_date - 30), 0) AS last30 " +
                "FROM support.user_daily_logging WHERE user_id = ?",
            id,
        ) { rs ->
            if (rs.next()) {
                METHOD_COLUMNS.forEach { methods[it] = rs.getLong(it) }
                last7 = rs.getLong("last7")
                last30 = rs.getLong("last30")
            }
        }

        var closed = 0L
        exec("SELECT count(*) FILTER (WHERE closed) AS closed FROM support.user_daily_marks WHERE user_id = ?", id) { rs ->
            if (rs.next()) closed = rs.getLong("closed")
        }

        UserDetail(
            summary = found,
            installs = installs,
            settings = settings,
            methods = methods,
            entriesLast7Days = last7,
            entriesLast30Days = last30,
            daysClosed = closed,
            lastSyncedChangeAt = lastSynced,
        )
    }

    private companion object {
        const val SUMMARY_SELECT =
            "SELECT user_id, email, is_guest, display_name, joined_at, last_sign_in_at, last_seen_at, platform, " +
                "app_version, build_number, preset, entries, days_logged, last_log_day, last_synced_change_at " +
                "FROM support.users"

        /** The per-method columns of `support.user_daily_logging`. */
        val METHOD_COLUMNS = listOf(
            "search", "barcode", "describe", "saved", "copy", "recent", "favorite", "custom",
            "quick_number", "ate_no_details",
        )
    }
}

private fun <T> exec(sql: String, args: List<Pair<IColumnType<*>, Any?>>, read: (ResultSet) -> T): T? =
    TransactionManager.current().exec(stmt = sql, args = args) { read(it) }

private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@OptIn(ExperimentalTime::class)
private fun ResultSet.instant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant()

@OptIn(ExperimentalTime::class)
private fun ResultSet.toSummary() = UserSummary(
    userId = UserId(java.util.UUID.fromString(getString("user_id"))),
    email = getString("email"),
    isGuest = getBoolean("is_guest"),
    displayName = getString("display_name"),
    joinedAt = instant("joined_at"),
    lastSignInAt = instant("last_sign_in_at"),
    lastSeenAt = instant("last_seen_at"),
    platform = getString("platform"),
    appVersion = getString("app_version"),
    buildNumber = getObject("build_number") as? Int,
    preset = getString("preset"),
    entries = getLong("entries"),
    daysLogged = getLong("days_logged"),
    lastLogDay = getObject("last_log_day", LocalDate::class.java),
)
