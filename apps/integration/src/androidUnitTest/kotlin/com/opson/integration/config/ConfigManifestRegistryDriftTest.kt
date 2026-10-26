package com.opson.integration.config

import com.opson.features.logger.impl.BarcodeScanEnabled
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.ConfiguredValue
import com.opson.libraries.config.DoubleConfigValue
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.IntConfigValue
import com.opson.libraries.config.JsonConfigValue
import com.opson.libraries.config.LongConfigValue
import com.opson.libraries.config.StringConfigValue
import com.opson.libraries.config.impl.ConfigRefreshThrottleMs
import com.opson.libraries.config.impl.model.BasicMapAppConfig
import com.opson.libraries.reminders.RemindersEnabled
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.telemetry.impl.AppEventsEnabled
import com.opson.libraries.telemetry.impl.AppEventsSampleRate
import com.opson.libraries.telemetry.impl.KlogForwardingEnabled
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `apps/admin/config-manifest-registry.json` is a committed transcription of the
 * app's `ConfiguredValue` classes. `exportConfigManifest` stamps it with the
 * release's version code and CI PUTs it to `/v1/admin/config/manifest`, and from
 * there it is the *only* thing the server and the admin console know about the
 * in-code config: it is what `ConfigSchema` type-checks admin writes against, and
 * what the console shows as the baseline a remote override replaces.
 *
 * So when it drifts, nothing breaks loudly. A key missing from it is a key the
 * server will accept `"six"` for and the console will render with a blank type
 * and no editor. That is the gap this test closes.
 *
 * **Why a test and not a generator.** Generating the file would mean *running*
 * `:libraries:config` code from a Gradle task, and that module has only Android
 * and iOS targets — giving it (and everything under it) a JVM target purely to
 * feed codegen is a much larger change than reading the same classes from a test
 * that already has them on its classpath. The failure message prints the exact
 * JSON line to paste, so fixing a drift is the copy-and-paste a generator would
 * have done anyway.
 *
 * **Why `:apps:integration` and not `:libraries:config`.** The declared key set
 * spans two impl modules that cannot see each other: `config.refreshThrottleMs`
 * is declared in `:libraries:config:impl` next to the repository whose refresh it
 * throttles, and the `telemetry.*` three live in `:libraries:telemetry:impl`.
 * Only an `:apps:*` module may depend on an impl, so this is the only test module
 * that can hold both halves. A test in `:libraries:config` would have had to pin
 * the telemetry paths and defaults as literals, which is the hand-maintenance it
 * exists to remove.
 *
 * **The residual seam.** [declaredConfigValues] is itself a hand-written list;
 * nothing here comes from the DI graph's `Set<QaConfigValue>`, because no unit
 * test can resolve that without an app. [everyPathDeclaredInSourceIsEnumerated]
 * is what stops a value class added to DI and to neither list going unnoticed.
 */
class ConfigManifestRegistryDriftTest {

    private val declared: List<DeclaredValue> =
        declaredConfigValues(BasicMapAppConfig(emptyMap<String, Any>())).map { it.asDeclaredValue() }

    private val registry: List<RegistryEntry> = readRegistry()

    @Test
    fun registryListsEveryDeclaredKeyAndNothingElse() {
        val declaredPaths = declared.map { it.path }
        assertEquals(
            declaredPaths.size,
            declaredPaths.toSet().size,
            "Two ConfiguredValue classes declare the same path: " +
                declaredPaths.groupBy { it }.filterValues { it.size > 1 }.keys,
        )

        val registryPaths = registry.map { it.path }.toSet()
        val missing = declared.filter { it.path !in registryPaths }
        val extra = registryPaths - declaredPaths.toSet() - SERVER_OWNED_PATHS

        if (missing.isEmpty() && extra.isEmpty()) return
        fail(
            buildString {
                appendLine("$REGISTRY_NAME has drifted from the declared ConfiguredValue classes.")
                if (missing.isNotEmpty()) {
                    appendLine()
                    appendLine("ADD ${entries(missing.size)}:")
                    missing.forEach { appendLine(it.asRegistryLine()) }
                }
                if (extra.isNotEmpty()) {
                    appendLine()
                    appendLine("REMOVE ${entries(extra.size)} — no ConfiguredValue declares them:")
                    extra.sorted().forEach { appendLine("  $it") }
                }
            },
        )
    }

