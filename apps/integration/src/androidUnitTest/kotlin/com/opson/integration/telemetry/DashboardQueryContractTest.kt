package com.opson.integration.telemetry

import com.opson.libraries.telemetry.impl.GrafanaLogTree
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The dashboards in `ops/grafana/` are held against the `logEvent` calls that
 * feed them.
 *
 * A Grafana panel that filters on `strikes_used` against an app that emits
 * `strikes` is not an error anywhere. Loki accepts the query, the panel renders,
 * and it renders **empty** — which is exactly what a healthy panel looks like
 * before launch. Nothing in a build, a lint pass or a runtime test would catch
 * it, and the person who finds it is whoever eventually asks the dashboard a
 * question and believes the blank answer. A dashboard that lives only in Grafana
 * has no way to be wrong in a build; that is the argument for committing them
 * here, and this test is what makes committing them worth anything.
 *
 * So both ends are read from the real artifacts. Every `event_name` and every
 * attribute a committed query filters, groups or unwraps has to appear in a real
 * `logEvent(...)` call in the source tree, and renaming either end fails this.
 *
 * **What it proves, precisely.** That the attribute key is *spelled* the same in
 * the query and at the emit site. It does not prove the value is meaningful, or
 * that the code path ever runs — a `logEvent` in dead code still counts as
 * emitted. That is a much smaller hole than the one it closes: the mistake people
 * make is a rename, and a rename is exactly what this catches.
 *
 * **Why a source scan and not a runtime assertion.** The emitters live in
 * `:libraries:telemetry:impl`, `:libraries:networking:impl` and
 * `:features:onboarding:impl`. Only `:apps:*` may depend on an impl module, and
 * even here the Android-only ones (`AndroidJankMonitor`) are not on this
 * classpath. Text is what is left, and the dashboards are text too, so both sides
 * are read the same way.
 *
 * **The LogQL reader throws instead of shrugging**, and that is the single
 * decision this file rests on. It understands the stream selector, label filters
 * and `unwrap`, and rejects everything else. A looser regex reader would extract
 * nothing from a `| json` panel and report no violations — which is how a check
 * like this passes while proving nothing. A construct you want has to be taught
 * to the reader first. That is a real cost and it is the point.
 */
class DashboardQueryContractTest {

    private val dashboards: List<Dashboard> = readDashboards()
    private val emitted: Map<String, Map<String, List<String>>> = scanLogEventCalls()

    @Test
    fun everyQueriedEventIsActuallyEmitted() {
        val missing = dashboards.flatMap { dashboard ->
            dashboard.queries.flatMap { query ->
                query.events.filter { it !in emitted }.map { "${dashboard.file}: $it" }
            }
        }.distinct().sorted()

        assertTrue(
            missing.isEmpty(),
            "These dashboards query an event no logEvent call emits, so the panels are permanently " +
                "empty:\n" + missing.joinToString("\n") { "  $it" } +
                "\n\nEither the event is not wired up yet — drop the panel — or the name drifted.",
        )
    }

    @Test
    fun everyQueriedAttributeIsEmittedOnTheEventItIsQueriedAgainst() {
        val missing = dashboards.flatMap { dashboard ->
            dashboard.queries.flatMap { query ->
                query.events.flatMap { event ->
                    val attributes = emitted[event].orEmpty()
                    query.attributes
                        .filter { it !in attributes }
                        .map { "${dashboard.file} · $event has no `$it`  (emits: ${attributes.keys.sorted()})" }
                }
            }
        }.distinct().sorted()

        assertTrue(
            missing.isEmpty(),
            "These dashboard queries filter, group or unwrap on an attribute the event does not " +
                "carry. Loki accepts the query and the panel renders empty:\n" +
                missing.joinToString("\n") { "  $it" } +
                "\n\nThe emitting code wins. Fix the query, or add the attribute at the emit site " +
                "and to docs/practices/app-events.md.",
        )
    }

