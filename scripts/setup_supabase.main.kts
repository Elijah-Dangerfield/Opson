#!/usr/bin/env kotlin

@file:Import("lib/setup_store.main.kts")

/**
 * Creates (or adopts) this project's Supabase project and configures it for the
 * auth this template actually ships.
 *
 * What it does, all of it an upsert so re-running repairs drift:
 *  - creates the Supabase project, or adopts one that already has the name
 *  - waits for it to come up, because a fresh project is not immediately usable
 *  - turns on anonymous sign-ins (the guest flow does not work without it)
 *  - sets the site URL and the redirect allow-list to this project's custom
 *    scheme, so browser OAuth and the confirm-email link return to the app
 *  - enables Apple with your iOS bundle ID as the authorized client, which is
 *    all native Sign in with Apple needs — no Services ID, no expiring secret
 *  - writes `supabase.projectId` / `url` / `anonKey` into `local.properties`
 *  - fills the Supabase half of `apps/server/.env`, if this project has a server
 *
 * What it does NOT do: enable Google. That needs an OAuth client from the Google
 * Cloud console, which has no API worth automating, and the app works without
 * it.
 *
 * Run from the project root:
 *   ./scripts/setup_supabase.main.kts
 *   ./scripts/setup_supabase.main.kts --non-interactive
 */

import java.io.File
import java.security.SecureRandom
import kotlin.system.exitProcess

val SUPABASE_API = "https://api.supabase.com/v1"
val READY_POLL_ATTEMPTS = 60
val READY_POLL_INTERVAL_MS = 5_000L

// ── api ─────────────────────────────────────────────────────────────────────

fun supabase(method: String, path: String, token: String, body: String? = null): Response =
    request(method, "$SUPABASE_API$path", token, body)

fun Response.orDie(what: String): Json {
    if (code == 401) die("Supabase rejected that token (401). Create a new one at https://supabase.com/dashboard/account/tokens")
    if (!isSuccess) die("$what failed (HTTP $code): ${body.take(400)}")
    return runCatching { parseJson(body) }
        .getOrElse { die("$what returned something that is not JSON: ${body.take(200)}") }
}

fun resolveOrganization(token: String, interactive: Boolean, suggested: String?): String {
    val orgs = supabase("GET", "/organizations", token).orDie("Listing organizations").items
    val slugs = orgs.mapNotNull { it["slug"].text }
    if (slugs.isEmpty()) die("That token can see no organizations. Create one in the Supabase dashboard first.")
    if (slugs.size == 1) return slugs.single()
    println("Organizations: " + orgs.joinToString(", ") { "${it["name"].text} (${it["slug"].text})" })
    val default = suggested?.takeIf { it in slugs } ?: slugs.first()
    if (!interactive) return default
    val chosen = prompt("Organization slug", default)
    if (chosen !in slugs) die("'$chosen' is not one of: ${slugs.joinToString(", ")}")
    return chosen
}

/** The ref of a project named [name], or null. */
fun findProject(token: String, name: String): Json? =
    supabase("GET", "/projects", token).orDie("Listing projects").items
        .firstOrNull { it["name"].text == name }

/**
 * A database password for the new project.
 *
 * Generated rather than prompted, and deliberately alphanumeric: this value ends
 * up inside a `postgresql://` URL, and the punctuation classes a "strong
 * password" generator reaches for have to be percent-encoded there. That is a
 * footgun `apps/server/.env.example` already warns about (`$` becomes `%24`),
 * and the failure mode is a connection string that looks right and does not
 * parse. 40 alphanumeric characters is ~206 bits; the entropy is not the
 * constraint here.
 */
fun generateDatabasePassword(): String {
    val alphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    val random = SecureRandom()
    return (1..40).map { alphabet[random.nextInt(alphabet.size)] }.joinToString("")
}

fun awaitProjectReady(token: String, ref: String): Boolean {
    print("Waiting for $ref to come up")
    repeat(READY_POLL_ATTEMPTS) {
        val status = supabase("GET", "/projects/$ref", token)
            .takeIf { it.isSuccess }
            ?.let { parseJson(it.body)["status"].text }
        if (status == "ACTIVE_HEALTHY") {
            println()
            return true
        }
        if (status == "INIT_FAILED") {
            println()
            die("Supabase reported INIT_FAILED for $ref. Delete it in the dashboard and re-run.")
        }
        print(".")
        System.out.flush()
        Thread.sleep(READY_POLL_INTERVAL_MS)
    }
    println()
    return false
}

