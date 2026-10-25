#!/usr/bin/env kotlin

@file:Import("lib/setup_store.main.kts")

/**
 * Pushes every release secret this repo's CI needs, from one folder plus the
 * machine-local credential store.
 *
 * All but one of the fifteen values the release pipeline wants belong to your
 * Apple team, your Play developer account or your Sentry org, and are identical
 * for every project generated from this template. `SETUP.md` has said so for a
 * while and then left you to paste them in by hand. This is the script it was
 * describing.
 *
 * The binary material — upload keystore, Apple `.p12`, App Store Connect `.p8`,
 * Play service-account JSON — lives in one folder outside every repo. The store
 * remembers *where* that folder is, never what is in it. The passwords beside
 * them are account-wide, so those the store does hold.
 *
 * Nothing is required. Anything missing is reported with what it costs, and
 * everything else still goes up.
 *
 * Run from the project root:
 *   ./scripts/setup_github_secrets.main.kts
 *   ./scripts/setup_github_secrets.main.kts --dry-run   # show what it would do
 */

import java.io.File
import java.util.Base64
import kotlin.system.exitProcess

val arguments = args.toList()
val dryRun = "--dry-run" in arguments
val interactive = "--non-interactive" !in arguments

/** One GitHub secret, and where its value comes from. */
data class Secret(val name: String, val value: String?, val note: String)

val results = mutableListOf<Pair<String, String>>()

fun record(name: String, outcome: String) {
    results += name to outcome
}

fun push(secret: Secret) {
    if (secret.value.isNullOrBlank()) {
        record(secret.name, "skipped, ${secret.note}")
        return
    }
    if (dryRun) {
        record(secret.name, "would set (${secret.value.length} chars)")
        return
    }
    if (setGhSecret(secret.name, secret.value)) {
        record(secret.name, "set")
    } else {
        record(secret.name, "FAILED, set it by hand")
    }
}

fun base64(file: File): String = Base64.getEncoder().encodeToString(file.readBytes())

/**
 * The file in [dir] holding [what], matched on extension and then on a name
 * hint.
 *
 * Extension alone is not enough. `keytool` has produced PKCS12 keystores by
 * default since Java 9, so an upload keystore is as likely to be
 * `upload-keystore.p12` as `upload-keystore.jks` — and an Apple distribution
 * certificate is a `.p12` too. Keyed on extension alone, "the .p12" is
 * ambiguous in exactly the folder this script exists to read.
 *
 * Top level only, deliberately: retired material tends to get moved into a
 * subfolder rather than deleted, and a recursive search would offer
 * `apple-distribution.p12.superseded-…` as a candidate.
 *
 * Refuses to guess when several still match. Picking the wrong keystore signs
 * the app with the wrong key, and on Android that is the one mistake you cannot
 * walk back without asking Google to reset the upload key.
 */
fun findSigningFile(dir: File, what: String, extensions: Set<String>, nameHints: List<String>): File? {
    val byExtension = dir.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.lowercase() in extensions }
    val candidates = byExtension
        .filter { file -> nameHints.any { it in file.name.lowercase() } }
        .ifEmpty { byExtension }

    return when (candidates.size) {
        1 -> candidates.single().also { dim("  $what → ${it.name}") }
        0 -> null.also {
            yellow("  $what → nothing in ${dir.name} looks like ${extensions.joinToString("/") { ".$it" }}")
        }
        else -> {
            yellow("  $what → ${candidates.size} candidates: ${candidates.joinToString(", ") { it.name }}")
            if (!interactive) return null
            val chosen = prompt("  Which one", candidates.first().name)
            candidates.firstOrNull { it.name == chosen }
        }
    }
}

// ── main ────────────────────────────────────────────────────────────────────

val root = File(".").canonicalFile
if (!File(root, "settings.gradle.kts").exists()) {
    die("settings.gradle.kts not found. Run this from the project root.")
}

val values = SetupValues(interactive = interactive)

bold("\n━━ GitHub release secrets ━━")
println("Pushes the signing material CI needs, from one folder plus your credential store.")
if (dryRun) yellow("Dry run, nothing will be written.")

when (githubCliState()) {
    GithubCliState.NOT_INSTALLED -> die("The gh CLI is not installed. `brew install gh`, then `gh auth login`.")
    GithubCliState.NOT_AUTHENTICATED -> die("gh is installed but not authenticated. Run `gh auth login`.")
    GithubCliState.READY -> Unit
}
val repo = ghRepoSlug() ?: die("No GitHub repo reachable from here. Create and push it first.")
println("Repo: $repo")

values.plan(
    listOf(
        Keys.SIGNING_DIR,
        Keys.ANDROID_KEYSTORE_PASSWORD, Keys.ANDROID_KEY_ALIAS, Keys.ANDROID_KEY_PASSWORD,
        Keys.APPLE_TEAM_ID, Keys.ASC_KEY_ID, Keys.ASC_ISSUER_ID, Keys.APPLE_DIST_CERT_PASSWORD,
        Keys.GRAFANA_OTLP_BASE_URL, Keys.GRAFANA_OTLP_INSTANCE_ID, Keys.GRAFANA_LOGS_WRITE_TOKEN,
        Keys.NIGHTJAR_SITE_TOKEN,
    )
)