    @Test
    fun registryTypeDefaultAndAllowedValuesMatchTheDeclaration() {
        val registryByPath = registry.associateBy { it.path }

        val wrong = declared.mapNotNull { value ->
            val entry = registryByPath[value.path] ?: return@mapNotNull null
            val reasons = buildList {
                if (entry.type != value.type) add("type is \"${entry.type}\", declared ${value.type}")
                if (entry.default.canonical() != value.default.canonical()) {
                    add("default is ${entry.default}, declared ${value.default}")
                }
                if (entry.allowedValues?.canonical() != value.allowedValues?.canonical()) {
                    add("allowedValues are ${entry.allowedValues ?: "absent"}, declared ${value.allowedValues ?: "none"}")
                }
            }
            if (reasons.isEmpty()) null else value to reasons
        }

        if (wrong.isEmpty()) return
        fail(
            buildString {
                appendLine("$REGISTRY_NAME disagrees with the declared ConfiguredValue classes.")
                appendLine("The declaration wins — the app ships it. Replace each line:")
                wrong.forEach { (value, reasons) ->
                    appendLine()
                    reasons.forEach { appendLine("  ${value.path}: $it") }
                    appendLine(value.asRegistryLine())
                }
            },
        )
    }

    /**
     * The exemption above, held to its own terms in both directions.
     *
     * An allowance that only ever grows is how a set-equality check turns into a
     * one-sided one. So [SERVER_OWNED_PATHS] has to stay a set of rows that
     * really are in the registry and really have no `ConfiguredValue`: an entry
     * that gains one has to leave the list, and an entry deleted from the
     * registry cannot linger here excusing nothing.
     */
    @Test
    fun serverOwnedEntriesAreStillServerOwned() {
        val registryPaths = registry.map { it.path }.toSet()
        val declaredPaths = declared.map { it.path }.toSet()

        assertEquals(
            emptySet(),
            SERVER_OWNED_PATHS - registryPaths,
            "These are exempt from the drift check and are no longer in $REGISTRY_NAME. " +
                "Drop them from SERVER_OWNED_PATHS.",
        )
        assertEquals(
            emptySet(),
            SERVER_OWNED_PATHS intersect declaredPaths,
            "A ConfiguredValue now declares these, so they are ordinary keys and the exemption " +
                "hides real drift on them. Drop them from SERVER_OWNED_PATHS.",
        )
    }

    /**
     * Set comparison against a list that is itself computed passes vacuously if
     * both sides collapse to nothing — an empty registry against an empty
     * enumeration is "in sync". Both ends are pinned here.
     *
     * The floor is deliberately low. A template ships few config values and the
     * app built from it ships many, so a number tuned to this repo would be one
     * more thing to fight downstream; it only has to be high enough that a file
     * read as empty, or an enumeration that stopped enumerating, is reported.
     */
    @Test
    fun bothSidesAreNonTriviallyPopulated() {
        assertTrue(
            declared.size >= MINIMUM_KEY_COUNT,
            "only ${declared.size} ConfiguredValue classes were enumerated, so declaredConfigValues() " +
                "is not returning what it looks like it returns",
        )
        assertTrue(
            registry.size >= MINIMUM_KEY_COUNT,
            "$REGISTRY_NAME parsed to ${registry.size} entries, so this is a truncated or " +
                "misread file, not a smaller key set",
        )
    }

    // ── The guard on the guard: is the enumeration itself complete? ───────────

