#!/usr/bin/env kotlin

// No @file:DependsOn here on purpose: the setup scripts that import this file
// use only the stdlib, and a dependency declaration makes every cold run
// resolve Maven coordinates over the network (observed hanging the script
// runner entirely on flaky daemons).

/**
 * The shared half of the setup scripts: terminal prompting, and a machine-local
 * store for the credentials that are the same for every project you generate.
 *
 * Import it from a sibling script:
 *
 *     @file:Import("lib/setup_store.main.kts")
 *
 * ## Why this exists
 *
 * Spinning up a project from this template asks for the same values every time
 * and answers them from scratch every time, so project five is exactly as
 * tedious as project one. This is the one place those answers live.
 *
 * ## Precedence, highest first
 *
 *   1. Environment variable. CI already sets these, so env-first means CI keeps
 *      working untouched and a one-off override stays possible.
 *   2. This store.
 *   3. Prompt.
 *
 * ## The constraint that shapes the whole design: the store is often absent
 *
 * A different laptop, a fresh machine, a CI runner, a contributor who is not
 * you. So the store is an accelerator, never a requirement. Every script that
 * uses it must run correctly with none of it present, which is why
 * [SetupValues] falls back to prompting rather than failing, and why a
 * non-interactive run with a missing required value dies naming the value.
 *
 * ## The half that actually matters: saying what is missing
 *
 * The failure this template has already shipped once is a blank value that is
 * also a supported value — a blank Sentry DSN means "reporting off", so a
 * project with no DSN looks configured and reports nothing, silently, for
 * months. Any store that quietly fills some values and leaves others blank
 * reproduces that bug across every credential it touches. [printPlan] and
 * [printUnconfigured] are what stop it: every run says where each value came
 * from before it starts, and what is still not set (and what that costs) when
 * it finishes.
 */

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import kotlin.system.exitProcess

// ── terminal ────────────────────────────────────────────────────────────────

fun green(text: String) = println("[32m$text[0m")
fun yellow(text: String) = println("[33m$text[0m")
fun red(text: String) = println("[31m$text[0m")
fun bold(text: String) = println("[1m$text[0m")
fun dim(text: String) = println("[2m$text[0m")

fun die(message: String): Nothing {
    red("✗ $message")
    exitProcess(1)
}

fun prompt(label: String, default: String? = null): String {
    val suffix = default?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
    while (true) {
        print("$label$suffix: ")
        System.out.flush()
        // Every prompt treats end-of-input as a hard stop. These scripts are
        // interactive by nature, and a piped/empty stdin must not spin.
        val input = (readlnOrNull() ?: die("No input on stdin, run this in a terminal.")).trim()
        if (input.isNotEmpty()) return input
        if (!default.isNullOrBlank()) return default
    }
}

/** Hides typed input when the JVM has a real console; says so when it can't. */
fun promptSecret(label: String): String {
    val console = System.console()
    while (true) {
        val value = if (console != null) {
            console.readPassword("$label: ")
                ?.let { String(it) }
                ?: die("No input on stdin, run this in a terminal.")
        } else {
            print("$label (input will be visible): ")
            System.out.flush()
            readlnOrNull() ?: die("No input on stdin, run this in a terminal.")
        }.trim()
        if (value.isNotEmpty()) return value
    }
}

/**
 * Like [promptSecret], but an empty line means "skip" rather than "ask again".
 *
 * The editor needs a way to leave a field alone, and the obvious way to get one
 * was a visible prompt, since a hidden prompt that loops on empty input cannot
 * tell "skip" from "typed nothing". That reasoning was wrong and the cost was
 * real: it echoed every secret the editor collected into the terminal
 * scrollback, which is exactly what `promptSecret` exists to prevent. The
 * console hands back an empty array for a bare Enter, so returning null on it
 * gives both behaviours with the input still hidden.
 */
