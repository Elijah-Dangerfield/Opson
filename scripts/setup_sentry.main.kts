#!/usr/bin/env kotlin

@file:Import("lib/setup_store.main.kts")

/**
 * One-shot Sentry setup for a project generated from this template.
 *
 * Sentry ships off: `telemetry.properties` has a blank `sentry.dsn`, and blank
 * is a supported value meaning "reporting is disabled". Nothing warns, nothing
 * fails, and a project can sit like that for months. This script is the
 * on-ramp — it creates (or adopts) the Sentry project, writes the DSN into the
 * committed `telemetry.properties`, pushes the CI config, then sends a real
 * event and waits for it to show up, because the failure being fixed is
 * silence.
 *
 * Re-runnable. Everything it does is an upsert, so running it again repairs
 * drift instead of creating a second project.
 *
 * Credentials come from the environment first, then the machine-local store
 * (`scripts/lib/setup_store.main.kts`), then you. Nothing is required to be in
 * the store; it only stops you re-typing the same org and tokens for every new
 * project.
 *
 * Run from the project root:
 *   ./scripts/setup_sentry.main.kts
 *   ./scripts/setup_sentry.main.kts --non-interactive   # every value from env/store
 */

import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

val SENTRY_API = "https://sentry.io/api/0"
val TELEMETRY_FILE = "telemetry.properties"
val VERSIONS_FILE = "versions.properties"
val POLL_ATTEMPTS = 30
val POLL_INTERVAL_MS = 4000L

// Terminal helpers (green/yellow/red/bold/dim/die/prompt/promptSecret/confirm)
// come from lib/setup_store.main.kts, so every setup script prompts the same way.

// HTTP (`request`/`Response`/`encode`), JSON, `gh` helpers and the
// properties read/write live in lib/setup_store.main.kts.

/** Sentry payloads are large; these scripts only ever want one key out of them. */
fun stringValues(json: String, key: String): List<String> =
    Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"")
        .findAll(json)
        .map { it.groupValues[1] }
        .toList()

// ── sentry ──────────────────────────────────────────────────────────────────

fun explainTokenRequirement() {
    bold("\nSentry auth token")
    println(
        """
        This needs a PERSONAL token. Sentry has two kinds and they are not
        interchangeable. The prefix tells you which one you are holding:

          sntryu_   Personal token. Account dropdown, top left of sentry.io,
                    then Personal Tokens. Scopes are selectable: tick
                    project:read, project:write and org:read.
                    Direct link: https://sentry.io/settings/account/api/auth-tokens/

          sntrys_   Organization token. Settings > Developer Settings >
                    Organization Tokens. Its permissions are fixed and cannot
                    be selected, so it answers 403 to every read endpoint and
                    cannot look up an org or read a DSN. This is what CI wants,
                    and the script asks for it separately at the end.

        If the page you are on offers you permission checkboxes, you are on the
        personal one. If it does not, you are on the organization one.

        Nothing is written to this repo. If you say yes when asked, it is saved
        to the machine-local store so your next project does not ask again. See
        ./scripts/setup_credentials.main.kts.
        """.trimIndent()
    )
    println()
}

/**
 * Names the wrong-token mistake before spending a network call on it.
 *
 * Sentry's prefixes are unambiguous: `sntryu_` personal, `sntrys_`
 * organization, `sntrya_` user-app, `sntryi_` internal integration. An org
 * token here fails with a 403 from the first read, which reads as a broken or
 * under-scoped token rather than the wrong kind of token, and the natural next
 * move is to go back and add scopes that an org token cannot have.
 *
 * Legacy tokens are bare 64-hex with no prefix, so an unrecognised shape is
 * passed through rather than rejected.
 */
fun rejectWrongTokenKind(token: String) {
    val problem = when {
        token.startsWith("sntrys_") ->
            "an ORGANIZATION token. Its permissions are fixed and exclude every read " +
                "endpoint this script needs, so no amount of re-issuing it will help."
        token.startsWith("sntryi_") ->
            "an INTERNAL INTEGRATION token, which is org-scoped rather than user-scoped."
        else -> return
    }
    die(
        "That is $problem\n" +
            "   You want a personal token, prefixed sntryu_: sentry.io, Account dropdown\n" +
            "   in the top left, then Personal Tokens. Tick project:read, project:write\n" +
            "   and org:read. Keep the one you just pasted, it is exactly what the CI\n" +
            "   step at the end of this script asks for."
    )
}