    /**
     * `unwrap` needs a number. An attribute emitted as `duration.toString()`
     * instead of a `Long` still passes the spelling check above and still produces
     * an empty panel, because Loki cannot unwrap `"42s"` into a sample.
     *
     * The check is a heuristic over the emit-site expression and only rejects the
     * unambiguous cases — a string literal, `.name`, `.toString()`, `simpleName`,
     * `.lowercase()`. A method returning a `String` from a name that does not say
     * so gets through.
     */
    @Test
    fun everyUnwrappedAttributeIsEmittedAsANumber() {
        val stringy = dashboards.flatMap { dashboard ->
            dashboard.queries.flatMap { query ->
                query.events.flatMap { event ->
                    query.unwrapped.flatMap { attribute ->
                        emitted[event].orEmpty()[attribute].orEmpty()
                            .filter { it.looksLikeAString() }
                            .map { "${dashboard.file} · $event · $attribute is emitted as `$it`" }
                    }
                }
            }
        }.distinct().sorted()

        assertTrue(
            stringy.isEmpty(),
            "A dashboard unwraps these into a numeric sample, but they are emitted as strings. " +
                "Loki cannot unwrap a string, so the panel renders empty:\n" +
                stringy.joinToString("\n") { "  $it" },
        )
    }

    /**
     * One stack serves every project generated from this template, told apart
     * only by `service_name`. A query that forgets the matcher spans every app
     * ever shipped from here and looks like a healthy broad result — see
     * `docs/practices/observability.md`.
     */
    @Test
    fun everyDashboardTargetsTheRealLokiDatasourceAndService() {
        val problems = dashboards.flatMap { dashboard ->
            buildList {
                dashboard.datasourceUids.filterNot { it in ALLOWED_DATASOURCE_UIDS }
                    .forEach { add("${dashboard.file}: datasource uid \"$it\"") }
                dashboard.queries.filterNot { query -> SERVICE_NAMES.any { query.expr.contains("service_name=\"$it\"") } }
                    .forEach { add("${dashboard.file}: query does not select a service_name in $SERVICE_NAMES: ${it.expr}") }
            }
        }.distinct().sorted()

        assertTrue(
            problems.isEmpty(),
            "Dashboards must point at the provisioned Grafana Cloud Loki datasource and at the " +
                "service name GrafanaLogTree actually exports under:\n" +
                problems.joinToString("\n") { "  $it" },
        )
    }

    /**
     * The Postgres half of the same promise. A panel reading an `analytics`
     * view that no migration creates fails in Grafana with "relation does not
     * exist", and only for whoever opens the board. Every view a committed
     * panel reads has to be created by a Flyway migration, so renaming or
     * dropping a view without regenerating the boards fails here.
     */
    @Test
    fun everyAnalyticsViewADashboardReadsIsCreatedByAMigration() {
        val created = viewsInMigrations()
        assertTrue(
            "barcode_outcomes_daily" in created && created.size >= MINIMUM_ANALYTICS_VIEWS,
            "the migration scan found only $created, so it is reading the wrong directory",
        )
        val read = dashboards.flatMap { dashboard ->
            dashboard.sql.flatMap { query ->
                ANALYTICS_VIEW_READ.findAll(query).map { dashboard.file to it.groupValues[1] }.toList()
            }
        }
        assertTrue(read.size >= MINIMUM_ANALYTICS_VIEWS, "only ${read.size} analytics reads parsed from the dashboards")

        val missing = read.filter { (_, view) -> view !in created }.map { (file, view) -> "$file: analytics.$view" }
        assertTrue(
            missing.isEmpty(),
            "These panels read a view no migration creates, so they error in Grafana:\n" +
                missing.distinct().sorted().joinToString("\n") { "  $it" },
        )
    }