    @Test
    fun everyPathDeclaredInSourceIsEnumerated() {
        val unenumerated = declarationsInSource().map { it.path }.toSet() - declared.map { it.path }.toSet()

        assertTrue(
            unenumerated.isEmpty(),
            "These config keys are declared by a ConfiguredValue class that declaredConfigValues() " +
                "does not name:\n" + unenumerated.sorted().joinToString("\n") { "  $it" } +
                "\n\nA key nothing enumerates gets no registry entry and no test that would notice.",
        )
    }

    @Test
    fun everyEnumeratedPathIsDeclaredInSource() {
        // The half that stops the test above passing vacuously. A scan pointed at
        // the wrong root, or a regex that stopped matching, finds nothing — and
        // "nothing unenumerated" is exactly what a broken scan reports.
        val undeclared = declared.map { it.path }.toSet() - declarationsInSource().map { it.path }.toSet()

        assertTrue(
            undeclared.isEmpty(),
            "These paths are enumerated but the scan found no `override val path` declaring " +
                "them:\n" + undeclared.sorted().joinToString("\n") { "  $it" } +
                "\n\nEither a value class was deleted and its enumeration entry left behind, or " +
                "this scan is no longer reading the source it thinks it is.",
        )
    }

    @Test
    fun everyPathDeclaredInSourceIsWrittenAsALiteral() {
        // A `path` built from a constant or an interpolation reads as no
        // declaration at all to a text scan, so it would slip past
        // everyPathDeclaredInSourceIsEnumerated silently — the failure this whole
        // guard exists to stop. Fail on it instead.
        val unparsed = configSourceFiles().flatMap { file ->
            file.readLines()
                .filter { PATH_DECLARATION.containsMatchIn(it) && PATH_LITERAL.find(it) == null }
                .map { "${file.path}: ${it.trim()}" }
        }

        assertTrue(
            unparsed.isEmpty(),
            "These path declarations are not plain string literals, so this scan cannot read " +
                "them:\n" + unparsed.joinToString("\n") { "  $it" } +
                "\n\nWrite the path as a literal, or teach this scan to resolve it.",
        )
    }

    @Test
    fun theSourceScanCanActuallyFail() {
        val found = declarationsInSource()

        assertTrue(
            found.size >= MINIMUM_KEY_COUNT,
            "the scan found ${found.size} declarations, fewer than the repo has, so it is " +
                "reading the wrong tree",
        )
        assertTrue(
            found.any { it.path == "config.refreshThrottleMs" },
            "the scan missed the key declared outside :libraries:telemetry:impl, which is the " +
                "case that proves it walks more than one module",
        )
        assertTrue(
            found.none { it.path == "identity.googleSignInEnabled" },
            "the scan counted a KDoc example as a declaration — that path is only ever written " +
                "inside the ConfiguredValue doc comment",
        )
        assertTrue(
            found.none { it.file.path.contains("Test.kt") },
            "the scan read a test's throwaway value class as a shipped declaration:\n" +
                found.filter { it.file.path.contains("Test.kt") }.joinToString("\n") { "  ${it.file}" },
        )
    }

    private fun declarationsInSource(): List<Declaration> = configSourceFiles().flatMap { file ->
        file.readLines().mapNotNull { line ->
            PATH_LITERAL.find(line)?.let { Declaration(it.groupValues[1], file) }
        }
    }

    /**
     * Shipped source that could hold a `ConfiguredValue`. A value class has to
     * name the base it extends, so a file mentioning neither name cannot declare
     * one — which keeps an unrelated `override val path` (a route, a resource
     * handle) out of the scan.
     */
    private fun configSourceFiles(): List<File> {
        val files = listOf("libraries", "features", "apps")
            .map { File(repoRoot(), it) }
            .flatMap { dir -> dir.walkTopDown().onEnter { it.name !in NOT_SOURCE }.toList() }
            .filter { it.isShippedKotlin() }

        assertTrue(files.size > MINIMUM_SOURCE_FILES, "only found ${files.size} source files to scan")

        return files.filter { file -> CONFIG_BASE_NAMES.any { it in file.readText() } }
    }

