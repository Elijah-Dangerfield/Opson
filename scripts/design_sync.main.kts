#!/usr/bin/env kotlin

@file:Import("lib/setup_store.main.kts")

/**
 * Moves screen images between this app and Claude Design, in either direction.
 *
 *   export [--publish] [--skip-render]
 *                            render every @Preview, collect the PNGs and a manifest
 *                            into design-sync/app/, zip it to design-sync/app.zip.
 *                            --publish also force-pushes design-sync/app/ to the
 *                            orphan `design-sync` branch and prints its raw URL.
 *                            --skip-render reuses the last render instead.
 *   import <folder-or-zip>   take Claude Design's PNG export, pair each screen with
 *                            the app's render of it, and write side-by-side images
 *                            to design-sync/compare/.
 *   status                   what is on disk and when it was made.
 *
 * Screens are paired by screen id, the rule in docs/practices/design-sync.md.
 * Everything lands under design-sync/, which is gitignored.
 *
 * Run from the project root:
 *   ./scripts/design_sync.main.kts export
 */

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.system.exitProcess

System.setProperty("java.awt.headless", "true")

val root: File = File(".").canonicalFile
if (!File(root, "settings.gradle.kts").exists() || !File(root, "gradlew").exists()) {
    die("settings.gradle.kts not found. Run this from the project root.")
}

val syncDir = File(root, "design-sync")
val appDir = File(syncDir, "app")
val appZip = File(syncDir, "app.zip")
val designDir = File(syncDir, "design")
val compareDir = File(syncDir, "compare")
val stateFile = File(syncDir, "state.properties")

val screenshotsModule = File(root, "apps/screenshots")
val resultsDir = File(screenshotsModule, "build/outputs/screenshot-results")
val publishBranch = "design-sync"

// The render size the screenshot test pins. Written into the manifest so a designer
// knows what frame the app images were taken in.
val deviceWidthDp = 390
val deviceHeightDp = 844
val deviceDensity = "xxhdpi"

/** Must match `screenshotDir` in apps/screenshots/build.gradle.kts. */
fun pngDir(): File =
    if (readProperty(File(root, "gradle.properties"), "screenshots.commitBaseline") == "true") {
        File(screenshotsModule, "baseline")
    } else {
        File(screenshotsModule, "build/outputs/roborazzi")
    }

// ── shared ──────────────────────────────────────────────────────────────────

fun git(vararg args: String, env: Map<String, String> = emptyMap()): CommandResult = try {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(root)
        .redirectErrorStream(true)
        .also { it.environment().putAll(env) }
        .start()
    process.outputStream.close()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    CommandResult(process.waitFor(), output.trim())
} catch (e: Exception) {
    CommandResult(127, e.message.orEmpty())
}