    /** The same promise for the per-person `support` views the Users and User detail boards read. */
    @Test
    fun everySupportViewADashboardReadsIsCreatedByAMigration() {
        val created = viewsInMigrations("support")
        assertTrue("users" in created, "the migration scan found only $created, so it is reading the wrong directory")
        val read = dashboards.flatMap { dashboard ->
            dashboard.sql.flatMap { query -> SUPPORT_VIEW_READ.findAll(query).map { dashboard.file to it.groupValues[1] }.toList() }
        }
        assertTrue(read.isNotEmpty(), "no support reads parsed from the dashboards")
        val missing = read.filter { (_, view) -> view !in created }.map { (file, view) -> "$file: support.$view" }
        assertTrue(
            missing.isEmpty(),
            "These panels read a support view no migration creates, so they error in Grafana:\n" +
                missing.distinct().sorted().joinToString("\n") { "  $it" },
        )
    }

    /**
     * Emails live in `support.users` and may reach one board only, User detail, where the owner
     * looks one person up. A roster or a chart that selected them would put every address on a
     * screen nobody meant to share.
     */
    @Test
    fun onlyTheUserDetailBoardSelectsAnEmail() {
        val offending = dashboards.filter { it.uid != USER_DETAIL_UID }
            .filter { dashboard -> dashboard.sql.any { EMAIL_COLUMN.containsMatchIn(it) } }
            .map { it.file }
        assertEquals(emptyList(), offending)
        assertTrue(
            dashboards.single { it.uid == USER_DETAIL_UID }.sql.any { EMAIL_COLUMN.containsMatchIn(it) },
            "the User detail board no longer selects the email, so this check is guarding nothing",
        )
    }

    @Test
    fun noTwoDashboardsShareAUid() {
        val uids = dashboards.map { it.uid }
        assertTrue(uids.none { it.isBlank() }, "a dashboard has no uid, so importing it makes a new copy every time")
        assertEquals(
            uids.size,
            uids.toSet().size,
            "two dashboards share a uid, so importing both would overwrite one: " +
                uids.groupBy { it }.filterValues { it.size > 1 }.keys,
        )
    }

    /**
     * The guard against the guard.
     *
     * Every assertion above is "no violations found", which is exactly what a
     * reader that reads nothing reports. A wrong repo root, a moved source layout
     * or a LogQL construct the parser silently skipped would all turn this file
     * green while proving nothing. Both readers are pinned here against fixtures
     * whose answers are written out, and against floors on the real scan.
     */
    @Test
    fun bothReadersCanActuallyFail() {
        assertTrue(
            emitted.size >= MINIMUM_EMITTED_EVENTS,
            "only found ${emitted.size} emitted events in the source tree, so the scan is broken",
        )
        assertTrue(
            "app.launched" in emitted && "previous_exit" in emitted.getValue("app.launched"),
            "the scan did not find the attribute the exit-reason panels are built on",
        )
        assertTrue(
            "app.never_emitted_anywhere" !in emitted,
            "the scan reports an event that does not exist, so it matches too much",
        )

        val checked = dashboards.sumOf { d -> d.queries.sumOf { it.events.size * it.attributes.size } }
        assertTrue(
            checked >= MINIMUM_CHECKED_PAIRS,
            "only $checked (event, attribute) pairs were checked, so the dashboards parsed as empty",
        )

        val fixture = """
            logger.logEvent(
                "onboarding.completed",
                // A comment with commas in it: clear rate, time, retries.
                "duration_sec" to startedAt.elapsedNow().inWholeSeconds,
                "account_ready" to !state.creationFailed,
            )
        """.trimIndent()
        val scanned = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        scanned.absorbLogEventCalls(fixture)
        assertEquals(setOf("onboarding.completed"), scanned.keys)
        assertEquals(
            setOf("duration_sec", "account_ready"),
            scanned.getValue("onboarding.completed").keys,
            "a comment's commas were read as argument separators, which hides every attribute after it",
        )

        val empty = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        empty.absorbLogEventCalls("fun main() { println(\"logEventually\") }")
        assertTrue(empty.isEmpty(), "the scan invented an event out of a file with no logEvent call")

        val parsed = parseQuery(
            "quantile_over_time(0.9, {service_name=\"$SERVICE_NAME\"} | event_name=\"app.jank\" " +
                "| screen=\"home\" | unwrap jank_pct [\$__interval]) by (screen)",
            legendFormat = "{{screen}}",
        )
        assertEquals(setOf("app.jank"), parsed.events)
        assertEquals(setOf("screen", "jank_pct"), parsed.attributes)
        assertEquals(setOf("jank_pct"), parsed.unwrapped)

        // Stream-selector labels and the per-record correlation ids are never a
        // claim about a particular event, so they must not reach the checks above
        // — recorded, they would fail every correct query on the boards.
        // A Grafana variable in the selector, `"${env:text}"`, carries a `}` of its own.
        val withVariable = parseQuery(
            "sum(count_over_time({service_name=\"$SERVICE_NAME\", deployment_environment=\"\${env:text}\"} " +
                "| event_name=\"app.launched\" | previous_exit=\"crash\" [\$__range]))",
            legendFormat = null,
        )
        assertEquals(setOf("app.launched"), withVariable.events, "the pipeline after a braced variable was skipped")
        assertEquals(setOf("previous_exit"), withVariable.attributes)

        val ambient = parseQuery(
            "count(count by (install_id) (count_over_time({service_name=\"$SERVICE_NAME\", " +
                "deployment_environment=\"\$env\"} | event_name=\"app.launched\" [\$__range])))",
            legendFormat = null,
        )
        assertEquals(emptySet(), ambient.attributes)

        LOOSE_CONSTRUCTS.forEach { stage ->
            val rejected = runCatching {
                parseQuery("sum(count_over_time({service_name=\"$SERVICE_NAME\"} $stage [1d]))", null)
            }
            assertTrue(
                rejected.isFailure,
                "the LogQL reader accepted `$stage`, a pipeline stage it cannot understand — it " +
                    "would extract nothing from it and report no violations",
            )
        }
    }
}