    /**
     * `commonMain` and the platform source sets, not tests. A test fixture is
     * free to declare a throwaway value class with a made-up path.
     */
    private fun File.isShippedKotlin(): Boolean {
        if (!isFile || extension != "kt") return false
        val parts = path.split(File.separator)
        return "build" !in parts && parts.none { it.endsWith("Test") || it == "test" }
    }

    private data class Declaration(val path: String, val file: File)
}

/**
 * Every `ConfiguredValue` the app declares, from the one module that can see all
 * of them.
 *
 * Hand-written, because the runtime source of truth is the DI graph's
 * `Set<QaConfigValue>` multibinding and no unit test can resolve it. Adding a
 * value class and forgetting this list is the mistake
 * [ConfigManifestRegistryDriftTest.everyPathDeclaredInSourceIsEnumerated] exists
 * to catch.
 */
internal fun declaredConfigValues(appConfigMap: AppConfigMap): List<ConfiguredValue<*>> = listOf(
    ConfigRefreshThrottleMs(appConfigMap),
    AppEventsEnabled(appConfigMap),
    AppEventsSampleRate(appConfigMap),
    KlogForwardingEnabled(appConfigMap),
    RemindersEnabled(appConfigMap),
    HealthReleaseFlag(appConfigMap),
    BarcodeScanEnabled(appConfigMap),
)

private const val REGISTRY_NAME = "apps/admin/config-manifest-registry.json"

/**
 * Paths the registry carries that no client `ConfiguredValue` declares.
 *
 * The kill-switch trio is seeded straight into `app_config_values` by the V4
 * migration and driven from the admin console's pinned panel; no client code
 * reads it yet, so there is nothing here to transcribe. Their registry rows are
 * not dead weight: `ConfigSchema` waves through any path the manifest does not
 * mention, so deleting them is what would let `maintenanceMode = "banana"`
 * through the admin API.
 *
 * Whoever wires a force-upgrade gate into the client declares these as ordinary
 * `ConfiguredValue`s and deletes this list;
 * [ConfigManifestRegistryDriftTest.serverOwnedEntriesAreStillServerOwned] fails
 * until they do.
 */
private val SERVER_OWNED_PATHS = setOf(
    "upgrade.minSupportedVersionCode",
    "upgrade.maintenanceMode",
    "upgrade.maintenanceMessage",
)

private fun entries(count: Int): String = if (count == 1) "1 entry" else "$count entries"

/**
 * Paths handed in by `apps/integration/build.gradle.kts`. An Android unit test's
 * working directory is not something to guess at, and a wrong guess would read
 * nothing and pass.
 */
private const val REGISTRY_PROPERTY = "configManifestRegistry"
private const val REPO_ROOT_PROPERTY = "repoRoot"

/** Floors, not real counts — see [ConfigManifestRegistryDriftTest.bothSidesAreNonTriviallyPopulated]. */
private const val MINIMUM_KEY_COUNT = 4
private const val MINIMUM_SOURCE_FILES = 200

/** `.claude` holds agent worktrees, which are full checkouts of this repo. */
private val NOT_SOURCE = setOf("build", ".git", ".claude")

private val CONFIG_BASE_NAMES = listOf("ConfigValue", "ConfiguredValue")

/** Anchored at the start of the line, so a KDoc example indented behind its `*` does not match. */
private val PATH_DECLARATION = Regex("""^\s*override\s+val\s+path\b""")
private val PATH_LITERAL = Regex("""^\s*override\s+val\s+path\s*(?::\s*String\s*)?=\s*"([^"]+)"\s*$""")

private data class DeclaredValue(
    val path: String,
    val type: String,
    val default: JsonElement,
    val allowedValues: JsonArray?,
)