fun promptSecretOrSkip(label: String): String? {
    val console = System.console()
    if (console == null) {
        // No console means output is redirected, and there is no way to stop
        // the echo. Say so rather than quietly leaking.
        print("$label (input will be VISIBLE, no console available; Enter to skip): ")
        System.out.flush()
        return readlnOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
    return console.readPassword("$label (hidden, Enter to skip): ")
        ?.let { String(it) }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

fun confirm(label: String, default: Boolean = true): Boolean {
    val hint = if (default) "Y/n" else "y/N"
    print("$label ($hint): ")
    System.out.flush()
    return when (readlnOrNull()?.trim()?.lowercase()) {
        "y", "yes" -> true
        "n", "no" -> false
        else -> default
    }
}

/** `sntrys_abcdef…` → `sntr…cdef`. Enough to recognise a value, not enough to use it. */
fun maskSecret(value: String): String = when {
    value.length <= 8 -> "•".repeat(value.length.coerceAtLeast(4))
    else -> value.take(4) + "…" + value.takeLast(4)
}

// ── the key catalog ─────────────────────────────────────────────────────────

/**
 * One value the setup scripts need.
 *
 * @param storeKey property name inside the store file.
 * @param env environment variable checked before the store. Keep these equal to
 *   the names CI already uses, so a value exported for a workflow also serves a
 *   local run.
 * @param label how the value is described when prompting for it.
 * @param secret hide input while typing, and mask it whenever it is printed.
 * @param where one line on where to obtain the value, printed with the prompt.
 * @param consequence what stays broken while this value is unset, phrased as an
 *   outcome ("crash reporting is off until …") rather than as a missing field.
 *   This is the line someone reads at the end of a run, so it has to say what
 *   it costs them.
 */
data class SetupKey(
    val storeKey: String,
    val env: String,
    val label: String,
    val secret: Boolean,
    val where: String,
    val consequence: String,
)

/**
 * Every key the store knows about. These are the values that are identical
 * across every project you create — per-person and per-machine, never per
 * project. Anything a single app owns is derived instead: the Sentry project
 * slug comes from the `applicationId`, the Fly app name from the project name,
 * the Supabase project URL and anon key from that project's
 * `local.properties`. Deriving beats asking.
 *
 * To add one: append it here, then have the script that needs it name the key
 * in its `SetupValues.plan(...)` call. Nothing else has to change —
 * `setup_credentials.main.kts` enumerates this list, so a new key shows up in
 * the editor for free.
 */
object Keys {
    val SENTRY_USER_TOKEN = SetupKey(
        storeKey = "sentry.userToken",
        env = "SENTRY_USER_TOKEN",
        label = "Sentry personal token (sntryu_...)",
        secret = true,
        where = "sentry.io, Account dropdown top left, then Personal Tokens. " +
            "Tick project:read, project:write, org:read. NOT the sntrys_ org token.",
        consequence = "setup_sentry.main.kts cannot run unattended and will ask for it each time.",
    )
    val SENTRY_ORG = SetupKey(
        storeKey = "sentry.org",
        env = "SENTRY_ORG",
        label = "Sentry org slug",
        secret = false,
        where = "the slug in your sentry.io URL",
        consequence = "setup_sentry.main.kts will ask which org to use.",
    )
    val SENTRY_CI_TOKEN = SetupKey(
        storeKey = "sentry.ciToken",
        env = "SENTRY_AUTH_TOKEN",
        label = "Sentry organization token (sntrys_…)",
        secret = true,
        where = "Settings → Organization Tokens. One org token covers every project you generate.",
        consequence = "Release builds still ship, but mappings and dSYMs never upload, " +
            "so every store crash stays unsymbolicated.",
    )

    val FLY_TOKEN_DEV = SetupKey(
        storeKey = "fly.tokenDev",
        env = "FLY_API_TOKEN_DEV",
        label = "Fly.io deploy token (dev)",
        secret = true,
        where = "fly tokens create deploy -a <app>-server-dev --expiry 8760h",
        consequence = "server-deploy.yml fails at the deploy step on every push to main.",
    )
    val FLY_TOKEN_PROD = SetupKey(
        storeKey = "fly.tokenProd",
        env = "FLY_API_TOKEN_PROD",
        label = "Fly.io deploy token (prod)",
        secret = true,
        where = "fly tokens create deploy -a <app>-server-prod --expiry 8760h",
        consequence = "server-deploy-prod.yml fails after you approve the deploy.",
    )

    val APPLE_TEAM_ID = SetupKey(
        storeKey = "apple.teamId",
        env = "APPLE_TEAM_ID",
        label = "Apple Team ID",
        secret = false,
        where = "Apple Developer → Membership → Team ID",
        consequence = "iOS signing and Sign in with Apple token rotation both ask for it.",
    )
    val ASC_KEY_ID = SetupKey(
        storeKey = "apple.ascKeyId",
        env = "ASC_KEY_ID",
        label = "App Store Connect key ID",
        secret = false,
        where = "App Store Connect → Users and Access → Keys → Key ID",
        consequence = "TestFlight uploads cannot authenticate.",
    )
    val ASC_ISSUER_ID = SetupKey(
        storeKey = "apple.ascIssuerId",
        env = "ASC_ISSUER_ID",
        label = "App Store Connect issuer ID",
        secret = false,
        where = "App Store Connect → Users and Access → Keys (top of the tab)",
        consequence = "TestFlight uploads cannot authenticate.",
    )
    val ASC_KEY_PATH = SetupKey(
        storeKey = "apple.ascKeyPath",
        env = "ASC_KEY_P8_PATH",
        label = "Path to your App Store Connect .p8",
        secret = false,
        where = "wherever you saved the .p8, App Store Connect lets you download it exactly once",
        consequence = "TestFlight uploads have no key to sign with.",
    )

    // No Sign in with Apple key here on purpose. This template does Apple
    // sign-in natively (ASAuthorizationController → an identity token →
    // Supabase `signInWith(IDToken)`), which authenticates against the bundle
    // ID in Supabase's Authorized Client IDs list. The Services ID and the
    // six-month client-secret JWT belong to Apple's *browser* OAuth flow, which
    // nothing here uses. See decisions.md, 2026-09-20.

    val SUPABASE_ACCESS_TOKEN = SetupKey(
        storeKey = "supabase.accessToken",
        env = "SUPABASE_ACCESS_TOKEN",
        label = "Supabase personal access token (sbp_…)",
        secret = true,
        where = "https://supabase.com/dashboard/account/tokens",
        consequence = "setup_supabase.main.kts cannot run; you will create and configure the " +
            "project in the dashboard by hand.",
    )
    val SUPABASE_ORG = SetupKey(
        storeKey = "supabase.org",
        env = "SUPABASE_ORG",
        label = "Supabase organization slug",
        secret = false,
        where = "the slug in your supabase.com/dashboard/org URL",
        consequence = "setup_supabase.main.kts will ask which organization to create in.",
    )

    // One Grafana stack serves every project you generate, so these three are
    // account-wide like the Sentry org token. Apps are separated inside it by
    // the `service_name` label, which the rename pass makes per-project. See
    // decisions.md, 2026-09-21.
    val GRAFANA_OTLP_BASE_URL = SetupKey(
        storeKey = "grafana.otlpBaseUrl",
        env = "GRAFANA_OTLP_BASE_URL",
        label = "Grafana OTLP base URL",
        secret = false,
        where = "Grafana Cloud > OpenTelemetry > OTLP endpoint base URL",
        consequence = "App events and Warn+ logs never leave the device, so Loki has nothing " +
            "to correlate against a Sentry crash.",
    )
    val GRAFANA_OTLP_INSTANCE_ID = SetupKey(
        storeKey = "grafana.otlpInstanceId",
        env = "GRAFANA_OTLP_INSTANCE_ID",
        label = "Grafana OTLP instance ID",
        secret = false,
        where = "same page as the OTLP endpoint, the numeric user",
        consequence = "The OTLP exporter cannot authenticate, so client telemetry is dropped.",
    )
    val GRAFANA_LOGS_WRITE_TOKEN = SetupKey(
        storeKey = "grafana.logsWriteToken",
        env = "GRAFANA_LOGS_WRITE_TOKEN",
        label = "Grafana logs:write token (glc_...)",
        secret = true,
        where = "Grafana Cloud access policy with logs:write. Never commit one; " +
            "Grafana auto-revokes glc_ tokens it finds in public repos.",
        consequence = "The OTLP exporter cannot authenticate, so client telemetry is dropped.",
    )

    val FLY_ORG = SetupKey(
        storeKey = "fly.org",
        env = "FLY_ORG",
        label = "Fly.io organization slug",
        secret = false,
        where = "`fly orgs list`, or the slug in your fly.io dashboard URL",
        consequence = "setup_fly.main.kts will use your personal org.",
    )

    /**
     * The token `legal-sync.yml` uses to open its pull request on the studio
     * website repo.
     *
     * Stored because it is scoped to the *website* repo rather than to any app,
     * so one token serves every project you generate. GitHub has no API for
     * minting one, so it is the rare value that can only be typed in once and
     * then has to be remembered somewhere.
     *
     * Scope it to the website repo with Contents and Pull requests set to read
     * and write. A token scoped to the app repo instead answers 404, which reads
     * like the website repo does not exist.
     */
    val NIGHTJAR_SITE_TOKEN = SetupKey(
        storeKey = "nightjar.siteToken",
        env = "NIGHTJAR_SITE_TOKEN",
        label = "Website repo PAT (github_pat_...)",
        secret = true,
        where = "github.com/settings/personal-access-tokens/new, scoped to the website " +
            "repo only, Contents and Pull requests read and write. " +
            "setup_legal_sync.sh walks you through it.",
        consequence = "setup_legal_sync.sh asks for a fresh token in every app you set up.",
    )

    /**
     * The folder holding the binary signing material: the upload keystore, the
     * Apple distribution `.p12`, the App Store Connect `.p8`, and the Play
     * service-account JSON.
     *
     * A path, never the files. They are account-wide, so one folder outside
     * every repo serves every project you generate — which is what
     * `setup_github_secrets.main.kts` reads.
     */
    val SIGNING_DIR = SetupKey(
        storeKey = "signing.dir",
        env = "SIGNING_DIR",
        label = "Folder holding your signing material",
        secret = false,
        where = "wherever you keep the keystore, .p12, .p8 and Play service-account JSON",
        consequence = "setup_github_secrets.main.kts cannot run; every signing secret is a " +
            "manual `gh secret set`.",
    )
    val ANDROID_KEYSTORE_PASSWORD = SetupKey(
        storeKey = "android.keystorePassword",
        env = "ANDROID_KEYSTORE_PASSWORD",
        label = "Android keystore password",
        secret = true,
        where = "whatever you set when you created the upload keystore",
        consequence = "Release Android builds cannot be signed in CI.",
    )
    val ANDROID_KEY_ALIAS = SetupKey(
        storeKey = "android.keyAlias",
        env = "ANDROID_KEY_ALIAS",
        label = "Android key alias",
        secret = false,
        where = "`keytool -list -v -keystore upload-keystore.jks`",
        consequence = "Release Android builds cannot be signed in CI.",
    )
    val ANDROID_KEY_PASSWORD = SetupKey(
        storeKey = "android.keyPassword",
        env = "ANDROID_KEY_PASSWORD",
        label = "Android key password",
        secret = true,
        where = "whatever you set for the key inside the keystore",
        consequence = "Release Android builds cannot be signed in CI.",
    )
    val APPLE_DIST_CERT_PASSWORD = SetupKey(
        storeKey = "apple.distCertPassword",
        env = "APPLE_DIST_CERT_PASSWORD",
        label = "Apple distribution .p12 password",
        secret = true,
        where = "whatever you set when exporting the certificate from Keychain",
        consequence = "iOS release builds cannot import the signing certificate in CI.",
    )

    /**
     * Deliberately not a stored value: whether `gh` is authenticated is checked
     * live by [githubCliState]. A cached "yes" goes stale the moment a token
     * expires, and a script that skips asking because of a stale flag then
     * fails somewhere less obvious — which is the exact silent-misconfiguration
     * failure this file exists to prevent.
     */
    val all: List<SetupKey> = listOf(
        SENTRY_USER_TOKEN, SENTRY_ORG, SENTRY_CI_TOKEN,
        FLY_TOKEN_DEV, FLY_TOKEN_PROD,
        APPLE_TEAM_ID, ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_PATH, APPLE_DIST_CERT_PASSWORD,
        SUPABASE_ACCESS_TOKEN, SUPABASE_ORG,
        GRAFANA_OTLP_BASE_URL, GRAFANA_OTLP_INSTANCE_ID, GRAFANA_LOGS_WRITE_TOKEN,
        FLY_ORG, NIGHTJAR_SITE_TOKEN,
        SIGNING_DIR, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD,
    )
}

// ── the store ───────────────────────────────────────────────────────────────

/**
 * Where the store lives, and why the path says nothing about this template.
 *
 * The directory name is intentionally project-agnostic. `init_project.main.kts`
 * rewrites every occurrence of the template's name in every text file it
 * copies, so a path containing it would be rewritten per project — and the
 * whole point of this store is that the app you generate next month reads what
 * you typed today. Any identifier that has to stay equal across generated
 * projects has to carry no project name for the same reason. Don't rename this
 * one to match your app.
 *
 * Outside every repo, so it is never committed and never copied into a
 * generated project.
 */
// Not `const`: a Kotlin script compiles to a class body, where const is illegal.
val STORE_FILE_NAME = "credentials.properties"

/**
 * Where a store would go if none exists: outside every repo, and not in a
 * folder that syncs anywhere by default.
 *
 * Conservative on purpose. A store holds live deploy tokens, and putting them
 * somewhere that uploads itself is a decision for the person whose tokens they
 * are — not one a script makes quietly on their behalf. `--move-to` is how you
 * opt in, and [setupStoreCandidates] is what makes that opt-in survive a new
 * machine.
 */
fun defaultStoreDirectory(): File {
    val home = System.getProperty("user.home")
    val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.config"
    return File("$configHome/appsetup")
}

/**
 * Every directory a store might be in, most explicit first.
 *
 * The search exists so relocating the store survives a machine change. Somebody
 * who moves it into a synced folder — a reasonable trade for a keystore
 * password you cannot regenerate — gets it back on a new laptop with no setup,
 * because the sync brings the file down and this finds it. Without the search,
 * the opt-in would silently un-opt itself the moment it mattered most.
 */
/** An explicit override, which wins outright when set. */
fun overriddenStoreDirectory(): File? =
    System.getenv("APPSETUP_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }

fun setupStoreCandidates(): List<File> {
    val home = System.getProperty("user.home")
    overriddenStoreDirectory()?.let { return listOf(it) }
    return listOf(
        defaultStoreDirectory(),
        // macOS syncs Desktop & Documents to iCloud when that is turned on, so
        // this is the conventional place for a store someone chose to sync.
        File("$home/Documents/appsetup"),
    ).distinctBy { it.absolutePath }
}

/**
 * The store to read and write.
 *
 * `APPSETUP_DIR` is authoritative rather than first-of-several: somebody who
 * names a directory means that directory, and falling through to another one
 * because the named one is empty is how a test run ends up pointed at a real
 * store full of live credentials. The remaining two are a genuine search, so
 * that a store relocated into a synced folder is found again on a new machine.
 */
fun setupStoreFile(): File {
    overriddenStoreDirectory()?.let { return File(it, STORE_FILE_NAME) }
    return setupStoreCandidates().firstOrNull { File(it, STORE_FILE_NAME).isFile }
        ?.let { File(it, STORE_FILE_NAME) }
        ?: File(defaultStoreDirectory(), STORE_FILE_NAME)
}

/**
 * Reads and writes [setupStoreFile]. Every read tolerates the file being
 * absent; that is the normal state on a machine you have not set up yet.
 */
class SetupStore(val file: File = setupStoreFile()) {

    val exists: Boolean get() = file.exists()

    private val properties: Properties by lazy {
        Properties().also { loaded ->
            if (file.exists()) file.inputStream().use(loaded::load)
        }
    }

    operator fun get(key: SetupKey): String? =
        properties.getProperty(key.storeKey)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Writes one value, creating the file at 0600 and its directory at 0700.
     * Permissions are set before the value is written, not after, so the secret
     * is never briefly world-readable.
     */
    operator fun set(key: SetupKey, value: String) {
        val directory = file.parentFile
        if (!directory.exists() && !directory.mkdirs()) {
            die("Could not create ${directory.absolutePath}")
        }
        restrictPermissions(directory, executable = true)
        if (!file.exists()) {
            file.createNewFile()
            restrictPermissions(file)
        }

        properties.setProperty(key.storeKey, value)
        write()
    }

    fun remove(key: SetupKey) {
        if (!file.exists()) return
        properties.remove(key.storeKey)
        write()
    }

    private fun write() {
        // Deliberately unbranded, like the directory name: one store serves
        // every project you generate, so naming one of them here would be wrong
        // the moment there are two.
        val header = "Machine-local setup credentials, shared by every project you generate.\n" +
            "Not a repo file: never commit it, never sync it to a shared drive. " +
            "It holds live credentials."
        file.outputStream().use { properties.store(it, header) }
        restrictPermissions(file)
    }

    private fun restrictPermissions(target: File, executable: Boolean = false) =
        restrictToOwner(target, executable)
}

/**
 * Makes [target] readable and writable by its owner and nobody else.
 *
 * Shared so the store's own writes and a relocation agree. A directory needs
 * the execute bit as well or its owner cannot traverse into it. Failing to
 * tighten permissions is worth a warning and never worth aborting setup —
 * Windows and some network filesystems have no POSIX view at all.
 */
fun restrictToOwner(target: File, executable: Boolean) {
    val permissions = setOfNotNull(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE.takeIf { executable },
    )
    runCatching { Files.setPosixFilePermissions(target.toPath(), permissions) }
        .onFailure { yellow("⚠ Could not restrict permissions on ${target.absolutePath}: ${it.message}") }
}

// ── resolution ──────────────────────────────────────────────────────────────

enum class ValueSource(val display: String) {
    ENVIRONMENT("environment"),
    STORE("machine store"),
    PROMPTED("asked just now"),
    ABSENT("not set"),
}

/**
 * Resolves [SetupKey]s through env → store → prompt, and remembers where each
 * answer came from so the run can report it.
 *
 * @param interactive false for automation. A missing required value then dies
 *   naming the value and how to supply it, rather than proceeding with a blank.
 */
class SetupValues(
    private val store: SetupStore = SetupStore(),
    private val interactive: Boolean = true,
) {
    private val planned = mutableListOf<SetupKey>()
    private val sources = mutableMapOf<String, ValueSource>()
    private val unsaved = mutableListOf<SetupKey>()

    private fun fromEnvironment(key: SetupKey): String? =
        System.getenv(key.env)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Declares the values this run needs and prints where each one will come
     * from — before the first prompt, so the person can see the shape of the
     * run rather than discovering it one question at a time.
     */
    fun plan(keys: List<SetupKey>) {
        planned.clear()
        planned += keys

        bold("\nWhere this run's values come from")
        if (!store.exists) {
            dim("  No machine store at ${store.file.path}, everything below is a prompt.")
            dim("  Run ./scripts/setup_credentials.main.kts once to stop re-typing these.")
        }
        for (key in keys) {
            val source = when {
                fromEnvironment(key) != null -> "environment (${'$'}${key.env})"
                store[key] != null -> "machine store"
                interactive -> "will ask you"
                else -> "NOT SET"
            }
            println("  ${key.label.padEnd(42)} $source")
        }
        println()
    }

    /**
     * The value for [key], asking for it if neither the environment nor the
     * store has it. Offers to remember a prompted answer, because a store that
     * never fills itself is a store nobody ever populates.
     */
    fun require(key: SetupKey, default: String? = null): String {
        fromEnvironment(key)?.let { sources[key.storeKey] = ValueSource.ENVIRONMENT; return it }
        store[key]?.let { sources[key.storeKey] = ValueSource.STORE; return it }

        if (!interactive) {
            die(
                "${key.label} is not set, and this is a non-interactive run.\n" +
                    "   Supply it as ${key.env}, or save it once with " +
                    "./scripts/setup_credentials.main.kts\n" +
                    "   Where to find it: ${key.where}"
            )
        }

        dim("  ${key.where}")
        val value = if (key.secret) promptSecret(key.label) else prompt(key.label, default)
        sources[key.storeKey] = ValueSource.PROMPTED
        offerToRemember(key, value)
        return value
    }

    /** As [require], but returns null instead of prompting or dying. */
    fun optional(key: SetupKey): String? {
        val value = fromEnvironment(key)?.also { sources[key.storeKey] = ValueSource.ENVIRONMENT }
            ?: store[key]?.also { sources[key.storeKey] = ValueSource.STORE }
        if (value == null) sources[key.storeKey] = ValueSource.ABSENT
        return value
    }

    /** Records a value the script obtained some other way, so it can be saved. */
    fun offerToRemember(key: SetupKey, value: String) {
        // The value exists now however it was obtained, so the closing summary
        // must stop calling it unset even when the answer below is "don't save".
        if (isUnset(key)) sources[key.storeKey] = ValueSource.PROMPTED
        if (!interactive) return
        if (store[key] == value) return
        val shown = if (key.secret) maskSecret(value) else value
        if (confirm("  Save $shown to ${store.file.path} for your next project?", default = true)) {
            store[key] = value
            green("  ✓ Saved. Delete it any time with ./scripts/setup_credentials.main.kts")
        } else {
            unsaved += key
        }
    }

    /**
     * The closing summary: what is still unset and what that costs. Printed at
     * the end of a run, whether the run succeeded or not — a script that
     * finishes green while leaving a credential blank is the failure mode this
     * whole file exists to prevent.
     */
    private fun isUnset(key: SetupKey): Boolean =
        sources[key.storeKey].let { it == null || it == ValueSource.ABSENT }

    fun printUnconfigured() {
        val absent = planned.filter(::isUnset)
        if (absent.isEmpty() && unsaved.isEmpty()) return

        bold("\nStill not configured")
        for (key in absent) {
            yellow("  • ${key.label}")
            println("      ${key.consequence}")
            println("      Set ${key.env}, or save it with ./scripts/setup_credentials.main.kts")
        }
        for (key in unsaved) {
            dim("  • ${key.label}, answered but not saved; the next project will ask again.")
        }
        println()
    }
}

// ── github ──────────────────────────────────────────────────────────────────

enum class GithubCliState { READY, NOT_AUTHENTICATED, NOT_INSTALLED }

/**
 * Probes `gh` rather than trusting a stored flag — see the note on [Keys]. The
 * output is discarded because nothing reads it and a full pipe would block the
 * child.
 */
fun githubCliState(): GithubCliState = try {
    val exit = ProcessBuilder("gh", "auth", "status")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor()
    if (exit == 0) GithubCliState.READY else GithubCliState.NOT_AUTHENTICATED
} catch (_: Exception) {
    GithubCliState.NOT_INSTALLED
}

/** True when `gh` can see a repo from the working directory. */
fun hasGhRepo(): Boolean = run("gh", "repo", "view", "--json", "name").ok

fun runGh(vararg args: String, stdin: String? = null): Boolean =
    run("gh", *args, stdin = stdin).ok

/** `owner/name` for the repo in the working directory, or null. */
fun ghRepoSlug(): String? =
    run("gh", "repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner")
        .takeIf { it.ok }
        ?.output
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * Sets a repo secret, reporting what happened so a caller can summarise rather
 * than guess. The value goes in on stdin, never as an argv element — arguments
 * are visible to every process on the machine.
 */
fun setGhSecret(name: String, value: String): Boolean =
    runGh("secret", "set", name, stdin = value)

fun setGhVariable(name: String, value: String): Boolean =
    runGh("variable", "set", name, "--body", value)

// ── processes ───────────────────────────────────────────────────────────────

data class CommandResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Runs [command], capturing stdout+stderr together.
 *
 * Reads the pipe on this thread *before* waiting: a child that outfills the
 * ~64KB pipe buffer blocks writing while the parent blocks in `waitFor`, and
 * the pair deadlocks. `fly deploy` prints well past that. The same trap is
 * documented at `resetGitHistory` in init_project.main.kts, which dodges it by
 * discarding output instead.
 */
fun run(vararg command: String, stdin: String? = null): CommandResult = try {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (stdin != null) {
        process.outputStream.use { it.write(stdin.toByteArray()) }
    } else {
        process.outputStream.close()
    }
    val output = process.inputStream.bufferedReader().use { it.readText() }
    CommandResult(process.waitFor(), output)
} catch (e: Exception) {
    CommandResult(exitCode = 127, output = e.message.orEmpty())
}

/** Runs [command] with its output going straight to this terminal. */
fun runInteractive(vararg command: String): Boolean = try {
    ProcessBuilder(*command).inheritIO().start().waitFor() == 0
} catch (_: Exception) {
    false
}

fun commandExists(name: String): Boolean = run("which", name).ok

// ── http ────────────────────────────────────────────────────────────────────

data class Response(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

fun request(
    method: String,
    url: String,
    token: String? = null,
    body: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
): Response {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 20_000
        readTimeout = 60_000
        token?.let { setRequestProperty("Authorization", "Bearer $it") }
        setRequestProperty("Accept", "application/json")
        extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
    }
    body?.let { connection.outputStream.use { stream -> stream.write(it.toByteArray()) } }
    val code = connection.responseCode
    val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
        ?.bufferedReader()?.use { it.readText() }.orEmpty()
    connection.disconnect()
    return Response(code, text)
}

fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

// ── json ────────────────────────────────────────────────────────────────────

/**
 * Just enough JSON for the setup scripts, hand-rolled.
 *
 * A `@file:DependsOn` would make every cold run resolve Maven coordinates over
 * the network, which has been observed hanging the script runner outright — the
 * note at the top of `init_project.main.kts` is about the same hazard. These
 * scripts exist to run immediately on a machine that has just been handed a
 * checkout.
 *
 * Regex over the payload was the earlier approach and it does not survive
 * contact with arrays of objects: "the `ref` of the project whose `name`
 * matches" is not a question a regex can answer, and getting it wrong means
 * configuring somebody else's project.
 */
sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Text(val value: String) : Json
    data class Number(val value: Double) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json
}

operator fun Json?.get(key: String): Json? = (this as? Json.Obj)?.fields?.get(key)
operator fun Json?.get(index: Int): Json? = (this as? Json.Arr)?.items?.getOrNull(index)
val Json?.text: String? get() = (this as? Json.Text)?.value
val Json?.bool: Boolean? get() = (this as? Json.Bool)?.value
val Json?.items: List<Json> get() = (this as? Json.Arr)?.items.orEmpty()

fun parseJson(input: String): Json {
    val parser = JsonParser(input)
    val value = parser.readValue()
    parser.skipWhitespace()
    return value
}

private class JsonParser(private val source: String) {
    private var index = 0

    fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    fun readValue(): Json {
        skipWhitespace()
        if (index >= source.length) fail("unexpected end of input")
        return when (val c = source[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> Json.Text(readString())
            't' -> readLiteral("true").let { Json.Bool(true) }
            'f' -> readLiteral("false").let { Json.Bool(false) }
            'n' -> readLiteral("null").let { Json.Null }
            else -> if (c == '-' || c.isDigit()) readNumber() else fail("unexpected '$c'")
        }
    }

    private fun readObject(): Json {
        index++
        val fields = LinkedHashMap<String, Json>()
        skipWhitespace()
        if (peek() == '}') { index++; return Json.Obj(fields) }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            fields[key] = readValue()
            skipWhitespace()
            when (val c = next()) {
                ',' -> Unit
                '}' -> return Json.Obj(fields)
                else -> fail("expected ',' or '}' but found '$c'")
            }
        }
    }

    private fun readArray(): Json {
        index++
        val items = mutableListOf<Json>()
        skipWhitespace()
        if (peek() == ']') { index++; return Json.Arr(items) }
        while (true) {
            items += readValue()
            skipWhitespace()
            when (val c = next()) {
                ',' -> Unit
                ']' -> return Json.Arr(items)
                else -> fail("expected ',' or ']' but found '$c'")
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            when (val c = next()) {
                '"' -> return builder.toString()
                '\\' -> when (val escape = next()) {
                    '"' -> builder.append('"')
                    '\\' -> builder.append('\\')
                    '/' -> builder.append('/')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'u' -> {
                        builder.append(source.substring(index, index + 4).toInt(16).toChar())
                        index += 4
                    }
                    else -> fail("unknown escape '\\$escape'")
                }
                else -> builder.append(c)
            }
        }
    }

    private fun readNumber(): Json {
        val start = index
        while (index < source.length && (source[index].isDigit() || source[index] in "-+.eE")) index++
        return Json.Number(source.substring(start, index).toDouble())
    }

    private fun readLiteral(literal: String) {
        if (!source.startsWith(literal, index)) fail("expected '$literal'")
        index += literal.length
    }

    private fun peek(): Char? = source.getOrNull(index)
    private fun next(): Char = source.getOrNull(index++) ?: fail("unexpected end of input")
    private fun expect(c: Char) { if (next() != c) fail("expected '$c'") }
    private fun fail(message: String): Nothing =
        throw IllegalArgumentException("Malformed JSON at offset $index: $message")
}

/** Escapes [value] for use as a JSON string body, quotes included. */
fun jsonString(value: String): String {
    val builder = StringBuilder("\"")
    for (c in value) {
        when {
            c == '"' -> builder.append("\\\"")
            c == '\\' -> builder.append("\\\\")
            c == '\n' -> builder.append("\\n")
            c == '\r' -> builder.append("\\r")
            c == '\t' -> builder.append("\\t")
            c < ' ' -> builder.append("\\u%04x".format(c.code))
            else -> builder.append(c)
        }
    }
    return builder.append('"').toString()
}

/** `{"a":"x","b":true}` from pairs whose values are already JSON-encoded. */
fun jsonObject(vararg fields: Pair<String, String>): String =
    fields.joinToString(",", "{", "}") { (key, value) -> "${jsonString(key)}:$value" }

// ── project properties ──────────────────────────────────────────────────────

fun readProperty(file: File, key: String): String? {
    if (!file.exists()) return null
    val properties = Properties()
    file.inputStream().use(properties::load)
    return properties.getProperty(key)?.takeIf { it.isNotBlank() }
}

/**
 * Rewrites a key in place, keeping the file's comments and ordering. Those
 * comments are load-bearing — the ones in `telemetry.properties` explain why the
 * DSN is committed, which is the line that stops someone "tidying" it back into
 * local.properties.
 */
fun upsertProperty(file: File, key: String, value: String) {
    val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
    val index = lines.indexOfFirst { it.trimStart().startsWith("$key=") }
    if (index >= 0) lines[index] = "$key=$value" else lines += "$key=$value"
    file.writeText(lines.joinToString("\n").trimEnd() + "\n")
}