/**
 * The key the client ships with.
 *
 * Supabase is mid-migration from legacy `anon` JWTs to publishable `sb_…` keys,
 * so a project can hand back either and a new project hands back both. Prefer
 * publishable and fall back, rather than assuming whichever this account
 * happens to have today.
 */
fun readAnonKey(token: String, ref: String): String {
    val keys = supabase("GET", "/projects/$ref/api-keys?reveal=true", token)
        .orDie("Reading API keys").items
    fun keyOfType(type: String) = keys.firstOrNull { it["type"].text == type }?.get("api_key").text
    return keyOfType("publishable")
        ?: keys.firstOrNull { it["name"].text == "anon" }?.get("api_key").text
        ?: keyOfType("legacy")
        ?: die("$ref has no readable publishable/anon key. Check the token has the api-keys scope.")
}

/** The service-role key, for the server only. Null when the token cannot see it. */
fun readServiceRoleKey(token: String, ref: String): String? {
    val keys = supabase("GET", "/projects/$ref/api-keys?reveal=true", token)
        .takeIf { it.isSuccess }
        ?.let { parseJson(it.body) }.items
    return keys.firstOrNull { it["name"].text == "service_role" }?.get("api_key").text
        ?: keys.firstOrNull { it["type"].text == "secret" }?.get("api_key").text
}

// ── main ────────────────────────────────────────────────────────────────────

val root = File(".").canonicalFile
if (!File(root, "settings.gradle.kts").exists()) {
    die("settings.gradle.kts not found. Run this from the project root.")
}

val interactive = "--non-interactive" !in args.toList()
val values = SetupValues(interactive = interactive)

bold("\n━━ Supabase setup ━━")
println("Creates or adopts a Supabase project and configures the auth this app ships with.")

values.plan(listOf(Keys.SUPABASE_ACCESS_TOKEN))
val token = values.require(Keys.SUPABASE_ACCESS_TOKEN)

val organization = resolveOrganization(token, interactive, values.optional(Keys.SUPABASE_ORG))
values.offerToRemember(Keys.SUPABASE_ORG, organization)

// The app id is the stable name for this project — the directory can be
// renamed and the display name can have spaces in it.
val applicationId = readProperty(File(root, "versions.properties"), "applicationId")
val defaultName = applicationId?.substringAfterLast('.') ?: root.name.lowercase()
val projectName = if (interactive) prompt("Supabase project name", defaultName) else defaultName

// The custom scheme the app registers for its auth callbacks. Derived, not
// asked: it is already baked into AndroidManifest.xml and Info.plist, and a
// value typed here that disagreed with those would fail at redirect time with
// nothing pointing at this script.
val scheme = applicationId?.substringAfterLast('.')?.lowercase() ?: root.name.lowercase()
val redirectUrls = listOf("$scheme://login-callback", "$scheme://auth/confirmed")

// A function, not an `if` around a deferred `val`: top-level declarations in a
// .main.kts are class properties, so they have to be initialized where they are
// declared.
fun adoptOrCreateProject(): String {
    findProject(token, projectName)?.let { existing ->
        val ref = existing["ref"].text ?: die("Supabase returned a project with no ref")
        green("✓ Adopted existing Supabase project $projectName ($ref)")
        return ref
    }
    if (interactive && !confirm("Create a new free-tier Supabase project '$projectName' in $organization?")) {
        yellow("Stopped. Nothing was created.")
        exitProcess(0)
    }
    val databasePassword = generateDatabasePassword()
    val created = supabase(
        "POST", "/projects", token,
        jsonObject(
            "name" to jsonString(projectName),
            "organization_slug" to jsonString(organization),
            "db_pass" to jsonString(databasePassword),
            "plan" to jsonString("free"),
            "region" to jsonString("us-east-1"),
        ),
    ).orDie("Creating the project")
    val ref = created["ref"].text ?: die("Supabase created a project but returned no ref")
    green("✓ Created Supabase project $projectName ($ref)")

    // Printed once and never stored: it is per project, so it does not belong
    // in the machine store, and the Management API will not hand it back.
    // Losing it means resetting the database password in the dashboard.
    bold("\nDatabase password, save this now, it is not recoverable")
    println("  $databasePassword")
    println("  Connection string for apps/server/.env:")
    println("  postgresql://postgres:$databasePassword@db.$ref.supabase.co:5432/postgres")
    println()
    return ref
}

