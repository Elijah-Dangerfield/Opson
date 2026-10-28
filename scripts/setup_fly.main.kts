#!/usr/bin/env kotlin

@file:Import("lib/setup_store.main.kts")

/**
 * Stands up this project's Fly.io apps and wires CI to deploy to them.
 *
 * What it does, all of it an upsert so re-running repairs drift:
 *  - creates the dev and prod Fly apps (skipping any that exist)
 *  - points `apps/server/fly.toml` and `fly.prod.toml` at them
 *  - pushes the Supabase values from `apps/server/.env` as Fly secrets
 *  - mints a deploy token per app and sets FLY_API_TOKEN_DEV / _PROD as repo
 *    secrets, which is what `server-deploy.yml` authenticates with
 *  - gives dev an admin token, on the Fly app and as ADMIN_API_TOKEN_DEV, so
 *    the dev deploy can upload the config manifest and turn on new flags
 *  - offers the first deploy, and proves it by curling `/_health`
 *
 * Creating apps is free; they cost nothing until something runs on them. The
 * first deploy starts a machine, so that step asks first and is easy to decline.
 *
 * Run from the project root:
 *   ./scripts/setup_fly.main.kts
 *   ./scripts/setup_fly.main.kts --non-interactive   # no deploy, no prompts
 */

import java.io.File
import java.security.SecureRandom
import kotlin.system.exitProcess

// ── fly ─────────────────────────────────────────────────────────────────────

fun flyAppExists(app: String): Boolean = run("fly", "status", "-a", app).ok

fun ensureFlyApp(app: String, organization: String?): Boolean {
    if (flyAppExists(app)) {
        green("✓ Fly app $app already exists")
        return true
    }
    val command = mutableListOf("fly", "apps", "create", app)
    organization?.let { command += listOf("--org", it) }
    val result = run(*command.toTypedArray())
    if (result.ok) {
        green("✓ Created Fly app $app")
        return true
    }
    yellow("Could not create $app: ${result.output.trim().take(300)}")
    return false
}

/**
 * Rewrites `app = '…'` in a fly config.
 *
 * The name in the toml is what `fly deploy --config` and the deploy workflow
 * both resolve against, so a mismatch here does not fail loudly — it deploys
 * somewhere else, or to an app that does not exist, depending on which side is
 * stale.
 */
fun pointConfigAt(file: File, app: String) {
    if (!file.exists()) return
    val updated = file.readLines().joinToString("\n") { line ->
        if (line.trimStart().startsWith("app = ")) "app = '$app'" else line
    }
    file.writeText(updated.trimEnd() + "\n")
    green("✓ ${file.name} → $app")
}

/** Every `KEY=value` in a dotenv file, skipping blanks and comments. */
fun readEnvFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().trim('\'', '"') }
        .filterValues { it.isNotEmpty() }
}

/**
 * Sets [secrets] on [app] through `fly secrets import`, which reads
 * `NAME=VALUE` lines on stdin. `fly secrets set NAME=VALUE` would put the
 * database password and the service-role key on argv, which every process on
 * the machine can read (the reason [createDeployToken] pipes its token too).
 */
fun setFlySecrets(app: String, secrets: Map<String, String>): Boolean {
    if (secrets.isEmpty()) return true
    // `--stage` then `deploy` would be tidier, but staged secrets are invisible
    // until the next deploy — which is exactly the kind of "configured but not
    // live" state this repo keeps getting burned by. Set them for real.
    val lines = secrets.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" }
    val result = run("fly", "secrets", "import", "-a", app, stdin = lines)
    if (!result.ok) yellow("Could not set secrets on $app: ${result.output.trim().take(300)}")
    return result.ok
}

/** Whether a listing (`fly secrets list`, `gh secret list`) names [secret]; null when it failed. */
fun listsSecret(listing: CommandResult, secret: String): Boolean? =
    listing.takeIf { it.ok }?.output?.let { Regex("""(?m)^\s*${Regex.escape(secret)}\s""").containsMatchIn(it) }