fun resolveOrg(token: String, suggested: String?, interactive: Boolean): String {
    val response = request("GET", "$SENTRY_API/organizations/", token)
    if (response.code == 401) die("Sentry rejected that token (401). Create a new personal token and re-run.")
    if (response.code == 403) {
        die(
            "That token answered 403 to /organizations/, so it cannot read an org. " +
                "Organization tokens (sntrys_) do this by design. Use a personal token " +
                "(sntryu_) with org:read instead."
        )
    }
    if (!response.isSuccess) die("Could not list organizations (HTTP ${response.code}): ${response.body.take(300)}")

    val slugs = stringValues(response.body, "slug").distinct()
    if (slugs.isEmpty()) die("That token can see no organizations. Check it has org:read.")
    if (slugs.size > 1) println("Organizations visible to this token: ${slugs.joinToString(", ")}")

    val default = suggested?.takeIf { it in slugs } ?: slugs.first()
    val chosen = if (interactive) prompt("Sentry org slug", default) else default
    if (chosen !in slugs) {
        val check = request("GET", "$SENTRY_API/organizations/$chosen/", token)
        if (!check.isSuccess) die("Org '$chosen' is not readable with this token (HTTP ${check.code}).")
    }
    return chosen
}

fun firstTeam(token: String, org: String, interactive: Boolean): String {
    val response = request("GET", "$SENTRY_API/organizations/$org/teams/", token)
    if (!response.isSuccess) die("Could not list teams in '$org' (HTTP ${response.code}).")
    val slugs = stringValues(response.body, "slug").distinct()
    if (slugs.isEmpty()) die("Org '$org' has no teams. Create one in Sentry, then re-run.")
    if (slugs.size == 1) return slugs.first()
    println("Teams in $org: ${slugs.joinToString(", ")}")
    if (!interactive) return slugs.first()
    return prompt("Team to own the project", slugs.first())
}

fun ensureProject(token: String, org: String, project: String, interactive: Boolean): Boolean {
    val existing = request("GET", "$SENTRY_API/projects/$org/$project/", token)
    if (existing.isSuccess) {
        green("✓ Adopted existing Sentry project $org/$project")
        return false
    }
    if (existing.code != 404) {
        die("Unexpected response looking up $org/$project (HTTP ${existing.code}): ${existing.body.take(300)}")
    }

    val team = firstTeam(token, org, interactive)
    val payload = """{"name":"$project","slug":"$project","platform":"android"}"""
    val created = request("POST", "$SENTRY_API/teams/$org/$team/projects/", token, payload)
    if (created.code == 403) {
        die("Creating the project was refused (403). The token needs project:write (a user token scope).")
    }
    if (!created.isSuccess) {
        die("Could not create $org/$project (HTTP ${created.code}): ${created.body.take(300)}")
    }
    green("✓ Created Sentry project $org/$project under team $team")
    return true
}

fun readDsn(token: String, org: String, project: String): String {
    val response = request("GET", "$SENTRY_API/projects/$org/$project/keys/", token)
    if (!response.isSuccess) die("Could not read client keys for $org/$project (HTTP ${response.code}).")
    return stringValues(response.body, "public")
        .firstOrNull { it.startsWith("https://") }
        ?: die("$org/$project has no enabled client key. Add one in Project Settings → Client Keys.")
}

data class Dsn(val publicKey: String, val host: String, val projectId: String)

fun parseDsn(dsn: String): Dsn {
    val match = Regex("^https://([^@]+)@([^/]+)/(.+)$").find(dsn.trim())
        ?: die("DSN is not in the expected https://<key>@<host>/<id> shape: $dsn")
    val (key, host, id) = match.destructured
    return Dsn(key, host, id)
}

/**
 * Posts straight to the DSN's store endpoint rather than shelling out to
 * sentry-cli. One less thing to install, and it exercises the DSN exactly as
 * the app will: if this is accepted, the value written to telemetry.properties
 * is live.
 */
fun sendTestEvent(dsn: Dsn, marker: String): Boolean {
    val eventId = java.util.UUID.randomUUID().toString().replace("-", "")
    val payload = """
        {"event_id":"$eventId","timestamp":"${Instant.now()}","platform":"other",
         "level":"info","logger":"setup_sentry","environment":"setup-check",
         "message":{"formatted":"$marker"},"tags":{"setup_check":"$marker"}}
    """.trimIndent().replace("\n", "")

    val response = request(
        method = "POST",
        url = "https://${dsn.host}/api/${dsn.projectId}/store/",
        body = payload,
        extraHeaders = mapOf(
            "X-Sentry-Auth" to "Sentry sentry_version=7, sentry_client=setup-sentry/1.0, sentry_key=${dsn.publicKey}"
        ),
    )
    if (!response.isSuccess) {
        red("✗ Sentry rejected the test event (HTTP ${response.code}): ${response.body.take(300)}")
        return false
    }
    green("✓ Test event accepted by ingest (id $eventId)")
    return true
}

