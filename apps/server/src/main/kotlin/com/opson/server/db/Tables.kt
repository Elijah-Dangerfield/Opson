package com.opson.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Exposed table definitions.
 *
 * Flyway SQL (resources/db/migration) is the source of truth for the schema;
 * these objects are read-side projections used to type-check queries and map
 * rows. Keep them in sync — `DatabaseSchemaTest` fails if a column declared here
 * doesn't exist in the migrated schema.
 *
 * Convention: object named `XxxTable`, SQL table name in the `Table("…")`
 * constructor, camelCase Kotlin vals mapping to snake_case columns.
 */
object ProfilesTable : Table("profiles") {
    val userId = uuid("user_id")
    val displayName = text("display_name").uniqueIndex("profiles_display_name_uq")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

/**
 * Append-only player reports (trust and safety). One row per report; reads
 * are deferred to a future moderation-review surface. See
 * `V3__player_reports.sql`.
 */
object PlayerReportsTable : Table("player_reports") {
    val id = long("id").autoIncrement()
    val reporterUserId = uuid("reporter_user_id")
    val reportedUserId = uuid("reported_user_id")
    val context = text("context").nullable()
    val reason = text("reason").nullable()
    val reasonCategories = text("reason_categories").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * DB-backed remote-config base values. One row per `ConfiguredValue.path`
 * the server overrides; `value_jsonb` is the value served when no targeting
 * rule matches. Backs [com.opson.server.data.PostgresAppConfigSource].
 * See `V4__app_config.sql`.
 */
object AppConfigValuesTable : Table("app_config_values") {
    val path = text("path")
    val valueJsonb = jsonb("value_jsonb")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(path)
}

/**
 * Per-flag targeting rules. One row per rule; evaluated in ascending `priority`
 * with first-match-wins, falling back to the [AppConfigValuesTable] base value.
 * `conditions_jsonb` is the serialized predicate (platform / version-code range
 * / country / locale / user-id allow-deny / rollout bucket). FK-cascaded off the
 * flag's base value so deleting a flag drops its rules. See `V5__app_config_targeting.sql`.
 */
object AppConfigRulesTable : Table("app_config_rules") {
    val id = uuid("id")
    val flagPath = text("flag_path")
    val priority = integer("priority")
    val valueJsonb = jsonb("value_jsonb")
    val conditionsJsonb = jsonb("conditions_jsonb")
    val enabled = bool("enabled")
    val description = text("description").nullable()
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * Append-only audit log of every config mutation made through the admin API.
 * `before_jsonb` / `after_jsonb` capture the changed row's state for diffing.
 * See `V5__app_config_targeting.sql`.
 */
object AppConfigAuditTable : Table("app_config_audit") {
    val id = uuid("id")
    val at = timestamp("at")
    val actor = text("actor")
    val action = text("action")
    val flagPath = text("flag_path").nullable()
    val beforeJsonb = jsonb("before_jsonb").nullable()
    val afterJsonb = jsonb("after_jsonb").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * The in-code config registry captured per app version: every `ConfiguredValue`
 * the build ships with, its type, in-code default, and (for enum-like flags)
 * allowed values. Uploaded by CI at release time (one row per flag per
 * `version_code`); the admin tool reads it to answer "what did 1.0.1 ship with"
 * and to show the baseline a remote override would replace. See
 * `V6__app_config_manifest.sql`.
 */
object AppConfigManifestTable : Table("app_config_manifest") {
    val versionCode = integer("version_code")
    val appVersion = text("app_version").nullable()
    val path = text("path")
    val type = text("type")
    val defaultJsonb = jsonb("default_jsonb")
    val description = text("description").nullable()
    val allowedValuesJsonb = jsonb("allowed_values_jsonb").nullable()
    val capturedAt = timestamp("captured_at")
    override val primaryKey = PrimaryKey(versionCode, path)
}