private const val REPO_ROOT_PROPERTY = "repoRoot"
private const val USER_DETAIL_UID = "opson-user"
private val EMAIL_COLUMN = Regex("""\bemail\b""")
private const val DASHBOARD_DIR_PROPERTY = "grafanaDashboards"

private val SERVICE_NAME = GrafanaLogTree.SERVICE_NAME

/** Floors, not real counts — see [DashboardQueryContractTest.bothReadersCanActuallyFail]. */
private const val MINIMUM_EMITTED_EVENTS = 8
private const val MINIMUM_CHECKED_PAIRS = 15
private const val MINIMUM_SOURCE_FILES = 200
private const val MINIMUM_ANALYTICS_VIEWS = 10

/**
 * Stages the reader must reject. Each one is a real way to write a panel, and
 * each one would contribute zero references to a reader that skipped it quietly.
 */
private val LOOSE_CONSTRUCTS = listOf(
    "| json",
    "| logfmt",
    "| line_format \"{{.msg}}\"",
    "| label_format screen=route",
    "|= \"crash\"",
)

/**
 * `-- Mixed --` is the panel-level datasource for a stat built from two Loki
 * targets and an `__expr__` math node; the query targets underneath it still name
 * the Loki uid, and those are what the contract checks.
 */
/**
 * `opson-db` (dev) and `opson-prod-db` are the Postgres datasources the long-term
 * dashboards read, picked by their `${env}` variable (`${db}` before it), through
 * the read-only `grafana_ro` role (see ops/grafana/README.md). Its panels are
 * SQL over aggregate views, so the event checks here do not apply to them.
 */
private val ALLOWED_DATASOURCE_UIDS = setOf(
    "grafanacloud-logs", "opson-db", "opson-prod-db", "\${db}", "\${env}", "__expr__", "-- Mixed --", "-- Grafana --",
)

/**
 * The client exports as [GrafanaLogTree.SERVICE_NAME]; the server as the
 * default `serviceName` in `apps/server/.../config/ServerConfig.kt`. A Loki
 * query must select one of the two, never neither.
 */
private val SERVICE_NAMES = listOf(SERVICE_NAME, "opson-server")

/**
 * Keys every record carries regardless of the event: the two stream labels, the
 * correlation ids and connectivity flag `GrafanaLogTree` stamps per record, and
 * the resource attributes Grafana Cloud lands in structured metadata. Querying
 * one of these is never a claim about a particular event.
 */
