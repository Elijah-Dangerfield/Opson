package com.opson.plugin

import com.android.build.gradle.LibraryExtension
import com.opson.ext.ConfigurationExtension
import com.opson.util.configureAndroid
import com.opson.util.configureKotlinMultiplatform
import com.opson.util.configureKotlinInject
import com.opson.util.enforceModuleBoundaries
import com.opson.util.libs
import com.opson.util.loadSupabaseMetadata
import com.opson.util.loadTelemetryMetadata
import com.opson.util.loadVersionMetadata
import com.opson.util.optInKotlinMarkers
import com.opson.util.VersionMetadata
import com.opson.util.writeCommonMetadata
import com.opson.util.writeSupabaseMetadata
import com.opson.util.writeTelemetryMetadata
import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `opson.kotlin.multiplatform`: a library with no Compose, such as `:libraries:core`
 * or `:libraries:networking`.
 *
 * Android and iOS targets, kotlinx.serialization, kotlin-inject, coroutines and
 * kotlin-test, and the module-boundary check. `:libraries:core` also gets the
 * generated `OpsonBuildConfig` (version, commit, Supabase and telemetry config).
 * Libraries with composables use `opson.compose.multiplatform`.
 */
class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
                apply(libs.plugins.kotlinSerialization.get().pluginId)
            }

            configureKotlinMultiplatform()
            configureKotlinInject()

            project.optInKotlinMarkers("kotlin.time.ExperimentalTime")
            project.optInKotlinMarkers("kotlin.uuid.ExperimentalUuidApi")

            extensions.configure<LibraryExtension> {
                configureAndroid()
            }

            (extensions.findByName("moduleConfig") as? ConfigurationExtension)
                ?: extensions.create("moduleConfig", ConfigurationExtension::class.java)

            if (path == ":libraries:core") {
                pluginManager.apply(libs.plugins.buildconfig.get().pluginId)
                configureSharedBuildConfig(loadVersionMetadata())
            }

            enforceModuleBoundaries()
        }
    }

    private fun Project.configureSharedBuildConfig(metadata: VersionMetadata) {
        val supabaseMetadata = loadSupabaseMetadata()
        extensions.configure(BuildConfigExtension::class.java) {
            packageName("com.opson.buildinfo")
            className("OpsonBuildConfig")
            useKotlinOutput {
                internalVisibility = false
            }
            writeCommonMetadata(metadata)
            writeSupabaseMetadata(supabaseMetadata)
            writeTelemetryMetadata(loadTelemetryMetadata())
        }
    }
}
