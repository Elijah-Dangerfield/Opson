plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.networking.impl"
}

// WiretapKMP variant selection for iOS. Android uses the AGP debug/release
// split (see the `dependencies { }` block below); iOS has no such split at
// the Gradle dependency level, so we pick the real vs. noop artifact from a
// property. Defaults to the real inspector so it works out of the box in
// local iOS dev builds — pass `-Popson.wiretap.ios=false` to link the
// zero-overhead noop instead.
//
// Store builds flip it via the OPSON_WIRETAP_IOS env var (set by the
// beta / release pipelines and the Fastfile) rather than -P: the release
// framework is linked by TWO Gradle invocations — the CI pre-build step AND
// the xcodebuild-driven embedAndSign — and a -P flag on only one of them
// makes the configurations differ, silently re-linking the framework with
// Wiretap back in. An env var is inherited by both. Installation + launch
// are also gated on `BuildInfo.isDebug` at runtime, so a release build never
// activates the inspector either way.
val wiretapIosEnabled =
    (providers.environmentVariable("OPSON_WIRETAP_IOS").orNull
        ?: providers.gradleProperty("opson.wiretap.ios").orNull
        ?: "true").toBoolean()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.networking)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Wiretap's Ktor plugin + launcher API, for compilation only.
            // Each platform source set below supplies the real runtime
            // artifact (Android via debug/releaseImplementation, iOS via the
            // property above). The real and noop variants share an identical
            // API surface, so common code compiles against either.
            compileOnly(libs.wiretap.ktor)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.networking)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(if (wiretapIosEnabled) libs.wiretap.ktor else libs.wiretap.ktor.noop)
        }
    }
}

// Android swaps the real inspector (debug) for the noop (release) via the
// AGP variant configurations, keeping Wiretap entirely out of release APKs.
// These configurations only exist on the Android target, so they live in the
// top-level `dependencies { }` block rather than `androidMain.dependencies`.
dependencies {
    debugImplementation(libs.wiretap.ktor)
    releaseImplementation(libs.wiretap.ktor.noop)
}

tasks.matching { it.name.contains("kspCommonMainKotlinMetadata", ignoreCase = true) }
    .configureEach { enabled = false }

// The iOS test executable cannot link: Wiretap's shake detector is Swift, and
// a Kotlin/Native test binary does not pull in the Swift compatibility
// libraries an Xcode build would (`__swift_FORCE_LOAD_$_swiftCompatibility56`).
// These tests are commonTest and already run on Android in CI, so only the iOS
// run is skipped.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>()
    .matching { it.binary is org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable }
    .configureEach { enabled = false }
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>()
    .configureEach { enabled = false }
