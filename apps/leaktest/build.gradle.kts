plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.apps.leaktest"

    // The compose test rule launches a real ComponentActivity, which Robolectric only finds in the
    // merged manifest, and the compose resources (strings, fonts) the screens load only reach the
    // test as merged assets. Both need this flag.
    testOptions.unitTests.isIncludeAndroidResources = true
}

// BlindModeLeakTest: every screen and sheet rendered under Robolectric in every mode that hides
// calories, scanned for a calorie amount or an "N left". The handoff's acceptance test, and the
// plan is docs/plans/screens/blind-leak-test.md.
//
// An `:apps:*` module because it has to see every feature's impl, and only `:apps:*` may depend on
// an impl. Not `:apps:integration`, whose other tests need Docker, and not `:apps:screenshots`,
// which a plain `testDebugUnitTest` skips: this one has to run in the normal sweep so CI runs it.
// `commonMain` is empty, nothing ships from here.
kotlin {
    sourceSets {
        androidUnitTest.dependencies {
            implementation(projects.features.foods.impl)
            implementation(projects.features.logger.impl)
            implementation(projects.features.onboarding.impl)
            implementation(projects.features.settings.impl)
            implementation(projects.features.today.impl)
            implementation(projects.features.trends.impl)

            implementation(projects.libraries.core)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.ui)

            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.compose.uiTest.junit4)
            // Declares the `ComponentActivity` the rule launches; without it the merged manifest
            // has no activity to resolve and every test dies at setup.
            implementation(libs.androidx.compose.uiTest.manifest)
            implementation(libs.robolectric)
            implementation(libs.kotlin.testJunit)
        }
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

tasks.withType<Test>().configureEach {
    dependsOn(stageRobolectricJars)
    // Every screen in the app composed a few hundred times in one sandbox.
    maxHeapSize = "2g"
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-android-all").get().asFile.absolutePath,
    )

    // SurfacesCoverageTest reads the feature sources for `XLeakSurfaces` objects, so a feature
    // that lists surfaces this module never joins fails the build. A file read at test runtime is
    // invisible to Gradle's up-to-date check unless it is declared (see apps/integration).
    val repoRoot = rootProject.layout.projectDirectory
    systemProperty("leaktest.repoRoot", repoRoot.asFile.absolutePath)
    inputs.files(
        rootProject.fileTree(repoRoot) {
            include("features/**/*LeakSurfaces.kt")
            exclude("**/build/**", ".claude/**")
        },
    ).withPropertyName("leakSurfaceLists")
}