private val AMBIENT_KEYS = setOf(
    "service_name",
    "deployment_environment",
    "session_id",
    "install_id",
    "is_offline",
    "platform",
    "service_version",
    "build_number",
    "commit_sha",
    "release_channel",
    "detected_level",
    "event_name",
)

private data class Dashboard(
    val file: String,
    val uid: String,
    val queries: List<Query>,
    val datasourceUids: Set<String>,
    /** Every Postgres panel's `rawSql`. */
    val sql: List<String> = emptyList(),
)

private data class Query(
    val expr: String,
    val events: Set<String>,
    val attributes: Set<String>,
    val unwrapped: Set<String>,
)

private fun readDashboards(): List<Dashboard> {
    val dir = File(
        System.getProperty(DASHBOARD_DIR_PROPERTY)
            ?: error("$DASHBOARD_DIR_PROPERTY is unset — apps/integration/build.gradle.kts should supply it"),
    )
    val files = dir.listFiles { f: File -> f.isFile && f.extension == "json" }?.sortedBy { it.name }
    if (files.isNullOrEmpty()) error("no dashboards found in ${dir.absolutePath}")

    return files.map { file ->
        val root = Json.parseToJsonElement(file.readText()).jsonObjectOrFail(file.name)
        val uids = mutableSetOf<String>()
        val queries = mutableListOf<Query>()
        val sql = mutableListOf<String>()

        root.collectDatasourceUids(uids)
        (root["panels"] as? JsonArray).orEmpty().forEach { panel ->
            val obj = panel.jsonObjectOrFail(file.name)
            (obj["targets"] as? JsonArray).orEmpty().forEach { rawTarget ->
                val target = rawTarget.jsonObjectOrFail(file.name)
                target["rawSql"]?.stringOrNull()?.let(sql::add)
                val expr = target["expr"]?.stringOrNull() ?: return@forEach
                queries += parseQuery(expr, target["legendFormat"]?.stringOrNull())
            }
        }
        Dashboard(file.name, root["uid"]?.stringOrNull().orEmpty(), queries, uids, sql)
    }
}

private fun JsonElement.jsonObjectOrFail(file: String): JsonObject =
    this as? JsonObject ?: fail("$file: expected a JSON object, got $this")

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement.collectDatasourceUids(into: MutableSet<String>) {
    when (this) {
        is JsonObject -> {
            (this["datasource"] as? JsonObject)?.get("uid")?.stringOrNull()?.let { into += it }
            values.forEach { it.collectDatasourceUids(into) }
        }

        is JsonArray -> forEach { it.collectDatasourceUids(into) }
        else -> Unit
    }
}

private val IDENTIFIER = Regex("[a-z_][a-z0-9_]*")
private val GROUP_BY = Regex("""by\s*\(([^)]*)\)""")
private val LEGEND_FIELD = Regex("""\{\{\s*([a-z_][a-z0-9_]*)\s*}}""")
private val NUMBER = Regex("""-?[0-9]+(\.[0-9]+)?""")
private const val UNWRAP = "unwrap "

/** Longest first, so `=~` is never read as `=` followed by a stray `~`. */
private val FILTER_OPERATORS = listOf("=~", "!~", "!=", ">=", "<=", "=", ">", "<")

/**
 * Reads one panel query.
 *
 * Understands exactly the LogQL these dashboards are allowed to use — a stream
 * selector, label filters on structured metadata, `unwrap`, and the `by (…)` and
 * `{{…}}` that name attributes outside the pipeline — and throws on anything
 * else. That strictness is the point: a stage the reader quietly skipped would
 * contribute no references, and a query contributing no references passes every
 * assertion in this file.
 */