fun randomHexToken(): String {
    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Mints a deploy token scoped to one app.
 *
 * `fly tokens create deploy` prints the token and nothing else, so the whole
 * captured stdout is the value. It is piped straight into `gh secret set` on
 * stdin and never becomes a process argument, because argv is readable by every
 * process on the machine.
 */
fun createDeployToken(app: String): String? {
    val result = run("fly", "tokens", "create", "deploy", "-a", app, "--expiry", "8760h")
    if (!result.ok) {
        yellow("Could not mint a deploy token for $app: ${result.output.trim().take(200)}")
        return null
    }
    return result.output.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("FlyV1") }
        ?: result.output.trim().takeIf { it.isNotEmpty() }
}

// ── main ────────────────────────────────────────────────────────────────────

val root = File(".").canonicalFile
val flyConfig = File(root, "apps/server/fly.toml")
if (!flyConfig.exists()) {
    die(
        "apps/server/fly.toml not found.\n" +
            "   This project was generated without a backend (--backend=no), so there is " +
            "nothing to deploy.\n" +
            "   Nothing to do here, that is a valid configuration, not a missing step."
    )
}

val interactive = "--non-interactive" !in args.toList()
val values = SetupValues(interactive = interactive)

bold("\n━━ Fly.io setup ━━")
println("Creates the dev and prod server apps, wires CI to deploy to them, and proves /_health.")

if (!commandExists("fly")) {
    die("The fly CLI is not installed. `brew install flyctl`, then `fly auth login`, then re-run.")
}
if (!run("fly", "auth", "whoami").ok) {
    die("fly is installed but not logged in. Run `fly auth login`, then re-run.")
}

values.plan(listOf(Keys.FLY_ORG))
val organization = values.optional(Keys.FLY_ORG)

val applicationId = readProperty(File(root, "versions.properties"), "applicationId")
val defaultBase = applicationId?.substringAfterLast('.')?.lowercase() ?: root.name.lowercase()
val base = if (interactive) prompt("Fly app name prefix", defaultBase) else defaultBase
val devApp = "$base-server-dev"
val prodApp = "$base-server-prod"

bold("\nApps")
println("  dev:  $devApp")
println("  prod: $prodApp")
dim("  Creating an app is free. Nothing is billed until a machine runs.")
if (interactive && !confirm("Create these on Fly?")) {
    yellow("Stopped. Nothing was created.")
    exitProcess(0)
}

val devReady = ensureFlyApp(devApp, organization)
val prodReady = ensureFlyApp(prodApp, organization)
if (!devReady && !prodReady) die("Neither app could be created. Fix the errors above and re-run.")

pointConfigAt(flyConfig, devApp)
pointConfigAt(File(root, "apps/server/fly.prod.toml"), prodApp)

bold("\nServer secrets")
// Only the values this project has actually configured. An empty DATABASE_URL
// is meaningful to the server — it boots in limited mode — so pushing a blank
// would be worse than pushing nothing.
val serverEnv = readEnvFile(File(root, "apps/server/.env"))
val pushable = listOf("DATABASE_URL", "SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY")
    .mapNotNull { key -> serverEnv[key]?.let { key to it } }
    .toMap()
if (pushable.isEmpty()) {
    yellow("apps/server/.env has none of DATABASE_URL / SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY.")
    yellow("Run ./scripts/setup_supabase.main.kts first, or set them by hand, then re-run this.")
    yellow("The server will still boot, in limited mode, with DB-backed routes off.")
} else {
    if (devReady && setFlySecrets(devApp, pushable)) {
        green("✓ Set ${pushable.keys.joinToString(", ")} on $devApp")
    }
    dim("  Prod deliberately left alone, point it at a different database than dev.")
    dim("  fly secrets set DATABASE_URL='…' -a $prodApp")
}

