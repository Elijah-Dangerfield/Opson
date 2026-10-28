plugins {
    id("opson.compose.multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.opson.libraries.navigation"

    // Compose's test rule launches a real ComponentActivity, which Robolectric can only find in
    // the merged manifest, and only this flag hands it one. Without it every test in the tier dies
    // at rule setup with "Unable to resolve activity for Intent", which reads like a broken
    // Robolectric rather than a missing build flag.
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.flowroutines)
            api(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }

        // The composition test tier: a real composition, driven and asserted on the host JVM.
        // `FloatingWindowHostTest` is the worked example, and docs/practices/testing.md says when
        // to reach for this layer and when not to.
        //
        // It lives in `androidUnitTest` rather than on a `jvm()` target for the reason
        // `:apps:integration` already gives: the Android variants already run on the host JVM.
        // A JVM target here would cascade `actual` stubs through every library that already
        // carries one, and add a third value to the two-case `Platform` enum that several
        // exhaustive `when`s read. That is a lot of shipped surface invented to serve a test.
        // `commonTest` is the wrong home too: it also compiles for iOS, where none of this
        // resolves.
        androidUnitTest.dependencies {
            // ObserveWithLifecycleTest drives flowroutines' composable here, the only composition tier.
            implementation(projects.libraries.flowroutines)
            implementation(compose.foundation)
            implementation(libs.androidx.compose.uiTest.junit4)
            // Declares the `ComponentActivity` the rule launches; without it the merged manifest
            // has no activity to resolve and every test dies at setup.
            implementation(libs.androidx.compose.uiTest.manifest)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.robolectric)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Left alone, Robolectric fetches the Android framework jar itself, from Maven, at test runtime,
// into ~/.m2, a directory no CI cache covers. That is a ~200MB download per run and a network call
// inside a test, which is how a tier earns a reputation for flaking. Resolve it through Gradle
// instead, stage it, and point Robolectric at the staged copy in offline mode. Offline is the
// half that makes it provable: with no staged jar the run fails naming the path it wanted rather
// than quietly downloading one.
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
    // Headroom, not a measured requirement: the tests here pass on Gradle's default 512m today. A
    // Robolectric sandbox per test class plus a live composition is the heaviest thing in the unit
    // test sweep, and an OOM in it reads as a flake rather than as the resource problem it is,
    // which is a much more expensive hour than this line.
    maxHeapSize = "2g"
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-android-all").get().asFile.absolutePath,
    )
}
