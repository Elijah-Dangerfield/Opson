package com.opson.plugin

import com.opson.ext.ConfigurationExtension
import com.opson.util.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `opson.feature`: a feature module, its api or its impl (`:features:today`,
 * `:features:today:impl`).
 *
 * Everything `opson.compose.multiplatform` gives, plus kotlinx.serialization,
 * the lifecycle ViewModel and runtime-compose artifacts, and the three
 * libraries every screen uses: `:libraries:ui`, `:libraries:resources` and
 * `:libraries:navigation`.
 */
class FeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("opson.compose.multiplatform")
                apply(libs.plugins.kotlinSerialization.get().pluginId)
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.apply {
                    commonMain.dependencies {
                        implementation(libs.androidx.lifecycle.viewmodelCompose)
                        implementation(libs.androidx.lifecycle.runtimeCompose)
                        implementation(libs.kotlinx.serialization.json)

                        implementation(project(":libraries:ui"))
                        implementation(project(":libraries:resources"))
                        implementation(project(":libraries:navigation"))
                    }
                }
            }

            if (extensions.findByName("moduleConfig") == null) {
                extensions.create("moduleConfig", ConfigurationExtension::class.java)
            }
        }
    }
}
