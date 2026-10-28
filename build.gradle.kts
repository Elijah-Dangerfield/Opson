plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.sentryKmp) apply false
    alias(libs.plugins.sentryAndroid) apply false
    alias(libs.plugins.baselineProfile) apply false
    alias(libs.plugins.kotlinCocoapods) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.aboutLibraries) apply false
    // Declared here (version only) so the conditional block below can apply it.
    alias(libs.plugins.detekt) apply false
}

// One root-level detekt task lints every module's Kotlin source with the custom
// :detekt-rules ruleset, behind a baseline, and hangs off `check`. A single
// source-scanning task (detekt's "plain" task) sidesteps per-module KMP source-set
// wiring. Skipped in the server-only Docker build, where :detekt-rules isn't on the
// build graph (it's a client/dev module) and linting isn't wanted anyway.
if (System.getProperty("serverOnly") != "true") {
    apply(plugin = "dev.detekt")
    apply(plugin = "base")

    dependencies {
        "detektPlugins"(project(":detekt-rules"))
    }

    configure<dev.detekt.gradle.extensions.DetektExtension> {
        parallel.set(true)
        buildUponDefaultConfig.set(false)
        // Run ONLY the custom `opson` ruleset — not detekt's hundreds of
        // built-in rules, which would flag the whole codebase. New rules are
        // added in :detekt-rules, not by turning defaults on.
        disableDefaultRuleSets.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline.set(rootProject.file("config/detekt/baseline.xml"))
        source.setFrom(subprojects.map { it.projectDir.resolve("src") })
    }

    tasks.named("check") { dependsOn("detekt") }

    // Backslash escapes in composeResources string XML are literal characters.
    //
    // In Android's `res/values/strings.xml` an apostrophe MUST be escaped or aapt
    // fails the build, so anyone who has written Android strings types `\'`
    // without thinking. Compose Multiplatform's file looks identical and behaves
    // differently: `XmlValuesConverterTask` reads the DOM's `textContent` and
    // writes it into the `.cvr` verbatim, with no unescape step anywhere in the
    // resources package. The backslash is just another character, and the app
    // renders it. A downstream app shipped `IT\'S ALIVE` to TestFlight this way,
    // and it was found by reading a photograph of a phone.
    //
    // Nothing else catches this. Not the compiler, not detekt, not a screenshot
    // test, not a reviewer skimming the diff — because `\'` is exactly what a
    // correct Android string looks like.
    //
    // It has to be a Gradle task rather than a detekt rule: `VerifyStrings` runs
    // on Kotlin PSI and cannot see XML, and detekt has no XML frontend to give it
    // one. Hangs off `check` so it fails CI rather than printing a warning nobody
    // reads.
    //
    // Every backslash is flagged, not just `\'`. The converter passes all of them
    // through, so `\"`, `\n` and `\t` are equally literal — a real newline or a
    // Unicode escape is what the author actually meant.
    val composeResourceStringXml = fileTree(rootDir) {
        include("**/src/**/composeResources/values*/**/*.xml")
        // build/ holds generated copies; .claude/ holds agent worktrees, which
        // are whole second checkouts and would double every finding.
        exclude("**/build/**", "**/.git/**", "**/.claude/**", "**/.gradle/**")
    }

    val verifyComposeResourceStrings = tasks.register("verifyComposeResourceStrings") {
        group = "verification"
        description = "Fails on backslash escapes in composeResources string XML, which render literally."
        val files = composeResourceStringXml
        // Captured at configuration time. Touching `rootDir` (a Project member)
        // inside doLast would drag the whole Project into the config cache,
        // which it cannot serialize.
        val root = rootDir
        inputs.files(files).withPropertyName("composeResourceStringXml")
            .skipWhenEmpty()
        // No outputs, so Gradle runs it on every check. It reads a few XML files.
        doLast {
            // Body of any <string>/<item> element. DOTALL so a wrapped string is
            // still one body rather than silently unchecked.
            val element = Regex("""<(string|item)\b[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            val problems = mutableListOf<String>()
            files.forEach { file ->
                val text = file.readText()
                element.findAll(text).forEach { match ->
                    val body = match.groupValues[2]
                    if (!body.contains('\\')) return@forEach
                    val line = text.take(match.range.first).count { it == '\n' } + 1
                    problems += "${file.relativeTo(root)}:$line  ${body.trim()}"
                }
            }
            if (problems.isNotEmpty()) {
                throw org.gradle.api.GradleException(
                    buildString {
                        appendLine("Backslash escape in composeResources string XML — it will render literally.")
                        appendLine("Android needs \\' here; Compose Multiplatform does not and does not strip it.")
                        appendLine("Write the apostrophe directly, or use a real newline / &#..; entity.")
                        appendLine()
                        problems.forEach { appendLine("  $it") }
                    },
                )
            }
        }
    }

    tasks.named("check") { dependsOn(verifyComposeResourceStrings) }

    // The baseline-profile journey and the R8 smoke test find screens by their
    // copy (`const val`s in BenchmarkJourney.kt). They run only on an emulator,
    // monthly or by hand, so a copy change broke them unseen: the Foods tab
    // dropped the placeholder line the journey waited for, and nothing said so.
    // This fails `check` the day a string they wait for leaves the app.
    val benchmarkJourney = file("apps/baselineprofile/src/main/kotlin/com/opson/baselineprofile/BenchmarkJourney.kt")
    val verifyBenchmarkJourneyCopy = tasks.register("verifyBenchmarkJourneyCopy") {
        group = "verification"
        description = "Fails when a string the baseline-profile journey waits for is not in the app's strings."
        val journey = benchmarkJourney
        val strings = composeResourceStringXml
        inputs.file(journey).withPropertyName("benchmarkJourney")
        inputs.files(strings).withPropertyName("composeResourceStringXml")
        doLast {
            val copy = strings.files.joinToString("\n") { it.readText() }
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&")
            val missing = Regex("""const val (\w+) = "([^"]+)"""").findAll(journey.readText())
                .map { it.groupValues[1] to it.groupValues[2] }
                // The package name and the instrumentation argument names are not copy.
                .filter { (name, text) -> name != "PACKAGE" && !name.endsWith("_ARGUMENT") && text !in copy }
                .toList()
            if (missing.isNotEmpty()) {
                throw org.gradle.api.GradleException(
                    buildString {
                        appendLine("BenchmarkJourney waits for copy the app no longer has, so the profile journey")
                        appendLine("and the R8 smoke test would fail on the emulator. Point them at text the screen still shows:")
                        missing.forEach { (name, text) -> appendLine("  $name = \"$text\"") }
                    },
                )
            }
        }
    }

    tasks.named("check") { dependsOn(verifyBenchmarkJourneyCopy) }

    // verifyReleaseEnvironment (ApplicationConventionPlugin) stops a release build that
    // would sign in to the wrong Supabase project or ship without crash reporting, but only
    // because the release Android and iOS builds depend on it. build-logic's tests cover what
    // it checks, not that it runs, so a refactor or an AGP rename of preReleaseBuild could
    // drop it with every test green. This fails `check` when the tasks release.yml, beta.yml
    // and the Fastfile reach stop depending on it.
    val verifyReleaseEnvironmentWired = tasks.register("verifyReleaseEnvironmentWired") {
        group = "verification"
        description = "Fails when a release build would no longer run verifyReleaseEnvironment."
        val app = project(":apps:compose")
        val verify = "${app.path}:verifyReleaseEnvironment"
        // Resolved here, at configuration: the configuration cache keeps the result, and
        // doLast must not touch the project.
        val unwired = listOf("preReleaseBuild", "linkReleaseFrameworkIosArm64").mapNotNull { name ->
            val task = app.tasks.findByName(name) ?: return@mapNotNull "${app.path}:$name does not exist"
            val dependencies = task.taskDependencies.getDependencies(task).map { it.path }
            if (verify in dependencies) null else "${task.path} does not depend on $verify"
        }
        doLast {
            if (unwired.isNotEmpty()) {
                throw org.gradle.api.GradleException(
                    buildString {
                        appendLine("A release build would skip the release environment check:")
                        unwired.forEach { appendLine("  $it") }
                        appendLine("See verifyReleaseEnvironment() in build-logic's ApplicationConventionPlugin.")
                    },
                )
            }
        }
    }

    tasks.named("check") { dependsOn(verifyReleaseEnvironmentWired) }

    // build-logic is an included build, so no sweep over this build's modules reaches its
    // tests. They decide which builds leave the QA menu out (isStoreChannel).
    tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":test")) }
}
// Kotlin/Native links run inside the Gradle daemon and are the heaviest step in
// the build, several GB each for a release framework. `./gradlew build` starts
// every iOS link it can in parallel, and that ran the daemon out of heap on
// `linkReleaseFrameworkIosArm64`, which links fine on its own. One link at a
// time costs a full build some minutes and costs Xcode and CI nothing, since
// those link a single framework per invocation anyway.
abstract class NativeLinkLimit : BuildService<BuildServiceParameters.None>

val nativeLinkLimit = gradle.sharedServices.registerIfAbsent("nativeLinkLimit", NativeLinkLimit::class) {
    maxParallelUsages.set(1)
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
        usesService(nativeLinkLimit)
    }
}