private fun parseQuery(expr: String, legendFormat: String?): Query {
    val events = mutableSetOf<String>()
    val attributes = mutableSetOf<String>()
    val unwrapped = mutableSetOf<String>()

    var i = 0
    while (i < expr.length) {
        if (expr[i] != '{') {
            i++
            continue
        }
        // The selector's own contents are the two stream labels and are skipped
        // deliberately; AMBIENT_KEYS covers them if a `by (…)` names one. Its end
        // is the first `}` outside a quoted value: `"${env:text}"` has one inside,
        // and stopping there would skip the whole pipeline after it unread.
        val selectorEnd = selectorEnd(expr, i)
        require(selectorEnd > 0) { "unterminated stream selector in: $expr" }
        i = selectorEnd + 1

        while (true) {
            while (i < expr.length && expr[i] == ' ') i++
            if (i >= expr.length || expr[i] != '|') break
            i++
            while (i < expr.length && expr[i] == ' ') i++

            if (expr.startsWith(UNWRAP, i)) {
                i += UNWRAP.length
                val name = IDENTIFIER.matchAt(expr, i) ?: error("unwrap without a name in: $expr")
                unwrapped += name.value
                attributes += name.value
                i = name.range.last + 1
                continue
            }

            val key = IDENTIFIER.matchAt(expr, i)
                ?: error("unsupported LogQL stage at offset $i in: $expr")
            i = key.range.last + 1
            while (i < expr.length && expr[i] == ' ') i++

            val operator = FILTER_OPERATORS.firstOrNull { expr.startsWith(it, i) }
                ?: error(
                    "unsupported LogQL stage `| ${key.value}` in: $expr\nThis reader understands " +
                        "the stream selector, label filters and `unwrap`, and nothing else. Teach " +
                        "it the stage before writing a panel that needs one.",
                )
            i += operator.length
            while (i < expr.length && expr[i] == ' ') i++

            i = if (i < expr.length && expr[i] == '"') {
                val end = expr.indexOf('"', i + 1)
                require(end > 0) { "unterminated string in: $expr" }
                val value = expr.substring(i + 1, end)
                if (key.value == "event_name") events += value.split("|") else attributes += key.value
                end + 1
            } else {
                val number = NUMBER.matchAt(expr, i)
                    ?: error("`${key.value}` compared to something unreadable in: $expr")
                attributes += key.value
                number.range.last + 1
            }
        }
    }

    GROUP_BY.findAll(expr).forEach { match ->
        match.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { attributes += it }
    }
    legendFormat?.let { format -> LEGEND_FIELD.findAll(format).forEach { attributes += it.groupValues[1] } }

    return Query(
        expr = expr,
        events = events,
        attributes = attributes - AMBIENT_KEYS,
        unwrapped = unwrapped - AMBIENT_KEYS,
    )
}

/** The index of the `}` closing the stream selector opened at [open], skipping quoted values; -1 if none. */
private fun selectorEnd(expr: String, open: Int): Int {
    var inString = false
    for (index in open + 1 until expr.length) {
        when (expr[index]) {
            '"' -> inString = !inString
            '}' -> if (!inString) return index
        }
    }
    return -1
}

/**
 * Comment lines inside an argument list, stripped *before* the arguments are
 * split.
 *
 * Emit sites here are heavily commented, and a comment is prose, and prose has
 * commas in it. Arguments are separated on top-level commas, so a comment reading
 * "clear rate, time, retries" becomes three arguments and the genuine
 * `"key" to value` after it is left fused to the last fragment where
 * [ATTRIBUTE_PAIR] cannot match it. Every attribute introduced by a comment goes
 * invisible, and the failure that follows points at the dashboard when the bug is
 * here — listing "emits: […]" without the attribute the emit site plainly has.
 */
private val COMMENT_LINE = Regex("""(?m)^\s*//[^\n]*\n""")

private fun String.withoutComments(): String = COMMENT_LINE.replace(this, "")

private val LOG_EVENT_CALL = Regex("""logEvent\s*\(""")
private val ATTRIBUTE_PAIR = Regex("""^\s*"([a-z0-9_]+)"\s+to\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)
private val STRINGY_VALUE = Regex("""^"|\.name\b|\.toString\(\)|simpleName|\.lowercase\(\)""")
private val EVENT_NAME = Regex("""[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*""")

private fun String.looksLikeAString(): Boolean = STRINGY_VALUE.containsMatchIn(this)