bold("\nCI deploy tokens")
if (!hasGhRepo()) {
    yellow("No GitHub repo reachable via gh, so there is nowhere to put the deploy tokens yet.")
    yellow("Once the repo exists, re-run this script, it will skip everything already done.")
} else {
    listOf(devApp to "FLY_API_TOKEN_DEV", prodApp to "FLY_API_TOKEN_PROD").forEach { (app, secret) ->
        if (!flyAppExists(app)) return@forEach
        val token = createDeployToken(app) ?: return@forEach
        if (setGhSecret(secret, token)) {
            green("✓ Set repo secret $secret (scoped to $app, expires in a year)")
        } else {
            yellow("Could not set $secret, `gh secret set $secret` it yourself.")
        }
    }
    yellow("These expire in a year. When a deploy starts failing on auth, re-run this script.")
}

bold("\nDev admin token")
// The config admin routes mount only when the server has ADMIN_API_TOKEN, and
// server-deploy.yml uses ADMIN_API_TOKEN_DEV to upload the manifest and turn
// new release flags on in dev. The two must match, and neither Fly nor GitHub
// shows a secret back, so when either side is missing both get a fresh value.
// Prod is left alone, like its database.
if (!devReady || !hasGhRepo()) {
    yellow("Skipped: needs the dev app and a GitHub repo.")
} else {
    val onFly = listsSecret(run("fly", "secrets", "list", "-a", devApp), "ADMIN_API_TOKEN")
    val onGithub = listsSecret(run("gh", "secret", "list"), "ADMIN_API_TOKEN_DEV")
    when {
        onFly == null || onGithub == null ->
            yellow("Could not list the secrets on $devApp or the repo, so the admin token was left alone.")
        onFly && onGithub -> green("✓ ADMIN_API_TOKEN on $devApp and ADMIN_API_TOKEN_DEV are both set")
        else -> {
            val token = randomHexToken()
            if (setFlySecrets(devApp, mapOf("ADMIN_API_TOKEN" to token)) && setGhSecret("ADMIN_API_TOKEN_DEV", token)) {
                green("✓ Set ADMIN_API_TOKEN on $devApp and ADMIN_API_TOKEN_DEV to one new value ($devApp restarts)")
            } else {
                yellow("Could not set both. Re-run this script; it makes a fresh pair whenever one side is missing.")
            }
        }
    }
}

bold("\nFirst deploy")
val deployed = when {
    !interactive -> {
        dim("  Skipped in non-interactive mode. `fly deploy --config apps/server/fly.toml --remote-only`")
        false
    }
    !confirm("Deploy the dev server now? (starts a machine, which is where billing begins)", default = false) -> {
        dim("  Skipped. `fly deploy --config apps/server/fly.toml --remote-only` when you are ready.")
        false
    }
    // --remote-only builds on Fly's builders, so this works with no local
    // Docker. Output goes straight to the terminal: a deploy is long enough
    // that a silent wait looks like a hang.
    else -> runInteractive("fly", "deploy", "--config", "apps/server/fly.toml", "--remote-only")
}

if (deployed) {
    bold("\nProving it works")
    val health = request("GET", "https://$devApp.fly.dev/_health")
    if (health.isSuccess && health.body.contains("\"ok\"")) {
        green("✓ https://$devApp.fly.dev/_health → ${health.body.trim()}")
    } else {
        red("✗ /_health answered HTTP ${health.code}: ${health.body.take(200)}")
        yellow("  `fly logs -a $devApp` will say why. A deploy that succeeds and then fails")
        yellow("  its health check is usually a missing secret, not a bad build.")
    }
}

values.printUnconfigured()

bold("\n━━ Fly is configured ━━")
println("  dev:  https://$devApp.fly.dev")
println("  prod: https://$prodApp.fly.dev")
println()
println("Still yours to do:")
println("  • Point prod at its own database: fly secrets set DATABASE_URL='…' -a $prodApp")
println("  • Create the `production` GitHub Environment with yourself as a required")
println("    reviewer, or server-deploy-prod.yml runs unguarded. See SETUP.md.")

exitProcess(0)
