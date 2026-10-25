#!/usr/bin/env kotlin

@file:Import("lib/setup_store.main.kts")

/**
 * Walks the setup steps in the order their dependencies allow, offering each
 * one and skipping anything that does not apply.
 *
 * Every step is also a standalone script. This exists because the *order*
 * matters and is not obvious: Supabase has to exist before its values can be
 * pushed to Fly, and both GitHub-facing steps need a repo that does not exist
 * at the moment a project is generated. Running the pieces in the wrong order
 * does not fail loudly — it half-configures things and leaves you to work out
 * which half.
 *
 * Re-runnable. Every step underneath is an upsert, so a second run repairs
 * drift rather than duplicating anything. That is what makes "skip it for now"
 * a safe answer to any of them.
 *
 * Run from the project root:
 *   ./scripts/setup.main.kts
 *   ./scripts/setup.main.kts --status   # what is done and what is not, no changes
 */

import java.io.File
import kotlin.system.exitProcess

val root = File(".").canonicalFile
if (!File(root, "settings.gradle.kts").exists()) {
    die("settings.gradle.kts not found. Run this from the project root.")
}

val statusOnly = "--status" in args.toList()
val hasBackend = File(root, "apps/server/fly.toml").exists()
val hasCi = File(root, ".github/workflows").isDirectory

// ── what is already done ────────────────────────────────────────────────────

/**
 * Each check asks the project, not the store: the question is "is this
 * configured *here*", and a value present on this machine for some other
 * project is not an answer.
 */
fun sentryDone(): Boolean = readProperty(File(root, "telemetry.properties"), "sentry.dsn") != null
fun supabaseDone(): Boolean = readProperty(File(root, "local.properties"), "supabase.projectId") != null

fun flyAppName(): String? = File(root, "apps/server/fly.toml").takeIf { it.exists() }
    ?.readLines()
    ?.firstOrNull { it.trimStart().startsWith("app = ") }
    ?.substringAfter('=')?.trim()?.trim('\'', '"')
    ?.takeIf { it.isNotEmpty() }

/**
 * Asks Fly whether the app exists. Null when it cannot be asked.
 *
 * The tempting cheap check — "has `app = ` been changed away from the template's
 * default name" — cannot work *in this file*, because this file goes through the
 * same rename pass as everything else: a template name written here becomes the
 * generated project's own name, and the comparison inverts. That is not
 * hypothetical, it is what the first version of this function did, and it gave
 * opposite answers in the template and in a generated project. The de-branding
 * grep cannot catch it either, because a renamed literal is exactly what that
 * grep is looking for the absence of. The only honest answer comes from Fly.
 */
fun flyDone(): Boolean? {
    val app = flyAppName() ?: return null
    if (!commandExists("fly") || !run("fly", "auth", "whoami").ok) return null
    return run("fly", "status", "-a", app).ok
}

/**
 * `install_hooks.sh` points `core.hooksPath` at `.githooks/` rather than
 * copying anything into `.git/hooks/`, so the presence of files is not the
 * question — the git config is.
 */
fun hooksInstalled(): Boolean =
    run("git", "config", "core.hooksPath").output.trim() == ".githooks"

fun mark(done: Boolean?) = when (done) {
    true -> "\u001b[32m✓\u001b[0m"
    false -> "\u001b[33m·\u001b[0m"
    null -> "\u001b[2m?\u001b[0m"
}

fun printStatus() {
    bold("\nWhere this project is")
    println("  ${mark(hooksInstalled())} git hooks installed")
    println("  ${mark(supabaseDone())} Supabase configured (local.properties)")
    println("  ${mark(sentryDone())} Sentry configured (telemetry.properties)")
    if (hasBackend) println("  ${mark(flyDone())} Fly apps exist ${flyAppName()?.let { "($it)" } ?: ""}")
    val repo = ghRepoSlug()
    println("  ${mark(repo != null)} GitHub repo ${repo?.let { "($it)" } ?: "— push one to enable the CI steps"}")
    println()
}

// ── steps ───────────────────────────────────────────────────────────────────

data class Step(
    val name: String,
    val script: String,
    val what: String,
    val applicable: Boolean,
    val alreadyDone: Boolean?,
    val blockedBy: String? = null,
)

fun steps(): List<Step> {
    val repo = ghRepoSlug()
    return listOf(
        Step(
            name = "Credentials",
            script = "scripts/setup_credentials.main.kts",
            what = "remember the values that are the same for every project you generate",
            applicable = true,
            alreadyDone = SetupStore().exists,
        ),
        Step(
            name = "Supabase",
            script = "scripts/setup_supabase.main.kts",
            what = "create the project, turn on anonymous sign-ins, wire the redirect URLs",
            applicable = true,
            alreadyDone = supabaseDone(),
        ),
        Step(
            name = "Sentry",
            script = "scripts/setup_sentry.main.kts",
            what = "create the project, commit the DSN, prove an event arrives",
            applicable = true,
            alreadyDone = sentryDone(),
        ),
        Step(
            name = "Fly.io",
            script = "scripts/setup_fly.main.kts",
            // Reads apps/server/.env, which Supabase writes — so it runs after.
            what = "create the dev and prod server apps, push their secrets, wire CI deploys",
            applicable = hasBackend,
            alreadyDone = flyDone() == true,
            blockedBy = if (repo == null) "needs a GitHub repo for the deploy tokens" else null,
        ),
        Step(
            name = "Release secrets",
            script = "scripts/setup_github_secrets.main.kts",
            what = "push the signing material CI needs, from one folder",
            applicable = hasCi,
            alreadyDone = false,
            blockedBy = if (repo == null) "needs a GitHub repo" else null,
        ),
    ).filter { it.applicable }
}

fun runStep(step: Step): Boolean {
    println()
    bold("━━ ${step.name} ━━")
    println("  ${step.what}")
    step.blockedBy?.let {
        yellow("  Skipping: $it.")
        yellow("  Run ./${step.script} once that is sorted, it skips whatever is already done.")
        return false
    }
    if (step.alreadyDone == true) {
        green("  Already configured.")
        if (!confirm("  Run it again anyway?", default = false)) return false
    } else if (!confirm("  Run it now?", default = true)) {
        dim("  Skipped. ./${step.script} whenever you are ready.")
        return false
    }
    // Inherited IO: these are interactive, and several of them hide secret
    // input, which only works against a real terminal.
    return runInteractive("kotlin", step.script)
}

// ── main ────────────────────────────────────────────────────────────────────

bold("\n━━ Project setup ━━")
println("Each step below is its own script. This runs them in the order their")
println("dependencies allow, and skips anything already done.")

printStatus()

if (statusOnly) exitProcess(0)

if (!commandExists("kotlin")) {
    die("The kotlin CLI is needed to run the steps. `brew install kotlin`, then re-run.")
}

val outcomes = steps().associate { it.name to runStep(it) }

println()
bold("━━ Where you landed ━━")
printStatus()

val skipped = outcomes.filterValues { !it }.keys
if (skipped.isNotEmpty()) {
    println("Not done this run: ${skipped.joinToString(", ")}")
    println("Re-run ./scripts/setup.main.kts any time, it picks up where you left off.")
    println()
}

println("Always yours, because these have no API:")
println("  • Play Console: create the app entry, invite your service account,")
println("    fill in data safety and content rating")
println("  • App Store Connect: create the app record, listing copy, screenshots")
println("  • The first release in each store has to be promoted by hand once")
println()
println("SETUP.md is the full checklist, including those.")

exitProcess(0)