val ref = adoptOrCreateProject()

if (!awaitProjectReady(token, ref)) {
    yellow("$ref is still provisioning. The config below will fail until it is up, re-run in a minute.")
}

val projectUrl = "https://$ref.supabase.co"

bold("\nAuth configuration")
val bundleId = applicationId ?: prompt("iOS bundle ID (for native Sign in with Apple)")
val authBody = jsonObject(
    // The guest flow mints anonymous sessions during onboarding. Without this
    // every first launch fails at the point the user has done nothing wrong.
    "external_anonymous_users_enabled" to "true",
    // Confirm-email on. The app has a verify-email surface and a
    // `://auth/confirmed` redirect wired to it.
    "mailer_autoconfirm" to "false",
    "site_url" to jsonString(redirectUrls.first()),
    "uri_allow_list" to jsonString(redirectUrls.joinToString(",")),
    // Native Sign in with Apple validates the identity token against this list.
    // No Services ID and no client secret — see decisions.md, 2026-09-20.
    "external_apple_enabled" to "true",
    "external_apple_client_id" to jsonString(bundleId),
)
val authResponse = supabase("PATCH", "/projects/$ref/config/auth", token, authBody)
if (authResponse.isSuccess) {
    green("✓ Anonymous sign-ins on, email confirmation on, Apple authorized for $bundleId")
    green("✓ Redirect allow-list: ${redirectUrls.joinToString(", ")}")
} else {
    yellow("Could not update auth config (HTTP ${authResponse.code}): ${authResponse.body.take(300)}")
    yellow("Set these by hand in Authentication → Providers / URL Configuration.")
}

bold("\nClient configuration")
val anonKey = readAnonKey(token, ref)
val localProperties = File(root, "local.properties")
upsertProperty(localProperties, "supabase.projectId", ref)
upsertProperty(localProperties, "supabase.url", projectUrl)
upsertProperty(localProperties, "supabase.anonKey", anonKey)
green("✓ Wrote supabase.projectId / url / anonKey into local.properties")
dim("  local.properties is gitignored, CI reads SUPABASE_PROJECT_ID / SUPABASE_ANON_KEY instead.")

if (hasGhRepo()) {
    val projectOk = setGhSecret("SUPABASE_PROJECT_ID", ref)
    val keyOk = setGhSecret("SUPABASE_ANON_KEY", anonKey)
    if (projectOk && keyOk) {
        green("✓ Set repo secrets SUPABASE_PROJECT_ID and SUPABASE_ANON_KEY")
    } else {
        yellow("Could not set the repo secrets, set SUPABASE_PROJECT_ID / SUPABASE_ANON_KEY by hand.")
    }
} else {
    yellow("No GitHub repo reachable via gh, set SUPABASE_PROJECT_ID / SUPABASE_ANON_KEY yourself once there is one.")
}

val serverEnvExample = File(root, "apps/server/.env.example")
if (serverEnvExample.exists()) {
    bold("\nServer configuration")
    val serverEnv = File(root, "apps/server/.env")
    if (!serverEnv.exists()) serverEnvExample.copyTo(serverEnv)
    upsertProperty(serverEnv, "SUPABASE_URL", projectUrl)
    val serviceRoleKey = readServiceRoleKey(token, ref)
    if (serviceRoleKey != null) {
        upsertProperty(serverEnv, "SUPABASE_SERVICE_ROLE_KEY", serviceRoleKey)
        green("✓ Wrote SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY into apps/server/.env (gitignored)")
        yellow("  The service role key bypasses row-level security. Server secrets only, never the client.")
    } else {
        green("✓ Wrote SUPABASE_URL into apps/server/.env")
        yellow("  Could not read the service role key. Copy it from Settings → API Keys if you want")
        yellow("  in-app account deletion and display-name mirroring.")
    }
    dim("  DATABASE_URL is still yours to set, see the connection string above, or apps/server/.env.example.")
    dim("  ./scripts/setup_fly.main.kts pushes these to the deployed server.")
}

values.printUnconfigured()

bold("\n━━ Supabase is configured ━━")
println("  Project:   $projectUrl")
println("  Dashboard: https://supabase.com/dashboard/project/$ref")
println()
println("Next: rebuild, complete onboarding as a guest, and check")
println("Authentication → Users for a row with is_anonymous = true.")
println()
println("Not done here: Google sign-in (needs a Google Cloud OAuth client).")
println("The app runs without it, the button is simply not wired.")

exitProcess(0)
