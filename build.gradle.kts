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
        // No output; up-to-date is driven purely by the inputs above.
        outputs.upToDateWhen { true }
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
}