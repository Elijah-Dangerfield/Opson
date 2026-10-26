plugins {
    id("opson.compose.multiplatform")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.opson.apps.screenshots"

    // The compose test rule launches a real ComponentActivity, which Robolectric only finds in the
    // merged manifest, and the compose resources (fonts, strings, drawables) the previews load
    // only reach the test as merged assets. Both need this flag.
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Preview screenshots: every `@Preview` in the project rendered to a PNG on the host JVM.
// `PreviewScreenshotTest` is the whole tier; docs/practices/design-sync.md is the reference.
//
// An `:apps:*` module because it has to see every feature and library at once, impls included,
// and only `:apps:*` may depend on an impl. `commonMain` is empty: nothing ships from here and the
// iOS target has nothing to link.
//
// The dependency list is computed, not written out, so a new feature module's previews are picked
// up the day it is added. Every Gradle project under `:features:` and `:libraries:` is a
// multiplatform module with an Android target, so all of them resolve here. The application module
// is the one gap: an Android library cannot depend on `com.android.application`, so previews that
// live in `:apps:compose` are not rendered. Keep screens and their previews in feature modules.
val previewModules = rootProject.subprojects
    .filter { it.path.startsWith(":features:") || it.path.startsWith(":libraries:") }
    .filter { it.buildFile.exists() }
    .map { it.path }
    .sorted()

kotlin {
    sourceSets {
        androidUnitTest.dependencies {
            previewModules.forEach { implementation(project(it)) }

            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.androidx.compose.uiTest.junit4)
            implementation(libs.androidx.compose.uiTest.manifest)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junitRule)
            implementation(libs.composablePreviewScanner.common)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlin.testJunit)
        }
    }
}

// Where the PNGs land. By default under build/, so a render never shows up in `git status` and
// the repo does not grow by a few MB every time a screen changes. The cost is that `clean` wipes
// the baseline that `verifyRoborazziDebug` compares against: record, change code, verify, all
// without a clean in between.
//
// A project that wants before/after diffs across machines or in CI can commit a baseline instead:
// set `screenshots.commitBaseline=true` in gradle.properties and the images go to
// apps/screenshots/baseline/, which is not ignored. Diff output always stays under build/ so it is
// never committed by accident.
val commitBaseline = providers.gradleProperty("screenshots.commitBaseline").orNull == "true"
val screenshotDir = if (commitBaseline) {
    layout.projectDirectory.dir("baseline")
} else {
    layout.buildDirectory.dir("outputs/roborazzi").get()
}
val resultsDir = layout.buildDirectory.dir("outputs/screenshot-results").get()

roborazzi {
    outputDir.set(screenshotDir)
    compare {
        outputDir.set(layout.buildDirectory.dir("outputs/roborazzi-compare"))
    }
}

// Same reasoning as libraries/navigation/build.gradle.kts: resolve Robolectric's framework jar
// through Gradle and run offline against the staged copy, rather than letting Robolectric fetch
// ~200MB into ~/.m2 at test time.
val robolectricAndroidAll: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    robolectricAndroidAll(libs.robolectric.androidAll)
}

val stageRobolectricJars = tasks.register<Sync>("stageRobolectricJars") {
    from(robolectricAndroidAll)
    into(layout.buildDirectory.dir("robolectric-android-all"))
}

// Rendering every preview is the slowest thing in the unit-test sweep and it never fails on its
// own (a preview that cannot render is recorded, not thrown), so a plain `testDebugUnitTest` run
// skips it. It runs when a Roborazzi task asks for it: record, verify, compare, or the
// `-Proborazzi.test.*` properties those tasks are shorthand for.
val roborazziRequested =
    gradle.startParameter.taskNames.any { it.contains("Roborazzi") } ||
        listOf("record", "verify", "compare").any {
            providers.gradleProperty("roborazzi.test.$it").orNull == "true"
        }

tasks.withType<Test>().configureEach {
    // Locals, not the script-level vals: a task action that captures a script property drags the
    // whole script object into the configuration cache, which cannot serialize it.
    val requested = roborazziRequested
    val results = resultsDir.asFile
    dependsOn(stageRobolectricJars)
    onlyIf("a Roborazzi task was requested") { requested }
    // One result file per preview, so a preview deleted since the last run must not leave its
    // stale result behind for the export script to pick up.
    doFirst { results.deleteRecursively() }
    maxHeapSize = "4g"
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-android-all").get().asFile.absolutePath,
    )
    // The test resolves its own output paths from these, so the PNG names are exactly the screen
    // ids and nothing depends on the test's working directory.
    systemProperty("screenshots.outputDir", screenshotDir.asFile.absolutePath)
    systemProperty("screenshots.resultsDir", resultsDir.asFile.absolutePath)
    systemProperty("screenshots.repoRoot", rootProject.layout.projectDirectory.asFile.absolutePath)
    // A render is not up to date just because the code is: the export script relies on every run
    // actually rendering, and Roborazzi's verify needs to run against the current baseline.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