private val ANALYTICS_VIEW_READ = Regex("""\banalytics\.([a-z0-9_]+)""")
private val SUPPORT_VIEW_READ = Regex("""\bsupport\.([a-z0-9_]+)""")

/** The views in [schema] the server's Flyway migrations create, by name. */
private fun viewsInMigrations(schema: String = "analytics"): Set<String> {
    val root = System.getProperty(REPO_ROOT_PROPERTY)
        ?: error("$REPO_ROOT_PROPERTY is unset — apps/integration/build.gradle.kts should supply it")
    val created = Regex("""CREATE\s+(?:OR\s+REPLACE\s+)?VIEW\s+$schema\.([a-z0-9_]+)""", RegexOption.IGNORE_CASE)
    val migrations = File(root, "apps/server/src/main/resources/db/migration")
        .listFiles { f: File -> f.isFile && f.extension == "sql" }
        .orEmpty()
    return migrations.flatMap { file -> created.findAll(file.readText()).map { it.groupValues[1] }.toList() }
        .toSet()
}

/** Event name → attribute key → every emit-site value expression seen for it. */
private fun scanLogEventCalls(): Map<String, Map<String, List<String>>> {
    val root = System.getProperty(REPO_ROOT_PROPERTY)
        ?: error("$REPO_ROOT_PROPERTY is unset — apps/integration/build.gradle.kts should supply it")

    val sources = listOf("libraries", "features", "apps")
        .map { File(root, it) }
        .flatMap { dir -> dir.walkTopDown().onEnter { it.name !in NOT_SOURCE }.toList() }
        .filter { it.isProductionKotlin() }
    assertTrue(sources.size > MINIMUM_SOURCE_FILES, "only found ${sources.size} source files to scan")

    val found = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
    sources.forEach { found.absorbLogEventCalls(it.readText()) }
    return found
}

/** `.claude` holds agent worktrees, which are full checkouts of this repo. */
private val NOT_SOURCE = setOf("build", ".git", ".claude")

/**
 * Test sources are excluded because they emit invented events — `example.completed`
 * and friends — and an invented event is not evidence that a dashboard has
 * something to query.
 */
private fun File.isProductionKotlin(): Boolean {
    if (!isFile || extension != "kt") return false
    val parts = path.split(File.separator)
    return parts.none { it.endsWith("Test") || it == "test" }
}

private fun MutableMap<String, MutableMap<String, MutableList<String>>>.absorbLogEventCalls(source: String) {
    LOG_EVENT_CALL.findAll(source).forEach { call ->
        val open = call.range.last
        val arguments = balancedArguments(source, open) ?: return@forEach
        val parts = splitTopLevel(arguments.withoutComments())
        val name = parts.firstOrNull()?.trim()?.removeSurrounding("\"") ?: return@forEach
        if (!name.matches(EVENT_NAME)) return@forEach

        val attributes = getOrPut(name) { mutableMapOf() }
        parts.drop(1).forEach { part ->
            val pair = ATTRIBUTE_PAIR.matchEntire(part) ?: return@forEach
            attributes.getOrPut(pair.groupValues[1]) { mutableListOf() } += pair.groupValues[2].trim().trimEnd(',')
        }
    }
}

/** The text between the call's parentheses, tracking nesting and string literals. */
private fun balancedArguments(source: String, openParen: Int): String? {
    var depth = 0
    var index = openParen
    var inString = false
    var escaped = false
    while (index < source.length) {
        val c = source[index]
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '(' -> depth++
            c == ')' -> {
                depth--
                if (depth == 0) return source.substring(openParen + 1, index)
            }
        }
        index++
    }
    return null
}

private fun splitTopLevel(arguments: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    var inString = false
    var escaped = false
    arguments.forEach { c ->
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '(' || c == '[' -> depth++
            c == ')' || c == ']' -> depth--
            c == ',' && depth == 0 -> {
                parts += current.toString()
                current.clear()
                return@forEach
            }
        }
        current.append(c)
    }
    if (current.isNotBlank()) parts += current.toString()
    return parts
}
