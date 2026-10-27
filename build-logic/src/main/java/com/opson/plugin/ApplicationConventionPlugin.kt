package com.opson.plugin

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.opson.ext.ConfigurationExtension
import com.opson.util.SharedConstants
import com.opson.util.configureAndroid
import com.opson.util.configureComposeMetrics
import com.opson.util.configureKotlinInject
import com.opson.util.configureKotlinMultiplatform
import com.opson.util.configureReleaseSigning
import com.opson.util.enforceModuleBoundaries
import com.opson.util.libs
import com.opson.util.loadSupabaseMetadata
import com.opson.util.loadTelemetryMetadata
import com.opson.util.releaseProblems
import com.opson.util.releaseWarnings
import com.opson.util.verifyGitHooksInstalled
import com.opson.util.loadVersionMetadata
import com.opson.util.optInKotlinMarkers
import com.opson.util.VersionMetadata
import com.opson.util.writeCommonMetadata
import com.opson.util.writeSupabaseMetadata
import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

/**
 * `opson.application`: `:apps:compose` only, the app on both platforms.
 *
 * The Android application (applicationId and version from `versions.properties`,
 * R8 and release signing), the static `ComposeApp` framework Xcode links, Compose,
 * kotlin-inject, the generated `AppBuildConfig`, and the git hooks check.
 */
class ApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val versionMetadata = loadVersionMetadata()
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.application")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply(libs.plugins.kotlinSerialization.get().pluginId)
                apply(libs.plugins.buildconfig.get().pluginId)
            }

            configureComposeMetrics()

            project.optInKotlinMarkers("kotlin.time.ExperimentalTime")
            project.optInKotlinMarkers("kotlin.uuid.ExperimentalUuidApi")

            configureKotlinMultiplatform {
                binaries.framework {
                    baseName = "ComposeApp"
                    isStatic = true
                    binaryOption("bundleId", "com.opson")
                    export(project(":libraries:core"))
                }
            }
            configureKotlinInject()

            extensions.configure<ApplicationExtension> {
                configureAndroid()

                defaultConfig {
                    applicationId = versionMetadata.applicationId
                    targetSdk = SharedConstants.targetSdk
                    versionCode = versionMetadata.versionCode
                    versionName = versionMetadata.versionName
                }

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }

                val releaseSigning = configureReleaseSigning(this)

                buildTypes {
                    debug {
                        applicationIdSuffix = ".debug"
                    }
                    release {
                        // R8 on. Play flags an app under 25% obfuscation as
                        // below its threshold with a Feb 2027 deadline, and the
                        // size and startup wins come with it. The keep rules in
                        // apps/compose/proguard-rules.pro are what make this
                        // survive: R8 breaks whatever is resolved by name at
                        // runtime, and every failure mode is at runtime, so a
                        // release build that merely compiles proves nothing.
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
                    }
                }
            }

            if (extensions.findByName("moduleConfig") == null) {
                extensions.create("moduleConfig", ConfigurationExtension::class.java)
            }
            configureAppBuildConfig(versionMetadata)

            verifyGitHooksInstalled()
            enforceModuleBoundaries()
            verifyReleaseEnvironment()
        }
    }

    /**
     * `verifyReleaseEnvironment`: fails a build that would ship pointing at the wrong
     * place (a placeholder or dev Supabase project, no Sentry DSN, half-set Grafana) and
     * warns about telemetry that is off. Every non-debug Android variant and every
     * release iOS link depends on it, because those are the builds that read the prod
     * values (`SupabaseInfo` in :libraries:core keys on `BuildInfo.isDebug`), so
     * release.yml, beta.yml, the Fastfile and scripts/beta_ios.sh all run it.
     */
    private fun Project.verifyReleaseEnvironment() {
        val telemetry = loadTelemetryMetadata()
        val problems = loadSupabaseMetadata().releaseProblems() + telemetry.releaseProblems()
        val warnings = telemetry.releaseWarnings()
        // GitHub turns a line starting ::warning:: into an annotation on the run.
        val warningPrefix = if (System.getenv("GITHUB_ACTIONS") == "true") "::warning::" else ""
        val verify = tasks.register("verifyReleaseEnvironment") {
            group = "verification"
            description = "Fails if a release build would sign in to the wrong Supabase project or ship without crash reporting."
            doLast {
                warnings.forEach { logger.warn("$warningPrefix$it") }
                if (problems.isNotEmpty()) {
                    throw GradleException(
                        "This release build is misconfigured:\n" + problems.joinToString("\n") { "  - $it" },
                    )
                }
            }
        }

        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                if (variant.buildType == "debug") return@onVariants
                val preBuild = "pre${variant.name.replaceFirstChar(Char::titlecase)}Build"
                tasks.matching { it.name == preBuild }.configureEach { dependsOn(verify) }
            }
        }
        tasks.withType<KotlinNativeLink>()
            .matching { it.binary.buildType == NativeBuildType.RELEASE }
            .configureEach { dependsOn(verify) }
    }

    private fun Project.configureAppBuildConfig(metadata: VersionMetadata) {
        val supabaseMetadata = loadSupabaseMetadata()
        extensions.configure(BuildConfigExtension::class.java) {
            packageName("${metadata.applicationId}.appconfig")
            className("AppBuildConfig")
            useKotlinOutput {
                internalVisibility = false
            }
            writeCommonMetadata(metadata)
            writeSupabaseMetadata(supabaseMetadata)
        }
    }
}