fun appName(): String =
    File(root, "settings.gradle.kts").readLines()
        .firstNotNullOfOrNull { Regex("""rootProject\.name\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
        ?: root.name

fun recordState(vararg entries: Pair<String, String>) {
    syncDir.mkdirs()
    entries.forEach { (key, value) -> upsertProperty(stateFile, key, value) }
}

/**
 * The forgiving half of pairing: case, separators, file extensions and the word
 * "preview" do not count, so `FeedbackScreen_FeedbackScreen.png` still finds
 * `feedback-screen__feedback-screen`.
 */
fun normalizedId(id: String): String =
    id.substringBeforeLast(".png")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() && it != "preview" }
        .joinToString("")

fun requireSafeName(name: String): String {
    require(name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != "." && name != "..") {
        "Refusing unsafe file name '$name'"
    }
    return name
}

data class AppScreen(
    val id: String,
    val file: String?,
    val module: String,
    val preview: String,
    val previewName: String?,
    val failure: String?,
)

fun readResults(): List<AppScreen> =
    resultsDir.listFiles { f -> f.extension == "json" }.orEmpty()
        .map { parseJson(it.readText()) }
        .map { json ->
            AppScreen(
                id = json["id"].text ?: die("A result file has no id. Re-run export."),
                file = json["file"].text,
                module = json["module"].text ?: "unknown",
                preview = json["preview"].text.orEmpty(),
                previewName = json["previewName"].text,
                failure = json["failure"].text,
            )
        }
        .sortedBy { it.id }

fun readAppManifest(): List<AppScreen> {
    val manifest = File(appDir, "manifest.json")
    if (!manifest.exists()) die("No app export yet. Run ./scripts/design_sync.main.kts export first.")
    return parseJson(manifest.readText())["screens"].items.map { json ->
        AppScreen(
            id = json["id"].text.orEmpty(),
            file = json["file"].text,
            module = json["module"].text ?: "unknown",
            preview = json["preview"].text.orEmpty(),
            previewName = json["previewName"].text,
            failure = json["failure"].text,
        )
    }
}

fun nullable(value: String?): String = value?.let(::jsonString) ?: "null"

// ── export ──────────────────────────────────────────────────────────────────

/** `git@github.com:owner/repo.git` or `https://github.com/owner/repo(.git)` → owner/repo. */
fun githubSlug(): String? {
    val url = git("remote", "get-url", "origin").takeIf { it.ok }?.output ?: return null
    return Regex("""github\.com[:/]([^/]+)/([^/]+?)(\.git)?$""").find(url)
        ?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
}

fun rawBaseUrl(slug: String) = "https://raw.githubusercontent.com/$slug/$publishBranch/app"

fun writeManifest(screens: List<AppScreen>, slug: String?) {
    val commit = git("rev-parse", "HEAD").takeIf { it.ok }?.output ?: "unknown"
    val dirty = git("status", "--porcelain").output.isNotBlank()
    val screenJson = screens.joinToString(",\n", "[\n", "\n  ]") { s ->
        "    " + jsonObject(
            "id" to jsonString(s.id),
            "file" to nullable(s.file),
            "module" to jsonString(s.module),
            "preview" to jsonString(s.preview),
            "previewName" to nullable(s.previewName),
            "failure" to nullable(s.failure),
        )
    }
    val manifest = """
        |{
        |  "app": ${jsonString(appName())},
        |  "commit": ${jsonString(commit)},
        |  "uncommittedChanges": $dirty,
        |  "generatedAt": ${jsonString(Instant.now().toString())},
        |  "device": {"widthDp": $deviceWidthDp, "heightDp": $deviceHeightDp, "density": ${jsonString(deviceDensity)}, "widthPx": ${deviceWidthDp * 3}, "heightPx": ${deviceHeightDp * 3}},
        |  "screenIdRule": "<file>__<preview>[--<@Preview name>][--<parameter index>], kebab-case, the word 'preview' dropped from <preview>",
        |  "imageBaseUrl": ${nullable(slug?.let(::rawBaseUrl))},
        |  "screens": $screenJson
        |}
        |""".trimMargin()
    File(appDir, "manifest.json").writeText(manifest)
}

fun zipDirectory(source: File, target: File) {
    target.delete()
    ZipOutputStream(target.outputStream().buffered()).use { zip ->
        source.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { file ->
            zip.putNextEntry(ZipEntry(file.name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/**
 * Commits design-sync/app/ as the only content of a fresh, parentless commit and
 * force-pushes it to the publish branch.
 *
 * Plumbing only, through a throwaway index file, so the current branch, the real
 * index and the working tree are never touched. Parentless on purpose: the branch
 * holds the latest export and nothing else, so it never accumulates a history of
 * images that would bloat every clone that fetches it.
 */
fun publish(slug: String): Boolean {
    val index = File.createTempFile("design-sync-index", "")
    index.delete()
    val env = mapOf("GIT_INDEX_FILE" to index.absolutePath)
    try {
        val readme = File.createTempFile("design-sync-readme", ".md").apply {
            writeText(
                "# design-sync\n\nGenerated by `scripts/design_sync.main.kts export --publish`. " +
                    "Force-pushed on every publish, so do not commit here.\n\n" +
                    "`app/manifest.json` lists every rendered screen; each `file` sits next to it.\n",
            )
            deleteOnExit()
        }
        val files = listOf(readme to "README.md") +
            appDir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.map { it to "app/${it.name}" }
        for ((file, path) in files) {
            val blob = git("hash-object", "-w", file.absolutePath)
            if (!blob.ok) { red(blob.output); return false }
            val added = git("update-index", "--add", "--cacheinfo", "100644,${blob.output},$path", env = env)
            if (!added.ok) { red(added.output); return false }
        }
        val tree = git("write-tree", env = env)
        if (!tree.ok) { red(tree.output); return false }
        val head = git("rev-parse", "--short", "HEAD").output
        val commit = git("commit-tree", tree.output, "-m", "design-sync: screens from $head")
        if (!commit.ok) { red(commit.output); return false }
        dim("Pushing ${files.size} files to origin/$publishBranch …")
        val push = git("push", "--force", "origin", "${commit.output}:refs/heads/$publishBranch")
        if (!push.ok) { red(push.output); return false }
        return true
    } finally {
        index.delete()
    }
}

fun repoVisibility(slug: String): String? =
    if (commandExists("gh")) {
        run("gh", "repo", "view", slug, "--json", "visibility", "-q", ".visibility")
            .takeIf { it.ok }?.output?.trim()?.lowercase()
    } else {
        null
    }

fun export(publishToo: Boolean, render: Boolean) {
    if (render) {
        bold("Rendering every @Preview (./gradlew :apps:screenshots:recordRoborazziDebug) …")
        if (!runInteractive(File(root, "gradlew").absolutePath, ":apps:screenshots:recordRoborazziDebug")) {
            die("The render failed. The Gradle output above says why.")
        }
    } else {
        dim("Reusing the last render in ${resultsDir.relativeTo(root)}/ (--skip-render).")
    }

    val screens = readResults()
    if (screens.isEmpty()) die("The render produced no results in ${resultsDir.relativeTo(root)}.")
    val source = pngDir()

    appDir.deleteRecursively()
    appDir.mkdirs()
    val missing = mutableListOf<String>()
    screens.filter { it.file != null }.forEach { screen ->
        val png = File(source, screen.file!!)
        if (png.exists()) png.copyTo(File(appDir, requireSafeName(screen.file)), overwrite = true) else missing += screen.id
    }
    if (missing.isNotEmpty()) die("Rendered but not on disk in ${source.relativeTo(root)}: ${missing.joinToString()}")

    val slug = if (publishToo) githubSlug() ?: die("origin is not a GitHub remote, so there is nowhere to publish.") else null
    writeManifest(screens, slug)
    zipDirectory(appDir, appZip)

    val failed = screens.filter { it.failure != null }
    green("✓ ${screens.size - failed.size} screens exported to ${appDir.relativeTo(root)}/, zipped to ${appZip.relativeTo(root)} (${appZip.length() / 1024} KB)")
    if (failed.isNotEmpty()) {
        yellow("! ${failed.size} previews did not render (recorded in the manifest):")
        failed.forEach { println("    ${it.id}: ${it.failure}") }
    }
    recordState("lastExport" to Instant.now().toString())

    if (!publishToo) return
    if (!publish(slug!!)) die("Publishing failed. The export itself is fine; give Claude Design ${appZip.relativeTo(root)} instead.")
    val manifestUrl = "${rawBaseUrl(slug)}/manifest.json"
    recordState("lastPublish" to Instant.now().toString(), "publishUrl" to manifestUrl)
    green("✓ Published to the $publishBranch branch.")
    println()
    bold("Manifest URL for Claude Design:")
    println("  $manifestUrl")
    when (repoVisibility(slug)) {
        "public" -> Unit
        null -> yellow("Could not tell whether $slug is public (no gh CLI). That URL only works for a public repo; for a private one, give Claude Design ${appZip.relativeTo(root)}.")
        else -> yellow("$slug is private, so that URL will 404 for Claude Design. Upload ${appZip.relativeTo(root)} instead.")
    }
}

// ── import ──────────────────────────────────────────────────────────────────

/** Extracts only PNGs and manifest.json, flattened, so a hostile path in the zip goes nowhere. */
fun extractZip(zip: File, into: File) {
    ZipFile(zip).use { archive ->
        archive.entries().asSequence()
            .filter { !it.isDirectory && !it.name.startsWith("__MACOSX/") }
            .forEach { entry ->
                val name = entry.name.substringAfterLast('/')
                if (name.startsWith(".")) return@forEach
                if (!name.endsWith(".png", ignoreCase = true) && name != "manifest.json") return@forEach
                val target = File(into, requireSafeName(name))
                if (target.exists()) yellow("! ${entry.name}: a file named $name was already extracted; keeping the first.")
                else archive.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
    }
}

/**
 * Design screen id → source PNG. When the export carries a manifest, its ids win
 * (a designer may name files loosely); otherwise the file name is the id.
 */
fun designScreens(source: File): Map<String, File> {
    val pngs = source.walkTopDown()
        .filter { it.isFile && it.extension.equals("png", ignoreCase = true) && "__MACOSX" !in it.path && !it.name.startsWith(".") }
        .toList()
    val byName = pngs.associateBy { it.name }
    val manifest = source.walkTopDown().firstOrNull { it.isFile && it.name == "manifest.json" }
    val fromManifest = manifest?.let { parseJson(it.readText())["screens"].items }
        ?.mapNotNull { screen ->
            val id = screen["id"].text ?: return@mapNotNull null
            val file = screen["file"].text ?: "$id.png"
            byName[file.substringAfterLast('/')]?.let { id to it }
        }
        .orEmpty()
        .toMap()
    val claimed = fromManifest.values.toSet()
    return fromManifest + pngs.filter { it !in claimed }.associateBy { it.nameWithoutExtension }
}

/** Design id → the designer's `title` / `notes`, when the export's manifest has them. */
fun designNotes(): Map<String, String> {
    val manifest = File(designDir, "manifest.json").takeIf { it.exists() } ?: return emptyMap()
    return parseJson(manifest.readText())["screens"].items.mapNotNull { screen ->
        val id = screen["id"].text ?: return@mapNotNull null
        listOfNotNull(screen["title"].text, screen["notes"].text)
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let { id to it.joinToString(": ") }
    }.toMap()
}

data class ScreenPair(val id: String, val app: AppScreen, val designId: String, val design: File, val exact: Boolean)

fun scaledToHeight(image: BufferedImage, height: Int): BufferedImage {
    val width = (image.width.toDouble() * height / image.height).toInt().coerceAtLeast(1)
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    out.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        color = Color.WHITE
        fillRect(0, 0, width, height)
        drawImage(image, 0, 0, width, height, null)
        dispose()
    }
    return out
}

/** App on the left, design on the right, same height, each labelled. */
fun sideBySide(app: File, design: File, label: String, target: File) {
    val left = ImageIO.read(app) ?: die("Could not read ${app.relativeTo(root)} as an image.")
    val right = ImageIO.read(design) ?: die("Could not read ${design.name} as an image.")
    // Tall enough to read text, small enough that an agent can take the whole pair in.
    val height = minOf(1600, maxOf(left.height, right.height))
    val a = scaledToHeight(left, height)
    val b = scaledToHeight(right, height)
    val pad = 24
    val header = 72
    val out = BufferedImage(a.width + b.width + pad * 3, height + header + pad, BufferedImage.TYPE_INT_RGB)
    out.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        color = Color(0xEE, 0xEE, 0xEE)
        fillRect(0, 0, out.width, out.height)
        color = Color(0x22, 0x22, 0x22)
        font = Font(Font.SANS_SERIF, Font.BOLD, 26)
        drawString("APP (code)", pad, 40)
        drawString("DESIGN", pad * 2 + a.width, 40)
        font = Font(Font.SANS_SERIF, Font.PLAIN, 18)
        drawString(label, pad, 64)
        drawString(design.name, pad * 2 + a.width, 64)
        drawImage(a, pad, header, null)
        drawImage(b, pad * 2 + a.width, header, null)
        color = Color(0xBB, 0xBB, 0xBB)
        drawRect(pad - 1, header - 1, a.width + 1, height + 1)
        drawRect(pad * 2 + a.width - 1, header - 1, b.width + 1, height + 1)
        dispose()
    }
    ImageIO.write(out, "png", target)
}

fun import(sourcePath: String) {
    val source = File(sourcePath).canonicalFile
    if (!source.exists()) die("$sourcePath does not exist.")
    val appScreens = readAppManifest()

    val staging = kotlin.io.path.createTempDirectory("design-sync-import").toFile()
    try {
        val sourceRoot = if (source.isFile) {
            if (!source.name.endsWith(".zip", ignoreCase = true)) die("$sourcePath is a file but not a .zip.")
            extractZip(source, staging)
            staging
        } else {
            source
        }
        val design = designScreens(sourceRoot)
        if (design.isEmpty()) die("No PNGs found in $sourcePath.")

        designDir.deleteRecursively()
        designDir.mkdirs()
        design.forEach { (id, file) -> file.copyTo(File(designDir, requireSafeName("$id.png")), overwrite = true) }
        sourceRoot.walkTopDown().firstOrNull { it.isFile && it.name == "manifest.json" }
            ?.copyTo(File(designDir, "manifest.json"), overwrite = true)

        // Exact id first; then the normalized form, but only when it is unambiguous on both sides.
        val rendered = appScreens.filter { it.file != null }
        val appById = rendered.associateBy { it.id }
        val appByNormal = rendered.groupBy { normalizedId(it.id) }
        val pairs = mutableListOf<ScreenPair>()
        val unmatchedDesign = mutableListOf<String>()
        val ambiguous = mutableListOf<String>()
        for (id in design.keys.sorted()) {
            val exact = appById[id]
            if (exact != null) {
                pairs += ScreenPair(exact.id, exact, id, File(designDir, "$id.png"), exact = true)
                continue
            }
            val candidates = appByNormal[normalizedId(id)].orEmpty().filter { c -> pairs.none { it.app == c } }
            when (candidates.size) {
                1 -> pairs += ScreenPair(candidates[0].id, candidates[0], id, File(designDir, "$id.png"), exact = false)
                0 -> unmatchedDesign += id
                else -> { unmatchedDesign += id; ambiguous += "$id (could be ${candidates.joinToString { it.id }})" }
            }
        }
        val pairedApp = pairs.map { it.app.id }.toSet()
        val unmatchedApp = rendered.filter { it.id !in pairedApp }.map { it.id }

        compareDir.deleteRecursively()
        compareDir.mkdirs()
        pairs.forEach { pair ->
            sideBySide(File(appDir, pair.app.file!!), pair.design, pair.id, File(compareDir, "${pair.id}.png"))
        }
        writeCompareReadme(sourcePath, pairs, unmatchedDesign, ambiguous, unmatchedApp, appScreens.filter { it.failure != null })
        recordState("lastImport" to Instant.now().toString(), "lastImportSource" to source.path)

        green("✓ ${design.size} design screens imported to ${designDir.relativeTo(root)}/")
        green("✓ ${pairs.size} paired (${pairs.count { !it.exact }} by a loose match), side-by-sides in ${compareDir.relativeTo(root)}/")
        if (unmatchedDesign.isNotEmpty()) yellow("! ${unmatchedDesign.size} design screens have no app screen: ${unmatchedDesign.joinToString()}")
        dim("  ${unmatchedApp.size} app screens have no design. Full list in ${File(compareDir, "README.md").relativeTo(root)}")
    } finally {
        staging.deleteRecursively()
    }
}

fun writeCompareReadme(
    source: String,
    pairs: List<ScreenPair>,
    unmatchedDesign: List<String>,
    ambiguous: List<String>,
    unmatchedApp: List<String>,
    failed: List<AppScreen>,
) {
    val manifest = parseJson(File(appDir, "manifest.json").readText())
    val text = buildString {
        appendLine("# Design vs app")
        appendLine()
        appendLine("Imported from `$source` at ${Instant.now()}. App screens are from commit `${manifest["commit"].text}` (${manifest["generatedAt"].text}).")
        appendLine("Each image in this folder is the app on the left and the design on the right, at the same height.")
        appendLine()
        appendLine("## Pairs (${pairs.size})")
        appendLine()
        if (pairs.isEmpty()) appendLine("None.") else {
            appendLine("| Screen id | Side by side | App | Design | Match | Preview |")
            appendLine("|---|---|---|---|---|---|")
            pairs.forEach { p ->
                appendLine("| `${p.id}` | [${p.id}.png](${p.id}.png) | `app/${p.app.file}` | `design/${p.designId}.png` | ${if (p.exact) "exact" else "loose (`${p.designId}`)"} | `${p.app.preview}` in `${p.app.module}` |")
            }
        }
        val notes = designNotes()
        if (notes.isNotEmpty()) {
            appendLine()
            appendLine("## Designer notes")
            appendLine()
            notes.toSortedMap().forEach { (id, note) -> appendLine("- `$id`: $note") }
        }
        appendLine()
        appendLine("## Design screens with no app screen (${unmatchedDesign.size})")
        appendLine()
        appendLine("New screens, or a name that does not follow the screen id rule. Do not build one without asking.")
        appendLine()
        if (unmatchedDesign.isEmpty()) appendLine("None.") else unmatchedDesign.forEach { appendLine("- `design/$it.png`") }
        if (ambiguous.isNotEmpty()) {
            appendLine()
            appendLine("Ambiguous loose matches, left unpaired:")
            appendLine()
            ambiguous.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("## App screens with no design (${unmatchedApp.size})")
        appendLine()
        if (unmatchedApp.isEmpty()) appendLine("None.") else unmatchedApp.forEach { appendLine("- `$it`") }
        if (failed.isNotEmpty()) {
            appendLine()
            appendLine("## App previews that did not render (${failed.size})")
            appendLine()
            failed.forEach { appendLine("- `${it.id}`: ${it.failure}") }
        }
    }
    File(compareDir, "README.md").writeText(text)
}

// ── status ──────────────────────────────────────────────────────────────────

fun status() {
    fun state(key: String) = readProperty(stateFile, key)
    bold("design-sync status")
    val manifest = File(appDir, "manifest.json")
    if (manifest.exists()) {
        val json = parseJson(manifest.readText())
        val screens = json["screens"].items
        val failed = screens.count { it["failure"].text != null }
        println("  app      ${screens.size - failed} screens, $failed failed. Commit ${json["commit"].text?.take(10)}, exported ${json["generatedAt"].text}")
        if (appZip.exists()) println("           ${appZip.relativeTo(root)} (${appZip.length() / 1024} KB)")
    } else {
        println("  app      not exported yet (export)")
    }
    state("publishUrl")?.let { println("  publish  $it, last ${state("lastPublish")}") }
        ?: println("  publish  never (export --publish)")
    val designs = designDir.listFiles { f -> f.extension == "png" }.orEmpty()
    if (designs.isNotEmpty()) {
        println("  design   ${designs.size} screens, imported ${state("lastImport")} from ${state("lastImportSource")}")
    } else {
        println("  design   nothing imported yet (import <folder-or-zip>)")
    }
    val compares = compareDir.listFiles { f -> f.extension == "png" }.orEmpty()
    if (compares.isNotEmpty()) println("  compare  ${compares.size} side-by-sides, see ${File(compareDir, "README.md").relativeTo(root)}")
}

// ── main ────────────────────────────────────────────────────────────────────

val argv = args.toList()
when (argv.firstOrNull()) {
    "export" -> export(publishToo = "--publish" in argv, render = "--skip-render" !in argv)
    "import" -> import(argv.getOrNull(1) ?: die("Usage: design_sync.main.kts import <folder-or-zip>"))
    "status" -> status()
    else -> {
        println(
            """
            Usage:
              ./scripts/design_sync.main.kts export [--publish] [--skip-render]
              ./scripts/design_sync.main.kts import <folder-or-zip>
              ./scripts/design_sync.main.kts status
            """.trimIndent(),
        )
        if (argv.isNotEmpty() && argv.first() !in listOf("-h", "--help", "help")) exitProcess(1)
    }
}