fun awaitEvent(token: String, org: String, project: String, marker: String): Boolean {
    print("Waiting for the event to appear in $org/$project")
    repeat(POLL_ATTEMPTS) {
        val url = "$SENTRY_API/projects/$org/$project/issues/" +
            "?query=${encode("setup_check:$marker")}&statsPeriod=1h"
        val response = request("GET", url, token)
        // A tag search that matched returns a populated array; a miss returns
        // literally "[]", so length is the whole test.
        if (response.isSuccess && response.body.trim().length > 2) {
            println()
            return true
        }
        print(".")
        System.out.flush()
        Thread.sleep(POLL_INTERVAL_MS)
    }
    println()
    return false
}

// ── ci ──────────────────────────────────────────────────────────────────────

fun printManualCiCommands(org: String, project: String) {
    yellow("\nSet these yourself once the repo exists (paste as-is; it prompts you):")
    println(
        """
        stty -echo; printf "Paste the Sentry org token (sntrys_...): "; read -r T; stty echo; printf "\n"
        gh secret set SENTRY_AUTH_TOKEN --body "${'$'}T" && unset T
        gh variable set SENTRY_ORG --body "$org"
        gh variable set SENTRY_PROJECT --body "$project"
        """.trimIndent()
    )
}

/**
 * Whether [token] can actually upload debug files to [org], checked before the
 * secret is set rather than discovered from a failed release build.
 *
 * A token that authenticates but cannot upload is indistinguishable from a
 * working one until something needs it, and the failure hides well: on iOS
 * fastlane logs sentry-cli's error and carries on, so the run stays green. An
 * app generated from this template carried a rejected token for nine days
 * before an Android release job surfaced it as "Invalid org token (401)".
 *
 * `/organizations/<org>/chunk-upload/` is the endpoint sentry-cli uploads
 * through, so a 200 means the thing CI does will work. The friendlier-looking
 * endpoints are actively wrong here: an `org:ci` token answers 403 to
 * `/organizations/<org>/` and `/projects/<org>/<project>/` while being
 * perfectly good for uploads, so validating against those would reject working
 * tokens.
 *
 * A network failure is not a verdict on the token, so it warns and proceeds.
 * Only an answer from Sentry refuses.
 */
fun ciTokenCanUpload(token: String, org: String): Boolean {
    val response = try {
        request("GET", "https://sentry.io/api/0/organizations/$org/chunk-upload/", token = token)
    } catch (e: Exception) {
        yellow("Could not reach sentry.io to check the token (${e.message}). Setting it unchecked.")
        return true
    }
    if (response.isSuccess) {
        green("✓ Token can upload to $org")
        return true
    }
    val reason = when (response.code) {
        401 -> "rejected outright, so it is revoked, expired, or was mistyped"
        403 -> "authenticates but lacks the org:ci scope uploads need"
        404 -> "no such organization, so check the org slug rather than the token"
        else -> "unexpected response"
    }
    red("That token cannot upload: HTTP ${response.code}, $reason")
    yellow("Nothing was set, deliberately: a token that fails here fails in CI, where")
    yellow("iOS logs the error and stays green.")
    // Named only when it is actually present. It ships with CI, and this script
    // runs whether or not CI was enabled.
    if (File("scripts/setup_sentry_ci.sh").exists()) {
        yellow("Fix the token, then ./scripts/setup_sentry_ci.sh sets just this secret.")
    } else {
        yellow("Fix the token, then re-run this script.")
    }
    return false
}

fun configureCi(org: String, project: String, values: SetupValues, interactive: Boolean) {
    bold("\nCI configuration")
    when (githubCliState()) {
        GithubCliState.NOT_INSTALLED -> yellow("gh is not installed.")
        GithubCliState.NOT_AUTHENTICATED -> yellow("gh is installed but not authenticated, run `gh auth login`.")
        GithubCliState.READY -> Unit
    }
    if (!hasGhRepo()) {
        yellow("No GitHub repo reachable via gh (not installed, not authenticated, or no remote yet).")
        printManualCiCommands(org, project)
        return
    }

    val orgOk = runGh("variable", "set", "SENTRY_ORG", "--body", org)
    val projectOk = runGh("variable", "set", "SENTRY_PROJECT", "--body", project)
    if (orgOk && projectOk) {
        green("✓ Set repo variables SENTRY_ORG=$org, SENTRY_PROJECT=$project")
    } else {
        yellow("Could not set repo variables, set SENTRY_ORG / SENTRY_PROJECT by hand.")
    }

    println(
        "\nCI uploads mappings and dSYMs with an ORGANIZATION token (sntrys_…), " +
            "not the user token above. One org token covers every project you generate, " +
            "so this is the value the machine store earns its keep on."
    )
    // Skipping is only offered when the token would otherwise have to be typed.
    // Once the env or the store already has it there is nothing to weigh up.
    val alreadyKnown = values.optional(Keys.SENTRY_CI_TOKEN) != null
    if (!alreadyKnown && interactive &&
        !confirm("Set the SENTRY_AUTH_TOKEN repo secret now?", default = true)
    ) {
        yellow("Skipped. Release builds will still ship; mappings just won't upload.")
        printManualCiCommands(org, project)
        return
    }
    val ciToken = values.require(Keys.SENTRY_CI_TOKEN)
    if (!ciToken.startsWith("sntrys_")) {
        yellow("That does not look like an organization token. Setting it anyway, CI needs org:ci.")
    }
    if (!ciTokenCanUpload(ciToken, org)) return
    if (runGh("secret", "set", "SENTRY_AUTH_TOKEN", stdin = ciToken)) {
        green("✓ Set repo secret SENTRY_AUTH_TOKEN")
    } else {
        yellow("Could not set the secret.")
        printManualCiCommands(org, project)
    }
}

