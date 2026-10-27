import com.opson.util.isStoreBuild

plugins {
    id("opson.application")
    id("co.touchlab.skie") version "0.10.12"
    alias(libs.plugins.sentryAndroid)
    alias(libs.plugins.baselineProfile)
    alias(libs.plugins.aboutLibraries)
}

/**
 * The open-source licences for Support › Credits, read from the app's own dependency graph.
 * Regenerate after changing dependencies, and commit the result:
 *
 *     ./gradlew :apps:compose:exportLibraryDefinitions
 *
 * What ships: the Android release classpath and the iOS source set's dependencies, merged.
 * Licences come from each artifact's POM, with the full text of each SPDX licence fetched once
 * per export. `aboutlibraries/libraries` adds what Gradle never sees: Sentry Cocoa, which Xcode
 * links through Swift Package Manager (keep its version with `project.pbxproj`).
 */
aboutLibraries {
    collect {
        configPath = file("aboutlibraries")
        filterVariants.addAll("release", "metadataIosMain")
    }
    export {
        outputFile = rootProject.file("libraries/resources/src/commonMain/composeResources/files/licenses.json")
        excludeFields.addAll("description", "developers", "funding", "organization", "scm", "generated")
        prettyPrint = true
    }
    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
        mergePlatformArtifacts = true
    }
}

android {
    namespace = "com.opson"
}

/**
 * Consume the committed Baseline Profile rather than regenerating it on every
 * release build — generation starts an emulator and walks the app, which is
 * minutes nobody wants in the release path.
 *
 * Regenerate deliberately when the app's shape changes:
 * `./gradlew :apps:compose:generateBaselineProfile`
 */
baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(projects.apps.baselineprofile)
}

/**
 * Sentry's Android Gradle plugin, for exactly one job: making obfuscated crash
 * reports readable.
 *
 * R8 renames methods, so from the first minified release every Sentry frame
 * arrives as `a.b.c`. Deobfuscating needs two things — the mapping file
 * uploaded, and a ProGuard UUID stamped into the build tying that mapping to
 * this APK. A hand-rolled `sentry-cli upload-proguard` step supplies only the
 * first: with no UUID to match against, the upload associates with nothing and
 * still reports success. Confirm this is working by unzipping the APK and
 * checking `assets/sentry-debug-meta.properties` for `io.sentry.ProguardUuids`,
 * not by trusting a green upload step.
 */
sentry {
    org.set(providers.environmentVariable("SENTRY_ORG"))
    projectName.set(providers.environmentVariable("SENTRY_PROJECT"))
    authToken.set(providers.environmentVariable("SENTRY_AUTH_TOKEN"))

    // This project already uses the Kotlin Multiplatform Sentry SDK.
    // Auto-installation would add `sentry-android` on top of it, and two SDKs
    // initialising in one process is not a thing to discover in production.
    autoInstallation { enabled.set(false) }

    // Always stamp the UUID: it is what makes a mapping associable at all, and
    // it costs nothing in a build without a token.
    includeProguardMapping.set(true)

    // Only upload when a token exists, so a contributor can still build a
    // release locally without one.
    autoUploadProguardMapping.set(
        providers.environmentVariable("SENTRY_AUTH_TOKEN").isPresent,
    )

    // No build-time telemetry to Sentry about our Gradle builds.
    telemetry.set(false)
}

kotlin {

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.work.runtime)
            implementation(compose.uiTooling)
        }

        commonMain.dependencies {
            // Project dependencies
            api(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.opson.impl)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.navigation.impl)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.review)
            implementation(projects.libraries.review.impl)
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.reminders.impl)
            implementation(projects.libraries.scanner)
            implementation(projects.libraries.scanner.impl)
            implementation(projects.libraries.speech)
            implementation(projects.libraries.speech.impl)
            implementation(projects.libraries.widgets)
            implementation(projects.libraries.widgets.impl)

            implementation(projects.libraries.storage)
            implementation(projects.libraries.storage.impl)
            implementation(projects.libraries.opson.storage)
            implementation(projects.libraries.config)
            implementation(projects.libraries.config.impl)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.networking.impl)
            implementation(projects.libraries.telemetry.impl)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.identity.impl)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.impl)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.foods.impl)
            implementation(projects.libraries.health)
            implementation(projects.libraries.health.impl)
            implementation(projects.libraries.progressphotos)
            implementation(projects.libraries.progressphotos.impl)

            implementation(projects.features.foods)
            implementation(projects.features.foods.impl)
            implementation(projects.features.home)
            implementation(projects.features.home.impl)
            implementation(projects.features.logger)
            implementation(projects.features.logger.impl)
            implementation(projects.features.onboarding)
            implementation(projects.features.onboarding.impl)
            implementation(projects.features.settings)
            implementation(projects.features.settings.impl)
            implementation(projects.features.today)
            implementation(projects.features.today.impl)
            implementation(projects.features.trends)
            implementation(projects.features.trends.impl)
            implementation(projects.features.upgrade)
            implementation(projects.features.upgrade.impl)

            // The QA menu, and with it More's Testing card, exists only off the store channel.
            // Both Android variants and the iOS framework follow the channel, not the build type.
            if (!isStoreBuild()) {
                implementation(projects.features.qa)
                implementation(projects.features.qa.impl)
            }

            implementation(libs.atomicfu)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
        }
    }
}
// The iOS test executable cannot link outside Xcode: the Kotlin Multiplatform
// Sentry SDK needs the Sentry framework, which only reaches the app through
// the Xcode project's Swift package. These tests are commonTest and already run
// on Android (testDebugUnitTest in CI), so only the iOS run is skipped, which
// is what stops `./gradlew build` failing on `ld: framework 'Sentry' not found`.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>()
    .matching { it.binary is org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable }
    .configureEach { enabled = false }
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>()
    .configureEach { enabled = false }
