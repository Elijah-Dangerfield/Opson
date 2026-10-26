package com.opson.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
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

/**
 * The shared food catalog (USDA + Open Food Facts). Read-only for users;
 * written by the USDA import script and by barcode lookups. The generated
 * `search` tsvector column is deliberately not mapped: it is only used in raw
 * SQL, and Exposed must never try to write it. See `V7__food_catalog.sql`.
 */
object FoodsTable : Table("foods") {
    val id = uuid("id")
    val sourceKind = text("source")
    val sourceId = text("source_id")
    val name = text("name")
    val brand = text("brand").nullable()
    val barcode = text("barcode").nullable()
    val kcal100g = float("kcal_100g").nullable()
    val protein100g = float("protein_100g").nullable()
    val carbs100g = float("carbs_100g").nullable()
    val fat100g = float("fat_100g").nullable()
    val fiber100g = float("fiber_100g").nullable()
    val micros = jsonb("micros")
    val portions = jsonb("portions")
    val popularity = integer("popularity")

    /**
     * Set from `source` by the `foods_set_tier` trigger (V10), so both are
     * `databaseGenerated()`: Kotlin writes the source and reads these back.
     */
    val tier = short("tier").databaseGenerated()
    val verified = bool("verified").databaseGenerated()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Open Food Facts products (ODbL), kept apart from [FoodsTable] so the
 * share-alike licence stays with these rows. Replaced in full every month by
 * `scripts/import_off_foods.py` through a staging table and a rename swap;
 * nothing in the server writes it. The generated `search` tsvector is not
 * mapped, as on [FoodsTable]. See `V12__off_foods.sql`.
 */
object OffFoodsTable : Table("off_foods") {
    val id = uuid("id")
    val barcode = text("barcode")
    val name = text("name")
    val brand = text("brand").nullable()
    val quantity = text("quantity").nullable()
    val serving = text("serving").nullable()
    val kcal100g = float("kcal_100g")
    val protein100g = float("protein_100g")
    val carbs100g = float("carbs_100g")
    val fat100g = float("fat_100g")
    val fiber100g = float("fiber_100g").nullable()
    val micros = jsonb("micros")
    val portions = jsonb("portions")
    val countriesTags = array<String>("countries_tags")
    val sourceKind = text("source")
    val sourceVersion = text("source_version")
    val tier = short("tier")
    val verified = bool("verified")
    val lastModifiedAt = timestamp("last_modified_at").nullable()
    val scans = integer("scans")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** One row per catalog search, no user id. See `V7__food_catalog.sql`. */
object FoodSearchEventsTable : Table("food_search_events") {
    val id = long("id").autoIncrement()
    val at = timestamp("at")
    val queryNorm = text("query_norm")
    val resultCount = integer("result_count")
    val picked = bool("picked")
    override val primaryKey = PrimaryKey(id)
}

/** One row per barcode lookup and where it was answered from. See `V7__food_catalog.sql`. */
object BarcodeLookupsTable : Table("barcode_lookups") {
    val id = long("id").autoIncrement()
    val at = timestamp("at")
    val barcode = text("barcode")
    val outcome = text("outcome")
    override val primaryKey = PrimaryKey(id)
}

/** One row per barcode the app reported as found nowhere, no user id. See `V13__barcode_misses.sql`. */
object BarcodeMissesTable : Table("barcode_misses") {
    val id = uuid("id")
    val createdAt = timestamp("created_at")
    val barcode = text("barcode")
    val gtin = text("gtin").nullable()
    val country = text("country").nullable()
    val platform = text("platform")
    val appVersion = text("app_version").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Columns every synced diary table shares (see `V8__diary.sql`). `server_seq`
 * is set by the `bump_server_seq` trigger, so it is `databaseGenerated()` and
 * never written from Kotlin.
 */
abstract class SyncedTable(name: String) : Table(name) {
    val userId = uuid("user_id")
    val clientUpdatedAt = timestamp("client_updated_at")
    val serverSeq = long("server_seq").databaseGenerated()
    val deleted = bool("deleted")
}

object LogEntriesTable : SyncedTable("log_entries") {
    val id = uuid("id")
    val day = date("day")
    val slot = text("slot")
    val loggedAt = timestamp("logged_at")
    val foodId = uuid("food_id").nullable()
    val customFoodId = uuid("custom_food_id").nullable()
    val name = text("name")
    val amountText = text("amount_text").nullable()
    val quantity = float("quantity").nullable()
    val unit = text("unit").nullable()
    val grams = float("grams").nullable()
    val precision = text("precision")
    val kcal = float("kcal").nullable()
    val protein = float("protein").nullable()
    val carbs = float("carbs").nullable()
    val fat = float("fat").nullable()
    val fiber = float("fiber").nullable()
    val micros = jsonb("micros").nullable()
    val method = text("method")
    override val primaryKey = PrimaryKey(id)
}

object DayRecordsTable : SyncedTable("day_records") {
    val day = date("day")
    val waterMl = integer("water_ml").nullable()
    val closedAt = timestamp("closed_at").nullable()
    val note = text("note").nullable()
    val ateNoDetails = jsonb("ate_no_details")
    val mealGroups = bool("meal_groups").nullable()
    val untracked = bool("untracked")
    val feeling = text("feeling").nullable()
    override val primaryKey = PrimaryKey(userId, day)
}

object MealFeelingsTable : SyncedTable("meal_feelings") {
    val day = date("day")
    val slot = text("slot")
    val hunger = text("hunger").nullable()
    val fullness = text("fullness").nullable()
    val moods = array<String>("moods")
    val note = text("note").nullable()
    override val primaryKey = PrimaryKey(userId, day, slot)
}

object WeighInsTable : SyncedTable("weigh_ins") {
    val id = uuid("id")
    val measuredAt = timestamp("measured_at")
    val kg = float("kg")
    override val primaryKey = PrimaryKey(id)
}

object CustomFoodsTable : SyncedTable("custom_foods") {
    val id = uuid("id")
    val name = text("name")
    val brand = text("brand").nullable()
    val servingAmount = float("serving_amount")
    val servingUnit = text("serving_unit")
    val kcal = float("kcal").nullable()
    val protein = float("protein").nullable()
    val carbs = float("carbs").nullable()
    val fat = float("fat").nullable()
    val fiber = float("fiber").nullable()
    override val primaryKey = PrimaryKey(id)
}

object SavedMealsTable : SyncedTable("saved_meals") {
    val id = uuid("id")
    val name = text("name")
    val usualSlot = text("usual_slot").nullable()
    val items = jsonb("items")
    override val primaryKey = PrimaryKey(id)
}

/** One fast per user per evening (V14). The end a fast reaches on its own is derived, never stored. */
object FastsTable : SyncedTable("fasts") {
    val day = date("day")
    val startedAt = timestamp("started_at")
    val endedAt = timestamp("ended_at").nullable()
    val goalHours = short("goal_hours").nullable()
    override val primaryKey = PrimaryKey(userId, day)
}

/** The whole client `Settings` object as one jsonb document per user. */
object UserSettingsTable : SyncedTable("user_settings") {
    val settings = jsonb("settings")
    val changedAt = timestamp("changed_at").nullable()
    override val primaryKey = PrimaryKey(userId)
}