// ── main ────────────────────────────────────────────────────────────────────

val root = File(".").canonicalFile
val telemetryFile = File(root, TELEMETRY_FILE)
if (!telemetryFile.exists()) {
    die("$TELEMETRY_FILE not found. Run this from the project root.")
}

val interactive = "--non-interactive" !in args.toList()
val values = SetupValues(interactive = interactive)

bold("\n━━ Sentry setup ━━")
println("Creates or adopts a Sentry project, commits its DSN, wires CI, and proves an event arrives.")

values.plan(listOf(Keys.SENTRY_USER_TOKEN, Keys.SENTRY_ORG, Keys.SENTRY_CI_TOKEN))

explainTokenRequirement()
val userToken = values.require(Keys.SENTRY_USER_TOKEN).also(::rejectWrongTokenKind)

// This project's own telemetry.properties wins over the machine store: a repo
// that already names an org is describing where its events actually go, and
// pointing a re-run somewhere else would split one app's issues across two orgs.
val knownOrg = readProperty(telemetryFile, "sentry.org") ?: values.optional(Keys.SENTRY_ORG)
val org = resolveOrg(userToken, knownOrg, interactive)
values.offerToRemember(Keys.SENTRY_ORG, org)

// Derived from the app id rather than the directory name, so the Sentry slug
// and the installed package stay recognisably the same app. Only the last two
// segments: the reverse-DNS prefix is the same for every app you ship, so
// including it makes every slug start identically. Sentry slugs are
// lowercase-alphanumeric-and-hyphen.
val applicationId = readProperty(File(root, VERSIONS_FILE), "applicationId")
val defaultProject = readProperty(telemetryFile, "sentry.project")
    ?: applicationId?.split('.')?.filter { it.isNotBlank() }?.takeLast(2)
        ?.joinToString("-")?.lowercase()?.replace(Regex("[^a-z0-9-]"), "-")
    ?: root.name.lowercase()
val project = if (interactive) prompt("Sentry project slug", defaultProject) else defaultProject

ensureProject(userToken, org, project, interactive)
val dsn = readDsn(userToken, org, project)
val parsedDsn = parseDsn(dsn)

upsertProperty(telemetryFile, "sentry.dsn", dsn)
upsertProperty(telemetryFile, "sentry.org", org)
upsertProperty(telemetryFile, "sentry.project", project)
green("✓ Wrote the DSN into $TELEMETRY_FILE, commit it, so a fresh clone reports with no local setup")

if (readProperty(File(root, "local.properties"), "sentry.dsn") != null) {
    yellow(
        "\nlocal.properties still sets sentry.dsn, which overrides the committed value " +
            "for you but for nobody else. Delete that line unless you mean to point " +
            "your own builds somewhere different."
    )
}

configureCi(org, project, values, interactive)

bold("\nProving it works")
val marker = "setupcheck" + java.util.UUID.randomUUID().toString().take(8).replace("-", "")
val delivered = sendTestEvent(parsedDsn, marker) && awaitEvent(userToken, org, project, marker)

// Before the verdict rather than after it, so the last thing on screen stays
// the one line that says whether Sentry is actually on. Unconditional: a run
// that ends green while leaving a credential blank is the failure this whole
// script exists to stop.
values.printUnconfigured()

println()
if (delivered) {
    green("━━ Sentry is live ━━")
    println("A test event reached $org/$project and was found by search.")
    println("https://sentry.io/organizations/$org/issues/?project=&query=${encode("setup_check:$marker")}")
    println()
    println("Next: commit $TELEMETRY_FILE, then rebuild. Debug builds report from the next launch.")
} else {
    red("━━ Setup did NOT finish ━━")
    println(
        """
        The DSN is written, but no event came back. Do not assume it works.
        Common causes, in the order worth checking:
          - the project has inbound filters or a spike-protection quota on it
          - the event is still in the ingest queue (rare, but wait a minute and
            search Sentry for setup_check:$marker by hand)
          - the org/project pair is not the one the DSN belongs to
        """.trimIndent()
    )
    exitProcess(1)
}