private data class RegistryEntry(
    val path: String,
    val type: String,
    val default: JsonElement,
    val allowedValues: JsonArray?,
)

private fun repoRoot(): File = File(
    System.getProperty(REPO_ROOT_PROPERTY)
        ?: error("$REPO_ROOT_PROPERTY is unset — apps/integration/build.gradle.kts should supply it"),
)

private fun readRegistry(): List<RegistryEntry> {
    val path = System.getProperty(REGISTRY_PROPERTY)
        ?: error("$REGISTRY_PROPERTY is unset — apps/integration/build.gradle.kts should supply it")
    val file = File(path)
    if (!file.isFile) error("$REGISTRY_NAME not found at $path")

    return Json.parseToJsonElement(file.readText()).jsonArray.mapIndexed { index, element ->
        val entry = element.jsonObject
        RegistryEntry(
            path = entry.stringField("path", index),
            type = entry.stringField("type", index),
            default = entry["default"] ?: error("$REGISTRY_NAME[$index] has no \"default\""),
            allowedValues = entry["allowedValues"] as? JsonArray,
        )
    }
}

private fun JsonObject.stringField(name: String, index: Int): String =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: error("$REGISTRY_NAME[$index] has no string \"$name\"")

private fun ConfiguredValue<*>.asDeclaredValue() = DeclaredValue(
    path = path,
    type = registryType(),
    default = default.asJsonElement(),
    allowedValues = allowedValues?.let { values -> JsonArray(values.map { it.asJsonElement() }) },
)

/**
 * The `type` string the manifest schema uses, from the typed base the value
 * extends. A new base has to be taught to the registry format before it can be
 * transcribed, so an unrecognised one fails rather than guessing "json".
 */
private fun ConfiguredValue<*>.registryType(): String = when (this) {
    is FlagConfigValue -> "boolean"
    is IntConfigValue -> "int"
    is LongConfigValue -> "long"
    is DoubleConfigValue -> "double"
    is StringConfigValue -> "string"
    is JsonConfigValue<*> -> "json"
    else -> error(
        "$path extends ${this::class.simpleName}, which has no manifest type. Add one to the " +
            "validTypes set in apps/admin/build.gradle.kts, to ConfigSchema, and to this mapping.",
    )
}

private fun Any?.asJsonElement(): JsonElement = when (this) {
    null -> JsonPrimitive(null as String?)
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.asJsonElement() })
    is Iterable<*> -> JsonArray(map { it.asJsonElement() })
    // Naming the composite cases here rather than reflecting over @Serializable is
    // deliberate: this `error` is what tells whoever adds the first typed default
    // that the comparison needs teaching about it, and reflection would swallow
    // that silently.
    else -> error("A ConfiguredValue default of type ${this::class.simpleName} can't be written as JSON")
}

/**
 * Normalises numbers so the comparison is about the value, not its spelling.
 * Kotlin renders `1.0` as `1.0` and `5L * 60L * 1000L` as `300000`, and JSON does
 * not have to agree with either — without this, a registry entry that is right
 * would fail for writing `1` where the class says `1.0`.
 */
private fun JsonElement.canonical(): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.canonical() })
    is JsonArray -> JsonArray(map { it.canonical() })
    is JsonPrimitive -> when {
        isString -> this
        booleanOrNull != null -> JsonPrimitive(booleanOrNull)
        doubleOrNull != null -> JsonPrimitive(doubleOrNull)
        else -> this
    }
}

private fun DeclaredValue.asRegistryLine(): String = buildString {
    append("  { \"path\": ")
    append(JsonPrimitive(path))
    append(", \"type\": ")
    append(JsonPrimitive(type))
    append(", \"default\": ")
    append(default)
    allowedValues?.let { append(", \"allowedValues\": ").append(it) }
    append(", \"description\": \"…\" },")
}
