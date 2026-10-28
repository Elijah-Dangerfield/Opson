package com.opson.plugin

import com.android.build.gradle.LibraryExtension
import com.opson.ext.ConfigurationExtension
import com.opson.util.configureAndroid
import com.opson.util.configureComposeMetrics
import com.opson.util.configureComposeStability
import com.opson.util.configureKotlinInject
import com.opson.util.configureKotlinMultiplatform
import com.opson.util.enforceModuleBoundaries
import com.opson.util.libs
import com.opson.util.optInKotlinMarkers
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `opson.compose.multiplatform`: a library that exports `@Composable`s, such as
 * `:libraries:ui` or `:libraries:diary:ui`.
 *
 * Android and iOS targets, Compose Multiplatform and its compiler, kotlin-inject,
 * the Compose runtime for common tests, and the module-boundary check. Feature
 * modules use `opson.feature`, which applies this; plain Kotlin uses
 * `opson.kotlin.multiplatform`; the app uses `opson.application`.
 */
class ComposeMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            project.optInKotlinMarkers("kotlin.time.ExperimentalTime")
            project.optInKotlinMarkers("kotlin.uuid.ExperimentalUuidApi")

            configureKotlinMultiplatform()
            configureKotlinInject()
            configureComposeTestDependencies()
            configureComposeMetrics()
            configureComposeStability()

            extensions.configure<LibraryExtension> {
                configureAndroid()
            }

            if (extensions.findByName("moduleConfig") == null) {
                extensions.create("moduleConfig", ConfigurationExtension::class.java)
            }

            enforceModuleBoundaries()
        }
    }

    private fun Project.configureComposeTestDependencies() {
        // The Compose compiler runs over test sources too and needs the runtime
        // there. From the catalog, so it moves with the Compose plugin.
        dependencies {
            add("commonTestImplementation", libs.compose.runtime)
        }
    }
}