val signingDir = File(values.require(Keys.SIGNING_DIR).replaceFirst("~", System.getProperty("user.home")))
if (!signingDir.isDirectory) die("Not a directory: ${signingDir.absolutePath}")

bold("\nFiles")
val keystore = findSigningFile(
    signingDir, "Android upload keystore",
    extensions = setOf("jks", "keystore", "p12", "bks"),
    nameHints = listOf("keystore", "upload", "android"),
)
val playJson = findSigningFile(
    signingDir, "Play service account",
    extensions = setOf("json"),
    nameHints = listOf("play", "service-account", "service_account"),
)
val ascKey = findSigningFile(
    signingDir, "App Store Connect key",
    extensions = setOf("p8"),
    nameHints = listOf("authkey", "asc"),
)
val distCert = findSigningFile(
    signingDir, "Apple distribution certificate",
    extensions = setOf("p12"),
    nameHints = listOf("distribution", "dist", "apple", "cert"),
)

bold("\nPushing")

push(
    Secret(
        "NIGHTJAR_SITE_TOKEN", values.optional(Keys.NIGHTJAR_SITE_TOKEN),
        "not in the store. Legal Sync cannot publish edits to legal/*.md.",
    )
)
push(
    Secret(
        "ANDROID_KEYSTORE_BASE64", keystore?.let(::base64),
        "no keystore in ${signingDir.name}. Release Android builds cannot be signed.",
    )
)
push(
    Secret(
        "ANDROID_KEYSTORE_PASSWORD", values.optional(Keys.ANDROID_KEYSTORE_PASSWORD),
        "not in the store. Release Android builds cannot be signed.",
    )
)
push(
    Secret(
        "ANDROID_KEY_ALIAS", values.optional(Keys.ANDROID_KEY_ALIAS),
        "not in the store. Release Android builds cannot be signed.",
    )
)
push(
    Secret(
        "ANDROID_KEY_PASSWORD", values.optional(Keys.ANDROID_KEY_PASSWORD),
        "not in the store. Release Android builds cannot be signed.",
    )
)
// Raw JSON, not base64 — the release workflow writes it straight to a file for
// fastlane's supply action. Base64ing it here would ship an unusable secret
// that only fails at upload time.
push(
    Secret(
        "PLAY_SERVICE_ACCOUNT_JSON", playJson?.readText(),
        "no service-account JSON found. Play uploads will fail.",
    )
)

push(
    Secret(
        "APPLE_TEAM_ID", values.optional(Keys.APPLE_TEAM_ID),
        "not in the store. iOS signing cannot resolve your team.",
    )
)
push(
    Secret(
        "ASC_KEY_ID", values.optional(Keys.ASC_KEY_ID),
        "not in the store. TestFlight uploads cannot authenticate.",
    )
)
push(
    Secret(
        "ASC_ISSUER_ID", values.optional(Keys.ASC_ISSUER_ID),
        "not in the store. TestFlight uploads cannot authenticate.",
    )
)
push(
    Secret(
        "ASC_KEY_P8_BASE64", ascKey?.let(::base64),
        "no AuthKey_*.p8 found. TestFlight uploads cannot authenticate.",
    )
)
push(
    Secret(
        "APPLE_DIST_CERT_P12_BASE64", distCert?.let(::base64),
        "no .p12 found. iOS release builds cannot import a signing certificate.",
    )
)
push(
    Secret(
        "APPLE_DIST_CERT_PASSWORD", values.optional(Keys.APPLE_DIST_CERT_PASSWORD),
        "not in the store. The .p12 cannot be imported in CI.",
    )
)

// Optional by design: with these unset the OTLP pipe stays dormant and the app
// builds and runs fine, so a missing one is reported rather than fatal.
push(
    Secret(
        "GRAFANA_OTLP_BASE_URL", values.optional(Keys.GRAFANA_OTLP_BASE_URL),
        "not in the store. Client telemetry stays on the device.",
    )
)
push(
    Secret(
        "GRAFANA_OTLP_INSTANCE_ID", values.optional(Keys.GRAFANA_OTLP_INSTANCE_ID),
        "not in the store. Client telemetry stays on the device.",
    )
)
push(
    Secret(
        "GRAFANA_LOGS_WRITE_TOKEN", values.optional(Keys.GRAFANA_LOGS_WRITE_TOKEN),
        "not in the store. Client telemetry stays on the device.",
    )
)

bold("\nResult")
val width = results.maxOf { it.first.length }
results.forEach { (name, outcome) ->
    val line = "  ${name.padEnd(width)}  $outcome"
    when {
        outcome.startsWith("FAILED") -> red(line)
        outcome.startsWith("skipped") -> yellow(line)
        else -> green(line)
    }
}

val missing = results.count { it.second.startsWith("skipped") }
val failed = results.count { it.second.startsWith("FAILED") }

values.printUnconfigured()

println()
if (failed > 0) {
    red("$failed secret(s) could not be set. Fix those before cutting a release.")
    exitProcess(1)
}
if (missing > 0) {
    yellow("$missing secret(s) are still unset, see the reasons above.")
    yellow("The pipeline will build, and fail at whichever step needs the missing one.")
} else {
    green("Every release secret is set.")
}
println()
println("Not covered here, because they have no API:")
println("  • SENTRY_*, ./scripts/setup_sentry.main.kts does those")
println("  • Inviting your Play service account to the developer account (once, ever)")
println("  • Store listings, data safety, content rating")

exitProcess(0)
